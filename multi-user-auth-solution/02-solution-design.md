# 整体方案设计

## 1. 架构设计

### 1.1 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      Opik Frontend                           │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ User Pages  │  │ Workspace    │  │ Admin Platform   │   │
│  │  - Login    │  │ Pages        │  │ Management       │   │
│  │  - Register │  │  - Projects  │  │  - Users         │   │
│  │  - Profile  │  │  - Traces    │  │  - Workspaces    │   │
│  │             │  │  - Datasets  │  │  - Roles         │   │
│  │             │  │  - ...       │  │  - Audit Logs    │   │
│  └─────────────┘  └──────────────┘  └──────────────────┘   │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP/REST API
┌────────────────────────┴────────────────────────────────────┐
│                 Opik Backend (Dropwizard)                    │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              AuthFilter (Request Interceptor)        │   │
│  │  - 拦截 /v1/private/* 和 /v1/session/*             │   │
│  │  - 提取认证信息 (Cookie / API Key / Header)        │   │
│  │  - 调用 AuthService.authenticate()                  │   │
│  │  - 设置 RequestContext                              │   │
│  └────────────────┬─────────────────────────────────────┘   │
│                   │                                           │
│  ┌────────────────┴─────────────────────────────────────┐   │
│  │           LocalAuthService (新增实现)              │   │
│  │  - 验证 Session Token / API Key                    │   │
│  │  - 检查用户状态和工作空间权限                      │   │
│  │  - 加载用户角色和权限                              │   │
│  │  - Redis 缓存认证结果                              │   │
│  │  - 设置扩展的 RequestContext                       │   │
│  └────────────┬─────────────────────────────────────────┘   │
│               │                                               │
│  ┌────────────┴──────────┐  ┌────────────────────────────┐ │
│  │  User Management      │  │  Workspace Management      │ │
│  │  - UserService        │  │  - WorkspaceService        │ │
│  │  - SessionService     │  │  - MemberService           │ │
│  │  - ApiKeyService      │  │  - RoleService             │ │
│  │  - PasswordService    │  │  - PermissionService       │ │
│  └───────────────────────┘  └────────────────────────────┘ │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │             RBAC Permission System (新增)            │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ PermissionInterceptor (AOP)                     │ │  │
│  │  │  - 拦截 @RequiresPermission 注解的方法        │ │  │
│  │  │  - 检查用户是否具有所需权限                   │ │  │
│  │  │  - 系统管理员自动通过                         │ │  │
│  │  │  - 支持 AND/OR 逻辑                            │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ PermissionService                               │ │  │
│  │  │  - 根据用户角色计算权限                       │ │  │
│  │  │  - 支持三级权限(系统/工作空间/项目)          │ │  │
│  │  │  - 权限继承和覆盖                             │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Audit Logging System (新增)             │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ AuditInterceptor (AOP)                          │ │  │
│  │  │  - 拦截 @Auditable 注解的方法                 │ │  │
│  │  │  - 记录操作前的上下文信息                     │ │  │
│  │  │  - 记录操作结果和耗时                         │ │  │
│  │  │  - 异步写入 ClickHouse                        │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ AuditLogService                                 │ │  │
│  │  │  - 构建审计日志记录                           │ │  │
│  │  │  - 批量写入 ClickHouse                        │ │  │
│  │  │  - 提供查询和导出接口                         │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 DAO Layer (数据访问层)               │  │
│  │  新增:                                                 │  │
│  │  - UserDAO, WorkspaceDAO, RoleDAO, MemberDAO         │  │
│  │  - ApiKeyDAO, SessionDAO, AuditLogDAO                │  │
│  │  现有:                                                 │  │
│  │  - ProjectDAO, TraceDAO, SpanDAO, DatasetDAO, ...    │  │
│  └───────────────────────┬───────────────────────────────┘  │
└──────────────────────────┼───────────────────────────────────┘
                           │
            ┌──────────────┴──────────────┐
            │                             │
   ┌────────┴────────┐          ┌────────┴─────────┐
   │  MySQL 9.3      │          │  ClickHouse 0.9  │
   │  (状态数据)     │          │  (分析+审计)     │
   │                 │          │                  │
   │  新增:          │          │  现有:           │
   │  - users        │          │  - traces        │
   │  - workspaces   │          │  - spans         │
   │  - roles        │          │  - ...           │
   │  - members      │          │                  │
   │  - api_keys     │          │  新增:           │
   │  - sessions     │          │  - audit_logs    │
   │  - ...          │          │                  │
   └─────────────────┘          └──────────────────┘
```

### 1.2 认证流程

```
┌──────────────┐
│  Client      │
│  Request     │
└───────┬──────┘
        │
        │ 1. HTTP Request
        │    Headers:
        │      - Comet-Workspace: workspace-name
        │      - Cookie: sessionToken=xxx OR
        │      - Authorization: opik_xxxxx
        │
        ▼
┌───────────────────────────────────────────┐
│  AuthFilter                               │
│  - 提取 workspaceName                     │
│  - 提取 sessionToken 或 apiKey           │
│  - 调用 AuthService.authenticate()       │
└───────┬───────────────────────────────────┘
        │
        │ 2. Authenticate
        │
        ▼
┌───────────────────────────────────────────┐
│  LocalAuthService                         │
├───────────────────────────────────────────┤
│  Session 认证分支                         │
│  ├─ 1. 验证 Session Token                │
│  ├─ 2. 获取 User 信息                    │
│  ├─ 3. 检查 User 状态                    │
│  ├─ 4. 验证 Workspace 访问权限           │
│  └─ 5. 加载角色和权限                    │
│                                           │
│  API Key 认证分支                         │
│  ├─ 1. 检查 Redis 缓存                   │
│  ├─ 2. 验证 API Key                      │
│  ├─ 3. 检查状态和过期时间                │
│  ├─ 4. 验证 Workspace 匹配               │
│  ├─ 5. 加载用户和权限信息                │
│  └─ 6. 缓存结果                          │
└───────┬───────────────────────────────────┘
        │
        │ 3. Set RequestContext
        │    - userId
        │    - userName
        │    - workspaceId
        │    - workspaceName
        │    - roleIds
        │    - permissions
        │    - isSystemAdmin
        │
        ▼
┌───────────────────────────────────────────┐
│  Business Logic                           │
│  - ProjectService.createProject()         │
│  - TraceService.createTrace()             │
│  - ...                                    │
└───────┬───────────────────────────────────┘
        │
        │ 4. Permission Check (if @RequiresPermission)
        │
        ▼
┌───────────────────────────────────────────┐
│  PermissionInterceptor                    │
│  - 检查 RequestContext 中的权限          │
│  - 系统管理员自动通过                    │
│  - 否则检查具体权限                      │
└───────┬───────────────────────────────────┘
        │
        │ 5. Execute Business Logic
        │
        ▼
┌───────────────────────────────────────────┐
│  DAO Layer                                │
│  - 强制传入 workspaceId                  │
│  - 数据隔离                              │
└───────┬───────────────────────────────────┘
        │
        │ 6. Audit Log (if @Auditable)
        │
        ▼
┌───────────────────────────────────────────┐
│  AuditInterceptor                         │
│  - 记录操作信息                          │
│  - 异步写入 ClickHouse                   │
└───────────────────────────────────────────┘
```

### 1.3 权限检查流程

```
┌──────────────────────────┐
│  API Request             │
│  @RequiresPermission(    │
│    PROJECT_CREATE,       │
│    TRACE_CREATE          │
│  )                       │
└───────┬──────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│  PermissionInterceptor.invoke()           │
│  1. 获取 RequestContext                   │
│  2. 检查是否为系统管理员                 │
│     - 是 → 直接通过                      │
│     - 否 → 继续检查                      │
└───────┬───────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│  PermissionService.hasPermission()        │
│  1. 获取用户在当前工作空间的角色         │
│  2. 计算角色拥有的所有权限               │
│  3. 检查是否满足所需权限                 │
│     - requireAll=true: 需要全部权限      │
│     - requireAll=false: 需要任意权限     │
└───────┬───────────────────────────────────┘
        │
        ├─── 有权限 ──────┐
        │                  │
        └─── 无权限 ──────┤
                           │
                           ▼
                    ┌──────────────┐
                    │ throw        │
                    │ Forbidden    │
                    │ Exception    │
                    └──────────────┘
```

## 2. 核心组件设计

### 2.1 扩展的 RequestContext

```java
@RequestScoped
@Data
public class RequestContext {
    // 现有字段
    private String userName;
    private String workspaceId;
    private String workspaceName;
    private String apiKey;
    private MultivaluedMap<String, String> headers;
    private List<Quota> quotas;
    private Visibility visibility;
    
    // 新增字段
    private String userId;                    // 用户 ID
    private boolean systemAdmin;              // 是否系统管理员
    private Set<String> roleIds;              // 用户角色 ID 列表
    private Set<Permission> permissions;      // 用户权限列表
    private String projectId;                 // 当前操作的项目 ID (可选)
}
```

### 2.2 认证服务接口

```java
public interface AuthService {
    /**
     * 认证请求
     * @param headers HTTP 请求头
     * @param sessionToken Session Cookie
     * @param contextInfo 请求上下文信息
     * @throws ClientErrorException 认证失败
     */
    void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo);
    
    /**
     * 验证 Session
     * @param sessionToken Session Cookie
     * @throws ClientErrorException Session 无效
     */
    void authenticateSession(Cookie sessionToken);
}
```

### 2.3 权限注解

```java
/**
 * 标记需要权限检查的方法
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
     * 是否需要全部权限 (AND)
     * true: 需要拥有所有权限 (默认)
     * false: 需要拥有任意一个权限 (OR)
     */
    boolean requireAll() default true;
}
```

### 2.4 审计日志注解

```java
/**
 * 标记需要记录审计日志的方法
 * 
 * 示例:
 * @Auditable(
 *   action = "Create Project",
 *   resourceType = "project",
 *   operation = Operation.CREATE
 * )
 * public Project createProject(...) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    /**
     * 操作描述
     */
    String action();
    
    /**
     * 资源类型
     */
    String resourceType();
    
    /**
     * 操作类型
     */
    Operation operation();
    
    /**
     * 是否记录变更详情
     */
    boolean logChanges() default false;
}

public enum Operation {
    CREATE,   // 创建
    READ,     // 查看
    UPDATE,   // 更新
    DELETE,   // 删除
    EXECUTE,  // 执行
    LOGIN,    // 登录
    LOGOUT    // 登出
}
```

## 3. 配置设计

### 3.1 认证配置

```yaml
# apps/opik-backend/config.yml

authentication:
  # 是否启用认证 (默认: false, 兼容开源版本)
  enabled: ${AUTH_ENABLED:-false}
  
  # 认证类型: local, remote (默认: local)
  type: ${AUTH_TYPE:-local}
  
  # API Key 缓存 TTL (秒)
  apiKeyResolutionCacheTTLInSec: ${AUTH_API_KEY_RESOLUTION_CACHE_TTL_IN_SEC:-300}
  
  # Session 配置
  session:
    # Session 过期时间 (小时, 默认: 24)
    timeoutHours: ${AUTH_SESSION_TIMEOUT_HOURS:-24}
    
    # Cookie 配置
    cookie:
      httpOnly: true
      secure: ${AUTH_SESSION_COOKIE_SECURE:-true}
      sameSite: ${AUTH_SESSION_COOKIE_SAME_SITE:-Lax}
  
  # 密码策略
  password:
    # 最小长度 (默认: 8)
    minLength: ${AUTH_PASSWORD_MIN_LENGTH:-8}
    
    # 是否要求大写字母 (默认: true)
    requireUppercase: ${AUTH_PASSWORD_REQUIRE_UPPERCASE:-true}
    
    # 是否要求小写字母 (默认: true)
    requireLowercase: ${AUTH_PASSWORD_REQUIRE_LOWERCASE:-true}
    
    # 是否要求数字 (默认: true)
    requireDigit: ${AUTH_PASSWORD_REQUIRE_DIGIT:-true}
    
    # 是否要求特殊字符 (默认: true)
    requireSpecial: ${AUTH_PASSWORD_REQUIRE_SPECIAL:-true}
  
  # API Key 配置
  apiKey:
    # 每个用户最大 API Key 数量 (默认: 50)
    maxPerUser: ${AUTH_API_KEY_MAX_PER_USER:-50}
    
    # 是否默认设置过期时间 (默认: false, 不过期)
    defaultExpiry: ${AUTH_API_KEY_DEFAULT_EXPIRY:-false}
    
    # 默认过期天数 (如果启用过期, 默认: 365)
    defaultExpiryDays: ${AUTH_API_KEY_DEFAULT_EXPIRY_DAYS:-365}
  
  # 速率限制
  rateLimit:
    # 登录接口 (次/分钟)
    login: ${AUTH_RATE_LIMIT_LOGIN:-5}
    
    # API Key 生成 (次/小时)
    apiKeyGeneration: ${AUTH_RATE_LIMIT_API_KEY_GEN:-10}
    
    # 密码重置 (次/小时)
    passwordReset: ${AUTH_RATE_LIMIT_PASSWORD_RESET:-3}
  
  # React Service (企业版兼容)
  reactService:
    url: ${REACT_SERVICE_URL:-http://react-svc:8080}

# 工作空间配置
workspace:
  # 默认配额
  defaultQuota: ${WORKSPACE_DEFAULT_QUOTA:-10}
  
  # 是否限制用户创建工作空间数量 (默认: false, 不限制)
  limitPerUser: ${WORKSPACE_LIMIT_PER_USER:-false}
  
  # 每个用户最大工作空间数量 (如果限制)
  maxPerUser: ${WORKSPACE_MAX_PER_USER:-10}

# 审计日志配置
auditLog:
  # 是否启用审计日志 (默认: true)
  enabled: ${AUDIT_LOG_ENABLED:-true}
  
  # 日志保留天数 (默认: 90)
  retentionDays: ${AUDIT_LOG_RETENTION_DAYS:-90}
  
  # 批量写入大小
  batchSize: ${AUDIT_LOG_BATCH_SIZE:-100}
  
  # 批量写入间隔 (毫秒)
  flushIntervalMs: ${AUDIT_LOG_FLUSH_INTERVAL_MS:-5000}
  
  # 是否记录查看操作 (默认: true)
  logReadOperations: ${AUDIT_LOG_READ_OPERATIONS:-true}

# 国际化配置
i18n:
  # 默认语言 (zh-CN, en-US)
  defaultLocale: ${I18N_DEFAULT_LOCALE:-en-US}
  
  # 支持的语言列表
  supportedLocales: ${I18N_SUPPORTED_LOCALES:-en-US,zh-CN}
```

### 3.2 环境变量说明

```bash
# 认证相关
export AUTH_ENABLED=true
export AUTH_TYPE=local
export AUTH_SESSION_TIMEOUT_HOURS=24
export AUTH_PASSWORD_MIN_LENGTH=8
export AUTH_API_KEY_MAX_PER_USER=50

# 工作空间相关
export WORKSPACE_DEFAULT_QUOTA=10

# 审计日志相关
export AUDIT_LOG_ENABLED=true
export AUDIT_LOG_RETENTION_DAYS=90
export AUDIT_LOG_READ_OPERATIONS=true

# 国际化相关
export I18N_DEFAULT_LOCALE=zh-CN
export I18N_SUPPORTED_LOCALES=en-US,zh-CN
```

## 4. API 设计

### 4.1 认证相关 API

```
POST   /v1/public/auth/register          # 用户注册
POST   /v1/public/auth/login             # 用户登录
POST   /v1/public/auth/logout            # 用户登出
POST   /v1/public/auth/password/reset    # 请求重置密码
PUT    /v1/public/auth/password          # 修改密码

GET    /v1/session/current-user          # 获取当前用户信息
PUT    /v1/session/profile               # 更新用户资料
```

### 4.2 用户管理 API

```
GET    /v1/private/admin/users           # 用户列表 (系统管理员)
POST   /v1/private/admin/users           # 创建用户 (系统管理员)
GET    /v1/private/admin/users/{id}      # 用户详情 (系统管理员)
PUT    /v1/private/admin/users/{id}      # 更新用户 (系统管理员)
DELETE /v1/private/admin/users/{id}      # 删除用户 (系统管理员)
PUT    /v1/private/admin/users/{id}/status  # 修改用户状态 (系统管理员)
```

### 4.3 工作空间管理 API

```
GET    /v1/private/workspaces                    # 我的工作空间列表
POST   /v1/private/workspaces                    # 创建工作空间
GET    /v1/private/workspaces/{id}               # 工作空间详情
PUT    /v1/private/workspaces/{id}               # 更新工作空间
DELETE /v1/private/workspaces/{id}               # 删除工作空间
GET    /v1/private/workspaces/{id}/members       # 成员列表
POST   /v1/private/workspaces/{id}/members       # 添加成员
PUT    /v1/private/workspaces/{id}/members/{uid} # 更新成员角色
DELETE /v1/private/workspaces/{id}/members/{uid} # 移除成员
```

### 4.4 API Key 管理 API

```
GET    /v1/private/api-keys                 # 我的 API Key 列表
POST   /v1/private/api-keys                 # 创建 API Key
GET    /v1/private/api-keys/{id}            # API Key 详情
PUT    /v1/private/api-keys/{id}            # 更新 API Key
DELETE /v1/private/api-keys/{id}            # 删除/撤销 API Key
```

### 4.5 角色权限管理 API

```
GET    /v1/private/roles                    # 角色列表
POST   /v1/private/roles                    # 创建自定义角色
GET    /v1/private/roles/{id}               # 角色详情
PUT    /v1/private/roles/{id}               # 更新角色
DELETE /v1/private/roles/{id}               # 删除角色
GET    /v1/private/permissions              # 权限列表
```

### 4.6 审计日志 API

```
GET    /v1/private/admin/audit-logs        # 审计日志列表
GET    /v1/private/admin/audit-logs/{id}   # 审计日志详情
POST   /v1/private/admin/audit-logs/export # 导出审计日志
GET    /v1/private/admin/audit-logs/stats  # 审计日志统计
```

## 5. 前端路由设计

```typescript
// 公开路由 (无需认证)
/login                        // 登录页
/register                     // 注册页
/forgot-password              // 忘记密码
/reset-password/:token        // 重置密码

// 用户路由 (需要认证)
/:workspaceName/              // 工作空间首页
/:workspaceName/projects      // 项目列表
/:workspaceName/traces        // Trace 列表
/:workspaceName/datasets      // 数据集列表
/:workspaceName/prompts       // Prompt 列表
/:workspaceName/settings      // 工作空间设置
/:workspaceName/members       // 成员管理 (需要 Workspace Admin)
/:workspaceName/api-keys      // API Key 管理

// 平台管理路由 (需要系统管理员)
/admin                        // 管理首页
/admin/users                  // 用户管理
/admin/workspaces             // 工作空间管理
/admin/roles                  // 角色管理
/admin/audit-logs             // 审计日志
/admin/settings               // 系统设置

// 个人设置
/profile                      // 个人资料
/profile/security             // 安全设置
/profile/api-keys             // 我的 API Keys
```

## 6. 数据流设计

### 6.1 用户登录流程

```
┌─────────┐    1. POST /auth/login          ┌─────────────┐
│ Browser │ ─────────────────────────────────▶│  Backend    │
│         │    {username, password}           │             │
└─────────┘                                   └──────┬──────┘
                                                     │
                                                     │ 2. Verify Password
                                                     ▼
                                              ┌──────────────┐
                                              │ UserService  │
                                              │  - BCrypt    │
                                              └──────┬───────┘
                                                     │
                                                     │ 3. Create Session
                                                     ▼
                                              ┌──────────────┐
                                              │SessionService│
                                              │  - Generate  │
                                              │    Token     │
                                              └──────┬───────┘
                                                     │
                                                     │ 4. Save to DB
                                                     ▼
                                              ┌──────────────┐
                                              │   MySQL      │
                                              │   Sessions   │
                                              └──────┬───────┘
                                                     │
                                                     │ 5. Cache in Redis
                                                     ▼
                                              ┌──────────────┐
     6. Set-Cookie: sessionToken=xxx         │    Redis     │
┌─────────┐ ◀─────────────────────────────── └──────────────┘
│ Browser │    Response: {user, workspaces}
│         │
└─────────┘
```

### 6.2 API Key 认证流程

```
┌─────────┐    1. GET /projects               ┌─────────────┐
│   SDK   │ ──────────────────────────────────▶│  Backend    │
│         │    Authorization: opik_xxxxx       │             │
└─────────┘    Comet-Workspace: my-workspace   └──────┬──────┘
                                                      │
                                                      │ 2. Check Cache
                                                      ▼
                                               ┌──────────────┐
                                               │    Redis     │
                                               │  - Cache Hit?│
                                               └──────┬───────┘
                                                      │
                                              ┌───────┴───────┐
                                              │ Hit           │ Miss
                                              ▼               ▼
                                       ┌──────────┐    ┌───────────┐
                                       │ Use Cache│    │ Validate  │
                                       │ Data     │    │ API Key   │
                                       └──────────┘    └─────┬─────┘
                                                             │
                                                             │ 3. Query DB
                                                             ▼
                                                      ┌──────────────┐
                                                      │   MySQL      │
                                                      │  - api_keys  │
                                                      │  - users     │
                                                      │  - members   │
                                                      └──────┬───────┘
                                                             │
                                                             │ 4. Cache Result
                                                             ▼
                                                      ┌──────────────┐
     5. Response: {projects: [...]}                  │    Redis     │
┌─────────┐ ◀──────────────────────────────────────  └──────────────┘
│   SDK   │
│         │
└─────────┘
```

### 6.3 权限检查流程

```
Service Method Call
        │
        │ @RequiresPermission(PROJECT_CREATE)
        ▼
┌───────────────────────┐
│ PermissionInterceptor │
└───────┬───────────────┘
        │
        │ 1. Get RequestContext
        ▼
┌───────────────────────┐
│   RequestContext      │
│  - userId             │
│  - workspaceId        │
│  - isSystemAdmin      │
│  - permissions (Set)  │
└───────┬───────────────┘
        │
        │ 2. Is System Admin?
        ├────── Yes ──────▶ Allow
        │
        │ No
        ▼
┌───────────────────────┐
│  Check Permissions    │
│  - PROJECT_CREATE in  │
│    permissions?       │
└───────┬───────────────┘
        │
        ├────── Yes ──────▶ Allow
        │
        │ No
        ▼
    Throw ForbiddenException
```

## 7. 向后兼容性

### 7.1 默认工作空间模式

当 `authentication.enabled=false` 时，保持现有行为：
- 使用 `AuthServiceImpl`
- 默认工作空间: `default`
- 默认用户: `default`
- 无权限检查

### 7.2 企业版兼容

当 `authentication.type=remote` 时：
- 使用 `RemoteAuthService`
- 调用配置的 `reactService`
- 保持现有企业版功能

### 7.3 迁移路径

```
开源版 (单工作空间)
        │
        │ 1. 数据库迁移
        │    - 创建 users, workspaces, roles, members 表
        │    - 创建默认管理员用户
        │    - 创建默认工作空间
        │    - 关联现有数据到默认工作空间
        ▼
开源版 (多工作空间)
        │
        │ 2. 配置切换
        │    - authentication.enabled=true
        │    - authentication.type=local
        ▼
本地多用户版本
```

## 8. 性能优化

### 8.1 缓存策略

1. **认证信息缓存** (Redis)
   - API Key → User + Workspace + Permissions
   - TTL: 5 分钟
   - 减少数据库查询

2. **权限信息缓存** (Redis)
   - (userId, workspaceId) → Permissions Set
   - TTL: 10 分钟
   - 角色/权限变更时清除

3. **Session 缓存** (Redis)
   - sessionToken → Session Info
   - TTL: 与 Session 过期时间一致
   - 避免每次请求查数据库

### 8.2 数据库优化

1. **索引优化**
   - 所有外键建立索引
   - 常用查询字段建立复合索引
   - 避免全表扫描

2. **查询优化**
   - 使用 JOIN 减少多次查询
   - 使用批量操作
   - 分页查询大数据集

### 8.3 异步处理

1. **审计日志**: 异步批量写入 ClickHouse
2. **API Key 最后使用时间**: 异步更新
3. **Session 最后访问时间**: 异步更新

## 9. 监控和告警

### 9.1 关键指标

- 认证成功率
- 认证响应时间 (P50, P95, P99)
- 权限检查响应时间
- 缓存命中率
- 失败登录次数
- API Key 使用情况
- Session 活跃数

### 9.2 日志记录

- 所有认证失败事件
- 所有权限拒绝事件
- 所有审计日志
- 性能慢查询

## 10. 安全加固

### 10.1 认证安全

- ✅ BCrypt 密码加密 (cost=12)
- ✅ Session Token 随机生成 (UUID)
- ✅ API Key 安全生成 (SecureRandom)
- ✅ API Key 存储哈希 (SHA-256)
- ✅ HttpOnly, Secure, SameSite Cookie
- ✅ 速率限制

### 10.2 授权安全

- ✅ 最小权限原则
- ✅ 默认拒绝策略
- ✅ 权限继承和覆盖
- ✅ 审计所有操作

### 10.3 数据安全

- ✅ 工作空间数据隔离
- ✅ API Key 权限范围限制
- ✅ 敏感数据加密存储
- ✅ 审计日志不可篡改

下一章: [数据库设计](./03-database-design.md)

