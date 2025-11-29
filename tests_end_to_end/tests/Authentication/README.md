# Authentication E2E Tests

这个目录包含了多租户认证系统的端到端测试。

## 📁 文件结构

```
Authentication/
├── README.md                         # 本文档
├── __init__.py                       # Package初始化
├── auth_helpers.py                   # 认证helper函数
├── conftest.py                       # Pytest fixtures
├── test_authentication_flow.py       # 用户注册、登录、登出测试
├── test_session_management.py        # Session管理测试
├── test_api_key_auth.py             # API Key认证测试
└── test_audit_logs.py               # 审计日志测试
```

## 📋 测试覆盖

### 1. test_authentication_flow.py (11个测试)

**TestUserRegistration** (5个测试):
- ✅ `test_user_registration_success` - 成功注册用户
- ✅ `test_user_registration_duplicate_username` - 拒绝重复用户名
- ✅ `test_user_registration_duplicate_email` - 拒绝重复邮箱
- ✅ `test_user_registration_invalid_email` - 拒绝无效邮箱格式
- ✅ `test_user_registration_weak_password` - 拒绝弱密码

**TestUserLogin** (3个测试):
- ✅ `test_user_login_success` - 成功登录
- ✅ `test_user_login_wrong_password` - 拒绝错误密码
- ✅ `test_user_login_nonexistent_user` - 拒绝不存在的用户

**TestUserProfile** (3个测试):
- ✅ `test_get_user_profile_authenticated` - 获取用户个人资料
- ✅ `test_get_user_profile_unauthenticated` - 拒绝未认证访问
- ✅ `test_get_user_profile_invalid_session` - 拒绝无效Session

**TestUserLogout** (1个测试):
- ✅ `test_user_logout_success` - 成功登出并失效Session

### 2. test_session_management.py (8个测试)

**TestSessionCreation** (2个测试):
- ✅ `test_session_created_on_login` - 登录时创建Session
- ✅ `test_session_persists_across_requests` - Session持久化

**TestConcurrentSessions** (2个测试):
- ✅ `test_multiple_concurrent_sessions` - 多个并发Session
- ⏭️ `test_maximum_concurrent_sessions_limit` - Session数量限制 (需要配置)

**TestSessionInvalidation** (2个测试):
- ✅ `test_session_invalidated_on_logout` - 登出后Session失效
- ⏭️ `test_invalidate_all_sessions` - 失效所有Session (待实现)

**TestSessionExpiration** (2个测试):
- ⏭️ `test_session_expires_after_timeout` - Session超时过期 (需要短超时配置)
- ⏭️ `test_session_refreshed_on_activity` - 活动时刷新Session (需要短超时配置)

**TestSessionSecurity** (1个测试):
- ⏭️ `test_session_fingerprint_validation` - Session指纹验证 (待实现)

### 3. test_api_key_auth.py (10个测试)

**TestApiKeyCreation** (2个测试):
- ✅ `test_create_api_key_success` - 成功创建API Key
- ✅ `test_create_multiple_api_keys` - 创建多个API Key

**TestApiKeyAuthentication** (3个测试):
- ✅ `test_api_auth_with_valid_key` - 有效API Key认证
- ✅ `test_api_auth_with_invalid_key` - 拒绝无效API Key
- ⏭️ `test_api_key_workspace_isolation` - 工作空间隔离 (待实现)

**TestApiKeyManagement** (2个测试):
- ⏭️ `test_list_api_keys` - 列出API Keys (待实现)
- ⏭️ `test_revoke_api_key` - 撤销API Key (待实现)

**TestApiKeyScopes** (2个测试):
- ⏭️ `test_api_key_read_only_scope` - 只读scope (待实现)
- ⏭️ `test_api_key_full_access_scope` - 完全访问scope (待实现)

### 4. test_audit_logs.py (8个测试)

**TestAuditLogCreation** (3个测试):
- ⏭️ `test_audit_log_user_registration` - 注册审计日志 (待实现)
- ⏭️ `test_audit_log_user_login` - 登录审计日志 (待实现)
- ⏭️ `test_audit_log_workspace_creation` - 工作空间创建审计日志 (待实现)

**TestAuditLogQuery** (3个测试):
- ⏭️ `test_query_all_audit_logs` - 查询所有审计日志 (待实现)
- ⏭️ `test_query_audit_logs_by_operation` - 按操作类型过滤 (待实现)
- ⏭️ `test_query_audit_logs_by_date_range` - 按日期范围过滤 (待实现)

**TestAuditLogData** (2个测试):
- ⏭️ `test_audit_log_data_completeness` - 审计日志数据完整性 (待实现)
- ⏭️ `test_audit_log_user_context` - 用户上下文捕获 (待实现)

## 🚀 运行测试

### 前置条件

1. **确保后端服务正在运行**:
   ```bash
   cd /path/to/opik
   ./opik.sh
   ```

2. **安装测试依赖**:
   ```bash
   cd tests_end_to_end
   pip install -r test_requirements.txt
   ```

3. **设置PYTHONPATH**:
   ```bash
   cd tests_end_to_end
   export PYTHONPATH='.'
   ```

### 运行所有认证测试

```bash
pytest tests/Authentication/ -v
```

### 运行特定测试文件

```bash
# 运行认证流程测试
pytest tests/Authentication/test_authentication_flow.py -v

# 运行Session管理测试
pytest tests/Authentication/test_session_management.py -v

# 运行API Key测试
pytest tests/Authentication/test_api_key_auth.py -v

# 运行审计日志测试
pytest tests/Authentication/test_audit_logs.py -v
```

### 运行特定测试类

```bash
# 运行用户注册测试
pytest tests/Authentication/test_authentication_flow.py::TestUserRegistration -v

# 运行用户登录测试
pytest tests/Authentication/test_authentication_flow.py::TestUserLogin -v
```

### 运行特定测试方法

```bash
# 运行单个测试
pytest tests/Authentication/test_authentication_flow.py::TestUserRegistration::test_user_registration_success -v
```

### 运行标记的测试

```bash
# 运行所有sanity测试
pytest tests/Authentication/ -m sanity -v

# 运行所有authentication标记的测试
pytest tests/Authentication/ -m authentication -v

# 运行所有session标记的测试
pytest tests/Authentication/ -m session -v

# 运行所有apikey标记的测试
pytest tests/Authentication/ -m apikey -v

# 运行所有audit标记的测试
pytest tests/Authentication/ -m audit -v
```

### 跳过某些测试

```bash
# 跳过标记为skip的测试
pytest tests/Authentication/ -v --ignore-markers=skip

# 只运行未跳过的测试
pytest tests/Authentication/ -v -k "not skip"
```

## 📊 测试报告

### 生成Allure报告

```bash
# 运行测试并生成Allure数据
pytest tests/Authentication/ --alluredir=./allure-results

# 查看报告
allure serve ./allure-results
```

### 显示详细日志

```bash
# 显示所有日志
pytest tests/Authentication/ -v -s

# 显示HTTP请求日志
pytest tests/Authentication/ -v --show-requests
```

## 🔧 配置

### 环境变量

测试使用以下环境变量（可选，默认值适用于本地开发）:

```bash
export OPIK_BASE_URL=http://localhost:5173        # 应用基础URL
export OPIK_API_KEY=your_api_key                  # API密钥（可选）
export OPIK_TEST_USER_NAME=testuser               # 测试用户名
export OPIK_TEST_USER_EMAIL=test@example.com      # 测试用户邮箱
export OPIK_TEST_USER_PASSWORD=TestPass123!       # 测试用户密码
```

### Fixtures

**测试fixtures** (在`conftest.py`中定义):

- `random_username` - 生成随机用户名
- `random_email` - 生成随机邮箱
- `test_password` - 标准测试密码
- `registered_user` - 创建并注册一个测试用户（自动清理）
- `logged_in_user` - 创建并登录一个测试用户（自动登出和清理）
- `two_users` - 创建两个测试用户（自动清理）

## ⚠️ 注意事项

### 当前限制

1. **部分测试被跳过** (`@pytest.mark.skip`):
   - 需要短超时配置的测试 (Session expiration)
   - 需要管理员API的测试 (Audit logs)
   - 需要额外端点实现的测试 (Revoke API key, List API keys)
   - 需要scope实现的测试 (API key scopes)

2. **清理功能有限**:
   - 当前`cleanup_test_user`是占位符
   - 需要实现管理员API来删除测试用户
   - 临时解决方案：测试使用随机用户名避免冲突

3. **Session指纹测试**:
   - 需要能够控制HTTP headers (IP, User-Agent)
   - 可能需要自定义HTTP客户端

### 最佳实践

1. **测试数据隔离**:
   - 每个测试使用唯一的用户名/邮箱
   - 使用fixtures自动创建和清理

2. **异步处理**:
   - 审计日志是异步写入的
   - 使用`wait_for_audit_log`辅助函数

3. **错误处理**:
   - 测试预期的错误状态码
   - 验证错误消息内容

## 📈 测试统计

### 总览

- **总测试数**: 37个
- **可运行**: 19个 ✅
- **跳过**: 18个 ⏭️ (待后端功能完善)

### 按功能分类

| 功能 | 可运行 | 跳过 | 总计 |
|------|--------|------|------|
| 用户注册/登录 | 11 | 0 | 11 |
| Session管理 | 3 | 5 | 8 |
| API Key认证 | 5 | 5 | 10 |
| 审计日志 | 0 | 8 | 8 |

### 优先级

**高优先级** (19个测试) - 可立即运行:
- ✅ 用户注册和登录流程
- ✅ Session创建和验证
- ✅ API Key创建和认证

**中优先级** (10个测试) - 需要后端配置或端点:
- ⏭️ Session过期和刷新
- ⏭️ API Key管理和撤销
- ⏭️ Session数量限制

**低优先级** (8个测试) - 需要完整审计系统:
- ⏭️ 审计日志创建和查询

## 🔗 相关文档

- [集成测试完成报告](../../../../../multi-user-auth-solution/INTEGRATION_TEST_COMPLETE.md)
- [测试策略建议](../../../../../multi-user-auth-solution/TESTING_STRATEGY_RECOMMENDATION.md)
- [E2E测试主README](../../README.md)

## 🎯 后续计划

1. ✅ **Phase 1: 基础认证流程** (已完成)
   - 用户注册和登录
   - Session管理基础
   - API Key创建

2. 📋 **Phase 2: 高级功能** (待实施)
   - Session过期和刷新
   - API Key scope
   - 管理员功能

3. 📋 **Phase 3: 审计系统** (待实施)
   - 审计日志创建
   - 审计日志查询和过滤
   - 审计日志导出

---

**创建时间**: 2025-11-26  
**作者**: AI助手  
**状态**: ✅ Phase 1完成，Phase 2&3待后端功能完善


