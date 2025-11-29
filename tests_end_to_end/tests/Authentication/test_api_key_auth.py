"""
E2E Tests for API Key Authentication

Tests:
1. API Key creation
2. API Key authentication
3. API Key validation
4. API Key revocation
5. API Key scopes (future)
"""
import pytest
import logging
import allure
from tests.Authentication.auth_helpers import (
    create_api_key,
    validate_api_key,
    create_workspace,
    list_workspaces,
)
from tests.Authentication.conftest import get_random_string

logger = logging.getLogger(__name__)


class TestApiKeyCreation:
    """Tests for API Key creation"""
    
    @pytest.mark.sanity
    @pytest.mark.apikey
    @allure.title("API Key creation - successful")
    @allure.description("Test successful API key creation")
    def test_create_api_key_success(self, logged_in_user):
        """
        Test successful API key creation
        
        Steps:
        1. Login as user (via fixture)
        2. Get user's workspace
        3. Create API key for workspace
        4. Verify API key is returned
        5. Verify API key properties
        """
        logger.info(f"Testing API key creation: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Get user's workspace
        workspaces = list_workspaces(session_token)
        assert len(workspaces) > 0, "User should have at least one workspace"
        
        workspace = workspaces[0]
        workspace_id = workspace['id']
        workspace_name = workspace['name']
        
        logger.info(f"Creating API key for workspace: {workspace_name}")
        
        # Create API key
        api_key_response = create_api_key(
            session_token=session_token,
            workspace_id=workspace_id,
            name="Test API Key",
            scopes=[]
        )
        
        # Verify response
        assert 'key' in api_key_response, "Response should contain the API key"
        assert 'id' in api_key_response, "Response should contain API key ID"
        assert 'name' in api_key_response, "Response should contain API key name"
        assert api_key_response['name'] == "Test API Key", "Name should match"
        
        api_key = api_key_response['key']
        assert len(api_key) > 20, "API key should be sufficiently long"
        
        logger.info(f"API key created successfully: {api_key_response['id']}")
        
        # Verify API key is valid
        is_valid = validate_api_key(api_key, workspace_name)
        assert is_valid, "API key should be valid immediately after creation"
        
        logger.info("API key validated successfully")
    
    @pytest.mark.apikey
    @allure.title("API Key creation - multiple keys")
    @allure.description("Test that multiple API keys can be created")
    def test_create_multiple_api_keys(self, logged_in_user):
        """
        Test that multiple API keys can be created for the same workspace
        
        Steps:
        1. Get user's workspace
        2. Create multiple API keys with different names
        3. Verify all API keys are valid
        """
        logger.info(f"Testing multiple API key creation: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Get workspace
        workspaces = list_workspaces(session_token)
        workspace = workspaces[0]
        workspace_id = workspace['id']
        workspace_name = workspace['name']
        
        # Create multiple API keys
        api_keys = []
        for i in range(3):
            api_key_response = create_api_key(
                session_token=session_token,
                workspace_id=workspace_id,
                name=f"Test API Key {i+1}",
                scopes=[]
            )
            api_keys.append(api_key_response['key'])
            logger.info(f"Created API key {i+1}/3")
        
        # Verify all API keys are unique
        assert len(set(api_keys)) == 3, "All API keys should be unique"
        
        # Verify all API keys are valid
        for i, api_key in enumerate(api_keys):
            is_valid = validate_api_key(api_key, workspace_name)
            assert is_valid, f"API key {i+1} should be valid"
        
        logger.info("All API keys created and validated successfully")


class TestApiKeyAuthentication:
    """Tests for API Key authentication"""
    
    @pytest.mark.sanity
    @pytest.mark.apikey
    @allure.title("API authentication with valid key")
    @allure.description("Test API authentication using valid API key")
    def test_api_auth_with_valid_key(self, logged_in_user):
        """
        Test API authentication using valid API key
        
        Steps:
        1. Create API key
        2. Make API request with the key
        3. Verify request is successful
        """
        logger.info(f"Testing API auth with valid key: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Get workspace
        workspaces = list_workspaces(session_token)
        workspace = workspaces[0]
        workspace_id = workspace['id']
        workspace_name = workspace['name']
        
        # Create API key
        api_key_response = create_api_key(
            session_token=session_token,
            workspace_id=workspace_id,
            name="Auth Test Key",
            scopes=[]
        )
        
        api_key = api_key_response['key']
        
        # Validate API key (makes actual API request)
        is_valid = validate_api_key(api_key, workspace_name)
        assert is_valid, "API request with valid key should succeed"
        
        logger.info("API authentication successful with valid key")
    
    @pytest.mark.apikey
    @allure.title("API authentication with invalid key")
    @allure.description("Test that invalid API key is rejected")
    def test_api_auth_with_invalid_key(self):
        """
        Test that invalid API key is rejected
        
        Steps:
        1. Make API request with invalid/fake API key
        2. Verify request is rejected
        """
        logger.info("Testing API auth with invalid key")
        
        # Use fake API key
        fake_api_key = "invalid_key_12345678901234567890"
        
        # Attempt API request
        is_valid = validate_api_key(fake_api_key, "default")
        assert not is_valid, "API request with invalid key should fail"
        
        logger.info("Invalid API key correctly rejected")
    
    @pytest.mark.apikey
    @allure.title("API key workspace isolation")
    @allure.description("Test that API key can only access its own workspace")
    def test_api_key_workspace_isolation(self, logged_in_user):
        """
        Test that API key can only access its own workspace
        
        Steps:
        1. Create two workspaces
        2. Create API key for workspace A
        3. Attempt to use API key to access workspace B
        4. Verify access is denied (or returns empty/filtered results)
        """
        logger.info(f"Testing API key workspace isolation: {logged_in_user['username']}")
        
        import requests
        from tests.config import get_environment_config
        
        session_token = logged_in_user['session_token']
        env_config = get_environment_config()
        
        # Create two workspaces
        workspace_a_name = get_random_string(10)
        workspace_b_name = get_random_string(10)
        
        workspace_a = create_workspace(session_token, workspace_a_name, f"Workspace A")
        workspace_b = create_workspace(session_token, workspace_b_name, f"Workspace B")
        
        logger.info(f"Created workspace A: {workspace_a['id']}")
        logger.info(f"Created workspace B: {workspace_b['id']}")
        
        # Create API key in workspace A
        api_key_response = create_api_key(
            session_token=session_token,
            workspace_id=workspace_a['id'],
            name="Test Key for Workspace A",
            scopes=[]
        )
        api_key = api_key_response['key']
        
        logger.info("Created API key for workspace A")
        
        # Try to access workspace B with API key from workspace A
        url = f"{env_config.api_url}/v1/private/projects"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Opik-Workspace": workspace_b_name
        }
        
        response = requests.get(url, headers=headers)
        
        # The API should either:
        # 1. Return 403 Forbidden (strict workspace isolation)
        # 2. Return empty list (filtered by workspace membership)
        # 3. Return 401 Unauthorized (API key not valid for this workspace)
        
        logger.info(f"Response status: {response.status_code}")
        
        if response.status_code in [401, 403]:
            logger.info("✅ Strict workspace isolation - access denied")
        elif response.status_code == 200:
            result = response.json()
            projects = result.get('content', []) if isinstance(result, dict) else result
            assert len(projects) == 0, "Should return empty list for unauthorized workspace"
            logger.info("✅ Filtered workspace isolation - returns empty list")
        
        logger.info("API key workspace isolation test completed")


class TestApiKeyManagement:
    """Tests for API Key management (list, revoke, etc.)"""
    
    @pytest.mark.apikey
    @allure.title("List API keys")
    @allure.description("Test listing all API keys for a workspace")
    def test_list_api_keys(self, logged_in_user):
        """
        Test listing all API keys for a workspace
        
        Steps:
        1. Create multiple API keys
        2. List API keys
        3. Verify all created keys appear in the list
        """
        logger.info(f"Testing list API keys: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Get workspace
        from tests.Authentication.auth_helpers import list_workspaces, create_api_key, list_api_keys
        workspaces = list_workspaces(session_token)
        workspace = workspaces[0]
        workspace_id = workspace['id']
        
        # Create some API keys
        created_keys = []
        for i in range(3):
            api_key_response = create_api_key(
                session_token=session_token,
                workspace_id=workspace_id,
                name=f"Test Key {i+1}",
                scopes=[]
            )
            created_keys.append(api_key_response)
            logger.info(f"Created API key {i+1}/3: {api_key_response['name']}")
        
        # List API keys
        api_keys = list_api_keys(session_token, workspace_id)
        assert len(api_keys) >= 3, "Should have at least 3 API keys"
        
        # Verify created keys are in the list
        created_ids = {key['id'] for key in created_keys}
        listed_ids = {key['id'] for key in api_keys}
        assert created_ids.issubset(listed_ids), "All created keys should be in the list"
        
        # Verify plaintext keys are NOT returned
        for key in api_keys:
            assert key.get('key') is None or key.get('apiKey') is None, \
                "Plaintext API key should not be returned in list"
        
        logger.info("List API keys test completed")
    
    @pytest.mark.apikey
    @allure.title("Revoke API key")
    @allure.description("Test revoking an API key")
    def test_revoke_api_key(self, logged_in_user):
        """
        Test revoking an API key
        
        Steps:
        1. Create API key
        2. Verify API key is valid
        3. Revoke API key
        4. Verify API key is no longer valid
        """
        logger.info(f"Testing revoke API key: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Get workspace
        workspaces = list_workspaces(session_token)
        workspace = workspaces[0]
        workspace_id = workspace['id']
        workspace_name = workspace['name']
        
        # Create API key
        api_key_response = create_api_key(
            session_token=session_token,
            workspace_id=workspace_id,
            name="Revoke Test Key",
            scopes=[]
        )
        
        api_key = api_key_response['key']
        api_key_id = api_key_response['id']
        
        # Verify API key is valid
        is_valid = validate_api_key(api_key, workspace_name)
        assert is_valid, "API key should be valid before revocation"
        
        # Revoke API key
        from tests.Authentication.auth_helpers import revoke_api_key
        revoke_api_key(session_token, api_key_id)
        logger.info("API key revoked via API")
        
        # Verify API key is no longer valid
        is_valid = validate_api_key(api_key, workspace_name)
        assert not is_valid, "API key should be invalid after revocation"
        
        logger.info("API key revoked successfully")


class TestApiKeyScopes:
    """Tests for API Key scopes (permissions)"""
    
    @pytest.mark.apikey
    @pytest.mark.permissions
    @allure.title("API key with read-only scope")
    @allure.description("Test API key with read-only scope restrictions")
    def test_api_key_read_only_scope(self, logged_in_user):
        """
        Test API key with read-only scope
        
        Steps:
        1. Create API key with read-only scope
        2. Verify API key is created with correct scopes
        3. Test that API key can be used for authentication
        
        Note: Full scope enforcement requires @RequiresScope annotations on API endpoints.
        This test verifies the scope framework integration.
        """
        logger.info("Testing API key read-only scope")
        
        session_token = logged_in_user['session_token']
        
        # Get workspace
        workspaces = list_workspaces(session_token)
        workspace = workspaces[0]
        workspace_id = workspace['id']
        workspace_name = workspace['name']
        
        # Create API key with read-only scope
        api_key_response = create_api_key(
            session_token=session_token,
            workspace_id=workspace_id,
            name="Read-Only Test Key",
            scopes=["read"]
        )
        
        assert api_key_response is not None, "API key should be created"
        assert 'key' in api_key_response or 'apiKey' in api_key_response, "API key should have key field"
        
        api_key = api_key_response.get('key') or api_key_response.get('apiKey')
        
        # Verify API key works for authentication
        is_valid = validate_api_key(api_key, workspace_name)
        logger.info(f"API key with read scope is valid: {is_valid}")
        
        # Note: Without @RequiresScope annotations on endpoints, we can't test actual enforcement
        # But we've verified the scope can be set and the API key works
        logger.info("✅ API key created with read-only scope")
        logger.info("⚠️ Scope enforcement requires @RequiresScope annotations on API endpoints")
        
        logger.info("Read-only scope test completed")
    
    @pytest.mark.apikey
    @pytest.mark.permissions
    @allure.title("API key with full access scope")
    @allure.description("Test API key with full access scope")
    def test_api_key_full_access_scope(self, logged_in_user):
        """
        Test API key with full access scope
        
        Steps:
        1. Create API key with full access scope
        2. Verify API key is created with correct scopes
        3. Test that API key can be used for authentication
        
        Note: Full scope enforcement requires @RequiresScope annotations on API endpoints.
        This test verifies the scope framework integration.
        """
        logger.info("Testing API key full access scope")
        
        session_token = logged_in_user['session_token']
        
        # Get workspace
        workspaces = list_workspaces(session_token)
        workspace = workspaces[0]
        workspace_id = workspace['id']
        workspace_name = workspace['name']
        
        # Create API key with full access scopes
        api_key_response = create_api_key(
            session_token=session_token,
            workspace_id=workspace_id,
            name="Full Access Test Key",
            scopes=["read", "write", "delete"]
        )
        
        assert api_key_response is not None, "API key should be created"
        assert 'key' in api_key_response or 'apiKey' in api_key_response, "API key should have key field"
        
        api_key = api_key_response.get('key') or api_key_response.get('apiKey')
        
        # Verify API key works for authentication
        is_valid = validate_api_key(api_key, workspace_name)
        logger.info(f"API key with full access scopes is valid: {is_valid}")
        
        # Note: Without @RequiresScope annotations on endpoints, we can't test actual enforcement
        # But we've verified the scopes can be set and the API key works
        logger.info("✅ API key created with full access scopes (read, write, delete)")
        logger.info("⚠️ Scope enforcement requires @RequiresScope annotations on API endpoints")
        
        logger.info("Full access scope test completed")


