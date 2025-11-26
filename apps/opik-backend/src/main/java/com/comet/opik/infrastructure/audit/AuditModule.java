package com.comet.opik.infrastructure.audit;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 审计日志Guice模块
 * 配置审计日志服务和AOP拦截
 */
@Slf4j
public class AuditModule extends AbstractModule {

    @Override
    protected void configure() {
        log.info("Configuring AuditModule");

        // 绑定审计日志服务为单例
        bind(AuditLogService.class).in(Singleton.class);

        // 配置AOP拦截：拦截所有标记了@Auditable注解的方法
        // Note: Create AuditInterceptor manually with providers to avoid early instantiation
        bindInterceptor(
                Matchers.any(), // 匹配所有类
                Matchers.annotatedWith(Auditable.class), // 匹配标记了@Auditable的方法
                new AuditInterceptor(
                        getProvider(AuditLogService.class),
                        getProvider(com.comet.opik.infrastructure.auth.RequestContext.class),
                        getProvider(jakarta.servlet.http.HttpServletRequest.class)));

        log.info("AuditModule configured successfully");
    }
}
