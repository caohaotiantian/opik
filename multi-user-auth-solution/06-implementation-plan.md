# 实施计划

## 1. 阶段划分

### 总体时间表 (v1.1 优化版)

```
第一阶段: 基础设施 (1.5-2周) 【含并发控制优化】
    ├─ 数据库设计和迁移 (含 version 字段)
    ├─ DAO 层实现 (含乐观锁)
    ├─ 基础服务
    └─ 缓存服务 (CacheService)

第二阶段: 认证鉴权核心 (2.5-3周) 【含安全加固】
    ├─ LocalAuthService 实现
    ├─ 用户/Session/ApiKey 服务 (含指纹验证)
    ├─ RBAC 权限系统
    └─ 性能监控 (AuthMetrics)

第三阶段: 审计日志 (1.5周) 【含监控接入】
    ├─ AuditInterceptor
    ├─ 查询和导出 API
    └─ 监控 Dashboard 接入

第四阶段: 管理界面 (2-3周) 【前后端并行】
    ├─ 后端: API 实现 (并行 1周)
    ├─ 前端: 认证页面 (并行 2周)
    ├─ 前端: 管理页面 (并行 2-3周)
    └─ 国际化

第五阶段: 测试优化 (2周) 【增强测试】
    ├─ 单元测试
    ├─ 集成测试
    ├─ E2E 测试
    ├─ 性能测试 (压力测试)
    ├─ 安全测试 (渗透测试)
    └─ 文档编写

总计: 9.5-12 周 (含优化内容)
风险缓冲: +1-2 周
```

**v1.1 优化亮点**:
- ✅ 第一阶段增加乐观锁和缓存优化
- ✅ 第二阶段增加Session安全和监控
- ✅ 第三阶段增加监控Dashboard接入
- ✅ 第四阶段支持前后端并行开发
- ✅ 第五阶段增强性能和安全测试

## 2. 第一阶段: 基础设施 (1-2周)

### 2.1 数据库设计

**任务清单**:
- [x] MySQL 表结构设计
  - `users` - 用户表
  - `workspaces` - 工作空间表
  - `roles` - 角色表
  - `workspace_members` - 工作空间成员表
  - `project_members` - 项目成员表
  - `user_api_keys` - API Key 表
  - `user_sessions` - Session 表
  - `password_reset_tokens` - 密码重置令牌表

- [x] ClickHouse 表结构设计
  - `audit_logs` - 审计日志表

- [ ] Liquibase 迁移脚本
  - 创建 8 个 MySQL 迁移脚本
  - 创建 1 个 ClickHouse 迁移脚本
  - 插入默认数据 (admin 用户, 默认工作空间, 7 个内置角色)

**文件位置**:
```
apps/opik-backend/src/main/resources/liquibase/db-app-state/migrations/
  └─ 000xxx_add_users_table.sql
  └─ 000xxx_add_workspaces_table.sql
  └─ 000xxx_add_roles_table.sql
  └─ 000xxx_add_workspace_members_table.sql
  └─ 000xxx_add_project_members_table.sql
  └─ 000xxx_add_user_api_keys_table.sql
  └─ 000xxx_add_user_sessions_table.sql
  └─ 000xxx_add_password_reset_tokens_table.sql

apps/opik-backend/src/main/resources/liquibase/db-app-analytics/migrations/
  └─ 000xxx_add_audit_logs_table.sql
```

**验收标准**:
- ✅ 所有迁移脚本语法正确
- ✅ 能够成功执行迁移
- ✅ 能够成功回滚
- ✅ 默认数据正确插入

### 2.2 模型类实现

**任务清单**:
- [ ] 实体模型
  - `User.java`
  - `Workspace.java`
  - `Role.java`
  - `WorkspaceMember.java`
  - `ProjectMember.java`
  - `ApiKey.java`
  - `Session.java`
  - `PasswordResetToken.java`
  - `AuditLog.java`

- [ ] 枚举类
  - `UserStatus.java`
  - `WorkspaceStatus.java`
  - `RoleScope.java`
  - `MemberStatus.java`
  - `ApiKeyStatus.java`
  - `Permission.java`
  - `Operation.java`

- [ ] DTO 类
  - 请求 DTO (xxxRequest.java)
  - 响应 DTO (xxxResponse.java)

**文件位置**:
```
apps/opik-backend/src/main/java/com/comet/opik/api/
  └─ User.java, Workspace.java, Role.java, ...
  └─ UserStatus.java, Permission.java, ...
  └─ UserRegisterRequest.java, LoginRequest.java, ...
```

**验收标准**:
- ✅ 使用 Lombok 注解 (@Data, @Builder)
- ✅ 字段验证注解 (@NotNull, @Valid)
- ✅ Jackson 注解 (@JsonProperty, @JsonIgnore)

### 2.3 DAO 层实现

**任务清单**:
- [ ] JDBI DAO 接口
  - `UserDAO.java`
  - `WorkspaceDAO.java`
  - `RoleDAO.java`
  - `WorkspaceMemberDAO.java`
  - `ProjectMemberDAO.java`
  - `ApiKeyDAO.java`
  - `SessionDAO.java`
  - `PasswordResetTokenDAO.java`

- [ ] ClickHouse DAO
  - `AuditLogDAO.java`

**示例**:
```java
@RegisterConstructorMapper(User.class)
interface UserDAO {
    
    @SqlUpdate("INSERT INTO users (...) VALUES (...)")
    void save(@BindMethods("bean") User user);
    
    @SqlQuery("SELECT * FROM users WHERE id = :id")
    Optional<User> findById(@Bind("id") String id);
    
    @SqlQuery("SELECT * FROM users WHERE username = :username")
    Optional<User> findByUsername(@Bind("username") String username);
    
    @SqlUpdate("UPDATE users SET status = :status WHERE id = :id")
    void updateStatus(@Bind("id") String id, @Bind("status") UserStatus status);
}
```

**验收标准**:
- ✅ 使用 JDBI3 注解
- ✅ 参数验证
- ✅ 单元测试覆盖率 > 80%

### 2.4 基础服务实现

**任务清单**:
- [ ] `PasswordService.java`
  - BCrypt 密码加密
  - 密码强度验证

- [ ] `TokenService.java`
  - 生成安全随机 Token
  - Token 验证

- [ ] `CacheService.java` 【v1.1 增强】
  - Redis 缓存操作
  - 认证信息缓存
  - 权限信息缓存
  - **防缓存雪崩**: 随机 TTL (±10%)
  - **防缓存穿透**: 布隆过滤器 + 空值缓存
  - **防缓存击穿**: 分布式锁 + 双重检查

- [ ] `SessionCleanupService.java` 【v1.1 新增】
  - 定时清理过期 Session
  - 限制用户并发 Session 数量

- [ ] `AuthMetrics.java` 【v1.1 新增】
  - Metrics 指标定义
  - Counter, Timer, Histogram, Gauge
  - Prometheus 集成

- [ ] `IdGenerator.java`
  - UUID 生成

**验收标准**:
- ✅ BCrypt cost = 12
- ✅ 密码强度: 最少 8 位, 包含大小写字母+数字+特殊字符
- ✅ Token 使用 SecureRandom 生成
- ✅ 缓存 TTL 可配置
- ✅ 缓存命中率 > 80% 【v1.1】
- ✅ Session 自动清理正常工作 【v1.1】
- ✅ Metrics 正常上报 【v1.1】

### 2.5 配置类实现

**任务清单**:
- [ ] `AuthenticationConfig.java`
- [ ] `WorkspaceConfig.java`
- [ ] `AuditLogConfig.java`
- [ ] `I18nConfig.java`

**验收标准**:
- ✅ 使用 @Config 注解绑定配置
- ✅ 支持环境变量覆盖
- ✅ 合理的默认值

## 3. 第二阶段: 认证鉴权核心 (2-3周)

### 3.1 LocalAuthService 实现

**任务清单**:
- [ ] `LocalAuthService.java`
  - Session 认证逻辑
  - API Key 认证逻辑
  - 公开端点处理
  - RequestContext 设置

- [ ] `AuthModule.java` 更新
  - 根据配置选择 AuthService 实现
  - 依赖注入配置

**验收标准**:
- ✅ Session 认证功能正常
- ✅ API Key 认证功能正常
- ✅ 缓存命中率 > 80%
- ✅ 响应时间 P95 < 50ms

### 3.2 用户管理服务

**任务清单**:
- [ ] `UserService.java`
  - 用户注册
  - 用户登录/登出
  - 密码管理
  - 用户信息管理

- [ ] `SessionService.java`
  - Session 创建/验证/销毁
  - Session 过期处理

- [ ] `ApiKeyService.java`
  - API Key 生成/验证/撤销
  - 权限范围限制

**验收标准**:
- ✅ 注册流程简化 (无邮箱验证/审批)
- ✅ 密码安全 (BCrypt)
- ✅ Session 自动过期 (24 小时)
- ✅ API Key 每用户限制 50 个

### 3.3 工作空间管理服务

**任务清单**:
- [ ] `WorkspaceService.java`
  - 工作空间 CRUD
  - 配额管理

- [ ] `WorkspaceMemberService.java`
  - 成员添加/移除
  - 角色分配

- [ ] `ProjectMemberService.java`
  - 项目成员管理

**验收标准**:
- ✅ 无创建数量限制
- ✅ 默认配额 10
- ✅ 成员管理功能完整

### 3.4 RBAC 权限系统

**任务清单**:
- [ ] `Permission.java` 枚举定义
  - 所有权限定义
  - 权限作用域

- [ ] `RoleService.java`
  - 内置角色管理
  - 自定义角色 CRUD

- [ ] `PermissionService.java`
  - 权限计算
  - 三级权限检查

- [ ] `PermissionInterceptor.java`
  - AOP 拦截
  - @RequiresPermission 注解处理

- [ ] `PermissionCacheService.java`
  - 权限缓存
  - 缓存失效

**验收标准**:
- ✅ 7 个内置角色正确实现
- ✅ 支持自定义角色
- ✅ 三级权限检查正常工作
- ✅ System Admin 自动通过所有检查
- ✅ Workspace Admin 只能管理本工作空间
- ✅ 项目级权限独立控制

### 3.5 REST API 实现

**任务清单**:
- [ ] 认证 API
  - `POST /v1/public/auth/register`
  - `POST /v1/public/auth/login`
  - `POST /v1/public/auth/logout`
  - `PUT /v1/public/auth/password`

- [ ] 用户管理 API (System Admin)
  - `GET /v1/private/admin/users`
  - `POST /v1/private/admin/users`
  - `PUT /v1/private/admin/users/{id}`
  - `DELETE /v1/private/admin/users/{id}`

- [ ] 工作空间 API
  - `GET /v1/private/workspaces`
  - `POST /v1/private/workspaces`
  - `PUT /v1/private/workspaces/{id}`
  - `DELETE /v1/private/workspaces/{id}`
  - `GET /v1/private/workspaces/{id}/members`
  - `POST /v1/private/workspaces/{id}/members`

- [ ] API Key API
  - `GET /v1/private/api-keys`
  - `POST /v1/private/api-keys`
  - `DELETE /v1/private/api-keys/{id}`

- [ ] 角色管理 API
  - `GET /v1/private/roles`
  - `POST /v1/private/roles`
  - `PUT /v1/private/roles/{id}`
  - `DELETE /v1/private/roles/{id}`

**验收标准**:
- ✅ 所有 API 遵循 RESTful 规范
- ✅ OpenAPI/Swagger 文档完整
- ✅ 输入验证 (@Valid)
- ✅ 错误处理统一

## 4. 第三阶段: 审计日志 (1周)

### 4.1 审计日志实现

**任务清单**:
- [ ] `@Auditable` 注解定义
- [ ] `AuditInterceptor.java`
  - AOP 拦截
  - 日志构建
  - 异步写入

- [ ] `AuditLogService.java`
  - 批量写入 ClickHouse
  - 查询和导出

- [ ] 为关键操作添加 @Auditable
  - 所有 CRUD 操作
  - 登录/登出
  - 查看操作

**验收标准**:
- ✅ 记录所有登录/登出/CRUD/查看操作
- ✅ 异步批量写入 (100 条/批, 5 秒/次)
- ✅ 性能影响 < 5ms
- ✅ 支持按用户/操作/时间范围查询

### 4.2 审计日志 API

**任务清单**:
- [ ] `GET /v1/private/admin/audit-logs`
  - 分页查询
  - 多条件过滤

- [ ] `GET /v1/private/admin/audit-logs/{id}`
  - 日志详情

- [ ] `POST /v1/private/admin/audit-logs/export`
  - 导出为 CSV/JSON

- [ ] `GET /v1/private/admin/audit-logs/stats`
  - 统计信息

**验收标准**:
- ✅ 查询性能良好 (P95 < 500ms)
- ✅ 支持导出大数据量 (> 10万条)
- ✅ 统计信息准确

## 5. 第四阶段: 管理界面 (2-3周)

### 5.1 认证页面

**任务清单**:
- [ ] 登录页面 (`/login`)
  - 用户名/密码登录
  - 记住我
  - 错误提示

- [ ] 注册页面 (`/register`)
  - 用户名/邮箱/密码
  - 密码强度提示
  - 注册成功后自动登录

- [ ] 忘记密码页面 (`/forgot-password`)
  - 邮箱输入
  - 发送重置链接提示

- [ ] 重置密码页面 (`/reset-password/:token`)
  - 新密码输入
  - 密码强度提示

**技术栈**:
- React Hook Form
- Zod 验证
- TanStack Router

**验收标准**:
- ✅ 表单验证完整
- ✅ 错误提示友好
- ✅ 响应式设计
- ✅ 中英文切换

### 5.2 用户管理页面 (System Admin)

**任务清单**:
- [ ] 用户列表页 (`/admin/users`)
  - 分页表格
  - 搜索/过滤
  - 批量操作

- [ ] 用户详情页 (`/admin/users/{id}`)
  - 用户信息展示
  - 编辑表单
  - 工作空间列表

- [ ] 创建用户对话框
  - 表单验证
  - 默认工作空间分配

**验收标准**:
- ✅ 支持搜索和过滤
- ✅ 批量启用/禁用用户
- ✅ 实时数据更新

### 5.3 工作空间管理页面

**任务清单**:
- [ ] 工作空间列表 (`/:workspaceName`)
  - 我的工作空间
  - 切换工作空间
  - 创建工作空间

- [ ] 工作空间设置 (`/:workspaceName/settings`)
  - 基本信息编辑
  - 配额管理
  - 删除工作空间

- [ ] 成员管理 (`/:workspaceName/members`)
  - 成员列表
  - 添加成员
  - 修改角色
  - 移除成员

- [ ] API Key 管理 (`/:workspaceName/api-keys`)
  - API Key 列表
  - 创建 API Key
  - 撤销 API Key
  - 权限范围设置

**验收标准**:
- ✅ 工作空间切换流畅
- ✅ 成员管理功能完整
- ✅ API Key 创建时显示明文 (只一次)
- ✅ 支持权限范围限制

### 5.4 角色权限管理页面

**任务清单**:
- [ ] 角色列表 (`/admin/roles` 或 `/:workspaceName/roles`)
  - 内置角色 (不可编辑)
  - 自定义角色

- [ ] 创建/编辑角色对话框
  - 角色名称
  - 作用域选择
  - 权限选择器 (多选)

**验收标准**:
- ✅ 内置角色不可删除
- ✅ 权限选择器易用
- ✅ 支持作用域限制

### 5.5 审计日志页面

**任务清单**:
- [ ] 审计日志列表 (`/admin/audit-logs`)
  - 分页表格
  - 多条件过滤
  - 时间范围选择

- [ ] 日志详情对话框
  - 完整日志信息
  - 变更详情 (如有)

- [ ] 导出功能
  - CSV/JSON 格式
  - 批量导出

- [ ] 统计图表
  - 操作类型分布
  - 用户活跃度
  - 时间趋势

**验收标准**:
- ✅ 查询性能良好
- ✅ 支持复杂过滤
- ✅ 导出功能正常
- ✅ 图表展示清晰

### 5.6 国际化实现

**任务清单**:
- [ ] 复用现有 i18n 方案
  - 查找现有 i18n 实现
  - 了解翻译文件结构

- [ ] 添加翻译文件
  - `en-US.json` - 英文
  - `zh-CN.json` - 中文

- [ ] 语言切换器组件
  - 下拉选择
  - 保存用户偏好

**文件位置**:
```
apps/opik-frontend/src/locales/
  └─ en-US.json
  └─ zh-CN.json
```

**验收标准**:
- ✅ 所有文本支持翻译
- ✅ 语言切换实时生效
- ✅ 用户偏好持久化
- ✅ 日期/时间格式本地化

## 6. 第五阶段: 测试优化 (1-2周)

### 6.1 单元测试

**任务清单**:
- [ ] Service 层单元测试
  - UserService
  - SessionService
  - ApiKeyService
  - WorkspaceService
  - PermissionService

- [ ] DAO 层测试
  - 使用 H2 内存数据库
  - Testcontainers (可选)

**验收标准**:
- ✅ 覆盖率 > 80%
- ✅ 所有关键路径覆盖
- ✅ 边界条件测试

### 6.2 集成测试

**任务清单**:
- [ ] API 集成测试
  - 认证流程测试
  - RBAC 权限测试
  - 工作空间操作测试

- [ ] 数据库集成测试
  - 迁移脚本测试
  - 数据完整性测试

**验收标准**:
- ✅ 所有 API 集成测试通过
- ✅ 迁移脚本可以正确执行和回滚

### 6.3 E2E 测试

**任务清单**:
- [ ] 使用 Playwright 编写 E2E 测试
  - 用户注册登录流程
  - 工作空间创建流程
  - 成员管理流程
  - API Key 创建流程

**验收标准**:
- ✅ 核心流程 E2E 测试通过
- ✅ 在 CI/CD 中自动运行

### 6.4 性能测试

**任务清单**:
- [ ] 基准测试
  - 认证响应时间
  - 权限检查响应时间
  - API 吞吐量

- [ ] 负载测试
  - 并发用户测试
  - 缓存命中率测试

- [ ] 性能优化
  - 识别瓶颈
  - 优化慢查询
  - 调整缓存策略

**验收标准**:
- ✅ 认证响应时间 P95 < 100ms
- ✅ 权限检查响应时间 P95 < 50ms
- ✅ API 吞吐量 > 1000 req/s
- ✅ 缓存命中率 > 80%

### 6.5 安全测试

**任务清单**:
- [ ] 渗透测试
  - SQL 注入测试
  - XSS 测试
  - CSRF 测试
  - 认证绕过测试

- [ ] 依赖扫描
  - 使用 OWASP Dependency Check
  - 修复高危漏洞

- [ ] 代码审查
  - 安全编码规范检查
  - 敏感信息泄露检查

**验收标准**:
- ✅ 无高危安全漏洞
- ✅ 所有依赖版本安全
- ✅ 通过安全审查

### 6.6 文档编写

**任务清单**:
- [ ] API 文档
  - OpenAPI/Swagger 文档
  - API 使用示例

- [ ] 部署文档
  - 环境要求
  - 配置说明
  - 启动步骤
  - 迁移指南

- [ ] 用户文档
  - 功能说明
  - 操作指南
  - 常见问题

- [ ] 开发者文档
  - 架构说明
  - 代码结构
  - 扩展指南

**验收标准**:
- ✅ 文档完整准确
- ✅ 包含足够示例
- ✅ 支持中英文

## 7. 里程碑和验收

### 7.1 各阶段里程碑

| 阶段 | 里程碑 | 验收标准 |
|------|--------|----------|
| 第一阶段 | 数据库和 DAO 完成 | 迁移脚本通过,DAO 测试 > 80% |
| 第二阶段 | 认证鉴权功能完成 | 用户能注册登录,权限检查正常 |
| 第三阶段 | 审计日志完成 | 所有操作被正确记录 |
| 第四阶段 | 管理界面完成 | 所有管理功能可通过 UI 完成 |
| 第五阶段 | 测试和文档完成 | E2E 测试通过,文档完整 |

### 7.2 最终验收标准

**功能完整性**:
- ✅ 用户注册登录正常
- ✅ 工作空间管理正常
- ✅ 成员和角色管理正常
- ✅ API Key 管理正常
- ✅ 权限控制正常
- ✅ 审计日志正常

**性能要求**:
- ✅ 认证响应时间 P95 < 100ms
- ✅ 权限检查 P95 < 50ms
- ✅ 审计日志写入不影响主流程

**安全要求**:
- ✅ 无高危安全漏洞
- ✅ 密码安全存储
- ✅ Session 和 Cookie 安全

**质量要求**:
- ✅ 单元测试覆盖率 > 80%
- ✅ E2E 测试通过率 100%
- ✅ 代码质量检查通过

**文档要求**:
- ✅ API 文档完整
- ✅ 部署文档完整
- ✅ 用户文档完整

## 8. 风险管理

### 8.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 数据库迁移失败 | 高 | 低 | 充分测试,提供回滚方案 |
| 性能不达标 | 中 | 中 | 早期性能测试,及时优化 |
| 权限设计缺陷 | 高 | 低 | 详细设计评审,充分测试 |
| 缓存一致性问题 | 中 | 中 | 明确缓存失效策略 |

### 8.2 进度风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 人力不足 | 高 | 中 | 合理安排优先级,必要时增加人手 |
| 需求变更 | 中 | 低 | 需求冻结,重大变更需评审 |
| 技术难题 | 中 | 中 | 预研和技术验证,寻求专家支持 |

## 9. 资源需求

### 9.1 人力资源

- **后端开发**: 1-2 人
- **前端开发**: 1 人
- **测试**: 0.5 人 (兼职)
- **技术 Leader**: 0.5 人 (兼职)

### 9.2 基础设施

- **开发环境**: 本地 Docker
- **测试环境**: 完整的测试集群
- **CI/CD**: GitHub Actions
- **监控**: Prometheus + Grafana (可选)

下一章: [安全设计](./07-security.md)

