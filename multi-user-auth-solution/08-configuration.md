# 配置说明

## 1. 配置文件结构

### 1.1 配置文件位置

```
apps/opik-backend/
  └─ config.yml              # 主配置文件
  └─ config-dev.yml          # 开发环境配置 (可选)
  └─ config-prod.yml         # 生产环境配置 (可选)
```

### 1.2 配置加载顺序

```
1. config.yml (默认配置)
2. 环境变量覆盖
3. 系统属性覆盖 (-D参数)
```

## 2. 认证配置

### 2.1 基础配置

```yaml
authentication:
  # 是否启用认证
  # 默认: false (向后兼容开源版本)
  enabled: ${AUTH_ENABLED:-false}
  
  # 认证类型: local | remote
  # local: 本地认证 (新实现)
  # remote: 远程 reactService (企业版兼容)
  type: ${AUTH_TYPE:-local}
  
  # API Key 缓存 TTL (秒)
  # 0 表示不缓存
  apiKeyResolutionCacheTTLInSec: ${AUTH_API_KEY_RESOLUTION_CACHE_TTL_IN_SEC:-300}
```

**环境变量示例**:
```bash
export AUTH_ENABLED=true
export AUTH_TYPE=local
export AUTH_API_KEY_RESOLUTION_CACHE_TTL_IN_SEC=300
```

### 2.2 Session 配置

```yaml
authentication:
  session:
    # Session 过期时间 (小时)
    timeoutHours: ${AUTH_SESSION_TIMEOUT_HOURS:-24}
    
    # 是否启用滑动过期 (每次访问刷新)
    slidingExpiration: ${AUTH_SESSION_SLIDING_EXPIRATION:-false}
    
    # Cookie 配置
    cookie:
      # 仅 HTTP 访问 (防止 XSS)
      httpOnly: true
      
      # 仅 HTTPS 传输 (生产环境必须为 true)
      secure: ${AUTH_SESSION_COOKIE_SECURE:-true}
      
      # SameSite 策略: Strict | Lax | None
      sameSite: ${AUTH_SESSION_COOKIE_SAME_SITE:-Lax}
      
      # Cookie 域名 (可选,用于跨子域)
      domain: ${AUTH_SESSION_COOKIE_DOMAIN:-}
```

**配置说明**:
- `timeoutHours`: 推荐 24 小时,根据安全要求调整
- `slidingExpiration`: 启用后每次访问都会刷新过期时间
- `secure`: 生产环境必须为 `true`,开发环境可以为 `false`
- `sameSite`: `Lax` 适合大多数场景,`Strict` 更安全但可能影响用户体验

### 2.3 密码策略

```yaml
authentication:
  password:
    # 最小长度
    minLength: ${AUTH_PASSWORD_MIN_LENGTH:-8}
    
    # 最大长度
    maxLength: ${AUTH_PASSWORD_MAX_LENGTH:-128}
    
    # 是否要求大写字母
    requireUppercase: ${AUTH_PASSWORD_REQUIRE_UPPERCASE:-true}
    
    # 是否要求小写字母
    requireLowercase: ${AUTH_PASSWORD_REQUIRE_LOWERCASE:-true}
    
    # 是否要求数字
    requireDigit: ${AUTH_PASSWORD_REQUIRE_DIGIT:-true}
    
    # 是否要求特殊字符
    requireSpecial: ${AUTH_PASSWORD_REQUIRE_SPECIAL:-true}
    
    # 特殊字符列表
    specialCharacters: ${AUTH_PASSWORD_SPECIAL_CHARS:-@$!%*?&}
    
    # 密码重置令牌有效期 (分钟)
    resetTokenValidityMinutes: ${AUTH_PASSWORD_RESET_TOKEN_VALIDITY:-30}
```

**安全建议**:
- 生产环境所有强度要求都应为 `true`
- `minLength` 不应小于 8
- `maxLength` 建议不超过 128 (防止 DoS)

### 2.4 API Key 配置

```yaml
authentication:
  apiKey:
    # 每个用户最大 API Key 数量
    maxPerUser: ${AUTH_API_KEY_MAX_PER_USER:-50}
    
    # 每个工作空间最大 API Key 数量 (0 表示不限制)
    maxPerWorkspace: ${AUTH_API_KEY_MAX_PER_WORKSPACE:-0}
    
    # 是否默认设置过期时间
    defaultExpiry: ${AUTH_API_KEY_DEFAULT_EXPIRY:-false}
    
    # 默认过期天数 (如果启用)
    defaultExpiryDays: ${AUTH_API_KEY_DEFAULT_EXPIRY_DAYS:-365}
    
    # API Key 前缀
    prefix: ${AUTH_API_KEY_PREFIX:-opik_}
    
    # API Key 长度 (字节数)
    length: ${AUTH_API_KEY_LENGTH:-48}
```

**使用建议**:
- `maxPerUser`: 根据实际需求调整,防止滥用
- `defaultExpiry`: 建议启用,定期轮换 API Key
- `prefix`: 用于识别 API Key,建议保留默认值

### 2.5 速率限制

```yaml
authentication:
  rateLimit:
    # 登录接口 (次/分钟)
    login:
      limit: ${AUTH_RATE_LIMIT_LOGIN:-5}
      windowSeconds: ${AUTH_RATE_LIMIT_LOGIN_WINDOW:-60}
    
    # API Key 生成 (次/小时)
    apiKeyGeneration:
      limit: ${AUTH_RATE_LIMIT_API_KEY_GEN:-10}
      windowSeconds: ${AUTH_RATE_LIMIT_API_KEY_GEN_WINDOW:-3600}
    
    # 密码重置 (次/小时)
    passwordReset:
      limit: ${AUTH_RATE_LIMIT_PASSWORD_RESET:-3}
      windowSeconds: ${AUTH_RATE_LIMIT_PASSWORD_RESET_WINDOW:-3600}
    
    # 注册 (次/天/IP)
    registration:
      limit: ${AUTH_RATE_LIMIT_REGISTRATION:-10}
      windowSeconds: ${AUTH_RATE_LIMIT_REGISTRATION_WINDOW:-86400}
```

**安全建议**:
- 登录限制防止暴力破解
- API Key 生成限制防止滥用
- 密码重置限制防止骚扰
- 注册限制防止恶意注册

## 3. 工作空间配置

```yaml
workspace:
  # 默认配额
  defaultQuota: ${WORKSPACE_DEFAULT_QUOTA:-10}
  
  # 是否限制用户创建工作空间数量
  limitPerUser: ${WORKSPACE_LIMIT_PER_USER:-false}
  
  # 每个用户最大工作空间数量 (如果启用限制)
  maxPerUser: ${WORKSPACE_MAX_PER_USER:-10}
  
  # 工作空间名称规则
  namePattern: ${WORKSPACE_NAME_PATTERN:-^[a-zA-Z0-9_-]+$}
  
  # 工作空间名称最小长度
  nameMinLength: ${WORKSPACE_NAME_MIN_LENGTH:-3}
  
  # 工作空间名称最大长度
  nameMaxLength: ${WORKSPACE_NAME_MAX_LENGTH:-50}
```

**配置说明**:
- `defaultQuota`: 新建工作空间的默认配额
- `limitPerUser`: 是否限制用户创建数量
- `namePattern`: 工作空间名称正则表达式

## 4. 审计日志配置

```yaml
auditLog:
  # 是否启用审计日志
  enabled: ${AUDIT_LOG_ENABLED:-true}
  
  # 日志保留天数 (默认: 365天,即12个月)
  retentionDays: ${AUDIT_LOG_RETENTION_DAYS:-365}
  
  # 批量写入大小
  batchSize: ${AUDIT_LOG_BATCH_SIZE:-100}
  
  # 批量写入间隔 (毫秒)
  flushIntervalMs: ${AUDIT_LOG_FLUSH_INTERVAL_MS:-5000}
  
  # 是否记录查看操作
  logReadOperations: ${AUDIT_LOG_READ_OPERATIONS:-true}
  
  # 是否记录变更详情
  logChanges: ${AUDIT_LOG_CHANGES:-true}
  
  # 排除的路径 (正则表达式列表)
  excludePaths:
    - ^/health-check$
    - ^/metrics$
    - ^/v1/public/.*
  
  # 排除的操作类型
  excludeOperations:
    - ${AUDIT_LOG_EXCLUDE_OPS:-}
```

**性能建议**:
- `batchSize`: 越大性能越好,但内存占用增加
- `flushIntervalMs`: 越小实时性越好,但写入频率增加
- `logReadOperations`: 查看操作量大时可以关闭以减少日志量

**合规建议**:
- 生产环境 `enabled` 必须为 `true`
- `retentionDays` 根据合规要求设置 (通常 90-365 天)
- `logChanges` 建议启用,记录数据变更

## 5. 国际化配置

```yaml
i18n:
  # 默认语言
  defaultLocale: ${I18N_DEFAULT_LOCALE:-en-US}
  
  # 支持的语言列表
  supportedLocales: ${I18N_SUPPORTED_LOCALES:-en-US,zh-CN}
  
  # 翻译文件路径
  resourceBaseName: ${I18N_RESOURCE_BASE_NAME:-i18n/messages}
  
  # 编码
  encoding: ${I18N_ENCODING:-UTF-8}
  
  # 缓存翻译
  cacheSeconds: ${I18N_CACHE_SECONDS:-3600}
```

**配置说明**:
- `defaultLocale`: 默认语言,支持 `en-US`, `zh-CN`
- `supportedLocales`: 逗号分隔的语言列表
- `cacheSeconds`: 翻译缓存时间,0 表示不缓存

## 6. 缓存配置

```yaml
cache:
  # 缓存提供者: redis | local
  provider: ${CACHE_PROVIDER:-redis}
  
  # Redis 配置 (如果使用 Redis)
  redis:
    # Redis URL
    url: ${REDIS_URL:-redis://:opik@localhost:6379/0}
    
    # 连接池大小
    poolSize: ${REDIS_POOL_SIZE:-10}
    
    # 连接超时 (毫秒)
    connectTimeout: ${REDIS_CONNECT_TIMEOUT:-5000}
    
    # 响应超时 (毫秒)
    timeout: ${REDIS_TIMEOUT:-3000}
  
  # 本地缓存配置 (如果使用本地缓存)
  local:
    # 最大条目数
    maxSize: ${CACHE_LOCAL_MAX_SIZE:-10000}
    
    # 过期时间 (秒)
    ttl: ${CACHE_LOCAL_TTL:-300}
  
  # 各类缓存的 TTL 配置 (秒)
  ttl:
    # API Key 认证信息
    apiKeyAuth: ${CACHE_TTL_API_KEY_AUTH:-300}
    
    # Session 信息
    session: ${CACHE_TTL_SESSION:-3600}
    
    # 用户权限
    userPermissions: ${CACHE_TTL_USER_PERMISSIONS:-600}
    
    # 角色信息
    role: ${CACHE_TTL_ROLE:-1800}
    
    # 工作空间元数据
    workspaceMetadata: ${CACHE_TTL_WORKSPACE_METADATA:-3600}
```

**性能建议**:
- 生产环境推荐使用 Redis
- 调整 TTL 平衡实时性和性能
- `apiKeyAuth` TTL 不宜过长,避免权限变更延迟

## 7. 数据库配置

### 7.1 MySQL 配置

```yaml
database:
  # JDBC URL
  url: ${STATE_DB_PROTOCOL:-jdbc:mysql://}${STATE_DB_URL:-localhost:3306/opik?createDatabaseIfNotExist=true&rewriteBatchedStatements=true}
  
  # 用户名
  user: ${STATE_DB_USER:-opik}
  
  # 密码
  password: ${STATE_DB_PASS:-opik}
  
  # 驱动类
  driverClass: ${STATE_DB_DRIVER_CLASS:-com.mysql.cj.jdbc.Driver}
  
  # 连接池配置
  minSize: ${DB_POOL_MIN_SIZE:-5}
  maxSize: ${DB_POOL_MAX_SIZE:-20}
  
  # 连接超时 (毫秒)
  connectionTimeout: ${DB_CONNECTION_TIMEOUT:-30000}
  
  # 空闲超时 (毫秒)
  idleTimeout: ${DB_IDLE_TIMEOUT:-600000}
  
  # 最大生命周期 (毫秒)
  maxLifetime: ${DB_MAX_LIFETIME:-1800000}
```

**生产环境建议**:
- 使用连接池 (HikariCP)
- 合理设置连接池大小
- 启用 SSL 连接

### 7.2 ClickHouse 配置

```yaml
databaseAnalytics:
  # 协议: HTTP | HTTPS
  protocol: ${ANALYTICS_DB_PROTOCOL:-HTTP}
  
  # 主机
  host: ${ANALYTICS_DB_HOST:-localhost}
  
  # 端口
  port: ${ANALYTICS_DB_PORT:-8123}
  
  # 用户名
  username: ${ANALYTICS_DB_USERNAME:-opik}
  
  # 密码
  password: ${ANALYTICS_DB_PASS:-opik}
  
  # 数据库名
  databaseName: ${ANALYTICS_DB_DATABASE_NAME:-opik}
  
  # 查询参数
  queryParameters: ${ANALYTICS_DB_QUERY_PARAMETERS:-health_check_interval=2000&compress=1&auto_discovery=true}
```

## 8. 日志配置

```yaml
logging:
  # 全局日志级别: TRACE | DEBUG | INFO | WARN | ERROR
  level: ${GENERAL_LOG_LEVEL:-INFO}
  
  # 各模块日志级别
  loggers:
    com.comet.opik: ${OPIK_LOG_LEVEL:-INFO}
    com.comet.opik.infrastructure.auth: ${AUTH_LOG_LEVEL:-INFO}
    com.comet.opik.infrastructure.authorization: ${AUTHZ_LOG_LEVEL:-INFO}
    com.comet.opik.infrastructure.audit: ${AUDIT_LOG_LEVEL:-INFO}
  
  # 日志输出
  appenders:
    - type: console
      threshold: INFO
      target: stdout
      logFormat: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    
    - type: file
      threshold: INFO
      currentLogFilename: ./logs/opik.log
      archive: true
      archivedLogFilenamePattern: ./logs/opik-%d{yyyy-MM-dd}.log.gz
      archivedFileCount: 30
      logFormat: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

**日志级别建议**:
- 开发环境: DEBUG
- 测试环境: INFO
- 生产环境: INFO 或 WARN

## 9. 服务器配置

```yaml
server:
  # 是否启用虚拟线程 (Java 21+)
  enableVirtualThreads: ${ENABLE_VIRTUAL_THREADS:-false}
  
  # 应用连接器
  applicationConnectors:
    - type: http
      port: ${SERVER_APPLICATION_PORT:-8080}
      maxRequestHeaderSize: ${SERVER_MAX_REQUEST_HEADER_SIZE:-16KB}
  
  # 管理连接器
  adminConnectors:
    - type: http
      port: ${SERVER_ADMIN_PORT:-8081}
  
  # GZIP 压缩
  gzip:
    enabled: true
    minimumEntitySize: 256
```

## 10. 完整配置示例

### 10.1 开发环境配置

```yaml
# config-dev.yml

# 认证配置
authentication:
  enabled: true
  type: local
  apiKeyResolutionCacheTTLInSec: 300
  session:
    timeoutHours: 24
    cookie:
      secure: false  # 开发环境可以使用 HTTP
      sameSite: Lax
  password:
    minLength: 8
  apiKey:
    maxPerUser: 50
  rateLimit:
    login:
      limit: 10  # 开发环境放宽限制
      windowSeconds: 60

# 工作空间配置
workspace:
  defaultQuota: 10
  limitPerUser: false

# 审计日志配置
auditLog:
  enabled: true
  retentionDays: 30  # 开发环境保留较短时间
  batchSize: 50
  flushIntervalMs: 10000
  logReadOperations: false  # 开发环境不记录查看操作

# 国际化配置
i18n:
  defaultLocale: zh-CN
  supportedLocales: en-US,zh-CN

# 日志配置
logging:
  level: DEBUG  # 开发环境使用 DEBUG

# 服务器配置
server:
  applicationConnectors:
    - type: http
      port: 8080
```

### 10.2 生产环境配置

```yaml
# config-prod.yml

# 认证配置
authentication:
  enabled: true
  type: local
  apiKeyResolutionCacheTTLInSec: 300
  session:
    timeoutHours: 24
    cookie:
      secure: true   # 生产环境必须 HTTPS
      sameSite: Strict  # 更严格的安全策略
  password:
    minLength: 12  # 更强的密码要求
    requireUppercase: true
    requireLowercase: true
    requireDigit: true
    requireSpecial: true
  apiKey:
    maxPerUser: 50
    defaultExpiry: true  # 启用过期
    defaultExpiryDays: 90
  rateLimit:
    login:
      limit: 5
      windowSeconds: 60
    apiKeyGeneration:
      limit: 10
      windowSeconds: 3600

# 工作空间配置
workspace:
  defaultQuota: 10
  limitPerUser: true  # 生产环境可以启用限制
  maxPerUser: 20

# 审计日志配置
auditLog:
  enabled: true
  retentionDays: 365  # 12个月审计日志保留期
  batchSize: 100
  flushIntervalMs: 5000
  logReadOperations: true
  logChanges: true

# 国际化配置
i18n:
  defaultLocale: en-US
  supportedLocales: en-US,zh-CN

# 日志配置
logging:
  level: INFO  # 生产环境使用 INFO

# 服务器配置
server:
  enableVirtualThreads: true  # 生产环境启用虚拟线程
  applicationConnectors:
    - type: https  # 生产环境使用 HTTPS
      port: 8443
      keyStorePath: /path/to/keystore.jks
      keyStorePassword: ${KEYSTORE_PASSWORD}
```

## 11. 环境变量参考

### 11.1 必需的环境变量

```bash
# 数据库
export STATE_DB_URL="localhost:3306/opik"
export STATE_DB_USER="opik"
export STATE_DB_PASS="your-secure-password"

export ANALYTICS_DB_HOST="localhost"
export ANALYTICS_DB_USERNAME="opik"
export ANALYTICS_DB_PASS="your-secure-password"

# Redis
export REDIS_URL="redis://:your-redis-password@localhost:6379/0"

# 认证
export AUTH_ENABLED="true"
export AUTH_TYPE="local"

# 加密
export OPIK_ENCRYPTION_KEY="your-very-secure-random-key"
```

### 11.2 可选的环境变量

```bash
# Session 配置
export AUTH_SESSION_TIMEOUT_HOURS="24"
export AUTH_SESSION_COOKIE_SECURE="true"

# 密码策略
export AUTH_PASSWORD_MIN_LENGTH="12"

# API Key 配置
export AUTH_API_KEY_MAX_PER_USER="50"
export AUTH_API_KEY_DEFAULT_EXPIRY="true"

# 工作空间
export WORKSPACE_DEFAULT_QUOTA="10"

# 审计日志
export AUDIT_LOG_RETENTION_DAYS="365"
export AUDIT_LOG_READ_OPERATIONS="true"

# 国际化
export I18N_DEFAULT_LOCALE="zh-CN"

# 日志
export OPIK_LOG_LEVEL="INFO"
export AUTH_LOG_LEVEL="INFO"

# 服务器
export SERVER_APPLICATION_PORT="8080"
export ENABLE_VIRTUAL_THREADS="true"
```

## 12. 配置验证

### 12.1 启动时验证

应用启动时会自动验证关键配置:

```java
// 验证认证配置
if (config.getAuthentication().isEnabled()) {
    Preconditions.checkNotNull(config.getAuthentication().getType(), 
        "authentication.type is required when authentication is enabled");
}

// 验证密码策略
Preconditions.checkArgument(config.getAuthentication().getPassword().getMinLength() >= 8,
    "password.minLength must be at least 8");

// 验证数据库连接
Preconditions.checkNotNull(config.getDatabase().getUrl(), 
    "database.url is required");
```

### 12.2 配置检查命令

```bash
# 检查配置文件语法
java -jar opik-backend.jar check config.yml

# 输出完整配置 (包括环境变量覆盖后的值)
java -jar opik-backend.jar config config.yml
```

## 13. 配置最佳实践

### 13.1 安全配置

1. **不要硬编码敏感信息**
   ```yaml
   # ❌ 错误
   password: "mypassword123"
   
   # ✅ 正确
   password: ${DB_PASSWORD}
   ```

2. **使用强随机密钥**
   ```bash
   # 生成随机密钥
   openssl rand -base64 32
   ```

3. **限制配置文件权限**
   ```bash
   chmod 600 config.yml
   ```

### 13.2 性能配置

1. **调整连接池大小**
   ```
   公式: 连接数 = (核心数 * 2) + 磁盘数
   例如: 4核 + 1磁盘 = 10 个连接
   ```

2. **合理设置缓存 TTL**
   ```
   频繁变更的数据: 较短 TTL (5-10 分钟)
   不常变更的数据: 较长 TTL (30-60 分钟)
   ```

3. **启用虚拟线程 (Java 21+)**
   ```yaml
   server:
     enableVirtualThreads: true
   ```

### 13.3 监控配置

1. **启用指标收集**
   ```yaml
   metrics:
     reporters:
       - type: prometheus
         endpoint: /metrics
   ```

2. **配置健康检查**
   ```yaml
   health:
     healthCheckUrlPaths: ["/health-check"]
     healthChecks:
       - name: database
         critical: true
   ```

## 14. 故障排查

### 14.1 常见配置问题

**问题 1: 认证失败**
```
检查:
- AUTH_ENABLED 是否为 true
- AUTH_TYPE 是否正确
- Redis 连接是否正常
- 数据库连接是否正常
```

**问题 2: 权限检查不生效**
```
检查:
- 用户是否在工作空间中
- 角色权限配置是否正确
- 缓存是否过期
```

**问题 3: 审计日志不记录**
```
检查:
- AUDIT_LOG_ENABLED 是否为 true
- ClickHouse 连接是否正常
- 日志级别是否正确
```

### 14.2 配置诊断

```bash
# 查看当前配置
curl http://localhost:8081/config

# 查看健康状态
curl http://localhost:8081/health-check

# 查看指标
curl http://localhost:8081/metrics
```

---

**至此，所有技术方案文档已完成！**

文档清单:
- ✅ [00-overview.md](./00-overview.md)
- ✅ [01-current-architecture.md](./01-current-architecture.md)
- ✅ [02-solution-design.md](./02-solution-design.md)
- ✅ [03-database-design.md](./03-database-design.md)
- ✅ [04-rbac-design.md](./04-rbac-design.md)
- ✅ [05-core-services.md](./05-core-services.md)
- ✅ [06-implementation-plan.md](./06-implementation-plan.md)
- ✅ [07-security.md](./07-security.md)
- ✅ [08-configuration.md](./08-configuration.md)
- ✅ [README.md](./README.md)

