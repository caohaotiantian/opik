package com.comet.opik.infrastructure.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * 用于标记需要记录审计日志的方法
 *
 * <p>示例:
 * <pre>{@code
 * @Auditable(
 *   action = "Create Project",
 *   resourceType = "project",
 *   operation = Operation.CREATE
 * )
 * public Project createProject(ProjectCreateRequest request) {
 *     // ...
 * }
 * }</pre>
 *
 * <p>变更记录示例:
 * <pre>{@code
 * @Auditable(
 *   action = "Update Project",
 *   resourceType = "project",
 *   operation = Operation.UPDATE,
 *   logChanges = true
 * )
 * public Project updateProject(UUID id, ProjectUpdateRequest request) {
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Auditable {

    /**
     * 操作描述
     * 用于审计日志中显示的操作名称
     *
     * @return 操作描述，例如 "Create Project", "Delete User"
     */
    String action();

    /**
     * 资源类型
     * 标识被操作的资源类型
     *
     * @return 资源类型，例如 "project", "user", "workspace"
     */
    String resourceType();

    /**
     * 操作类型
     * 标识操作的类别
     *
     * @return 操作类型枚举值
     */
    Operation operation();

    /**
     * 是否记录变更详情
     * 对于UPDATE操作，可以记录修改前后的值
     *
     * @return true 记录变更详情，false 不记录（默认）
     */
    boolean logChanges() default false;

    /**
     * 是否异步记录
     * 异步记录不会阻塞主流程，但可能存在延迟
     *
     * @return true 异步记录（默认），false 同步记录
     */
    boolean async() default true;
}
