# 多租户认证鉴权系统 - 项目实施执行计划

## 文档信息

- **文档版本**: v1.0
- **创建日期**: 2025-01-24
- **状态**: 待执行
- **预计工期**: 11周 (含1-2周风险缓冲)

## 一、项目概述

### 1.1 项目目标

为 Opik 开源版本实现完整的多用户、多工作空间认证鉴权系统，包括：
- ✅ 用户注册登录系统
- ✅ 工作空间管理
- ✅ 三级RBAC权限控制（系统/工作空间/项目）
- ✅ API Key管理
- ✅ 审计日志系统
- ✅ 平台管理界面（中英文）

### 1.2 技术栈

**后端:**
- Java 21 + Dropwizard 4.0.14
- MySQL 9.3 + ClickHouse 0.9.0
- Redis (Redisson 3.50.0)
- JDBI3 + Liquibase

**前端:**
- React 18.3.1 + TypeScript 5.4.5
- Vite 5.2.11 + TanStack Router 1.36.3
- Tailwind CSS 3.4.3

### 1.3 时间表

```
阶段一: 数据库基础设施    2周   (第1-2周)
阶段二: 认证鉴权核心      3周   (第3-5周)
阶段三: 审计日志系统      1.5周 (第6-7周)
阶段四: 前端管理界面      2.5周 (第7-9周)
阶段五: 测试与优化        2周   (第10-11周)
```

---

## 二、阶段一：数据库基础设施 (2周)

**目标:** 完成数据库表结构设计、DAO层实现、基础服务实现

### 任务 1.1：MySQL 表结构设计与迁移 (3天)

**负责人:** 后端开发
**优先级:** P0（阻塞性任务）

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 1.1.1 | 创建 users 表迁移脚本 | 0.5天 | 用户基础信息表 |
| 1.1.2 | 创建 workspaces 表迁移脚本 | 0.5天 | 工作空间表 |
| 1.1.3 | 创建 roles 表迁移脚本 | 0.5天 | 角色表，插入7个内置角色 |
| 1.1.4 | 创建成员关系表迁移脚本 | 0.5天 | workspace_members, project_members |
| 1.1.5 | 创建 API Key 和 Session 表 | 0.5天 | user_api_keys, user_sessions |
| 1.1.6 | 创建密码重置令牌表 | 0.5天 | password_reset_tokens |

#### 验收标准

**功能验收:**
```bash
# 1. 执行迁移
cd apps/opik-backend
mvn liquibase:update

# 2. 验证表创建成功
mysql -u opik -p opik -e "SHOW TABLES;"
# 应输出: users, workspaces, roles, workspace_members, project_members, 
#        user_api_keys, user_sessions, password_reset_tokens

# 3. 验证表结构
mysql -u opik -p opik -e "DESCRIBE users; DESCRIBE roles;"

# 4. 验证默认数据
mysql -u opik -p opik -e "
  SELECT username, is_system_admin FROM users WHERE username='admin';
  SELECT COUNT(*) as role_count FROM roles;
  SELECT name FROM workspaces WHERE name='default';
"
# 应返回: admin用户(is_system_admin=1), 7个角色, default工作空间

# 5. 验证外键约束
mysql -u opik -p opik -e "
  SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME 
  FROM information_schema.KEY_COLUMN_USAGE 
  WHERE TABLE_SCHEMA='opik' AND REFERENCED_TABLE_NAME IS NOT NULL;
"

# 6. 测试回滚
mvn liquibase:rollback -Dliquibase.rollbackCount=8
mvn liquibase:status  # 应显示8个变更待执行
mvn liquibase:update  # 重新应用
```

**质量验收:**
- ✅ 所有表使用 `ENGINE=InnoDB`
- ✅ 所有字符串字段使用 `utf8mb4_unicode_ci` 排序规则
- ✅ 所有主键字段为 `CHAR(36)` (UUID)
- ✅ 所有时间字段使用 `TIMESTAMP(6)` (微秒精度)
- ✅ 所有表包含 `created_at`, `created_by` 审计字段
- ✅ 唯一约束包含必要的字段组合
- ✅ 索引覆盖常用查询字段

#### 交付物

- [ ] 8个 Liquibase 迁移脚本 (XML格式)
- [ ] 数据库设计文档更新
- [ ] 迁移测试报告
- [ ] 回滚测试报告

#### 文件位置

```
apps/opik-backend/src/main/resources/liquibase/db-app-state/migrations/
  ├─ 000030_add_users_table.xml
  ├─ 000031_add_workspaces_table.xml
  ├─ 000032_add_roles_table.xml
  ├─ 000033_add_workspace_members_table.xml
  ├─ 000034_add_project_members_table.xml
  ├─ 000035_add_user_api_keys_table.xml
  ├─ 000036_add_user_sessions_table.xml
  └─ 000037_add_password_reset_tokens_table.xml
```

---

### 任务 1.2：ClickHouse 审计日志表 (1天)

**负责人:** 后端开发
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 1.2.1 | 创建 audit_logs 表迁移脚本 | 0.5天 | ReplicatedReplacingMergeTree引擎 |
| 1.2.2 | 配置分区策略 | 0.25天 | 按月分区 `PARTITION BY toYYYYMM(timestamp)` |
| 1.2.3 | 添加 Bloom Filter 索引 | 0.25天 | user_id, action 字段 |

#### 验收标准

**功能验收:**
```bash
# 1. 验证表创建
clickhouse-client --query "SHOW TABLES FROM opik;"
# 应包含: audit_logs

# 2. 验证表结构
clickhouse-client --query "DESCRIBE opik.audit_logs FORMAT Vertical;"
# 验证字段: id, workspace_id, user_id, action, resource_type, 
#          operation, status, timestamp, etc.

# 3. 测试插入
clickhouse-client --query "
  INSERT INTO opik.audit_logs 
  (id, workspace_id, user_id, username, action, resource_type, 
   resource_id, operation, status, ip_address, timestamp) 
  VALUES 
  ('test-id-1', 'ws-1', 'user-1', 'testuser', 'Test Action', 
   'project', 'proj-1', 'create', 'success', '127.0.0.1', now64(9))
"

# 4. 验证查询
clickhouse-client --query "
  SELECT * FROM opik.audit_logs WHERE user_id = 'user-1' FORMAT Vertical;
"

# 5. 验证分区
clickhouse-client --query "
  SELECT partition, rows 
  FROM system.parts 
  WHERE database='opik' AND table='audit_logs' AND active;
"

# 6. 验证索引
clickhouse-client --query "
  SELECT name, type, expr 
  FROM system.data_skipping_indices 
  WHERE database='opik' AND table='audit_logs';
"
# 应包含: idx_user_id (bloom_filter), idx_action (bloom_filter)

# 7. 性能测试 - 写入
time for i in {1..1000}; do
  clickhouse-client --query "
    INSERT INTO opik.audit_logs (...) VALUES (...)
  "
done
# 应在10秒内完成

# 8. 性能测试 - 查询
clickhouse-client --query "
  SELECT COUNT(*) FROM opik.audit_logs 
  WHERE user_id = 'user-1' AND timestamp > now() - INTERVAL 1 DAY
" --time
# 应在100ms内完成
```

**质量验收:**
- ✅ 使用 `ReplicatedReplacingMergeTree` 引擎
- ✅ 按月分区，支持自动过期
- ✅ `ORDER BY` 子句优化常用查询
- ✅ Bloom Filter 索引提升过滤性能
- ✅ 字段类型选择合理（Enum, FixedString）

#### 交付物

- [ ] 1个 ClickHouse 迁移脚本
- [ ] 分区管理策略文档
- [ ] 性能测试报告

#### 文件位置

```
apps/opik-backend/src/main/resources/liquibase/db-app-analytics/migrations/
  └─ 000020_add_audit_logs_table.xml
```

---

### 任务 1.3：DAO 层实现 (4天)

**负责人:** 后端开发
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 1.3.1 | UserDAO 实现 | 1天 | CRUD + 查询方法 + 单元测试 |
| 1.3.2 | WorkspaceDAO 和 RoleDAO 实现 | 1天 | CRUD + 查询方法 + 单元测试 |
| 1.3.3 | MemberDAO 和 ApiKeyDAO 实现 | 1天 | 关联查询 + 单元测试 |
| 1.3.4 | SessionDAO 和 AuditLogDAO 实现 | 1天 | 批量操作 + 单元测试 |

#### 验收标准

**功能验收 - UserDAO:**
```java
@Test
void testUserDAO_CRUD() {
    // 1. 创建用户
    User user = User.builder()
        .id(UUID.randomUUID().toString())
        .username("testuser")
        .email("test@example.com")
        .passwordHash("hashed-password")
        .status(UserStatus.ACTIVE)
        .build();
    
    userDAO.save(user);
    
    // 2. 查询用户
    Optional<User> found = userDAO.findById(user.getId());
    assertTrue(found.isPresent());
    assertEquals("testuser", found.get().getUsername());
    
    // 3. 根据用户名查询
    Optional<User> foundByUsername = userDAO.findByUsername("testuser");
    assertTrue(foundByUsername.isPresent());
    
    // 4. 根据邮箱查询
    Optional<User> foundByEmail = userDAO.findByEmail("test@example.com");
    assertTrue(foundByEmail.isPresent());
    
    // 5. 更新用户状态
    userDAO.updateStatus(user.getId(), UserStatus.SUSPENDED);
    User updated = userDAO.findById(user.getId()).get();
    assertEquals(UserStatus.SUSPENDED, updated.getStatus());
    
    // 6. 检查用户名是否存在
    assertTrue(userDAO.existsByUsername("testuser"));
    assertFalse(userDAO.existsByUsername("nonexistent"));
}

@Test
void testUserDAO_Pagination() {
    // 插入10个用户
    for (int i = 0; i < 10; i++) {
        User user = User.builder()
            .id(UUID.randomUUID().toString())
            .username("user" + i)
            .email("user" + i + "@example.com")
            .passwordHash("hash")
            .build();
        userDAO.save(user);
    }
    
    // 分页查询
    List<User> page1 = userDAO.findAll(0, 5);
    assertEquals(5, page1.size());
    
    List<User> page2 = userDAO.findAll(1, 5);
    assertEquals(5, page2.size());
    
    // 验证不重复
    Set<String> ids = new HashSet<>();
    page1.forEach(u -> ids.add(u.getId()));
    page2.forEach(u -> ids.add(u.getId()));
    assertEquals(10, ids.size());
}
```

**功能验收 - WorkspaceMemberDAO:**
```java
@Test
void testWorkspaceMemberDAO_Relations() {
    // 1. 创建测试数据
    String workspaceId = "ws-1";
    String userId = "user-1";
    String roleId = "role-1";
    
    WorkspaceMember member = WorkspaceMember.builder()
        .id(UUID.randomUUID().toString())
        .workspaceId(workspaceId)
        .userId(userId)
        .roleId(roleId)
        .status(MemberStatus.ACTIVE)
        .build();
    
    memberDAO.save(member);
    
    // 2. 查询工作空间成员
    List<WorkspaceMember> members = memberDAO.findByWorkspace(workspaceId);
    assertEquals(1, members.size());
    
    // 3. 查询用户的工作空间
    List<WorkspaceMember> userWorkspaces = memberDAO.findByUser(userId);
    assertEquals(1, userWorkspaces.size());
    
    // 4. 检查成员关系是否存在
    assertTrue(memberDAO.exists(workspaceId, userId));
    assertFalse(memberDAO.exists(workspaceId, "other-user"));
    
    // 5. 更新成员角色
    memberDAO.updateRole(workspaceId, userId, "new-role-id");
    WorkspaceMember updated = memberDAO.findByWorkspaceAndUser(workspaceId, userId).get();
    assertEquals("new-role-id", updated.getRoleId());
}
```

**质量验收:**
```bash
# 1. 运行所有DAO测试
cd apps/opik-backend
mvn test -Dtest=*DAO*

# 2. 生成覆盖率报告
mvn jacoco:report

# 3. 验证覆盖率
# - UserDAO: > 85%
# - WorkspaceDAO: > 85%
# - RoleDAO: > 80%
# - MemberDAO: > 80%
# - ApiKeyDAO: > 80%
# - SessionDAO: > 80%
# - AuditLogDAO: > 75%

# 4. 检查代码质量
mvn spotless:check
mvn pmd:check
```

**性能验收:**
```java
@Test
void testDAO_Performance() {
    // 批量插入性能测试
    long start = System.currentTimeMillis();
    
    for (int i = 0; i < 1000; i++) {
        User user = createTestUser("user" + i);
        userDAO.save(user);
    }
    
    long duration = System.currentTimeMillis() - start;
    assertTrue(duration < 5000, "1000次插入应在5秒内完成");
    
    // 查询性能测试
    start = System.currentTimeMillis();
    
    for (int i = 0; i < 1000; i++) {
        userDAO.findByUsername("user" + (i % 100));
    }
    
    duration = System.currentTimeMillis() - start;
    assertTrue(duration < 2000, "1000次查询应在2秒内完成");
}
```

#### 交付物

- [ ] 8个 DAO 接口和实现类
- [ ] DAO 单元测试套件（覆盖率>80%）
- [ ] 性能测试报告
- [ ] DAO 使用文档

#### 文件位置

```
apps/opik-backend/src/main/java/com/comet/opik/domain/
  ├─ UserDAO.java
  ├─ WorkspaceDAO.java
  ├─ RoleDAO.java
  ├─ WorkspaceMemberDAO.java
  ├─ ProjectMemberDAO.java
  ├─ ApiKeyDAO.java
  ├─ SessionDAO.java
  └─ PasswordResetTokenDAO.java

apps/opik-backend/src/test/java/com/comet/opik/domain/
  ├─ UserDAOTest.java
  ├─ WorkspaceDAOTest.java
  └─ ... (其他测试)
```

---

### 任务 1.4：基础服务实现 (4天)

**负责人:** 后端开发
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 1.4.1 | PasswordService 实现 | 0.5天 | BCrypt 加密 + 强度验证 |
| 1.4.2 | CacheService 实现 | 1.5天 | Redis缓存 + 三防策略 |
| 1.4.3 | SessionCleanupService 实现 | 1天 | 定时清理 + 并发控制 |
| 1.4.4 | AuthMetrics 实现 | 1天 | 监控指标 + Prometheus集成 |

#### 验收标准

**功能验收 - PasswordService:**
```java
@Test
void testPasswordHashing() {
    String plainPassword = "Test@1234";
    
    // 1. 哈希密码
    String hash1 = passwordService.hashPassword(plainPassword);
    assertNotNull(hash1);
    assertNotEquals(plainPassword, hash1);
    assertTrue(hash1.startsWith("$2a$12$"));  // BCrypt cost=12
    
    // 2. 验证密码
    assertTrue(passwordService.verifyPassword(plainPassword, hash1));
    assertFalse(passwordService.verifyPassword("WrongPassword", hash1));
    
    // 3. 每次哈希结果不同（不同salt）
    String hash2 = passwordService.hashPassword(plainPassword);
    assertNotEquals(hash1, hash2);
    assertTrue(passwordService.verifyPassword(plainPassword, hash2));
}

@Test
void testPasswordStrength() {
    // 弱密码
    assertFalse(passwordService.isPasswordStrong("123456"));
    assertFalse(passwordService.isPasswordStrong("password"));
    assertFalse(passwordService.isPasswordStrong("Test123"));  // 缺少特殊字符
    
    // 强密码
    assertTrue(passwordService.isPasswordStrong("Test@1234"));
    assertTrue(passwordService.isPasswordStrong("MyP@ssw0rd"));
    assertTrue(passwordService.isPasswordStrong("Secure!Pass123"));
}

@Test
void testPasswordHashingPerformance() {
    // BCrypt cost=12 应该在100-500ms之间
    long start = System.currentTimeMillis();
    passwordService.hashPassword("Test@1234");
    long duration = System.currentTimeMillis() - start;
    
    assertTrue(duration >= 100 && duration <= 500, 
        "BCrypt cost=12 应该在100-500ms之间，实际: " + duration + "ms");
}
```

**功能验收 - CacheService:**
```java
@Test
void testCacheBasicOperations() {
    String key = "test-key";
    String value = "test-value";
    
    // 1. 设置缓存
    cacheService.set(key, value, 60);
    
    // 2. 获取缓存
    Optional<String> cached = cacheService.get(key);
    assertTrue(cached.isPresent());
    assertEquals(value, cached.get());
    
    // 3. 失效缓存
    cacheService.invalidate(key);
    assertFalse(cacheService.get(key).isPresent());
}

@Test
void testCacheExpiration() throws InterruptedException {
    String key = "expire-key";
    String value = "expire-value";
    
    // 设置2秒过期
    cacheService.set(key, value, 2);
    assertTrue(cacheService.get(key).isPresent());
    
    // 等待3秒
    Thread.sleep(3000);
    
    // 应该已过期
    assertFalse(cacheService.get(key).isPresent());
}

@Test
void testCachePenetrationPrevention() {
    String key = "non-existent-key";
    
    // 模拟加载器返回空值
    Optional<String> result = cacheService.getOrLoad(
        key,
        () -> Optional.empty(),  // 数据库中不存在
        60
    );
    
    assertFalse(result.isPresent());
    
    // 验证空值已被缓存（防止穿透）
    // 下次直接从缓存返回，不会再查询数据库
    Optional<String> cached = cacheService.get(key);
    assertNotNull(cached);  // 缓存存在，但值为空标记
}

@Test
void testCacheBreakdownPrevention() throws Exception {
    String key = "hot-key";
    AtomicInteger dbQueryCount = new AtomicInteger(0);
    
    // 模拟100个并发请求同时访问相同的热点数据
    ExecutorService executor = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(100);
    
    for (int i = 0; i < 100; i++) {
        executor.submit(() -> {
            try {
                cacheService.getOrLoad(key, () -> {
                    dbQueryCount.incrementAndGet();
                    Thread.sleep(100);  // 模拟数据库查询
                    return Optional.of("value");
                }, 60);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await();
    executor.shutdown();
    
    // 验证只有1次数据库查询（分布式锁生效）
    assertTrue(dbQueryCount.get() <= 2, 
        "应该只有1-2次数据库查询，实际: " + dbQueryCount.get());
}

@Test
void testCacheAvalanchePrevention() {
    // 设置100个key，都在60秒后过期
    for (int i = 0; i < 100; i++) {
        cacheService.set("key-" + i, "value-" + i, 60);
    }
    
    // 检查实际TTL分布（应该有随机偏移，防止雪崩）
    Set<Long> ttls = new HashSet<>();
    for (int i = 0; i < 100; i++) {
        Long ttl = redissonClient.getBucket("key-" + i).remainTimeToLive();
        ttls.add(ttl / 1000);  // 转换为秒
    }
    
    // TTL应该分布在54-66秒之间（60±10%）
    assertTrue(ttls.size() > 5, "TTL应该有随机分布，防止同时过期");
}
```

**功能验收 - SessionCleanupService:**
```java
@Test
void testSessionCleanup() throws InterruptedException {
    // 1. 创建过期Session
    Session expiredSession = Session.builder()
        .id(UUID.randomUUID().toString())
        .sessionToken("expired-token")
        .userId("user-1")
        .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
        .build();
    sessionDAO.save(expiredSession);
    
    // 2. 创建有效Session
    Session validSession = Session.builder()
        .id(UUID.randomUUID().toString())
        .sessionToken("valid-token")
        .userId("user-1")
        .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
        .build();
    sessionDAO.save(validSession);
    
    // 3. 触发清理
    sessionCleanupService.cleanupExpiredSessions();
    
    // 4. 验证过期Session已删除
    assertFalse(sessionDAO.findById(expiredSession.getId()).isPresent());
    
    // 5. 验证有效Session仍存在
    assertTrue(sessionDAO.findById(validSession.getId()).isPresent());
}

@Test
void testSessionConcurrencyLimit() {
    String userId = "user-1";
    
    // 创建6个Session（超过最大限制5个）
    List<Session> sessions = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
        Session session = sessionService.createSession(
            userId, "192.168.1." + i, "Chrome"
        );
        sessions.add(session);
    }
    
    // 验证只保留最新的5个Session
    int activeCount = sessionDAO.countActiveSessionsByUser(userId);
    assertEquals(5, activeCount, "应该只保留最新的5个Session");
    
    // 验证最老的Session已被删除
    assertFalse(sessionDAO.findById(sessions.get(0).getId()).isPresent());
}
```

**性能验收:**
```bash
# 1. 缓存命中率测试
# 运行1000次请求，缓存命中率应>80%
mvn test -Dtest=CacheServicePerformanceTest

# 2. Session清理性能测试
# 清理10000个过期Session应在1秒内完成
mvn test -Dtest=SessionCleanupPerformanceTest

# 3. 监控指标测试
# 验证所有指标正常上报到Prometheus
curl http://localhost:8081/metrics | grep -E "(auth_login|cache_hit|session_created)"
```

#### 交付物

- [ ] 4个基础服务实现
- [ ] 单元测试套件（覆盖率>85%）
- [ ] 性能测试报告
- [ ] 缓存策略文档
- [ ] 监控指标文档

---

### 阶段一里程碑验收

**完成标准:**
- ✅ 所有8个MySQL表创建成功
- ✅ ClickHouse审计日志表创建成功
- ✅ 所有8个DAO实现并通过测试（覆盖率>80%）
- ✅ 4个基础服务实现并通过测试（覆盖率>85%）
- ✅ 迁移脚本可正确执行和回滚
- ✅ 缓存命中率达到80%以上

**验收命令:**
```bash
# 1. 数据库迁移验收
mvn liquibase:status  # 应显示所有变更已应用
mysql -u opik -p opik -e "SELECT COUNT(*) FROM users;"  # 应返回1（admin用户）

# 2. DAO测试验收
mvn test -Dtest=*DAO*
mvn jacoco:report
# 打开 target/site/jacoco/index.html 验证覆盖率

# 3. 基础服务测试验收
mvn test -Dtest=PasswordServiceTest,CacheServiceTest,SessionCleanupServiceTest
mvn jacoco:report

# 4. 代码质量验收
mvn spotless:check
mvn pmd:check

# 5. 性能验收
mvn test -Dtest=*PerformanceTest
```

**交付文档:**
- [ ] 数据库设计文档
- [ ] DAO使用文档
- [ ] 缓存策略文档
- [ ] 测试报告
- [ ] 阶段一总结报告

---

## 三、阶段二：认证鉴权核心 (3周)

**目标:** 实现LocalAuthService、用户管理、RBAC权限系统、REST API

### 任务 2.1：LocalAuthService 实现 (5天)

**负责人:** 后端开发（高级）
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 2.1.1 | Session认证逻辑实现 | 2天 | 含指纹验证、缓存优化 |
| 2.1.2 | API Key认证逻辑实现 | 2天 | 含权限范围、缓存优化 |
| 2.1.3 | 公开端点处理 | 0.5天 | PUBLIC模式访问 |
| 2.1.4 | RequestContext扩展 | 0.5天 | 添加权限字段 |

#### 验收标准

**功能验收 - Session认证:**
```java
@Test
void testSessionAuthentication_Success() {
    // 1. 创建测试用户和工作空间
    User user = createTestUser();
    Workspace workspace = createTestWorkspace(user.getId());
    addWorkspaceMember(workspace.getId(), user.getId(), "Workspace Admin");
    
    // 2. 创建Session
    String ipAddress = "192.168.1.100";
    String userAgent = "Mozilla/5.0 Chrome";
    Session session = sessionService.createSession(user.getId(), ipAddress, userAgent);
    
    // 3. 模拟HTTP请求
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString("Comet-Workspace")).thenReturn(workspace.getName());
    
    Cookie sessionCookie = new Cookie("sessionToken", session.getSessionToken());
    
    ContextInfoHolder contextInfo = mock(ContextInfoHolder.class);
    when(contextInfo.uriInfo().getRequestUri()).thenReturn(URI.create("/v1/private/projects"));
    
    // 4. 执行认证
    authService.authenticate(headers, sessionCookie, contextInfo);
    
    // 5. 验证RequestContext
    RequestContext ctx = requestContext.get();
    assertEquals(user.getId(), ctx.getUserId());
    assertEquals(user.getUsername(), ctx.getUserName());
    assertEquals(workspace.getId(), ctx.getWorkspaceId());
    assertEquals(workspace.getName(), ctx.getWorkspaceName());
    assertFalse(ctx.isSystemAdmin());
    assertTrue(ctx.getPermissions().size() > 0);
    assertTrue(ctx.getPermissions().contains(Permission.WORKSPACE_VIEW));
}

@Test
void testSessionAuthentication_InvalidSession() {
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString("Comet-Workspace")).thenReturn("test-workspace");
    
    Cookie invalidCookie = new Cookie("sessionToken", "invalid-token");
    
    // 应抛出 UNAUTHORIZED 异常
    ClientErrorException ex = assertThrows(ClientErrorException.class, () -> {
        authService.authenticate(headers, invalidCookie, contextInfo);
    });
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), ex.getResponse().getStatus());
}

@Test
void testSessionAuthentication_ExpiredSession() {
    // 创建过期Session
    Session expiredSession = Session.builder()
        .sessionToken("expired-token")
        .userId("user-1")
        .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
        .build();
    sessionDAO.save(expiredSession);
    
    Cookie expiredCookie = new Cookie("sessionToken", "expired-token");
    
    // 应抛出 UNAUTHORIZED 异常
    assertThrows(ClientErrorException.class, () -> {
        authService.authenticate(headers, expiredCookie, contextInfo);
    });
}

@Test
void testSessionAuthentication_WorkspaceNotMember() {
    // 用户不是工作空间成员
    User user = createTestUser();
    Workspace workspace = createTestWorkspace("other-user-id");
    Session session = sessionService.createSession(user.getId(), "127.0.0.1", "Chrome");
    
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString("Comet-Workspace")).thenReturn(workspace.getName());
    
    Cookie sessionCookie = new Cookie("sessionToken", session.getSessionToken());
    
    // 应抛出 FORBIDDEN 异常
    ClientErrorException ex = assertThrows(ClientErrorException.class, () -> {
        authService.authenticate(headers, sessionCookie, contextInfo);
    });
    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), ex.getResponse().getStatus());
}

@Test
void testSessionAuthentication_SystemAdminBypass() {
    // 系统管理员可以访问任何工作空间
    User admin = createAdminUser();
    Workspace workspace = createTestWorkspace("other-user-id");
    Session session = sessionService.createSession(admin.getId(), "127.0.0.1", "Chrome");
    
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString("Comet-Workspace")).thenReturn(workspace.getName());
    
    Cookie sessionCookie = new Cookie("sessionToken", session.getSessionToken());
    
    // 应该成功认证
    assertDoesNotThrow(() -> {
        authService.authenticate(headers, sessionCookie, contextInfo);
    });
    
    RequestContext ctx = requestContext.get();
    assertTrue(ctx.isSystemAdmin());
}
```

**功能验收 - API Key认证:**
```java
@Test
void testApiKeyAuthentication_Success() {
    // 1. 创建API Key
    User user = createTestUser();
    Workspace workspace = createTestWorkspace(user.getId());
    addWorkspaceMember(workspace.getId(), user.getId(), "Developer");
    
    ApiKeyResponse apiKeyResponse = apiKeyService.generateApiKey(
        ApiKeyCreateRequest.builder()
            .userId(user.getId())
            .workspaceId(workspace.getId())
            .name("Test API Key")
            .build()
    );
    
    // 2. 模拟HTTP请求
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString(HttpHeaders.AUTHORIZATION))
        .thenReturn(apiKeyResponse.getApiKey());
    when(headers.getHeaderString("Comet-Workspace"))
        .thenReturn(workspace.getName());
    
    // 3. 执行认证
    authService.authenticate(headers, null, contextInfo);
    
    // 4. 验证RequestContext
    RequestContext ctx = requestContext.get();
    assertEquals(user.getId(), ctx.getUserId());
    assertEquals(workspace.getId(), ctx.getWorkspaceId());
    assertTrue(ctx.getPermissions().contains(Permission.PROJECT_CREATE));
}

@Test
void testApiKeyAuthentication_WithPermissionScope() {
    // 创建只读API Key
    ApiKeyResponse apiKeyResponse = apiKeyService.generateApiKey(
        ApiKeyCreateRequest.builder()
            .userId(user.getId())
            .workspaceId(workspace.getId())
            .name("Read-only API Key")
            .permissions(Set.of(Permission.PROJECT_VIEW, Permission.TRACE_VIEW))
            .build()
    );
    
    // 认证
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString(HttpHeaders.AUTHORIZATION))
        .thenReturn(apiKeyResponse.getApiKey());
    when(headers.getHeaderString("Comet-Workspace"))
        .thenReturn(workspace.getName());
    
    authService.authenticate(headers, null, contextInfo);
    
    // 验证权限受限
    RequestContext ctx = requestContext.get();
    assertTrue(ctx.getPermissions().contains(Permission.PROJECT_VIEW));
    assertTrue(ctx.getPermissions().contains(Permission.TRACE_VIEW));
    assertFalse(ctx.getPermissions().contains(Permission.PROJECT_CREATE));
    assertFalse(ctx.getPermissions().contains(Permission.PROJECT_DELETE));
}

@Test
void testApiKeyAuthentication_CacheHit() {
    // 第一次认证
    long start1 = System.nanoTime();
    authService.authenticate(headers, null, contextInfo);
    long duration1 = System.nanoTime() - start1;
    
    // 第二次认证（应该命中缓存）
    long start2 = System.nanoTime();
    authService.authenticate(headers, null, contextInfo);
    long duration2 = System.nanoTime() - start2;
    
    // 缓存命中的响应时间应该至少快50%
    assertTrue(duration2 < duration1 * 0.5, 
        "缓存命中应该更快: 第1次=" + duration1 + "ns, 第2次=" + duration2 + "ns");
}
```

**性能验收:**
```java
@Test
void testAuthentication_Performance() {
    // Session认证性能
    long start = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
        authService.authenticate(headers, sessionCookie, contextInfo);
    }
    long duration = System.currentTimeMillis() - start;
    
    // P95应该<100ms，即1000次<100秒
    assertTrue(duration < 100000, 
        "1000次Session认证应在100秒内完成，实际: " + duration + "ms");
    
    // 平均响应时间
    double avgMs = duration / 1000.0;
    assertTrue(avgMs < 100, 
        "Session认证平均响应时间应<100ms，实际: " + avgMs + "ms");
    
    // API Key认证性能（带缓存）
    start = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
        authService.authenticate(apiKeyHeaders, null, contextInfo);
    }
    duration = System.currentTimeMillis() - start;
    
    // API Key认证有缓存，应该更快
    assertTrue(duration < 50000, 
        "1000次API Key认证（带缓存）应在50秒内完成，实际: " + duration + "ms");
}

@Test
void testAuthentication_ConcurrentPerformance() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch latch = new CountDownLatch(1000);
    AtomicInteger successCount = new AtomicInteger(0);
    
    long start = System.currentTimeMillis();
    
    // 50个线程并发执行1000次认证
    for (int i = 0; i < 1000; i++) {
        executor.submit(() -> {
            try {
                authService.authenticate(headers, sessionCookie, contextInfo);
                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await();
    long duration = System.currentTimeMillis() - start;
    executor.shutdown();
    
    // 1000次并发认证应在30秒内完成
    assertTrue(duration < 30000, 
        "1000次并发认证应在30秒内完成，实际: " + duration + "ms");
    
    // 所有请求都应该成功
    assertEquals(1000, successCount.get(), "所有请求都应该成功");
}
```

#### 交付物

- [ ] LocalAuthService完整实现
- [ ] RequestContext扩展
- [ ] 单元测试套件（覆盖率>90%）
- [ ] 性能测试报告（P95<100ms）
- [ ] 认证流程文档

---

### 任务 2.2：用户管理服务 (4天)

**负责人:** 后端开发
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 2.2.1 | UserService 实现 | 1.5天 | 注册、登录、密码管理 |
| 2.2.2 | SessionService 实现 | 1天 | Session创建、验证、指纹检查 |
| 2.2.3 | ApiKeyService 实现 | 1天 | API Key生成、验证、权限范围 |
| 2.2.4 | 集成测试 | 0.5天 | 端到端流程测试 |

#### 验收标准

**功能验收 - 用户注册登录:**
```java
@Test
void testUserRegistration_Success() {
    UserRegisterRequest request = UserRegisterRequest.builder()
        .username("testuser")
        .email("test@example.com")
        .password("Test@1234")
        .fullName("Test User")
        .build();
    
    User user = userService.register(request);
    
    assertNotNull(user.getId());
    assertEquals("testuser", user.getUsername());
    assertEquals("test@example.com", user.getEmail());
    assertNotEquals("Test@1234", user.getPasswordHash());
    assertEquals(UserStatus.ACTIVE, user.getStatus());
    assertFalse(user.isSystemAdmin());
}

@Test
void testUserLogin_Success() {
    // 先注册
    userService.register(UserRegisterRequest.builder()
        .username("loginuser")
        .email("login@example.com")
        .password("Login@1234")
        .build());
    
    // 登录
    LoginRequest loginRequest = LoginRequest.builder()
        .username("loginuser")
        .password("Login@1234")
        .ipAddress("192.168.1.100")
        .userAgent("Chrome")
        .build();
    
    LoginResponse response = userService.login(loginRequest);
    
    assertNotNull(response.getSessionToken());
    assertNotNull(response.getUser());
    assertEquals("loginuser", response.getUser().getUsername());
    assertNotNull(response.getExpiresAt());
}

@Test
void testUserLogin_InvalidPassword() {
    userService.register(createRegisterRequest("testuser", "Test@1234"));
    
    LoginRequest request = LoginRequest.builder()
        .username("testuser")
        .password("WrongPassword")
        .build();
    
    assertThrows(BadRequestException.class, () -> {
        userService.login(request);
    });
}

@Test
void testChangePassword_Success() {
    User user = userService.register(createRegisterRequest("changeuser", "Old@1234"));
    
    ChangePasswordRequest request = ChangePasswordRequest.builder()
        .oldPassword("Old@1234")
        .newPassword("New@1234")
        .build();
    
    assertDoesNotThrow(() -> {
        userService.changePassword(user.getId(), request);
    });
    
    // 验证新密码可以登录
    LoginResponse response = userService.login(LoginRequest.builder()
        .username("changeuser")
        .password("New@1234")
        .build());
    assertNotNull(response.getSessionToken());
}
```

**集成测试 - 完整流程:**
```java
@Test
void testCompleteAuthenticationFlow() {
    // 1. 用户注册
    UserRegisterRequest registerRequest = UserRegisterRequest.builder()
        .username("flowtest")
        .email("flow@example.com")
        .password("Test@1234")
        .build();
    User user = userService.register(registerRequest);
    
    // 2. 用户登录
    LoginRequest loginRequest = LoginRequest.builder()
        .username("flowtest")
        .password("Test@1234")
        .ipAddress("127.0.0.1")
        .userAgent("Test Agent")
        .build();
    LoginResponse loginResponse = userService.login(loginRequest);
    
    // 3. 创建工作空间
    WorkspaceCreateRequest wsRequest = WorkspaceCreateRequest.builder()
        .name("test-workspace")
        .displayName("Test Workspace")
        .build();
    Workspace workspace = workspaceService.createWorkspace(wsRequest, user.getId());
    
    // 4. 生成API Key
    ApiKeyCreateRequest apiKeyRequest = ApiKeyCreateRequest.builder()
        .userId(user.getId())
        .workspaceId(workspace.getId())
        .name("Test API Key")
        .build();
    ApiKeyResponse apiKey = apiKeyService.generateApiKey(apiKeyRequest);
    
    // 5. 使用Session Token访问
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.getHeaderString("Comet-Workspace")).thenReturn(workspace.getName());
    Cookie sessionCookie = new Cookie("sessionToken", loginResponse.getSessionToken());
    
    assertDoesNotThrow(() -> {
        authService.authenticate(headers, sessionCookie, contextInfo);
    });
    
    // 6. 使用API Key访问
    when(headers.getHeaderString(HttpHeaders.AUTHORIZATION))
        .thenReturn(apiKey.getApiKey());
    
    assertDoesNotThrow(() -> {
        authService.authenticate(headers, null, contextInfo);
    });
    
    // 7. 登出
    assertDoesNotThrow(() -> {
        userService.logout(loginResponse.getSessionToken());
    });
    
    // 8. 验证Session已失效
    assertThrows(ClientErrorException.class, () -> {
        authService.authenticate(headers, sessionCookie, contextInfo);
    });
}
```

#### 交付物

- [ ] UserService、SessionService、ApiKeyService 完整实现
- [ ] 单元测试（覆盖率>85%）
- [ ] 集成测试套件
- [ ] API文档更新

---

### 任务 2.3：工作空间管理服务 (3天)

**负责人:** 后端开发
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 2.3.1 | WorkspaceService 实现 | 1天 | CRUD + 配额管理 |
| 2.3.2 | WorkspaceMemberService 实现 | 1天 | 成员管理 + 角色分配 |
| 2.3.3 | ProjectMemberService 实现 | 0.5天 | 项目成员管理 |
| 2.3.4 | 集成测试 | 0.5天 | 成员权限测试 |

#### 验收标准

**执行命令验收:**
```bash
# 运行工作空间服务测试
mvn test -Dtest=WorkspaceServiceTest,WorkspaceMemberServiceTest

# 验证工作空间隔离
mvn test -Dtest=WorkspaceIsolationTest

# 验证成员权限
mvn test -Dtest=WorkspaceMemberPermissionTest
```

#### 交付物

- [ ] 工作空间管理服务实现
- [ ] 成员管理服务实现
- [ ] 测试套件（覆盖率>80%）

---

### 任务 2.4：RBAC 权限系统 (5天)

**负责人:** 后端开发（高级）
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 2.4.1 | Permission 枚举定义 | 0.5天 | 30+权限定义 |
| 2.4.2 | RoleService 实现 | 1天 | 内置角色 + 自定义角色 |
| 2.4.3 | PermissionService 实现 | 1.5天 | 权限计算 + 三级检查 |
| 2.4.4 | PermissionInterceptor 实现 | 1天 | AOP拦截 + 性能优化 |
| 2.4.5 | 权限测试矩阵 | 1天 | 所有角色权限组合测试 |

#### 验收标准

**权限测试矩阵:**
```java
@Test
void testPermissionMatrix() {
    // 系统管理员 - 全部权限
    testUserPermissions("System Admin", Permission.values());
    
    // 工作空间管理员 - 工作空间内全部权限
    testUserPermissions("Workspace Admin", 
        Permission.WORKSPACE_ADMIN,
        Permission.PROJECT_CREATE,
        Permission.TRACE_CREATE,
        // ... 所有工作空间级和项目级权限
    );
    
    // 开发者 - 创建和编辑权限
    testUserPermissions("Developer",
        Permission.PROJECT_CREATE,
        Permission.TRACE_CREATE,
        Permission.DATASET_CREATE
        // ... 无删除权限
    );
    
    // 查看者 - 只读权限
    testUserPermissions("Viewer",
        Permission.PROJECT_VIEW,
        Permission.TRACE_VIEW,
        Permission.DATASET_VIEW
        // ... 无创建/编辑/删除权限
    );
}

@Test
void testPermissionInheritance() {
    // 工作空间管理员应该自动拥有所有项目的权限
    User wsAdmin = createWorkspaceAdmin();
    Project project = createProject();
    
    assertTrue(permissionService.hasProjectPermission(
        wsAdmin.getId(), project.getId(), 
        new Permission[]{Permission.TRACE_DELETE}, true
    ));
}

@Test
void testPermissionOverride() {
    // 项目级权限可以覆盖工作空间级权限
    User user = createUser("Developer");  // 工作空间级
    Project project = createProject();
    
    // 将用户添加为项目的 Viewer
    addProjectMember(project.getId(), user.getId(), "Project Viewer");
    
    // 在项目级别，用户只有查看权限
    assertFalse(permissionService.hasProjectPermission(
        user.getId(), project.getId(),
        new Permission[]{Permission.TRACE_CREATE}, true
    ));
}
```

**性能验收:**
```java
@Test
void testPermissionCheckPerformance() {
    // 权限检查应该<50ms (P95)
    long start = System.currentTimeMillis();
    
    for (int i = 0; i < 1000; i++) {
        permissionService.hasWorkspacePermission(
            userId, workspaceId,
            new Permission[]{Permission.PROJECT_CREATE}, true
        );
    }
    
    long duration = System.currentTimeMillis() - start;
    double avgMs = duration / 1000.0;
    
    assertTrue(avgMs < 50, 
        "权限检查平均响应时间应<50ms，实际: " + avgMs + "ms");
}
```

#### 交付物

- [ ] Permission 枚举（30+权限）
- [ ] RBAC 完整实现
- [ ] 权限测试矩阵
- [ ] 性能测试报告
- [ ] 权限设计文档

---

### 任务 2.5：REST API 实现 (4天)

**负责人:** 后端开发
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 2.5.1 | 认证 API | 1天 | 注册/登录/登出/密码管理 |
| 2.5.2 | 工作空间管理 API | 1天 | CRUD + 成员管理 |
| 2.5.3 | API Key 管理 API | 1天 | CRUD + 撤销 |
| 2.5.4 | 角色管理 API | 0.5天 | 查询 + 自定义角色 |
| 2.5.5 | Swagger 文档 | 0.5天 | OpenAPI 规范 |

#### 验收标准

**API 功能测试:**
```bash
# 1. 注册用户
curl -X POST http://localhost:8080/v1/public/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "apitest",
    "email": "apitest@example.com",
    "password": "Test@1234",
    "fullName": "API Test User"
  }'

# 2. 登录
curl -X POST http://localhost:8080/v1/public/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "apitest",
    "password": "Test@1234"
  }' \
  -c cookies.txt

# 3. 创建工作空间（使用Session）
curl -X POST http://localhost:8080/v1/private/workspaces \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "name": "api-test-workspace",
    "displayName": "API Test Workspace"
  }'

# 4. 生成API Key
curl -X POST http://localhost:8080/v1/private/api-keys \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -H "Comet-Workspace: api-test-workspace" \
  -d '{
    "name": "Test API Key",
    "description": "For API testing"
  }'

# 5. 使用API Key访问
curl -X GET http://localhost:8080/v1/private/workspaces \
  -H "Authorization: Bearer opik_xxxxx" \
  -H "Comet-Workspace: api-test-workspace"

# 6. 权限测试（普通用户访问admin API应该403）
curl -X GET http://localhost:8080/v1/private/admin/users \
  -H "Authorization: Bearer opik_xxxxx"
# 应返回 403 Forbidden

# 7. Swagger文档访问
curl http://localhost:8080/api/swagger.json
curl http://localhost:8080/api/swagger.yaml
```

**API 集成测试:**
```java
@Test
void testAuthenticationAPI() {
    // 注册
    Response registerResp = target("/v1/public/auth/register")
        .request()
        .post(Entity.json(registerRequest));
    assertEquals(201, registerResp.getStatus());
    
    // 登录
    Response loginResp = target("/v1/public/auth/login")
        .request()
        .post(Entity.json(loginRequest));
    assertEquals(200, loginResp.getStatus());
    
    LoginResponse loginData = loginResp.readEntity(LoginResponse.class);
    assertNotNull(loginData.getSessionToken());
}

@Test
void testWorkspaceAPI_CRUD() {
    // Create
    Response createResp = target("/v1/private/workspaces")
        .request()
        .cookie("sessionToken", sessionToken)
        .post(Entity.json(createRequest));
    assertEquals(201, createResp.getStatus());
    
    Workspace workspace = createResp.readEntity(Workspace.class);
    
    // Read
    Response getResp = target("/v1/private/workspaces/" + workspace.getId())
        .request()
        .cookie("sessionToken", sessionToken)
        .get();
    assertEquals(200, getResp.getStatus());
    
    // Update
    Response updateResp = target("/v1/private/workspaces/" + workspace.getId())
        .request()
        .cookie("sessionToken", sessionToken)
        .put(Entity.json(updateRequest));
    assertEquals(200, updateResp.getStatus());
    
    // Delete
    Response deleteResp = target("/v1/private/workspaces/" + workspace.getId())
        .request()
        .cookie("sessionToken", sessionToken)
        .delete();
    assertEquals(204, deleteResp.getStatus());
}
```

#### 交付物

- [ ] 20+ REST API 端点
- [ ] Swagger/OpenAPI 文档
- [ ] Postman 测试集合
- [ ] API 集成测试（覆盖率>80%）
- [ ] API 使用文档

---

### 阶段二里程碑验收

**完成标准:**
- ✅ LocalAuthService 实现并通过测试（P95<100ms）
- ✅ 用户可以成功注册和登录
- ✅ RBAC 权限系统正常工作
- ✅ 所有 REST API 实现并通过测试
- ✅ API 文档完整
- ✅ 集成测试通过率 100%

**验收命令:**
```bash
# 1. 认证服务验收
mvn test -Dtest=LocalAuthServiceTest
mvn test -Dtest=UserServiceTest,SessionServiceTest,ApiKeyServiceTest

# 2. RBAC 验收
mvn test -Dtest=PermissionServiceTest,PermissionInterceptorTest
mvn test -Dtest=PermissionMatrixTest

# 3. API 验收
mvn verify -P integration-tests
# 应通过所有 API 集成测试

# 4. 性能验收
mvn test -Dtest=*PerformanceTest
# - 认证 P95 < 100ms
# - 权限检查 P95 < 50ms

# 5. 文档验收
curl http://localhost:8080/api/swagger.json | jq .
# 应返回完整的 OpenAPI 规范
```

**交付文档:**
- [ ] 认证服务设计文档
- [ ] RBAC 权限设计文档
- [ ] API 文档（Swagger）
- [ ] 测试报告
- [ ] 性能测试报告
- [ ] 阶段二总结报告

---

## 三、阶段三：审计日志系统 (1.5周)

**目标:** 实现审计日志记录、查询、导出功能

### 任务 3.1：审计日志核心实现 (3天)

**负责人:** 后端开发
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 3.1.1 | @Auditable 注解实现 | 0.5天 | 注解定义 + 参数配置 |
| 3.1.2 | AuditInterceptor 实现 | 1.5天 | AOP拦截 + 异步批量写入 |
| 3.1.3 | AuditLogService 实现 | 0.5天 | 批量写入 ClickHouse |
| 3.1.4 | 为关键方法添加注解 | 0.5天 | 标记所有CRUD操作 |

#### 验收标准

**功能验收:**
```java
@Test
void testAuditLog_CreateOperation() {
    // 执行创建操作
    Project project = projectService.createProject(createRequest);
    
    // 等待异步批量写入
    await().atMost(10, SECONDS).until(() -> {
        List<AuditLog> logs = auditLogDAO.findByResourceId(project.getId());
        return !logs.isEmpty();
    });
    
    // 验证审计日志
    List<AuditLog> logs = auditLogDAO.findByResourceId(project.getId());
    assertEquals(1, logs.size());
    
    AuditLog log = logs.get(0);
    assertEquals("Create Project", log.getAction());
    assertEquals("project", log.getResourceType());
    assertEquals(project.getId(), log.getResourceId());
    assertEquals(Operation.CREATE, log.getOperation());
    assertEquals(AuditStatus.SUCCESS, log.getStatus());
    assertEquals(userId, log.getUserId());
    assertEquals(workspaceId, log.getWorkspaceId());
    assertNotNull(log.getTimestamp());
}

@Test
void testAuditLog_ReadOperation() {
    // 查看操作也应该被记录
    projectService.getProject(projectId);
    
    await().atMost(10, SECONDS).until(() -> {
        List<AuditLog> logs = auditLogDAO.findByUserAndOperation(
            userId, Operation.READ
        );
        return logs.stream().anyMatch(l -> 
            l.getAction().equals("View Project") && 
            l.getResourceId().equals(projectId.toString())
        );
    });
}

@Test
void testAuditLog_FailedOperation() {
    // 执行会失败的操作
    try {
        projectService.deleteProject(UUID.randomUUID());  // 不存在的项目
    } catch (NotFoundException e) {
        // 预期异常
    }
    
    // 验证失败操作也被记录
    await().atMost(10, SECONDS).until(() -> {
        List<AuditLog> logs = auditLogDAO.findByStatus(AuditStatus.FAILURE);
        return !logs.isEmpty();
    });
}

@Test
void testAuditLog_WithChanges() {
    // 更新操作应该记录变更详情
    Project before = projectService.getProject(projectId);
    
    UpdateRequest request = UpdateRequest.builder()
        .name("Updated Name")
        .description("Updated Description")
        .build();
    
    projectService.updateProject(projectId, request);
    
    await().atMost(10, SECONDS).until(() -> {
        List<AuditLog> logs = auditLogDAO.findByResourceId(projectId.toString());
        return logs.stream().anyMatch(l -> 
            l.getOperation() == Operation.UPDATE &&
            l.getChanges() != null
        );
    });
    
    // 验证变更详情
    AuditLog log = auditLogDAO.findByResourceId(projectId.toString())
        .stream()
        .filter(l -> l.getOperation() == Operation.UPDATE)
        .findFirst()
        .get();
    
    assertNotNull(log.getChanges());
    assertTrue(log.getChanges().contains("\"name\""));
    assertTrue(log.getChanges().contains("Updated Name"));
}
```

**性能验收:**
```java
@Test
void testAuditLog_PerformanceImpact() {
    // 测试审计日志对主流程的影响
    
    // 1. 不带审计日志的基准测试
    long baselineStart = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
        projectService.getProjectWithoutAudit(projectId);
    }
    long baselineDuration = System.currentTimeMillis() - baselineStart;
    
    // 2. 带审计日志的测试
    long auditStart = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
        projectService.getProject(projectId);
    }
    long auditDuration = System.currentTimeMillis() - auditStart;
    
    // 3. 验证影响小于5ms/次
    long overhead = (auditDuration - baselineDuration) / 100;
    assertTrue(overhead < 5, 
        "审计日志影响应<5ms/次，实际: " + overhead + "ms");
}

@Test
void testAuditLog_BatchWritePerformance() {
    // 批量写入性能测试
    List<AuditLog> logs = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        logs.add(createTestAuditLog());
    }
    
    long start = System.currentTimeMillis();
    auditLogService.batchWrite(logs);
    long duration = System.currentTimeMillis() - start;
    
    // 1000条日志应在1秒内写入完成
    assertTrue(duration < 1000, 
        "1000条审计日志应在1秒内写入，实际: " + duration + "ms");
}
```

#### 交付物

- [ ] @Auditable 注解
- [ ] AuditInterceptor 实现
- [ ] AuditLogService 实现
- [ ] 性能测试报告
- [ ] 审计日志设计文档

---

### 任务 3.2：审计日志查询和导出 (2天)

**负责人:** 后端开发
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 3.2.1 | 查询 API 实现 | 1天 | 分页、过滤、排序 |
| 3.2.2 | 导出功能实现 | 0.5天 | CSV、JSON 格式 |
| 3.2.3 | 统计功能实现 | 0.5天 | 操作统计、用户活跃度 |

#### 验收标准

**功能验收:**
```bash
# 1. 查询审计日志（分页）
curl "http://localhost:8080/v1/private/admin/audit-logs?page=0&size=20" \
  -H "Authorization: Bearer <admin-api-key>"

# 2. 按用户过滤
curl "http://localhost:8080/v1/private/admin/audit-logs?userId=user-123&page=0&size=20" \
  -H "Authorization: Bearer <admin-api-key>"

# 3. 按操作类型过滤
curl "http://localhost:8080/v1/private/admin/audit-logs?operation=CREATE&page=0&size=20" \
  -H "Authorization: Bearer <admin-api-key>"

# 4. 时间范围查询
curl "http://localhost:8080/v1/private/admin/audit-logs?startTime=2024-01-01T00:00:00Z&endTime=2024-01-31T23:59:59Z" \
  -H "Authorization: Bearer <admin-api-key>"

# 5. 复合条件查询
curl "http://localhost:8080/v1/private/admin/audit-logs?userId=user-123&operation=DELETE&startTime=2024-01-01T00:00:00Z" \
  -H "Authorization: Bearer <admin-api-key>"

# 6. 导出为CSV
curl -X POST http://localhost:8080/v1/private/admin/audit-logs/export \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-api-key>" \
  -d '{
    "format": "CSV",
    "filters": {
      "startTime": "2024-01-01T00:00:00Z",
      "endTime": "2024-01-31T23:59:59Z"
    }
  }' \
  -o audit_logs.csv

# 7. 统计信息
curl http://localhost:8080/v1/private/admin/audit-logs/stats \
  -H "Authorization: Bearer <admin-api-key>"
```

**性能验收:**
```java
@Test
void testAuditLogQuery_Performance() {
    // 插入10万条测试数据
    insertTestAuditLogs(100000);
    
    // 查询性能测试
    long start = System.currentTimeMillis();
    
    Page<AuditLog> page = auditLogService.query(AuditLogQuery.builder()
        .userId("user-123")
        .operation(Operation.CREATE)
        .startTime(Instant.now().minus(30, ChronoUnit.DAYS))
        .page(0)
        .size(100)
        .build());
    
    long duration = System.currentTimeMillis() - start;
    
    // 查询应在500ms内完成
    assertTrue(duration < 500, 
        "审计日志查询应在500ms内完成，实际: " + duration + "ms");
}

@Test
void testAuditLogExport_LargeDataset() {
    // 导出10万条数据
    long start = System.currentTimeMillis();
    
    File exportFile = auditLogService.export(AuditLogQuery.builder()
        .startTime(Instant.now().minus(30, ChronoUnit.DAYS))
        .format(ExportFormat.CSV)
        .build());
    
    long duration = System.currentTimeMillis() - start;
    
    // 导出应在30秒内完成
    assertTrue(duration < 30000, 
        "导出10万条日志应在30秒内完成，实际: " + duration + "ms");
    
    // 验证文件大小
    assertTrue(exportFile.length() > 0);
}
```

#### 交付物

- [ ] 审计日志查询 API
- [ ] 导出功能（CSV/JSON）
- [ ] 统计功能
- [ ] 性能测试报告

---

### 阶段三里程碑验收

**完成标准:**
- ✅ 所有关键操作被正确记录到审计日志
- ✅ 审计日志查询功能正常
- ✅ 导出功能正常（支持CSV和JSON）
- ✅ 性能达标（影响<5ms，查询P95<500ms）
- ✅ 统计功能正常

**验收命令:**
```bash
# 1. 功能验收
mvn test -Dtest=AuditLogServiceTest,AuditInterceptorTest

# 2. 性能验收
mvn test -Dtest=AuditLogPerformanceTest

# 3. 端到端测试
# 执行一系列操作，然后验证审计日志
./scripts/test-audit-log-e2e.sh

# 4. 导出测试
curl -X POST http://localhost:8080/v1/private/admin/audit-logs/export \
  -H "Authorization: Bearer <admin-api-key>" \
  -d '{"format":"CSV"}' \
  -o test_export.csv

# 验证导出文件
wc -l test_export.csv  # 应有数据行
head -5 test_export.csv  # 检查格式
```

**交付文档:**
- [ ] 审计日志设计文档
- [ ] 查询和导出功能文档
- [ ] 性能测试报告
- [ ] 阶段三总结报告

---

## 四、阶段四：前端管理界面 (2.5周)

**目标:** 实现认证页面、工作空间管理、管理员页面、国际化

### 任务 4.1：认证页面实现 (4天)

**负责人:** 前端开发
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 4.1.1 | 登录页面 | 1天 | 表单 + 验证 + 错误处理 |
| 4.1.2 | 注册页面 | 1天 | 表单 + 密码强度 + 验证 |
| 4.1.3 | 忘记密码页面 | 0.5天 | 邮箱输入 + 提示 |
| 4.1.4 | 重置密码页面 | 0.5天 | 新密码输入 + 验证 |
| 4.1.5 | E2E 测试 | 1天 | Playwright 测试 |

#### 验收标准

**E2E 测试:**
```typescript
// tests/auth.spec.ts
import { test, expect } from '@playwright/test';

test.describe('用户认证流程', () => {
  test('用户注册流程', async ({ page }) => {
    // 1. 访问注册页面
    await page.goto('/register');
    
    // 2. 填写表单
    await page.fill('[name="username"]', 'e2etest');
    await page.fill('[name="email"]', 'e2etest@example.com');
    await page.fill('[name="password"]', 'Test@1234');
    await page.fill('[name="confirmPassword"]', 'Test@1234');
    
    // 3. 验证密码强度提示
    const strengthIndicator = page.locator('.password-strength');
    await expect(strengthIndicator).toContainText('强');
    
    // 4. 提交表单
    await page.click('button[type="submit"]');
    
    // 5. 应跳转到登录页或自动登录
    await expect(page).toHaveURL(/\/(login|default)/);
  });

  test('用户登录流程', async ({ page }) => {
    // 1. 访问登录页面
    await page.goto('/login');
    
    // 2. 填写表单
    await page.fill('[name="username"]', 'e2etest');
    await page.fill('[name="password"]', 'Test@1234');
    
    // 3. 提交表单
    await page.click('button[type="submit"]');
    
    // 4. 应跳转到默认工作空间
    await expect(page).toHaveURL(/\/default/);
    
    // 5. 验证用户信息显示
    await expect(page.locator('[data-testid="user-menu"]')).toContainText('e2etest');
  });

  test('登录验证 - 错误密码', async ({ page }) => {
    await page.goto('/login');
    
    await page.fill('[name="username"]', 'e2etest');
    await page.fill('[name="password"]', 'WrongPassword');
    await page.click('button[type="submit"]');
    
    // 应显示错误提示
    await expect(page.locator('.error-message')).toContainText('用户名或密码错误');
  });

  test('密码强度验证', async ({ page }) => {
    await page.goto('/register');
    
    // 弱密码
    await page.fill('[name="password"]', '123456');
    await expect(page.locator('.password-strength')).toContainText('弱');
    await expect(page.locator('button[type="submit"]')).toBeDisabled();
    
    // 中等密码
    await page.fill('[name="password"]', 'Test123');
    await expect(page.locator('.password-strength')).toContainText('中');
    
    // 强密码
    await page.fill('[name="password"]', 'Test@1234');
    await expect(page.locator('.password-strength')).toContainText('强');
    await expect(page.locator('button[type="submit"]')).not.toBeDisabled();
  });

  test('响应式设计', async ({ page }) => {
    // 测试移动端
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/login');
    
    // 验证布局正常
    await expect(page.locator('form')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });
});
```

#### 交付物

- [ ] 4个认证页面（登录/注册/忘记密码/重置密码）
- [ ] 响应式设计（移动端适配）
- [ ] E2E 测试套件
- [ ] 页面截图/录屏

---

### 任务 4.2：工作空间管理页面 (3天)

**负责人:** 前端开发
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 4.2.1 | 工作空间列表和切换 | 1天 | 下拉菜单 + 切换逻辑 |
| 4.2.2 | 工作空间设置页面 | 1天 | 编辑表单 + 配额管理 |
| 4.2.3 | 成员管理页面 | 1天 | 成员列表 + 添加/移除/角色修改 |

#### 验收标准

**E2E 测试:**
```typescript
test('创建工作空间', async ({ page }) => {
  await loginAsUser(page, 'e2etest');
  await page.goto('/workspaces');
  
  // 点击创建按钮
  await page.click('[data-testid="create-workspace"]');
  
  // 填写表单
  await page.fill('[name="name"]', 'test-workspace');
  await page.fill('[name="displayName"]', 'Test Workspace');
  await page.fill('[name="description"]', 'Test Description');
  
  // 提交
  await page.click('button[type="submit"]');
  
  // 验证创建成功
  await expect(page.locator('.workspace-list')).toContainText('test-workspace');
});

test('添加工作空间成员', async ({ page }) => {
  await loginAsWorkspaceAdmin(page);
  await page.goto('/test-workspace/members');
  
  // 点击添加成员
  await page.click('[data-testid="add-member"]');
  
  // 搜索用户
  await page.fill('[name="username"]', 'newuser');
  await page.click('[data-testid="search-user"]');
  
  // 选择角色
  await page.selectOption('[name="role"]', 'Developer');
  
  // 添加
  await page.click('button[type="submit"]');
  
  // 验证成员已添加
  await expect(page.locator('.member-list')).toContainText('newuser');
  await expect(page.locator('.member-list')).toContainText('Developer');
});

test('修改成员角色', async ({ page }) => {
  await loginAsWorkspaceAdmin(page);
  await page.goto('/test-workspace/members');
  
  // 点击编辑按钮
  await page.click('[data-member="newuser"] [data-action="edit"]');
  
  // 修改角色
  await page.selectOption('[name="role"]', 'Viewer');
  await page.click('button[type="submit"]');
  
  // 验证角色已更新
  await expect(page.locator('[data-member="newuser"]')).toContainText('Viewer');
});
```

#### 交付物

- [ ] 工作空间管理完整功能
- [ ] 成员管理界面
- [ ] E2E 测试
- [ ] 功能演示视频

---

### 任务 4.3：管理员页面 (3天)

**负责人:** 前端开发
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 4.3.1 | 用户管理页面 | 1天 | 列表 + 搜索 + CRUD |
| 4.3.2 | 角色管理页面 | 1天 | 列表 + 自定义角色 |
| 4.3.3 | 审计日志页面 | 1天 | 列表 + 过滤 + 导出 |

#### 交付物

- [ ] 管理员页面完整功能
- [ ] 审计日志查询界面
- [ ] E2E 测试

---

### 任务 4.4：国际化 (2天)

**负责人:** 前端开发
**优先级:** P2

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 4.4.1 | 提取文本到i18n文件 | 1天 | en-US.json + zh-CN.json |
| 4.4.2 | 实现语言切换 | 0.5天 | 下拉选择器 + 持久化 |
| 4.4.3 | 翻译补充 | 0.5天 | 中文翻译 |

#### 验收标准

```typescript
test('语言切换', async ({ page }) => {
  await page.goto('/');
  
  // 默认英文
  await expect(page.locator('h1')).toContainText('Login');
  
  // 切换到中文
  await page.click('[data-testid="language-selector"]');
  await page.click('[data-value="zh-CN"]');
  
  // 验证语言已切换
  await expect(page.locator('h1')).toContainText('登录');
  
  // 刷新页面，语言应该保持
  await page.reload();
  await expect(page.locator('h1')).toContainText('登录');
});
```

#### 交付物

- [ ] 完整的中英文翻译文件
- [ ] 语言切换功能
- [ ] 翻译覆盖率100%

---

### 阶段四里程碑验收

**完成标准:**
- ✅ 所有认证页面实现并通过E2E测试
- ✅ 工作空间管理功能完整
- ✅ 管理员页面功能完整
- ✅ 支持中英文切换
- ✅ 响应式设计良好
- ✅ E2E测试通过率100%

**验收命令:**
```bash
# 1. 运行E2E测试
cd apps/opik-frontend
npm run test:e2e

# 2. 检查覆盖率
npm run test:e2e -- --reporter=html

# 3. 视觉回归测试
npm run test:visual

# 4. 构建验证
npm run build
npm run preview
```

**交付文档:**
- [ ] 前端组件文档
- [ ] E2E测试报告
- [ ] 功能演示视频
- [ ] 阶段四总结报告

---

## 五、阶段五：测试与优化 (2周)

**目标:** 完成单元测试、集成测试、E2E测试、性能测试、安全测试

### 任务 5.1：测试补充 (5天)

**负责人:** 后端+前端+QA
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 5.1.1 | 单元测试补充 | 2天 | 覆盖率>80% |
| 5.1.2 | 集成测试补充 | 2天 | API流程测试 |
| 5.1.3 | E2E测试补充 | 1天 | 关键用户流程 |

#### 验收标准

```bash
# 1. 单元测试覆盖率
mvn jacoco:report
open target/site/jacoco/index.html
# 验证: 整体>80%, Service层>85%, DAO层>80%

# 2. 集成测试
mvn verify -P integration-tests
# 所有测试应通过

# 3. E2E测试
npm run test:e2e
# 通过率应100%
```

#### 交付物

- [ ] 完整的测试套件
- [ ] 测试覆盖率报告
- [ ] 测试文档

---

### 任务 5.2：性能测试和优化 (3天)

**负责人:** 后端开发（高级）
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 5.2.1 | 基准性能测试 | 1天 | JMeter压力测试 |
| 5.2.2 | 性能瓶颈分析 | 1天 | Profiling分析 |
| 5.2.3 | 性能优化 | 1天 | 慢查询优化、缓存调优 |

#### 验收标准

**性能指标:**
```
1. 认证接口
   - P50: < 50ms
   - P95: < 100ms
   - P99: < 200ms
   - QPS: > 500

2. 权限检查
   - P95: < 50ms
   - 缓存命中率: > 80%

3. 审计日志
   - 写入影响: < 5ms/次
   - 查询P95: < 500ms

4. 并发性能
   - 100并发用户: 稳定运行
   - 错误率: < 0.1%
```

**压力测试:**
```bash
# 使用JMeter进行压力测试
jmeter -n -t auth_load_test.jmx -l results.jtl

# 分析结果
jmeter -g results.jtl -o performance_report/

# 打开报告
open performance_report/index.html
```

#### 交付物

- [ ] 性能测试报告
- [ ] 性能优化建议
- [ ] 优化前后对比

---

### 任务 5.3：安全测试 (3天)

**负责人:** 安全工程师 / 后端Lead
**优先级:** P0

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 5.3.1 | 渗透测试 | 1天 | OWASP ZAP扫描 |
| 5.3.2 | 依赖漏洞扫描 | 0.5天 | OWASP Dependency Check |
| 5.3.3 | 代码安全审查 | 1天 | SpotBugs + 手动审查 |
| 5.3.4 | 漏洞修复 | 0.5天 | 高危漏洞修复 |

#### 验收标准

```bash
# 1. OWASP ZAP扫描
zap-cli quick-scan http://localhost:8080
# 应无高危漏洞

# 2. 依赖漏洞扫描
mvn org.owasp:dependency-check-maven:check
# 应无高危漏洞

# 3. SQL注入测试
sqlmap -u "http://localhost:8080/v1/private/users?id=1" \
  --cookie="sessionToken=xxx"
# 应无SQL注入漏洞

# 4. XSS测试
# 在所有输入框测试XSS payload
# 应全部被正确转义

# 5. CSRF测试
# 验证所有Session认证的接口都有CSRF保护
```

#### 交付物

- [ ] 安全测试报告
- [ ] 漏洞列表和修复记录
- [ ] 安全加固建议

---

### 任务 5.4：文档编写 (3天)

**负责人:** 全员
**优先级:** P1

#### 子任务

| ID | 任务 | 工时 | 说明 |
|----|------|------|------|
| 5.4.1 | API文档完善 | 0.5天 | Swagger补充和示例 |
| 5.4.2 | 部署文档 | 1天 | 环境要求、配置、迁移 |
| 5.4.3 | 用户手册 | 1天 | 功能说明、操作指南 |
| 5.4.4 | 开发者文档 | 0.5天 | 架构、扩展指南 |

#### 交付物

- [ ] API文档（Swagger）
- [ ] 部署文档
- [ ] 用户手册（中英文）
- [ ] 开发者文档

---

### 阶段五里程碑验收

**完成标准:**
- ✅ 单元测试覆盖率>80%
- ✅ 集成测试通过率100%
- ✅ E2E测试通过率100%
- ✅ 性能指标达标
- ✅ 无高危安全漏洞
- ✅ 文档完整

**最终验收命令:**
```bash
# 1. 完整测试套件
mvn clean verify
npm run test:all

# 2. 覆盖率检查
mvn jacoco:report
# 整体>80%

# 3. 性能测试
jmeter -n -t full_load_test.jmx -l results.jtl

# 4. 安全扫描
mvn org.owasp:dependency-check-maven:check
zap-cli quick-scan http://localhost:8080

# 5. 构建Docker镜像
docker build -t opik:multi-tenant .
docker run -d -p 8080:8080 opik:multi-tenant

# 6. 健康检查
curl http://localhost:8080/health-check
```

**交付文档:**
- [ ] 测试总结报告
- [ ] 性能测试报告
- [ ] 安全测试报告
- [ ] 完整项目文档
- [ ] 项目交接文档

---

## 四、项目管理

### 4.1 每日站会

**时间:** 每天早上 10:00
**时长:** 15分钟
**内容:**
- 昨天完成了什么
- 今天计划做什么
- 遇到什么阻塞

### 4.2 周度评审

**时间:** 每周五下午 3:00
**时长:** 1小时
**内容:**
- 本周进度回顾
- 下周计划确认
- 风险识别和应对
- 演示本周成果

### 4.3 代码评审

**要求:**
- 所有代码必须至少1人review后才能合并
- 评审重点：功能正确性、代码质量、测试覆盖
- 评审工具：GitHub Pull Request

### 4.4 CI/CD流程

**自动化流程:**
```yaml
# 每次提交自动执行:
1. 编译检查
2. 代码格式检查 (Spotless)
3. 静态代码分析 (PMD, SpotBugs)
4. 单元测试
5. 覆盖率检查
6. 依赖漏洞扫描

# 合并到main分支后:
7. 集成测试
8. E2E测试
9. 性能测试
10. 构建Docker镜像
```

---

## 五、风险管理

### 5.1 风险登记表

| 风险ID | 风险描述 | 影响 | 概率 | 应对措施 | 责任人 |
|--------|---------|------|------|---------|--------|
| R1 | 数据库迁移失败 | 高 | 低 | 充分测试，准备回滚脚本 | 后端Lead |
| R2 | 性能不达标 | 中 | 中 | 早期性能测试，及时优化 | 后端Lead |
| R3 | 权限设计缺陷 | 高 | 低 | 详细设计评审，充分测试 | 架构师 |
| R4 | 前后端进度不同步 | 中 | 中 | 定期沟通，Mock API | PM |
| R5 | 人员变动 | 高 | 低 | 文档完善，知识共享 | PM |

### 5.2 问题跟踪

使用GitHub Issues跟踪所有问题：
- **Bug**: 功能缺陷
- **Enhancement**: 功能改进
- **Question**: 技术疑问
- **Blocker**: 阻塞性问题（最高优先级）

---

## 六、质量保证

### 6.1 质量门禁

**代码合并门禁:**
- ✅ 单元测试覆盖率 > 80%
- ✅ 所有测试通过
- ✅ 代码格式检查通过
- ✅ 静态分析无高危问题
- ✅ 至少1人Code Review通过

**发布门禁:**
- ✅ 集成测试100%通过
- ✅ E2E测试100%通过
- ✅ 性能测试达标
- ✅ 安全扫描无高危漏洞
- ✅ 文档完整

### 6.2 测试策略

**测试金字塔:**
```
      /\
     /E2E\    10% - 关键用户流程
    /------\
   /集成测试\  20% - API和数据库集成
  /----------\
 /  单元测试  \ 70% - 业务逻辑和工具类
/------------\
```

---

## 七、附录

### 附录A：环境配置

**开发环境要求:**
- JDK 21
- Maven 3.9+
- MySQL 9.3
- ClickHouse 24.x
- Redis 7.x
- Node.js 18+
- Docker & Docker Compose

**IDE配置:**
- IntelliJ IDEA 2024.x
- 安装插件：Lombok, Spotless
- 代码格式：Google Java Style

### 附录B：分支策略

```
main (受保护)
  ├─ develop (开发主分支)
  │   ├─ feature/phase1-database (阶段一)
  │   ├─ feature/phase2-auth (阶段二)
  │   ├─ feature/phase3-audit (阶段三)
  │   ├─ feature/phase4-frontend (阶段四)
  │   └─ feature/phase5-testing (阶段五)
  │
  └─ hotfix/* (紧急修复)
```

### 附录C：命名规范

**Java类命名:**
- Entity: `User`, `Workspace`, `Role`
- DAO: `UserDAO`, `WorkspaceDAO`
- Service: `UserService`, `AuthService`
- DTO Request: `UserRegisterRequest`, `LoginRequest`
- DTO Response: `LoginResponse`, `ApiKeyResponse`

**测试类命名:**
- 单元测试: `UserServiceTest`, `PasswordServiceTest`
- 集成测试: `AuthenticationIntegrationTest`
- E2E测试: `UserRegistrationE2ETest`

**数据库命名:**
- 表名: 小写+下划线，如 `users`, `workspace_members`
- 字段名: 小写+下划线，如 `user_id`, `created_at`
- 索引名: `idx_<table>_<fields>`，如 `idx_users_username`

---

## 变更历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| v1.0 | 2025-01-24 | 初始版本 | AI Assistant |

---

**文档结束**

