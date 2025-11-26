package com.comet.opik.infrastructure.audit;

import com.comet.opik.api.AuditLog;
import com.comet.opik.infrastructure.auth.RequestContext;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.time.Instant;
import java.util.UUID;

/**
 * 审计日志AOP拦截器
 * 拦截标记了@Auditable注解的方法，自动记录审计日志
 *
 * 特性:
 * - 异步记录不阻塞主流程
 * - 记录操作成功和失败
 * - 记录操作耗时
 * - 捕获异常信息
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @jakarta.inject.Inject)
public class AuditInterceptor implements MethodInterceptor {

    private final @NonNull Provider<AuditLogService> auditLogServiceProvider;
    private final @NonNull Provider<RequestContext> requestContextProvider;
    private final @NonNull Provider<HttpServletRequest> httpServletRequestProvider;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取@Auditable注解
        Auditable auditable = invocation.getMethod().getAnnotation(Auditable.class);
        if (auditable == null) {
            // 如果方法上没有注解，检查类上的注解
            auditable = invocation.getThis().getClass().getAnnotation(Auditable.class);
        }

        if (auditable == null) {
            // 没有@Auditable注解，直接执行方法
            return invocation.proceed();
        }

        // Get AuditLogService instance
        AuditLogService auditLogService = auditLogServiceProvider.get();

        // 记录开始时间
        long startTime = System.currentTimeMillis();
        Instant timestamp = Instant.now();

        // 构建审计日志（操作前）
        AuditLog.AuditLogBuilder logBuilder = buildAuditLogBase(auditable, timestamp);

        Object result = null;
        Throwable error = null;

        try {
            // 执行原方法
            result = invocation.proceed();

            // 操作成功
            logBuilder.status(AuditStatus.SUCCESS);

            // 如果返回值包含ID，记录为resourceId
            if (result != null) {
                extractResourceInfo(result, logBuilder, auditable);
            }

        } catch (Throwable t) {
            // 操作失败
            error = t;
            logBuilder.status(AuditStatus.FAILURE)
                    .errorMessage(t.getMessage());

            log.debug("Audit log captured exception for action='{}': {}",
                    auditable.action(), t.getMessage());
        } finally {
            // 记录操作耗时
            long duration = System.currentTimeMillis() - startTime;
            logBuilder.durationMs((int) duration);

            // 构建并记录审计日志
            AuditLog auditLog = logBuilder.build();

            if (auditable.async()) {
                // 异步记录
                auditLogService.log(auditLog);
            } else {
                // 同步记录
                auditLogService.logSync(auditLog);
            }
        }

        // 如果有异常，重新抛出
        if (error != null) {
            throw error;
        }

        return result;
    }

    /**
     * 构建审计日志基础信息
     */
    private AuditLog.AuditLogBuilder buildAuditLogBase(Auditable auditable, Instant timestamp) {
        RequestContext context = requestContextProvider.get();
        HttpServletRequest request = httpServletRequestProvider.get();

        return AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(timestamp)
                .createdAt(Instant.now())
                .createdBy("system")
                // 从RequestContext获取用户和工作空间信息
                .userId(context.getUserId())
                .username(context.getUserName())
                .workspaceId(context.getWorkspaceId())
                // 从注解获取操作信息
                .action(auditable.action())
                .resourceType(auditable.resourceType())
                .operation(auditable.operation())
                // 从HttpServletRequest获取请求信息
                .ipAddress(getClientIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestPath(request.getRequestURI())
                .requestMethod(request.getMethod());
    }

    /**
     * 从返回值提取资源信息
     */
    private void extractResourceInfo(Object result, AuditLog.AuditLogBuilder logBuilder, Auditable auditable) {
        try {
            // 尝试通过反射获取ID
            var idMethod = result.getClass().getMethod("id");
            Object id = idMethod.invoke(result);
            if (id != null) {
                logBuilder.resourceId(id.toString());
            }

            // 尝试获取名称
            try {
                var nameMethod = result.getClass().getMethod("name");
                Object name = nameMethod.invoke(result);
                if (name != null) {
                    logBuilder.resourceName(name.toString());
                }
            } catch (NoSuchMethodException ignored) {
                // name方法不存在，忽略
            }

        } catch (Exception e) {
            log.debug("Failed to extract resource info from result: {}", e.getMessage());
        }
    }

    /**
     * 获取客户端IP地址（支持代理）
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader != null && !xForwardedForHeader.isEmpty()) {
            return xForwardedForHeader.split(",")[0].trim();
        }
        String xRealIpHeader = request.getHeader("X-Real-IP");
        if (xRealIpHeader != null && !xRealIpHeader.isEmpty()) {
            return xRealIpHeader;
        }
        return request.getRemoteAddr();
    }
}
