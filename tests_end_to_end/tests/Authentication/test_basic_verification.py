"""
基础验证测试 - 验证测试框架是否正常工作
不依赖完整的E2E测试基础设施
"""
import pytest


class TestBasicVerification:
    """基础验证测试"""
    
    def test_imports(self):
        """测试所有必要的导入是否正常"""
        # 测试helper导入
        from tests.Authentication import auth_helpers
        assert hasattr(auth_helpers, 'register_user')
        assert hasattr(auth_helpers, 'login_user')
        assert hasattr(auth_helpers, 'logout_user')
        print("✅ Helper函数导入成功")
    
    def test_config(self):
        """测试配置是否正常"""
        from tests.config import get_environment_config
        config = get_environment_config()
        assert config.base_url
        assert config.api_url
        print(f"✅ 配置正常: base_url={config.base_url}, api_url={config.api_url}")
    
    def test_fixture_random_data(self):
        """测试随机数据生成"""
        import random
        import string
        
        def get_random_string(length=8):
            letters = string.ascii_lowercase + string.digits
            return ''.join(random.choice(letters) for _ in range(length))
        
        username = f"testuser_{get_random_string(8)}"
        email = f"test_{get_random_string(8)}@example.com"
        
        assert len(username) > 8
        assert '@' in email
        print(f"✅ 随机数据生成成功: username={username}, email={email}")


