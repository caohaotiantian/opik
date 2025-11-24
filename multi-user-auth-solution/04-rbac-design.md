# RBAC 权限设计

## 1. 权限模型概述

### 1.1 三级权限架构

Opik 采用三级 RBAC (Role-Based Access Control) 权限模型：

```
┌─────────────────────────────────────────────────┐
│           System Level (系统级)                 │
│  - 系统管理员                                    │
│  - 跨工作空间管理                                │
│  - 全局配置和审计                                │
└─────────────────┬───────────────────────────────┘
                  │ inherits
                  ▼
┌─────────────────────────────────────────────────┐
│        Workspace Level (工作空间级)             │
│  - 工作空间管理员                                │
│  - 工作空间内的所有资源                          │
│  - 成员和角色管理                                │
└─────────────────┬───────────────────────────────┘
                  │ inherits
                  ▼
┌─────────────────────────────────────────────────┐
│          Project Level (项目级)                 │
│  - 项目管理员                                    │
│  - 项目内的资源                                  │
│  - Traces, Spans, Datasets 等                   │
└─────────────────────────────────────────────────┘
```

### 1.2 权限继承规则

1. **系统管理员** → 自动拥有所有工作空间和项目的权限
2. **工作空间管理员** → 自动拥有工作空间内所有项目的权限
3. **项目成员** → 仅拥有分配项目的权限

### 1.3 权限检查优先级

```
1. 检查是否为系统管理员 → 是 → 通过
                        ↓ 否
2. 检查工作空间级权限   → 有 → 通过
                        ↓ 无
3. 检查项目级权限       → 有 → 通过
                        ↓ 无
4. 拒绝访问
```

## 2. 权限枚举定义

### 2.1 Permission 枚举类

```java
package com.comet.opik.infrastructure.authorization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 权限枚举
 * 定义系统中所有可用的权限
 */
@Getter
@RequiredArgsConstructor
public enum Permission {
    
    // ==================== 系统级权限 ====================
    
    /**
     * 系统管理员权限 (超级权限,拥有所有权限)
     */
    SYSTEM_ADMIN("system:admin", "系统管理员", PermissionScope.SYSTEM),
    
    /**
     * 管理用户
     */
    SYSTEM_USER_MANAGE("system:users:manage", "管理用户", PermissionScope.SYSTEM),
    
    /**
     * 管理工作空间
     */
    SYSTEM_WORKSPACE_MANAGE("system:workspaces:manage", "管理工作空间", PermissionScope.SYSTEM),
    
    /**
     * 系统设置
     */
    SYSTEM_SETTINGS("system:settings", "系统设置", PermissionScope.SYSTEM),
    
    /**
     * 查看审计日志
     */
    SYSTEM_AUDIT_VIEW("system:audit:view", "查看审计日志", PermissionScope.SYSTEM),
    
    // ==================== 工作空间级权限 ====================
    
    /**
     * 工作空间管理员权限 (工作空间内的超级权限)
     */
    WORKSPACE_ADMIN("workspace:admin", "工作空间管理员", PermissionScope.WORKSPACE),
    
    /**
     * 查看工作空间
     */
    WORKSPACE_VIEW("workspace:view", "查看工作空间", PermissionScope.WORKSPACE),
    
    /**
     * 工作空间设置
     */
    WORKSPACE_SETTINGS("workspace:settings", "工作空间设置", PermissionScope.WORKSPACE),
    
    /**
     * 管理工作空间成员
     */
    WORKSPACE_MEMBER_MANAGE("workspace:members:manage", "管理成员", PermissionScope.WORKSPACE),
    
    // ==================== 项目权限 ====================
    
    /**
     * 创建项目
     */
    PROJECT_CREATE("project:create", "创建项目", PermissionScope.WORKSPACE),
    
    /**
     * 查看项目
     */
    PROJECT_VIEW("project:view", "查看项目", PermissionScope.PROJECT),
    
    /**
     * 更新项目
     */
    PROJECT_UPDATE("project:update", "更新项目", PermissionScope.PROJECT),
    
    /**
     * 删除项目
     */
    PROJECT_DELETE("project:delete", "删除项目", PermissionScope.PROJECT),
    
    // ==================== Trace 权限 ====================
    
    /**
     * 创建 Trace
     */
    TRACE_CREATE("trace:create", "创建Trace", PermissionScope.PROJECT),
    
    /**
     * 查看 Trace
     */
    TRACE_VIEW("trace:view", "查看Trace", PermissionScope.PROJECT),
    
    /**
     * 更新 Trace
     */
    TRACE_UPDATE("trace:update", "更新Trace", PermissionScope.PROJECT),
    
    /**
     * 删除 Trace
     */
    TRACE_DELETE("trace:delete", "删除Trace", PermissionScope.PROJECT),
    
    // ==================== Dataset 权限 ====================
    
    /**
     * 创建数据集
     */
    DATASET_CREATE("dataset:create", "创建数据集", PermissionScope.WORKSPACE),
    
    /**
     * 查看数据集
     */
    DATASET_VIEW("dataset:view", "查看数据集", PermissionScope.WORKSPACE),
    
    /**
     * 更新数据集
     */
    DATASET_UPDATE("dataset:update", "更新数据集", PermissionScope.WORKSPACE),
    
    /**
     * 删除数据集
     */
    DATASET_DELETE("dataset:delete", "删除数据集", PermissionScope.WORKSPACE),
    
    // ==================== Prompt 权限 ====================
    
    /**
     * 创建 Prompt
     */
    PROMPT_CREATE("prompt:create", "创建Prompt", PermissionScope.WORKSPACE),
    
    /**
     * 查看 Prompt
     */
    PROMPT_VIEW("prompt:view", "查看Prompt", PermissionScope.WORKSPACE),
    
    /**
     * 更新 Prompt
     */
    PROMPT_UPDATE("prompt:update", "更新Prompt", PermissionScope.WORKSPACE),
    
    /**
     * 删除 Prompt
     */
    PROMPT_DELETE("prompt:delete", "删除Prompt", PermissionScope.WORKSPACE),
    
    // ==================== Experiment 权限 ====================
    
    /**
     * 创建实验
     */
    EXPERIMENT_CREATE("experiment:create", "创建实验", PermissionScope.WORKSPACE),
    
    /**
     * 查看实验
     */
    EXPERIMENT_VIEW("experiment:view", "查看实验", PermissionScope.WORKSPACE),
    
    /**
     * 更新实验
     */
    EXPERIMENT_UPDATE("experiment:update", "更新实验", PermissionScope.WORKSPACE),
    
    /**
     * 删除实验
     */
    EXPERIMENT_DELETE("experiment:delete", "删除实验", PermissionScope.WORKSPACE),
    
    // ==================== API Key 权限 ====================
    
    /**
     * 创建 API Key
     */
    API_KEY_CREATE("apikey:create", "创建API Key", PermissionScope.WORKSPACE),
    
    /**
     * 查看 API Key
     */
    API_KEY_VIEW("apikey:view", "查看API Key", PermissionScope.WORKSPACE),
    
    /**
     * 撤销 API Key
     */
    API_KEY_REVOKE("apikey:revoke", "撤销API Key", PermissionScope.WORKSPACE),
    
    // ==================== Feedback 权限 ====================
    
    /**
     * 创建反馈定义
     */
    FEEDBACK_DEFINITION_CREATE("feedback:definition:create", "创建反馈定义", PermissionScope.WORKSPACE),
    
    /**
     * 查看反馈定义
     */
    FEEDBACK_DEFINITION_VIEW("feedback:definition:view", "查看反馈定义", PermissionScope.WORKSPACE),
    
    /**
     * 更新反馈定义
     */
    FEEDBACK_DEFINITION_UPDATE("feedback:definition:update", "更新反馈定义", PermissionScope.WORKSPACE),
    
    /**
     * 删除反馈定义
     */
    FEEDBACK_DEFINITION_DELETE("feedback:definition:delete", "删除反馈定义", PermissionScope.WORKSPACE);
    
    @JsonValue
    private final String code;
    private final String description;
    private final PermissionScope scope;
    
    @JsonCreator
    public static Permission fromString(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission: " + code));
    }
    
    /**
     * 检查是否为系统级权限
     */
    public boolean isSystemLevel() {
        return scope == PermissionScope.SYSTEM;
    }
    
    /**
     * 检查是否为工作空间级权限
     */
    public boolean isWorkspaceLevel() {
        return scope == PermissionScope.WORKSPACE;
    }
    
    /**
     * 检查是否为项目级权限
     */
    public boolean isProjectLevel() {
        return scope == PermissionScope.PROJECT;
    }
}

/**
 * 权限作用域
 */
enum PermissionScope {
    SYSTEM,      // 系统级
    WORKSPACE,   // 工作空间级
    PROJECT      // 项目级
}
```

## 3. 预置角色定义

### 3.1 系统级角色

#### System Admin (系统管理员)
```json
{
  "name": "System Admin",
  "scope": "system",
  "description": "系统管理员,拥有所有权限",
  "permissions": [
    "SYSTEM_ADMIN",
    "SYSTEM_USER_MANAGE",
    "SYSTEM_WORKSPACE_MANAGE",
    "SYSTEM_SETTINGS",
    "SYSTEM_AUDIT_VIEW"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 管理所有用户
- ✅ 管理所有工作空间
- ✅ 系统配置
- ✅ 查看全局审计日志
- ✅ 自动拥有所有工作空间和项目的权限

### 3.2 工作空间级角色

#### Workspace Admin (工作空间管理员)
```json
{
  "name": "Workspace Admin",
  "scope": "workspace",
  "description": "工作空间管理员,管理工作空间内的所有资源",
  "permissions": [
    "WORKSPACE_ADMIN",
    "WORKSPACE_VIEW",
    "WORKSPACE_SETTINGS",
    "WORKSPACE_MEMBER_MANAGE",
    "PROJECT_CREATE", "PROJECT_VIEW", "PROJECT_UPDATE", "PROJECT_DELETE",
    "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE", "TRACE_DELETE",
    "DATASET_CREATE", "DATASET_VIEW", "DATASET_UPDATE", "DATASET_DELETE",
    "PROMPT_CREATE", "PROMPT_VIEW", "PROMPT_UPDATE", "PROMPT_DELETE",
    "EXPERIMENT_CREATE", "EXPERIMENT_VIEW", "EXPERIMENT_UPDATE", "EXPERIMENT_DELETE",
    "API_KEY_CREATE", "API_KEY_VIEW", "API_KEY_REVOKE",
    "FEEDBACK_DEFINITION_CREATE", "FEEDBACK_DEFINITION_VIEW", 
    "FEEDBACK_DEFINITION_UPDATE", "FEEDBACK_DEFINITION_DELETE"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 工作空间设置
- ✅ 成员管理
- ✅ 所有资源的 CRUD 权限
- ✅ API Key 管理
- ❌ 不能访问其他工作空间
- ❌ 不能修改系统设置

#### Developer (开发者)
```json
{
  "name": "Developer",
  "scope": "workspace",
  "description": "开发者,可以创建和管理项目、数据集等资源",
  "permissions": [
    "WORKSPACE_VIEW",
    "PROJECT_CREATE", "PROJECT_VIEW", "PROJECT_UPDATE", "PROJECT_DELETE",
    "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE", "TRACE_DELETE",
    "DATASET_CREATE", "DATASET_VIEW", "DATASET_UPDATE", "DATASET_DELETE",
    "PROMPT_CREATE", "PROMPT_VIEW", "PROMPT_UPDATE", "PROMPT_DELETE",
    "EXPERIMENT_CREATE", "EXPERIMENT_VIEW", "EXPERIMENT_UPDATE", "EXPERIMENT_DELETE",
    "API_KEY_CREATE", "API_KEY_VIEW",
    "FEEDBACK_DEFINITION_VIEW"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 创建和管理项目
- ✅ 创建和管理 Traces, Datasets, Prompts
- ✅ 创建和查看自己的 API Key
- ❌ 不能管理成员
- ❌ 不能修改工作空间设置
- ❌ 不能撤销他人的 API Key

#### Viewer (查看者)
```json
{
  "name": "Viewer",
  "scope": "workspace",
  "description": "查看者,只能查看资源",
  "permissions": [
    "WORKSPACE_VIEW",
    "PROJECT_VIEW",
    "TRACE_VIEW",
    "DATASET_VIEW",
    "PROMPT_VIEW",
    "EXPERIMENT_VIEW",
    "FEEDBACK_DEFINITION_VIEW"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 查看所有资源
- ❌ 不能创建、修改、删除任何资源
- ❌ 不能管理成员
- ❌ 不能创建 API Key

### 3.3 项目级角色

#### Project Admin (项目管理员)
```json
{
  "name": "Project Admin",
  "scope": "project",
  "description": "项目管理员,管理项目内的所有资源",
  "permissions": [
    "PROJECT_VIEW", "PROJECT_UPDATE", "PROJECT_DELETE",
    "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE", "TRACE_DELETE"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 项目设置
- ✅ 管理项目内的 Traces
- ❌ 不能删除项目本身（需要工作空间权限）
- ❌ 不能访问项目外的资源

#### Project Contributor (项目贡献者)
```json
{
  "name": "Project Contributor",
  "scope": "project",
  "description": "项目贡献者,可以创建和编辑 Traces",
  "permissions": [
    "PROJECT_VIEW",
    "TRACE_CREATE", "TRACE_VIEW", "TRACE_UPDATE"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 创建和编辑 Traces
- ❌ 不能删除 Traces
- ❌ 不能修改项目设置

#### Project Viewer (项目查看者)
```json
{
  "name": "Project Viewer",
  "scope": "project",
  "description": "项目查看者,只能查看项目和 Traces",
  "permissions": [
    "PROJECT_VIEW",
    "TRACE_VIEW"
  ],
  "builtin": true
}
```

**权限说明**:
- ✅ 查看项目和 Traces
- ❌ 不能修改任何内容

## 4. 自定义角色支持

### 4.1 自定义角色规则

1. **作用域限制**:
   - 工作空间级自定义角色: 只能在创建的工作空间内使用
   - 项目级自定义角色: 只能在所属工作空间的项目中使用
   - 系统级角色: 不支持自定义（保留内置角色）

2. **权限选择**:
   - 可以从所有权限中选择任意组合
   - 不能超出作用域（如工作空间角色不能包含系统级权限）
   - 不能创建空权限角色

3. **命名规则**:
   - 同一作用域内名称唯一
   - 不能与内置角色同名
   - 长度限制: 1-100 字符

### 4.2 自定义角色示例

```java
// 示例: 创建 "Data Analyst" 自定义角色
Role customRole = Role.builder()
    .id(UUID.randomUUID().toString())
    .name("Data Analyst")
    .scope(RoleScope.WORKSPACE)
    .description("数据分析师,可以查看和分析数据,但不能修改")
    .permissions(Set.of(
        Permission.WORKSPACE_VIEW,
        Permission.PROJECT_VIEW,
        Permission.TRACE_VIEW,
        Permission.DATASET_VIEW,
        Permission.EXPERIMENT_VIEW
    ))
    .builtin(false)
    .workspaceId(workspaceId)
    .build();

roleService.createCustomRole(customRole);
```

## 5. 权限检查实现

### 5.1 @RequiresPermission 注解

```java
package com.comet.opik.infrastructure.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限检查注解
 * 用于标记需要权限检查的方法
 * 
 * 示例:
 * @RequiresPermission(Permission.PROJECT_CREATE)
 * public Project createProject(...) { ... }
 * 
 * @RequiresPermission(value = {Permission.PROJECT_VIEW, Permission.TRACE_VIEW}, requireAll = false)
 * public Dashboard getDashboard(...) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {
    /**
     * 所需权限列表
     */
    Permission[] value();
    
    /**
     * 是否需要全部权限 (AND 逻辑)
     * true: 需要拥有所有权限 (默认)
     * false: 需要拥有任意一个权限 (OR 逻辑)
     */
    boolean requireAll() default true;
    
    /**
     * 是否检查项目级权限
     * true: 检查用户在当前项目的权限
     * false: 只检查工作空间级权限 (默认)
     */
    boolean checkProjectLevel() default false;
}
```

### 5.2 PermissionInterceptor 实现

```java
package com.comet.opik.infrastructure.authorization;

import com.comet.opik.infrastructure.auth.RequestContext;
import jakarta.inject.Provider;
import jakarta.ws.rs.ForbiddenException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class PermissionInterceptor implements MethodInterceptor {
    
    private final @NonNull Provider<RequestContext> requestContextProvider;
    private final @NonNull PermissionService permissionService;
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        
        // 检查方法是否有 @RequiresPermission 注解
        if (!method.isAnnotationPresent(RequiresPermission.class)) {
            return invocation.proceed();
        }
        
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        RequestContext context = requestContextProvider.get();
        
        // 1. 系统管理员自动通过
        if (context.isSystemAdmin()) {
            log.debug("User '{}' is system admin, permission check passed", context.getUserName());
            return invocation.proceed();
        }
        
        // 2. 检查所需权限
        Permission[] requiredPermissions = annotation.value();
        boolean requireAll = annotation.requireAll();
        boolean checkProjectLevel = annotation.checkProjectLevel();
        
        // 3. 执行权限检查
        boolean hasPermission;
        if (checkProjectLevel && context.getProjectId() != null) {
            // 检查项目级权限
            hasPermission = permissionService.hasProjectPermission(
                context.getUserId(),
                context.getProjectId(),
                requiredPermissions,
                requireAll
            );
        } else {
            // 检查工作空间级权限
            hasPermission = permissionService.hasWorkspacePermission(
                context.getUserId(),
                context.getWorkspaceId(),
                requiredPermissions,
                requireAll
            );
        }
        
        // 4. 权限不足,抛出异常
        if (!hasPermission) {
            log.warn("User '{}' lacks permission for operation: {}, required: {}, requireAll: {}",
                context.getUserName(),
                method.getName(),
                Arrays.toString(requiredPermissions),
                requireAll);
            throw new ForbiddenException("Insufficient permissions to perform this operation");
        }
        
        // 5. 权限检查通过,执行方法
        log.debug("Permission check passed for user '{}', method: '{}'",
            context.getUserName(), method.getName());
        return invocation.proceed();
    }
}
```

### 5.3 PermissionService 实现

```java
package com.comet.opik.infrastructure.authorization;

import com.google.inject.ImplementedBy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@ImplementedBy(PermissionServiceImpl.class)
public interface PermissionService {
    
    /**
     * 检查用户在工作空间中是否拥有指定权限
     */
    boolean hasWorkspacePermission(
        String userId,
        String workspaceId,
        Permission[] requiredPermissions,
        boolean requireAll
    );
    
    /**
     * 检查用户在项目中是否拥有指定权限
     */
    boolean hasProjectPermission(
        String userId,
        String projectId,
        Permission[] requiredPermissions,
        boolean requireAll
    );
    
    /**
     * 获取用户在工作空间中的所有权限
     */
    Set<Permission> getUserWorkspacePermissions(String userId, String workspaceId);
    
    /**
     * 获取用户在项目中的所有权限
     */
    Set<Permission> getUserProjectPermissions(String userId, String projectId);
}

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
class PermissionServiceImpl implements PermissionService {
    
    private final @NonNull WorkspaceMemberService memberService;
    private final @NonNull ProjectMemberService projectMemberService;
    private final @NonNull RoleService roleService;
    private final @NonNull PermissionCacheService cacheService;
    
    @Override
    public boolean hasWorkspacePermission(
        String userId,
        String workspaceId,
        Permission[] requiredPermissions,
        boolean requireAll
    ) {
        // 1. 从缓存获取用户权限
        Set<Permission> userPermissions = cacheService.getWorkspacePermissions(userId, workspaceId)
            .orElseGet(() -> {
                // 2. 缓存未命中,查询数据库
                Set<Permission> permissions = getUserWorkspacePermissions(userId, workspaceId);
                // 3. 缓存结果
                cacheService.cacheWorkspacePermissions(userId, workspaceId, permissions);
                return permissions;
            });
        
        // 4. 检查权限
        if (requireAll) {
            // AND 逻辑: 需要拥有所有权限
            return userPermissions.containsAll(Arrays.asList(requiredPermissions));
        } else {
            // OR 逻辑: 需要拥有任意一个权限
            return Arrays.stream(requiredPermissions)
                .anyMatch(userPermissions::contains);
        }
    }
    
    @Override
    public boolean hasProjectPermission(
        String userId,
        String projectId,
        Permission[] requiredPermissions,
        boolean requireAll
    ) {
        // 1. 先检查工作空间级权限 (Workspace Admin 自动拥有所有项目权限)
        String workspaceId = getWorkspaceIdByProjectId(projectId);
        Set<Permission> workspacePermissions = getUserWorkspacePermissions(userId, workspaceId);
        
        if (workspacePermissions.contains(Permission.WORKSPACE_ADMIN)) {
            return true;
        }
        
        // 2. 检查项目级权限
        Set<Permission> projectPermissions = getUserProjectPermissions(userId, projectId);
        Set<Permission> allPermissions = new HashSet<>();
        allPermissions.addAll(workspacePermissions);
        allPermissions.addAll(projectPermissions);
        
        // 3. 检查权限
        if (requireAll) {
            return allPermissions.containsAll(Arrays.asList(requiredPermissions));
        } else {
            return Arrays.stream(requiredPermissions)
                .anyMatch(allPermissions::contains);
        }
    }
    
    @Override
    public Set<Permission> getUserWorkspacePermissions(String userId, String workspaceId) {
        // 1. 获取用户在工作空间中的成员信息
        WorkspaceMember member = memberService.getMember(workspaceId, userId)
            .orElse(null);
        
        if (member == null || member.getStatus() != MemberStatus.ACTIVE) {
            return Set.of();
        }
        
        // 2. 获取角色权限
        Role role = roleService.getRole(member.getRoleId())
            .orElseThrow(() -> new IllegalStateException("Role not found: " + member.getRoleId()));
        
        return role.getPermissions();
    }
    
    @Override
    public Set<Permission> getUserProjectPermissions(String userId, String projectId) {
        // 1. 获取用户在项目中的成员信息
        ProjectMember member = projectMemberService.getMember(projectId, userId)
            .orElse(null);
        
        if (member == null || member.getStatus() != MemberStatus.ACTIVE) {
            return Set.of();
        }
        
        // 2. 获取角色权限
        Role role = roleService.getRole(member.getRoleId())
            .orElseThrow(() -> new IllegalStateException("Role not found: " + member.getRoleId()));
        
        return role.getPermissions();
    }
}
```

## 6. 使用示例

### 6.1 Service 层权限控制

```java
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProjectService {
    
    private final @NonNull ProjectDAO projectDAO;
    private final @NonNull Provider<RequestContext> requestContext;
    
    /**
     * 创建项目 - 需要 PROJECT_CREATE 权限
     */
    @RequiresPermission(Permission.PROJECT_CREATE)
    @Auditable(action = "Create Project", resourceType = "project", operation = Operation.CREATE)
    public Project createProject(ProjectCreateRequest request) {
        String workspaceId = requestContext.get().getWorkspaceId();
        String userName = requestContext.get().getUserName();
        
        var project = Project.builder()
            .id(UUID.randomUUID())
            .name(request.getName())
            .description(request.getDescription())
            .createdBy(userName)
            .lastUpdatedBy(userName)
            .build();
        
        projectDAO.save(workspaceId, project);
        return project;
    }
    
    /**
     * 更新项目 - 需要 PROJECT_UPDATE 权限,检查项目级权限
     */
    @RequiresPermission(value = Permission.PROJECT_UPDATE, checkProjectLevel = true)
    @Auditable(action = "Update Project", resourceType = "project", operation = Operation.UPDATE)
    public Project updateProject(UUID projectId, ProjectUpdateRequest request) {
        String workspaceId = requestContext.get().getWorkspaceId();
        String userName = requestContext.get().getUserName();
        
        // 设置项目ID到上下文,用于权限检查
        requestContext.get().setProjectId(projectId.toString());
        
        projectDAO.update(
            projectId,
            workspaceId,
            request.getName(),
            request.getDescription(),
            userName
        );
        
        return projectDAO.findById(projectId, workspaceId);
    }
    
    /**
     * 查看项目 - 需要 PROJECT_VIEW 权限,支持工作空间级或项目级
     */
    @RequiresPermission(value = {Permission.WORKSPACE_VIEW, Permission.PROJECT_VIEW}, requireAll = false)
    public Optional<Project> getProject(UUID projectId) {
        String workspaceId = requestContext.get().getWorkspaceId();
        return projectDAO.fetch(projectId, workspaceId);
    }
    
    /**
     * 删除项目 - 需要 PROJECT_DELETE 权限
     */
    @RequiresPermission(Permission.PROJECT_DELETE)
    @Auditable(action = "Delete Project", resourceType = "project", operation = Operation.DELETE)
    public void deleteProject(UUID projectId) {
        String workspaceId = requestContext.get().getWorkspaceId();
        projectDAO.delete(projectId, workspaceId);
    }
}
```

### 6.2 Resource 层使用

```java
@Path("/v1/private/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProjectsResource {
    
    private final @NonNull ProjectService projectService;
    
    @POST
    public Response createProject(@Valid ProjectCreateRequest request) {
        // 权限检查由 ProjectService.createProject() 的 @RequiresPermission 注解处理
        var project = projectService.createProject(request);
        return Response.status(Response.Status.CREATED)
            .entity(project)
            .build();
    }
    
    @GET
    @Path("/{id}")
    public Response getProject(@PathParam("id") UUID projectId) {
        // 权限检查由 ProjectService.getProject() 的 @RequiresPermission 注解处理
        var project = projectService.getProject(projectId)
            .orElseThrow(() -> new NotFoundException("Project not found"));
        return Response.ok(project).build();
    }
}
```

## 7. 权限管理 API

### 7.1 角色管理

```java
// 获取所有角色 (包括内置和自定义)
GET /v1/private/roles?scope=workspace

// 创建自定义角色
POST /v1/private/roles
{
  "name": "Data Analyst",
  "scope": "workspace",
  "description": "数据分析师",
  "permissions": ["WORKSPACE_VIEW", "PROJECT_VIEW", "TRACE_VIEW", "DATASET_VIEW"]
}

// 更新自定义角色
PUT /v1/private/roles/{id}

// 删除自定义角色 (内置角色不可删除)
DELETE /v1/private/roles/{id}
```

### 7.2 成员权限管理

```java
// 添加工作空间成员并分配角色
POST /v1/private/workspaces/{id}/members
{
  "userId": "user-uuid",
  "roleId": "role-uuid"
}

// 修改成员角色
PUT /v1/private/workspaces/{workspaceId}/members/{userId}
{
  "roleId": "new-role-uuid"
}

// 添加项目成员并分配角色
POST /v1/private/projects/{id}/members
{
  "userId": "user-uuid",
  "roleId": "project-role-uuid"
}
```

## 8. 最佳实践

### 8.1 权限设计原则

1. **最小权限原则**: 默认拒绝,只授予必要权限
2. **职责分离**: 不同角色承担不同职责
3. **权限继承**: 利用三级权限架构,避免重复配置
4. **显式检查**: 使用 @RequiresPermission 明确标记权限要求

### 8.2 常见场景

#### 场景 1: 新用户加入工作空间
```
1. 系统管理员/工作空间管理员邀请用户
2. 为用户分配角色 (通常是 Developer 或 Viewer)
3. 用户自动获得该角色的所有权限
4. 用户可以访问工作空间内的资源
```

#### 场景 2: 细粒度项目权限
```
1. 用户在工作空间中是 Viewer (只读)
2. 将用户添加为特定项目的 Project Contributor
3. 用户可以在该项目中创建和编辑 Traces
4. 但不能访问其他项目
```

#### 场景 3: 临时提升权限
```
1. 用户通常是 Viewer
2. 需要临时执行特定操作
3. 工作空间管理员创建临时自定义角色
4. 为用户分配该角色
5. 操作完成后撤销该角色
```

### 8.3 性能优化

1. **权限缓存**: Redis 缓存用户权限,TTL 10 分钟
2. **批量检查**: 一次加载用户所有权限,避免多次查询
3. **懒加载**: 只在需要时才计算权限
4. **缓存失效**: 角色/成员变更时主动清除缓存

下一章: [核心服务设计](./05-core-services.md)

