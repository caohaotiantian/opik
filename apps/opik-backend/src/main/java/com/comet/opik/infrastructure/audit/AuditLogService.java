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
import java.util.ArrayList;
import java.util.List;
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
            return Mono.just(0L);
        }

        log.debug("Writing '{}' audit logs to ClickHouse", logs.size());

        return transactionTemplate.nonTransaction(connection -> {
            Statement statement = createBatchInsertStatement(connection, logs);

            return Flux.from(statement.execute())
                    .flatMap(Result::getRowsUpdated)
                    .reduce(0L, Long::sum)
                    .doOnSuccess(count -> log.debug("Successfully wrote '{}' audit logs to ClickHouse", count))
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
                                    count -> log.trace("Batch write completed: '{}' logs", count),
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
     */
    private Statement createBatchInsertStatement(Connection connection, List<AuditLog> logs) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO audit_logs (
                    id, workspace_id, user_id, username, action,
                    resource_type, resource_id, resource_name, operation, status,
                    ip_address, user_agent, request_path, request_method,
                    changes, error_message, duration_ms, timestamp, created_at, created_by
                ) VALUES
                """);

        // 添加VALUES占位符
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sql.append(",\n");
            sql.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        }

        Statement statement = connection.createStatement(sql.toString());

        // 绑定参数
        int paramIndex = 0;
        for (AuditLog log : logs) {
            Instant timestamp = log.timestamp() != null ? log.timestamp() : Instant.now();
            Instant createdAt = log.createdAt() != null ? log.createdAt() : Instant.now();

            statement.bind(paramIndex++, log.id() != null ? log.id() : UUID.randomUUID().toString())
                    .bind(paramIndex++, log.workspaceId() != null ? log.workspaceId() : "")
                    .bind(paramIndex++, log.userId() != null ? log.userId() : "")
                    .bind(paramIndex++, log.username() != null ? log.username() : "")
                    .bind(paramIndex++, log.action())
                    .bind(paramIndex++, log.resourceType())
                    .bind(paramIndex++, log.resourceId() != null ? log.resourceId() : "")
                    .bind(paramIndex++, log.resourceName() != null ? log.resourceName() : "")
                    .bind(paramIndex++, log.operation().getCode())
                    .bind(paramIndex++, log.status().getCode())
                    .bind(paramIndex++, log.ipAddress() != null ? log.ipAddress() : "")
                    .bind(paramIndex++, log.userAgent() != null ? log.userAgent() : "")
                    .bind(paramIndex++, log.requestPath() != null ? log.requestPath() : "")
                    .bind(paramIndex++, log.requestMethod() != null ? log.requestMethod() : "")
                    .bind(paramIndex++, log.changes() != null ? log.changes() : "")
                    .bind(paramIndex++, log.errorMessage() != null ? log.errorMessage() : "")
                    .bind(paramIndex++, log.durationMs() != null ? log.durationMs() : 0)
                    .bind(paramIndex++, timestamp.toString())
                    .bind(paramIndex++, createdAt.toString())
                    .bind(paramIndex++, log.createdBy() != null ? log.createdBy() : "system");
        }

        return statement;
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
}
