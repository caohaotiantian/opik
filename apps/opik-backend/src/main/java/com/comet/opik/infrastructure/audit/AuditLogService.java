package com.comet.opik.infrastructure.audit;

import com.comet.opik.api.AuditLog;
import com.comet.opik.infrastructure.auth.RequestContext;
import com.comet.opik.infrastructure.db.TransactionTemplateAsync;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 审计日志服务
 * 提供异步批量写入ClickHouse的功能
 *
 * 特性:
 * - 批量写入（默认100条/批）
 * - 异步处理不阻塞主流程
 * - 定时刷新（默认5秒）
 * - 优雅关闭
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @jakarta.inject.Inject)
public class AuditLogService {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int FLUSH_INTERVAL_SECONDS = 5;
    private static final int QUEUE_CAPACITY = 10000;

    /**
     * ClickHouse DateTime64(9, 'UTC') 期望的格式: YYYY-MM-DD HH:MM:SS.NNNNNNNNN
     * 注意: 使用空格分隔日期和时间，不使用 'T'；不带 'Z' 后缀
     */
    private static final DateTimeFormatter CLICKHOUSE_DATETIME64_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS")
            .withZone(ZoneOffset.UTC);

    /**
     * 空 UUID 用于 FixedString(36) 列的默认值
     * ClickHouse FixedString 需要固定长度的字符串
     */
    private static final String EMPTY_UUID = "00000000-0000-0000-0000-000000000000";

    private final @NonNull TransactionTemplateAsync transactionTemplate;
    private final @NonNull Provider<RequestContext> requestContextProvider;

    private final BlockingQueue<AuditLog> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 启动审计日志服务
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting AuditLogService");

            // 启动批量写入线程
            Schedulers.boundedElastic().schedule(this::processQueue);

            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            log.info("AuditLogService started successfully");
        }
    }

    /**
     * 记录审计日志（异步）
     *
     * @param auditLog 审计日志对象
     */
    public void log(@NonNull AuditLog auditLog) {
        if (!running.get()) {
            log.warn("AuditLogService is not running, starting it now");
            start();
        }

        boolean added = queue.offer(auditLog);
        if (!added) {
            log.error("Failed to add audit log to queue - queue is full. Log may be lost: action='{}'",
                    auditLog.action());
        }
    }

    /**
     * 记录审计日志（同步，用于关键操作）
     *
     * @param auditLog 审计日志对象
     */
    public void logSync(@NonNull AuditLog auditLog) {
        log.info("Writing audit log synchronously: action='{}'", auditLog.action());

        List<AuditLog> logs = List.of(auditLog);
        batchWrite(logs).block();
    }

    /**
     * 批量写入审计日志到ClickHouse
     *
     * @param logs 审计日志列表
     * @return Mono<Long> 写入的行数
     */
    public Mono<Long> batchWrite(@NonNull List<AuditLog> logs) {
        if (logs.isEmpty()) {
            log.debug("No audit logs to write");
            return Mono.just(0L);
        }

        log.info("Writing '{}' audit logs to ClickHouse", logs.size());

        return transactionTemplate.nonTransaction(connection -> {
            Statement statement = createBatchInsertStatement(connection, logs);

            return Flux.from(statement.execute())
                    .flatMap(Result::getRowsUpdated)
                    .reduce(0L, Long::sum)
                    .doOnSuccess(count -> log.info("Successfully wrote '{}' audit logs to ClickHouse", count))
                    .doOnError(error -> log.error("Failed to write audit logs to ClickHouse", error));
        });
    }

    /**
     * 处理队列中的审计日志
     */
    private void processQueue() {
        log.info("Audit log queue processor started");

        while (running.get()) {
            try {
                List<AuditLog> batch = new ArrayList<>(DEFAULT_BATCH_SIZE);

                // 等待第一条日志
                AuditLog first = queue.poll(FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
                if (first != null) {
                    batch.add(first);

                    // 批量获取更多日志
                    queue.drainTo(batch, DEFAULT_BATCH_SIZE - 1);

                    // 批量写入
                    batchWrite(batch)
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(
                                    count -> log.debug("Batch write completed: '{}' logs", count),
                                    error -> log.error("Batch write failed", error));
                }
            } catch (InterruptedException e) {
                log.warn("Queue processor interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing audit log queue", e);
            }
        }

        log.info("Audit log queue processor stopped");
    }

    /**
     * 优雅关闭服务
     */
    private void shutdown() {
        log.info("Shutting down AuditLogService");

        running.set(false);

        // 处理剩余的日志
        if (!queue.isEmpty()) {
            log.info("Flushing remaining '{}' audit logs", queue.size());

            List<AuditLog> remaining = new ArrayList<>();
            queue.drainTo(remaining);

            if (!remaining.isEmpty()) {
                try {
                    batchWrite(remaining).block();
                    log.info("Successfully flushed remaining audit logs");
                } catch (Exception e) {
                    log.error("Failed to flush remaining audit logs", e);
                }
            }
        }

        log.info("AuditLogService shutdown completed");
    }

    /**
     * 创建批量插入的SQL Statement
     *
     * 注意：列名必须与 ClickHouse 表结构完全匹配
     * 表结构定义在: liquibase/db-app-analytics/migrations/000046_add_audit_logs_table.sql
     *
     * ClickHouse 表列: id, timestamp, workspace_id, user_id, username, action,
     *                  resource_type, resource_id, resource_name, ip_address, user_agent,
     *                  request_id, result, error_message, metadata, created_at, created_by
     */
    private Statement createBatchInsertStatement(Connection connection, List<AuditLog> logs) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO audit_logs (
                    id, timestamp, workspace_id, user_id, username, action,
                    resource_type, resource_id, resource_name, ip_address, user_agent,
                    request_id, result, error_message, metadata, created_at, created_by
                ) VALUES
                """);

        // 添加VALUES占位符（使用命名参数）
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sql.append(",\n");
            sql.append("(:id").append(i)
                    .append(", :timestamp").append(i)
                    .append(", :workspace_id").append(i)
                    .append(", :user_id").append(i)
                    .append(", :username").append(i)
                    .append(", :action").append(i)
                    .append(", :resource_type").append(i)
                    .append(", :resource_id").append(i)
                    .append(", :resource_name").append(i)
                    .append(", :ip_address").append(i)
                    .append(", :user_agent").append(i)
                    .append(", :request_id").append(i)
                    .append(", :result").append(i)
                    .append(", :error_message").append(i)
                    .append(", :metadata").append(i)
                    .append(", :created_at").append(i)
                    .append(", :created_by").append(i)
                    .append(")");
        }

        Statement statement = connection.createStatement(sql.toString());

        // 绑定参数（使用命名参数）
        for (int i = 0; i < logs.size(); i++) {
            AuditLog auditLog = logs.get(i);
            Instant timestamp = auditLog.timestamp() != null ? auditLog.timestamp() : Instant.now();
            Instant createdAt = auditLog.createdAt() != null ? auditLog.createdAt() : Instant.now();

            // 将 status 映射到 result (ClickHouse Enum8 值: 'success', 'failure', 'error')
            String resultValue = mapStatusToResult(auditLog.status());

            // id 是 FixedString(36)，必须是 36 个字符的 UUID
            String id = auditLog.id() != null ? auditLog.id() : UUID.randomUUID().toString();
            // user_id 是 FixedString(36)，null 时使用空 UUID
            String userId = auditLog.userId() != null ? auditLog.userId() : EMPTY_UUID;

            statement.bind("id" + i, id);
            statement.bind("timestamp" + i, formatForClickHouse(timestamp));
            statement.bind("workspace_id" + i, auditLog.workspaceId() != null ? auditLog.workspaceId() : "");
            statement.bind("user_id" + i, userId);
            statement.bind("username" + i, auditLog.username() != null ? auditLog.username() : "");
            statement.bind("action" + i, auditLog.action() != null ? auditLog.action() : "UNKNOWN");
            statement.bind("resource_type" + i, auditLog.resourceType() != null ? auditLog.resourceType() : "UNKNOWN");
            statement.bind("resource_id" + i, auditLog.resourceId() != null ? auditLog.resourceId() : "");
            statement.bind("resource_name" + i, auditLog.resourceName() != null ? auditLog.resourceName() : "");
            statement.bind("ip_address" + i, auditLog.ipAddress() != null ? auditLog.ipAddress() : "");
            statement.bind("user_agent" + i, auditLog.userAgent() != null ? auditLog.userAgent() : "");
            statement.bind("request_id" + i, "");
            statement.bind("result" + i, resultValue);
            statement.bind("error_message" + i, auditLog.errorMessage() != null ? auditLog.errorMessage() : "");
            statement.bind("metadata" + i, auditLog.changes() != null ? auditLog.changes() : "");
            statement.bind("created_at" + i, formatForClickHouse(createdAt));
            statement.bind("created_by" + i, auditLog.createdBy() != null ? auditLog.createdBy() : "system");
        }

        return statement;
    }

    /**
     * 将 AuditStatus 映射到 ClickHouse result Enum8 值
     * ClickHouse Enum8: 'success' = 1, 'failure' = 2, 'error' = 3
     */
    private String mapStatusToResult(AuditStatus status) {
        if (status == null) {
            return "success";
        }
        return switch (status) {
            case SUCCESS -> "success";
            case FAILURE -> "failure";
            case PARTIAL -> "error"; // Map PARTIAL to error
            default -> "success";
        };
    }

    /**
     * 将 Instant 格式化为 ClickHouse DateTime64(9, 'UTC') 格式
     * 格式: YYYY-MM-DD HH:MM:SS.NNNNNNNNN
     */
    private String formatForClickHouse(Instant instant) {
        if (instant == null) {
            instant = Instant.now();
        }
        return CLICKHOUSE_DATETIME64_FORMATTER.format(instant);
    }

    /**
     * 查询审计日志
     *
     * @param request 查询请求
     * @return Mono<AuditLogPage> 审计日志分页结果
     */
    public Mono<com.comet.opik.api.AuditLogPage> query(@NonNull com.comet.opik.api.AuditLogQueryRequest request) {
        log.debug("Querying audit logs: workspaceId='{}', operation='{}', startTime='{}', endTime='{}'",
                request.workspaceId(), request.operation(), request.startTime(), request.endTime());

        return transactionTemplate.nonTransaction(connection -> {
            // Build query with named parameters for ClickHouse R2DBC
            StringBuilder sql = new StringBuilder("SELECT * FROM audit_logs WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            // Add filters using named parameters
            if (request.workspaceId() != null) {
                sql.append(" AND workspace_id = :workspaceId");
                params.put("workspaceId", request.workspaceId());
            }

            if (request.userId() != null) {
                sql.append(" AND user_id = :userId");
                params.put("userId", request.userId());
            }

            if (request.operation() != null) {
                // Use action field with LIKE for operation filtering
                sql.append(" AND lower(action) LIKE :actionPattern");
                params.put("actionPattern", "%" + request.operation().getCode().toLowerCase() + "%");
            }

            if (request.resourceType() != null) {
                sql.append(" AND resource_type = :resourceType");
                params.put("resourceType", request.resourceType());
            }

            if (request.resourceId() != null) {
                sql.append(" AND resource_id = :resourceId");
                params.put("resourceId", request.resourceId());
            }

            if (request.status() != null) {
                // Use result field for status filtering
                sql.append(" AND result = :result");
                params.put("result", request.status().getCode().toLowerCase());
            }

            if (request.startTime() != null) {
                sql.append(" AND timestamp >= :startTime");
                params.put("startTime", formatForClickHouse(request.startTime()));
            }

            if (request.endTime() != null) {
                sql.append(" AND timestamp <= :endTime");
                params.put("endTime", formatForClickHouse(request.endTime()));
            }

            // Add ordering and pagination
            String sortBy = request.sortBy() != null ? request.sortBy() : "timestamp";
            String sortDirection = request.sortDirection() != null ? request.sortDirection() : "DESC";
            sql.append(" ORDER BY ").append(sortBy).append(" ").append(sortDirection);

            int page = request.page() != null ? request.page() : 1;
            int size = request.size() != null ? request.size() : 20;
            int offset = (page - 1) * size;

            sql.append(" LIMIT :limitSize OFFSET :offsetVal");
            params.put("limitSize", size);
            params.put("offsetVal", offset);

            // Execute query with named parameters
            Statement statement = connection.createStatement(sql.toString());
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                statement.bind(entry.getKey(), entry.getValue());
            }

            return Flux.from(statement.execute())
                    .flatMap(result -> result.map((row, metadata) -> {
                        // Map row to AuditLog - matching actual ClickHouse table structure
                        String action = row.get("action", String.class);
                        String resultStr = row.get("result", String.class);
                        String timestampStr = row.get("timestamp", String.class);
                        String createdAtStr = row.get("created_at", String.class);

                        // Parse operation from action field
                        Operation op = parseOperationFromAction(action);

                        // Parse status from result field
                        AuditStatus auditStatus = parseStatusFromResult(resultStr);

                        return AuditLog.builder()
                                .id(row.get("id", String.class))
                                .workspaceId(row.get("workspace_id", String.class))
                                .userId(row.get("user_id", String.class))
                                .username(row.get("username", String.class))
                                .action(action)
                                .resourceType(row.get("resource_type", String.class))
                                .resourceId(row.get("resource_id", String.class))
                                .resourceName(row.get("resource_name", String.class))
                                .operation(op)
                                .status(auditStatus)
                                .ipAddress(row.get("ip_address", String.class))
                                .userAgent(row.get("user_agent", String.class))
                                .requestPath(null) // Not in current table
                                .requestMethod(null) // Not in current table
                                .changes(row.get("metadata", String.class)) // Use metadata field
                                .errorMessage(row.get("error_message", String.class))
                                .durationMs(null) // Not in current table
                                .timestamp(parseInstant(timestampStr))
                                .createdAt(parseInstant(createdAtStr))
                                .createdBy(row.get("created_by", String.class))
                                .build();
                    }))
                    .collectList()
                    .flatMap(logs -> {
                        // Get total count
                        return countLogs(connection, request).map(total -> {
                            int totalPages = (int) Math.ceil((double) total / size);
                            boolean isFirst = page == 0;
                            boolean isLast = page >= totalPages - 1;

                            return com.comet.opik.api.AuditLogPage.builder()
                                    .content(logs)
                                    .page(page)
                                    .size(size)
                                    .totalElements(total)
                                    .totalPages(totalPages)
                                    .first(isFirst)
                                    .last(isLast)
                                    .build();
                        });
                    });
        });
    }

    /**
     * Count audit logs matching the query
     */
    private Mono<Long> countLogs(Connection connection, com.comet.opik.api.AuditLogQueryRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as count FROM audit_logs WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        // Add same filters as query using named parameters
        if (request.workspaceId() != null) {
            sql.append(" AND workspace_id = :workspaceId");
            params.put("workspaceId", request.workspaceId());
        }

        if (request.userId() != null) {
            sql.append(" AND user_id = :userId");
            params.put("userId", request.userId());
        }

        if (request.operation() != null) {
            // Use action field with LIKE for operation filtering
            sql.append(" AND lower(action) LIKE :actionPattern");
            params.put("actionPattern", "%" + request.operation().getCode().toLowerCase() + "%");
        }

        if (request.resourceType() != null) {
            sql.append(" AND resource_type = :resourceType");
            params.put("resourceType", request.resourceType());
        }

        if (request.resourceId() != null) {
            sql.append(" AND resource_id = :resourceId");
            params.put("resourceId", request.resourceId());
        }

        if (request.status() != null) {
            // Use result field for status filtering
            sql.append(" AND result = :result");
            params.put("result", request.status().getCode().toLowerCase());
        }

        if (request.startTime() != null) {
            sql.append(" AND timestamp >= :startTime");
            params.put("startTime", formatForClickHouse(request.startTime()));
        }

        if (request.endTime() != null) {
            sql.append(" AND timestamp <= :endTime");
            params.put("endTime", formatForClickHouse(request.endTime()));
        }

        Statement statement = connection.createStatement(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            statement.bind(entry.getKey(), entry.getValue());
        }

        return Flux.from(statement.execute())
                .flatMap(result -> result.map((row, metadata) -> row.get("count", Long.class)))
                .next();
    }

    /**
     * 获取队列当前大小
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取服务运行状态
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Parse Operation from action string
     */
    private Operation parseOperationFromAction(String action) {
        if (action == null || action.isEmpty()) {
            return Operation.OTHER;
        }
        String upperAction = action.toUpperCase();
        if (upperAction.contains("CREATE")) return Operation.CREATE;
        if (upperAction.contains("UPDATE") || upperAction.contains("EDIT")) return Operation.UPDATE;
        if (upperAction.contains("DELETE") || upperAction.contains("REMOVE")) return Operation.DELETE;
        if (upperAction.contains("READ") || upperAction.contains("GET") || upperAction.contains("LIST"))
            return Operation.READ;
        if (upperAction.contains("LOGIN")) return Operation.LOGIN;
        if (upperAction.contains("LOGOUT")) return Operation.LOGOUT;
        if (upperAction.contains("EXECUTE") || upperAction.contains("RUN")) return Operation.EXECUTE;
        return Operation.OTHER;
    }

    /**
     * Parse AuditStatus from result string (ClickHouse Enum8 value)
     */
    private AuditStatus parseStatusFromResult(String result) {
        if (result == null || result.isEmpty()) {
            return AuditStatus.SUCCESS;
        }
        return switch (result.toLowerCase()) {
            case "success" -> AuditStatus.SUCCESS;
            case "failure", "error" -> AuditStatus.FAILURE; // Map both failure and error to FAILURE
            case "partial" -> AuditStatus.PARTIAL;
            default -> AuditStatus.SUCCESS;
        };
    }

    /**
     * Parse Instant from timestamp string
     */
    private Instant parseInstant(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return Instant.now();
        }
        try {
            // Handle ClickHouse DateTime64 format
            if (timestampStr.contains(" ")) {
                // Format: "2023-12-01 12:34:56.789"
                return Instant.parse(timestampStr.replace(" ", "T") + "Z");
            }
            return Instant.parse(timestampStr);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp '{}': {}", timestampStr, e.getMessage());
            return Instant.now();
        }
    }
}
