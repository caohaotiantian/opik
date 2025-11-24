# 安全设计

## 1. 认证安全

### 1.1 密码安全

#### 密码存储
```java
// 使用 BCrypt 加密,cost = 12
public String hashPassword(String plainPassword) {
    return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
}
```

**安全措施**:
- ✅ BCrypt 算法 (cost=12, ~250ms)
- ✅ 每个密码独立的 salt
- ✅ 密码哈希不可逆
- ✅ 防止彩虹表攻击

#### 密码强度策略
```
强密码要求:
- 最少 8 个字符
- 至少 1 个大写字母 (A-Z)
- 至少 1 个小写字母 (a-z)
- 至少 1 个数字 (0-9)
- 至少 1 个特殊字符 (@$!%*?&)
```

**实现**:
```java
public boolean isPasswordStrong(String password) {
    if (password == null || password.length() < 8) {
        return false;
    }
    
    return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
}
```

#### 密码重置安全
```
1. 重置令牌
   - 使用 SecureRandom 生成
   - 长度 >= 32 字节
   - 有效期 30 分钟
   - 一次性使用

2. 重置流程
   - 验证邮箱存在性
   - 发送重置链接
   - 令牌验证
   - 更新密码
   - 令牌失效
```

### 1.2 Session 安全

#### Session Token 生成
```java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

public String generateSecureToken() {
    byte[] randomBytes = new byte[32];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
}
```

**安全措施**:
- ✅ 使用 SecureRandom 生成
- ✅ Token 长度 >= 32 字节
- ✅ URL-safe Base64 编码
- ✅ 无法预测和伪造

#### Cookie 安全配置
```yaml
authentication:
  session:
    cookie:
      httpOnly: true      # 防止 XSS 攻击
      secure: true        # 仅 HTTPS 传输
      sameSite: Lax       # 防止 CSRF 攻击
```

**Java 代码**:
```java
NewCookie sessionCookie = new NewCookie(
    "sessionToken",
    session.getSessionToken(),
    "/",                    // path
    null,                   // domain
    Cookie.DEFAULT_VERSION,
    null,                   // comment
    maxAge,
    null,                   // expiry
    true,                   // secure (HTTPS only)
    true,                   // httpOnly
    SameSite.LAX            // sameSite
);
```

#### Session 过期策略
```
1. 固定过期时间: 24 小时
2. 滑动过期: 每次访问刷新过期时间 (可选)
3. 强制登出: 
   - 密码修改
   - 账号状态变更
   - 管理员强制登出
```

### 1.3 API Key 安全

#### API Key 生成
```java
private static final String API_KEY_PREFIX = "opik_";
private static final int API_KEY_LENGTH = 48;
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

public String generateSecureApiKey() {
    byte[] randomBytes = new byte[API_KEY_LENGTH];
    SECURE_RANDOM.nextBytes(randomBytes);
    String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    return API_KEY_PREFIX + randomPart;
}
```

**安全措施**:
- ✅ 前缀标识: `opik_`
- ✅ 随机部分 >= 48 字节
- ✅ URL-safe 编码
- ✅ 无法预测和伪造

#### API Key 存储
```java
// 存储 SHA-256 哈希,不存储明文
public String hashApiKey(String apiKey) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hash);
}
```

**安全措施**:
- ✅ SHA-256 哈希存储
- ✅ 明文只在创建时返回一次
- ✅ 数据库泄露不影响安全

#### API Key 权限限制
```java
// 创建时指定权限范围
ApiKey apiKey = ApiKey.builder()
    .permissions(Set.of(
        Permission.PROJECT_VIEW,
        Permission.TRACE_VIEW
    ))  // 只读权限
    .build();
```

**安全措施**:
- ✅ 最小权限原则
- ✅ 支持权限范围限制
- ✅ 默认继承用户权限
- ✅ 可以创建只读 API Key

#### API Key 数量限制
```
限制:
- 每个用户最多 50 个 API Key
- 每个工作空间不限制

目的:
- 防止滥用
- 资源控制
```

#### API Key 过期策略
```
1. 默认不过期 (expiresAt = null)
2. 可选设置过期时间
3. 过期后自动失效
4. 支持手动撤销
```

## 2. 授权安全

### 2.1 RBAC 权限模型

#### 最小权限原则
```
设计原则:
1. 默认拒绝 (Deny by Default)
2. 显式授权 (Explicit Grant)
3. 权限最小化 (Least Privilege)
4. 职责分离 (Separation of Duties)
```

#### 权限边界
```
System Admin (系统管理员):
- 管理所有用户和工作空间
- 不能被其他人管理
- 数量应当受限

Workspace Admin (工作空间管理员):
- 只能管理本工作空间
- 不能访问其他工作空间
- 不能修改系统设置

Project Member (项目成员):
- 只能访问分配的项目
- 受工作空间权限约束
```

### 2.2 权限检查机制

#### AOP 拦截
```java
@RequiresPermission(Permission.PROJECT_CREATE)
public Project createProject(ProjectCreateRequest request) {
    // 业务逻辑
}
```

**安全措施**:
- ✅ 声明式权限检查
- ✅ 无法绕过
- ✅ 统一的拦截点

#### 数据级权限
```java
// 所有 DAO 方法强制传入 workspaceId
@SqlQuery("SELECT * FROM projects WHERE id = :id AND workspace_id = :workspaceId")
Project findById(@Bind("id") UUID id, @Bind("workspaceId") String workspaceId);
```

**安全措施**:
- ✅ 数据库层面隔离
- ✅ 无法跨工作空间访问
- ✅ SQL 注入防护

### 2.3 权限缓存安全

#### 缓存策略
```
缓存内容:
- 用户在工作空间中的权限
- TTL: 10 分钟

缓存失效:
- 角色变更
- 成员移除
- 自定义角色更新
```

#### 缓存一致性
```java
// 角色变更时清除相关缓存
public void updateMemberRole(String workspaceId, String userId, String newRoleId) {
    memberDAO.updateRole(workspaceId, userId, newRoleId);
    
    // 清除权限缓存
    permissionCacheService.invalidate(userId, workspaceId);
}
```

## 3. 数据安全

### 3.1 工作空间数据隔离

#### 数据库设计
```sql
-- 所有业务表都包含 workspace_id
CREATE TABLE projects (
    id CHAR(36) NOT NULL,
    workspace_id VARCHAR(150) NOT NULL,
    name VARCHAR(150) NOT NULL,
    -- 唯一约束包含 workspace_id
    CONSTRAINT projects_workspace_id_name_uk UNIQUE (workspace_id, name)
);
```

#### DAO 层强制隔离
```java
// 所有方法都要求 workspaceId 参数
void save(@Bind("workspaceId") String workspaceId, @BindMethods("bean") Project project);
Project findById(@Bind("id") UUID id, @Bind("workspaceId") String workspaceId);
void delete(@Bind("id") UUID id, @Bind("workspaceId") String workspaceId);
```

### 3.2 敏感数据加密

#### 数据分类
```
公开数据:
- 用户名 (唯一标识)
- 工作空间名称

敏感数据 (需加密):
- 密码哈希 (BCrypt)
- API Key 哈希 (SHA-256)
- Session Token (SecureRandom)

个人隐私数据:
- 邮箱地址 (需访问控制)
- 全名 (需访问控制)
```

#### 加密配置
```yaml
encryption:
  # 用于加密敏感配置的密钥
  key: ${OPIK_ENCRYPTION_KEY:-'GiTHubiLoVeYouAA'}
```

**注意**: 生产环境必须使用强随机密钥!

### 3.3 审计日志安全

#### 日志不可篡改
```
ClickHouse 特性:
- ReplicatedReplacingMergeTree 引擎
- 只允许插入和查询
- 不支持直接更新和删除
- 分区自动过期
```

#### 日志保留策略
```yaml
auditLog:
  retentionDays: 90  # 默认保留 90 天
```

#### 日志访问控制
```
只有以下角色可以查看审计日志:
- System Admin (所有日志)
- Workspace Admin (本工作空间日志)
```

## 4. 网络安全

### 4.1 HTTPS

#### 强制 HTTPS
```yaml
server:
  applicationConnectors:
    - type: https
      port: 8443
      keyStorePath: /path/to/keystore.jks
      keyStorePassword: ${KEYSTORE_PASSWORD}
      certAlias: opik
```

**生产环境必须启用 HTTPS!**

### 4.2 CORS 配置

```yaml
cors:
  enabled: ${CORS:-false}
  allowedOrigins: ${CORS_ALLOWED_ORIGINS:-http://localhost:5173}
  allowedMethods: GET,POST,PUT,DELETE,OPTIONS
  allowedHeaders: Content-Type,Authorization,Comet-Workspace
  allowCredentials: true
```

**安全配置**:
- ✅ 限制允许的源
- ✅ 限制允许的方法
- ✅ 限制允许的 Header
- ✅ 谨慎使用 allowCredentials

### 4.3 CSRF 防护

#### Session 认证需要 CSRF Token
```java
// 生成 CSRF Token
String csrfToken = generateCsrfToken();
session.setCsrfToken(csrfToken);

// 验证 CSRF Token
if (!csrfToken.equals(request.getHeader("X-CSRF-Token"))) {
    throw new ForbiddenException("Invalid CSRF token");
}
```

#### API Key 认证不需要 CSRF Token
```
原因:
- API Key 不通过 Cookie 传输
- 不受浏览器同源策略影响
- 不存在 CSRF 风险
```

## 5. 应用安全

### 5.1 输入验证

#### Bean Validation
```java
public class UserRegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscore and hyphen")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

#### SQL 注入防护
```java
// 使用 JDBI 参数化查询,自动防护 SQL 注入
@SqlQuery("SELECT * FROM users WHERE username = :username")
Optional<User> findByUsername(@Bind("username") String username);
```

### 5.2 输出编码

#### XSS 防护
```java
// 1. 后端: 不信任前端数据
@NotBlank
@Pattern(regexp = "^[a-zA-Z0-9\\s-_]+$")  // 限制字符集
private String name;

// 2. 前端: 使用 React 自动转义
<div>{userName}</div>  // React 自动 HTML 转义

// 3. 对于富文本: 使用 DOMPurify 清理
import DOMPurify from 'dompurify';
const cleanHtml = DOMPurify.sanitize(dirtyHtml);
```

### 5.3 速率限制

#### 登录速率限制
```yaml
rateLimit:
  login:
    limit: 5           # 5 次
    windowSeconds: 60  # 每分钟
```

#### API Key 生成速率限制
```yaml
rateLimit:
  apiKeyGeneration:
    limit: 10          # 10 次
    windowSeconds: 3600  # 每小时
```

#### 实现
```java
@RateLimit(
    bucket = "login",
    key = "#{request.getIpAddress()}",
    limit = 5,
    window = 60
)
public LoginResponse login(LoginRequest request) {
    // 登录逻辑
}
```

### 5.4 错误处理

#### 不暴露敏感信息
```java
// ❌ 错误: 暴露详细信息
catch (SQLException e) {
    throw new InternalServerErrorException(e.getMessage());
}

// ✅ 正确: 通用错误消息
catch (SQLException e) {
    log.error("Database error", e);  // 记录详细日志
    throw new InternalServerErrorException("Internal server error");  // 返回通用消息
}
```

#### 统一错误响应
```java
{
  "code": 500,
  "message": "Internal server error",
  "details": []  // 仅开发环境返回详情
}
```

## 6. 依赖安全

### 6.1 依赖扫描

#### OWASP Dependency Check
```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>8.4.0</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### 定期更新依赖
```bash
# 检查过时的依赖
mvn versions:display-dependency-updates

# 更新依赖
mvn versions:use-latest-releases
```

### 6.2 Dependabot 配置

```.github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/apps/opik-backend"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
```

## 7. 运维安全

### 7.1 配置管理

#### 敏感配置使用环境变量
```yaml
# ❌ 错误: 硬编码密码
database:
  password: "mypassword"

# ✅ 正确: 使用环境变量
database:
  password: ${DB_PASSWORD}
```

#### Secrets 管理
```
推荐工具:
- HashiCorp Vault
- AWS Secrets Manager
- Kubernetes Secrets

不要:
- 提交 secrets 到版本控制
- 在日志中打印 secrets
- 在错误消息中暴露 secrets
```

### 7.2 日志安全

#### 不记录敏感信息
```java
// ❌ 错误
log.info("User login: username={}, password={}", username, password);

// ✅ 正确
log.info("User login: username='{}'", username);
```

#### 日志访问控制
```
日志文件权限:
- 只有应用进程和管理员可读
- 定期轮转和归档
- 敏感日志加密存储
```

### 7.3 监控和告警

#### 安全事件监控
```
监控指标:
- 失败登录次数
- API 错误率
- 异常访问模式
- 权限拒绝次数
- 审计日志异常
```

#### 告警规则
```
立即告警:
- 5 分钟内失败登录 > 10 次
- API 错误率 > 5%
- 系统管理员账号登录 (可选)
- 大量权限拒绝
```

## 8. 安全检查清单

### 8.1 开发阶段

- [ ] 代码遵循安全编码规范
- [ ] 所有输入进行验证
- [ ] 所有输出进行编码
- [ ] 使用参数化查询
- [ ] 敏感数据加密存储
- [ ] 权限检查无法绕过

### 8.2 测试阶段

- [ ] 单元测试覆盖安全功能
- [ ] 集成测试包含权限测试
- [ ] 渗透测试通过
- [ ] SQL 注入测试通过
- [ ] XSS 测试通过
- [ ] CSRF 测试通过

### 8.3 部署阶段

- [ ] 启用 HTTPS
- [ ] 配置强密钥
- [ ] 设置适当的 CORS
- [ ] 启用速率限制
- [ ] 配置日志审计
- [ ] 设置监控告警

### 8.4 运维阶段

- [ ] 定期更新依赖
- [ ] 定期安全扫描
- [ ] 定期审查日志
- [ ] 定期备份数据
- [ ] 定期审查权限
- [ ] 定期安全培训

## 9. 应急响应

### 9.1 安全事件处理流程

```
1. 发现 → 2. 评估 → 3. 遏制 → 4. 根除 → 5. 恢复 → 6. 总结

1. 发现: 监控告警、用户报告
2. 评估: 确定影响范围和严重程度
3. 遏制: 阻止事件扩大 (封禁账号、IP)
4. 根除: 修复漏洞、清除后门
5. 恢复: 恢复正常服务
6. 总结: 事件报告、改进措施
```

### 9.2 常见安全事件

#### 账号被盗
```
应急措施:
1. 强制登出所有 Session
2. 撤销所有 API Key
3. 重置密码
4. 审查审计日志
5. 通知用户
```

#### 数据泄露
```
应急措施:
1. 确定泄露范围
2. 阻止继续泄露
3. 评估影响
4. 通知受影响用户
5. 监管报告 (如需要)
```

#### API 滥用
```
应急措施:
1. 识别滥用来源
2. 限流或封禁
3. 撤销 API Key
4. 审查审计日志
5. 加强监控
```

下一章: [配置说明](./08-configuration.md)

