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
    @pytest.mark.skip(reason="Requires multiple workspaces setup")
    def test_api_key_workspace_isolation(self, logged_in_user):
        """
        Test that API key can only access its own workspace
        
        Steps:
        1. Create two workspaces
        2. Create API key for workspace A
        3. Attempt to use API key to access workspace B
        4. Verify request is rejected
        """
        logger.info("Testing API key workspace isolation")
        
        # TODO: Implement when multiple workspace support is ready
        # 1. Create workspace A and B
        # 2. Create API key for workspace A
        # 3. Try to access workspace B with workspace A's key
        # 4. Verify rejection
        
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
    @pytest.mark.skip(reason="Requires scope implementation and enforcement")
    def test_api_key_read_only_scope(self, logged_in_user):
        """
        Test API key with read-only scope
        
        Steps:
        1. Create API key with read-only scope
        2. Verify GET requests succeed
        3. Verify POST/PUT/DELETE requests fail
        """
        logger.info("Testing API key read-only scope")
        
        # TODO: Implement when scope enforcement is ready
        # 1. Create API key with ['read'] scope
        # 2. Test GET request (should succeed)
        # 3. Test POST request (should fail)
        
        logger.info("Read-only scope test completed")
    
    @pytest.mark.apikey
    @pytest.mark.permissions
    @allure.title("API key with full access scope")
    @allure.description("Test API key with full access scope")
    @pytest.mark.skip(reason="Requires scope implementation")
    def test_api_key_full_access_scope(self, logged_in_user):
        """
        Test API key with full access scope
        
        Steps:
        1. Create API key with full access scope
        2. Verify all operations (GET/POST/PUT/DELETE) succeed
        """
        logger.info("Testing API key full access scope")
        
        # TODO: Implement when scope implementation is ready
        # 1. Create API key with ['read', 'write', 'delete'] scopes
        # 2. Test all operations
        
        logger.info("Full access scope test completed")


