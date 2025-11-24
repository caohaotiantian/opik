# 现状分析

## 1. 当前认证架构

### 1.1 认证服务实现

Opik 当前存在两个 `AuthService` 实现：

#### AuthServiceImpl（开源版本）
```java
// apps/opik-backend/src/main/java/com/comet/opik/infrastructure/auth/AuthService.java

@RequiredArgsConstructor
class AuthServiceImpl implements AuthService {
    private final @NonNull Provider<RequestContext> requestContext;
    
    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        var currentWorkspaceName = WorkspaceUtils.getWorkspaceName(
            headers.getHeaderString(WORKSPACE_HEADER)
        );
        
        // 只支持默认工作空间
        if (ProjectService.DEFAULT_WORKSPACE_NAME.equals(currentWorkspaceName)) {
            requestContext.get().setUserName(ProjectService.DEFAULT_USER);
            requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
            requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
            requestContext.get().setApiKey("default");
            return;
        }
        
        throw new ClientErrorException("Workspace not found", Response.Status.NOT_FOUND);
    }
    
    @Override
    public void authenticateSession(Cookie sessionToken) {
        // 开源版本无认证
    }
}
```

**特点**：
- ✅ 简单直接，无外部依赖
- ❌ 只支持单个默认工作空间 (`default`)
- ❌ 无用户概念
- ❌ 无权限控制

#### RemoteAuthService（企业版本）
```java
// apps/opik-backend/src/main/java/com/comet/opik/infrastructure/auth/RemoteAuthService.java

@RequiredArgsConstructor
@Slf4j
class RemoteAuthService implements AuthService {
    private final @NonNull Client client;
    private final @NonNull AuthenticationConfig.UrlConfig reactServiceUrl;
    private final @NonNull Provider<RequestContext> requestContext;
    private final @NonNull CacheService cacheService;
    
    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        String path = contextInfo.uriInfo().getRequestUri().getPath();
        String workspaceName = extractWorkspaceName(headers, contextInfo.uriInfo());
        
        if (sessionToken != null) {
            authenticateUsingSessionToken(sessionToken, workspaceName, path);
        } else {
            authenticateUsingApiKey(headers, workspaceName, path);
        }
    }
    
    private void authenticateUsingSessionToken(Cookie sessionToken, String workspaceName, String path) {
        // 调用 ReactService: POST /opik/auth-session
        try (var response = client.target(URI.create(reactServiceUrl.url()))
                .path("opik")
                .path("auth-session")
                .request()
                .cookie(sessionToken)
                .post(Entity.json(AuthRequest.builder()
                    .workspaceName(workspaceName)
                    .path(path)
                    .build()))) {
            
            var credentials = verifyResponse(response);
            setCredentialIntoContext(credentials);
        }
    }
    
    private void authenticateUsingApiKey(HttpHeaders headers, String workspaceName, String path) {
        // 1. 检查缓存
        var cached = cacheService.resolveApiKeyUserAndWorkspaceIdFromCache(apiKey, workspaceName);
        if (cached.isPresent()) {
            setCredentialIntoContext(cached.get());
            return;
        }
        
        // 2. 调用 ReactService: POST /opik/auth
        try (var response = client.target(URI.create(reactServiceUrl.url()))
                .path("opik")
                .path("auth")
                .request()
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .post(Entity.json(AuthRequest.builder()
                    .workspaceName(workspaceName)
                    .path(path)
                    .build()))) {
            
            var authResponse = verifyResponse(response);
            // 3. 缓存结果
            cacheService.cache(apiKey, workspaceName, authResponse);
            setCredentialIntoContext(authResponse);
        }
    }
}
```

**特点**：
- ✅ 支持多工作空间
- ✅ 支持 Session 和 API Key 认证
- ✅ Redis 缓存优化性能
- ❌ 依赖外部 reactService（闭源）
- ❌ 增加部署复杂度

### 1.2 配置方式

```yaml
# apps/opik-backend/config.yml

authentication:
  enabled: ${AUTH_ENABLED:-false}  # 默认关闭
  apiKeyResolutionCacheTTLInSec: ${AUTH_API_KEY_RESOLUTION_CACHE_TTL_IN_SEC:-5}
  reactService:
    url: ${REACT_SERVICE_URL:-http://react-svc:8080}
```

### 1.3 认证流程

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. HTTP Request                                                 │
│    - Header: Comet-Workspace: {workspaceName}                   │
│    - Cookie: sessionToken={token} OR                            │
│    - Header: Authorization: {apiKey}                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. AuthFilter (ContainerRequestFilter)                         │
│    - 拦截 /v1/private/* 和 /v1/session/*                       │
│    - 提取 workspaceName 和认证信息                             │
│    - 调用 AuthService.authenticate()                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. AuthService Implementation                                   │
│    ┌──────────────────────┐      ┌────────────────────────────┐│
│    │ AuthServiceImpl      │      │ RemoteAuthService          ││
│    │ (开源版本)           │      │ (企业版本)                ││
│    │                      │      │                            ││
│    │ - 只支持 default     │      │ - 调用 ReactService        ││
│    │ - 无用户概念         │      │ - 支持多工作空间           ││
│    │                      │      │ - Redis 缓存               ││
│    └──────────────────────┘      └────────────────────────────┘│
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. RequestContext 设置                                          │
│    - userName                                                    │
│    - workspaceId                                                 │
│    - workspaceName                                               │
│    - apiKey                                                      │
│    - quotas (可选)                                              │
│    - visibility (可选)                                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Business Logic                                               │
│    - 通过 RequestContext 获取当前用户和工作空间信息            │
│    - DAO 层强制传入 workspaceId 参数                           │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 数据隔离机制

### 2.1 数据库设计

所有业务表都包含 `workspace_id` 字段：

```sql
-- 项目表
CREATE TABLE projects (
    id CHAR(36) NOT NULL,
    name VARCHAR(150) NOT NULL,
    workspace_id VARCHAR(150) NOT NULL,  -- 工作空间隔离
    description VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT projects_pk PRIMARY KEY (id),
    CONSTRAINT projects_workspace_id_name_uk UNIQUE (workspace_id, name)
);

-- 数据集表
CREATE TABLE datasets (
    id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(150) NOT NULL,  -- 工作空间隔离
    description VARCHAR(255),
    CONSTRAINT datasets_pk PRIMARY KEY (id),
    CONSTRAINT datasets_workspace_id_name_uk UNIQUE (workspace_id, name)
);

-- 反馈定义表
CREATE TABLE feedback_definitions (
    id CHAR(36) NOT NULL,
    name VARCHAR(150) NOT NULL,
    workspace_id VARCHAR(150) NOT NULL,  -- 工作空间隔离
    type ENUM('numerical', 'categorical') NOT NULL,
    CONSTRAINT feedbacks_pk PRIMARY KEY (id),
    CONSTRAINT feedbacks_workspace_id_name_uk UNIQUE (workspace_id, name)
);

-- ClickHouse Spans 表
CREATE TABLE spans (
    id              FixedString(36),
    workspace_id    String,  -- 工作空间隔离
    project_id      String,
    trace_id        String,
    parent_span_id  String,
    -- ... 其他字段
)
ENGINE = ReplicatedReplacingMergeTree(...)
ORDER BY (workspace_id, project_id, trace_id, parent_span_id, id);
```

### 2.2 DAO 层设计

所有 DAO 方法都强制要求传入 `workspaceId`：

```java
// apps/opik-backend/src/main/java/com/comet/opik/domain/ProjectDAO.java

@RegisterConstructorMapper(Project.class)
interface ProjectDAO {
    
    @SqlUpdate("INSERT INTO projects (id, name, description, workspace_id, ...) VALUES (...)")
    void save(@Bind("workspaceId") String workspaceId, @BindMethods("bean") Project project);
    
    @SqlUpdate("UPDATE projects SET ... WHERE id = :id AND workspace_id = :workspaceId")
    void update(@Bind("id") UUID id, @Bind("workspaceId") String workspaceId, ...);
    
    @SqlUpdate("DELETE FROM projects WHERE id = :id AND workspace_id = :workspaceId")
    void delete(@Bind("id") UUID id, @Bind("workspaceId") String workspaceId);
    
    @SqlQuery("SELECT * FROM projects WHERE id = :id AND workspace_id = :workspaceId")
    Project findById(@Bind("id") UUID id, @Bind("workspaceId") String workspaceId);
    
    @SqlQuery("SELECT * FROM projects WHERE workspace_id = :workspaceId ...")
    List<Project> find(@Bind("workspaceId") String workspaceId, ...);
}
```

**关键特点**：
- ✅ 所有方法都要求 `workspaceId` 参数
- ✅ WHERE 子句必须包含 `workspace_id`
- ✅ 唯一约束包含 `workspace_id`
- ✅ 从 DAO 层强制数据隔离

### 2.3 Service 层使用

```java
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProjectService {
    private final @NonNull ProjectDAO projectDAO;
    private final @NonNull Provider<RequestContext> requestContext;
    
    public Project createProject(ProjectCreateRequest request) {
        String workspaceId = requestContext.get().getWorkspaceId();  // 从上下文获取
        
        var project = Project.builder()
            .id(UUID.randomUUID())
            .name(request.getName())
            .description(request.getDescription())
            .build();
        
        projectDAO.save(workspaceId, project);  // 强制传入
        return project;
    }
    
    public Optional<Project> getProject(UUID projectId) {
        String workspaceId = requestContext.get().getWorkspaceId();
        return projectDAO.fetch(projectId, workspaceId);
    }
}
```

## 3. RequestContext 设计

```java
// apps/opik-backend/src/main/java/com/comet/opik/infrastructure/auth/RequestContext.java

@RequestScoped
@Data
public class RequestContext {
    public static final String WORKSPACE_HEADER = "Comet-Workspace";
    public static final String SESSION_COOKIE = "sessionToken";
    
    // 用户信息
    private String userName;          // 当前用户名
    private String workspaceId;       // 当前工作空间 ID
    private String workspaceName;     // 当前工作空间名称
    private String apiKey;            // 当前使用的 API Key
    
    // 可选信息
    private MultivaluedMap<String, String> headers;  // 请求头
    private List<Quota> quotas;                      // 配额限制
    private Visibility visibility;                    // 可见性 (PUBLIC/PRIVATE)
}
```

**特点**：
- ✅ 请求作用域 (`@RequestScoped`)
- ✅ 每个请求独立的上下文
- ✅ 通过 DI 注入到 Service 层
- ❌ 缺少用户 ID
- ❌ 缺少角色/权限信息

## 4. 现有权限控制

### 4.1 可见性控制

```java
public enum Visibility {
    PRIVATE("private"),  // 仅工作空间内可见
    PUBLIC("public");    // 公开可见
}
```

部分资源（如 Project）支持设置可见性：
- `PRIVATE`: 只有工作空间成员可访问
- `PUBLIC`: 任何知道 URI 的人都可访问（只读）

### 4.2 公开端点

`RemoteAuthService` 定义了一组公开端点，认证失败时可以以 `PUBLIC` 模式访问：

```java
private static final Map<String, Set<String>> PUBLIC_ENDPOINTS = new HashMap<>() {{
    // Projects
    put("^/v1/private/projects/?$", Set.of("GET"));
    put("^/v1/private/projects/[uuid]/?$", Set.of("GET"));
    
    // Traces & Spans
    put("^/v1/private/traces/?$", Set.of("GET"));
    put("^/v1/private/spans/?$", Set.of("GET"));
    
    // Datasets
    put("^/v1/private/datasets/?$", Set.of("GET"));
    put("^/v1/private/datasets/[uuid]/?$", Set.of("GET"));
}};
```

### 4.3 配额限制

```java
@RequiredArgsConstructor
public class UsageLimitInterceptor implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        
        if (!method.isAnnotationPresent(UsageLimited.class)) {
            return invocation.proceed();
        }
        
        // 检查配额是否超限
        usageLimitService.isQuotaExceeded(requestContext.get())
            .ifPresent(msg -> {
                throw new ClientErrorException(msg, HttpStatus.SC_PAYMENT_REQUIRED);
            });
        
        return invocation.proceed();
    }
}
```

## 5. 推测的 ReactService API

根据 `RemoteAuthService` 的代码，推测出以下 API：

### 5.1 Session 认证
```
POST /opik/auth-session
Headers: Cookie: sessionToken={token}
Request Body:
{
  "workspaceName": "my-workspace",
  "path": "/v1/private/projects"
}

Response:
{
  "user": "john.doe",
  "workspaceId": "uuid",
  "workspaceName": "my-workspace",
  "quotas": [...]
}
```

### 5.2 API Key 认证
```
POST /opik/auth
Headers: Authorization: {apiKey}
Request Body:
{
  "workspaceName": "my-workspace",
  "path": "/v1/private/projects"
}

Response:
{
  "user": "john.doe",
  "workspaceId": "uuid",
  "workspaceName": "my-workspace",
  "quotas": [...]
}
```

### 5.3 获取工作空间 ID
```
GET /workspaces/workspace-id?name={workspaceName}

Response: "workspace-uuid"
```

## 6. 现有问题总结

### 6.1 开源版本的限制
- ❌ 只支持单个默认工作空间
- ❌ 无用户概念和用户管理
- ❌ 无认证机制
- ❌ 无权限控制
- ❌ 无法支持团队协作

### 6.2 企业版本的问题
- ❌ 依赖外部闭源 reactService
- ❌ 增加部署复杂度
- ❌ 无法独立运行
- ❌ API 黑盒，难以定制

### 6.3 缺失的功能
- ❌ 用户注册/登录系统
- ❌ 工作空间管理界面
- ❌ 细粒度的 RBAC 权限控制
- ❌ 审计日志系统
- ❌ 平台管理后台

## 7. 现有优势

### 7.1 架构优势
- ✅ 分层架构清晰
- ✅ DAO 层强制数据隔离
- ✅ 依赖注入，易于扩展
- ✅ Redis 缓存优化性能

### 7.2 可扩展性
- ✅ `AuthService` 接口抽象
- ✅ `RequestContext` 可扩展
- ✅ 支持多种认证方式
- ✅ 配置灵活

### 7.3 数据模型
- ✅ workspace_id 已经普遍存在
- ✅ 数据隔离机制完善
- ✅ 唯一约束设计合理

## 8. 解决方案方向

基于以上分析，我们的方案将：

1. **保留现有架构**: 不破坏现有的 `AuthServiceImpl`（开源版本）
2. **实现 LocalAuthService**: 替代 `RemoteAuthService`，提供本地认证
3. **扩展 RequestContext**: 添加用户 ID、角色、权限信息
4. **新增用户管理**: 实现用户注册、登录、管理
5. **新增工作空间管理**: 实现工作空间创建、配置、成员管理
6. **实现 RBAC**: 细粒度的角色权限控制
7. **实现审计日志**: 记录所有关键操作
8. **开发管理界面**: 提供完整的平台管理后台

