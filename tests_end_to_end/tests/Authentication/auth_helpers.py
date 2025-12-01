"""
Helper functions for authentication E2E tests
"""
import requests
import logging
import time
from typing import Optional, Dict, Any
from tests.config import get_environment_config

logger = logging.getLogger(__name__)


def get_auth_api_base_url() -> str:
    """Get the base URL for authentication APIs"""
    config = get_environment_config()
    return f"{config.api_url}/v1/public/auth"


def register_user(
    username: str,
    email: str,
    password: str,
    full_name: Optional[str] = None
) -> Dict[str, Any]:
    """
    Register a new user via API
    
    Args:
        username: Username for the new user
        email: Email address
        password: Password
        full_name: Optional full name
        
    Returns:
        Response JSON containing user details
        
    Raises:
        requests.HTTPError: If registration fails
    """
    url = f"{get_auth_api_base_url()}/register"
    payload = {
        "username": username,
        "email": email,
        "password": password,
    }
    if full_name:
        payload["fullName"] = full_name
    
    logger.info(f"Registering user: {username}")
    response = requests.post(url, json=payload)
    
    if response.status_code == 201:
        logger.info(f"User registered successfully: {username}")
        return response.json()
    else:
        logger.error(f"User registration failed: {response.status_code} - {response.text}")
        response.raise_for_status()


def login_user(username: str, password: str) -> tuple[Dict[str, Any], str]:
    """
    Login user and get session token
    
    Args:
        username: Username
        password: Password
        
    Returns:
        Tuple of (user info dict, session token)
        
    Raises:
        requests.HTTPError: If login fails
    """
    url = f"{get_auth_api_base_url()}/login"
    payload = {
        "username": username,
        "password": password
    }
    
    logger.info(f"Logging in user: {username}")
    response = requests.post(url, json=payload)
    
    if response.status_code == 200:
        logger.info(f"User logged in successfully: {username}")
        user_info = response.json()
        
        # Extract session token from cookies
        session_token = response.cookies.get('session_token')
        if not session_token:
            raise ValueError("No session token in response cookies")
            
        return user_info, session_token
    else:
        logger.error(f"User login failed: {response.status_code} - {response.text}")
        response.raise_for_status()


def logout_user(session_token: str) -> bool:
    """
    Logout user and invalidate session
    
    Args:
        session_token: Session token from login
        
    Returns:
        True if logout successful
        
    Raises:
        requests.HTTPError: If logout fails
    """
    url = f"{get_auth_api_base_url()}/logout"
    cookies = {'session_token': session_token}
    
    logger.info("Logging out user")
    response = requests.post(url, cookies=cookies)
    
    if response.status_code == 200:
        logger.info("User logged out successfully")
        return True
    else:
        logger.error(f"User logout failed: {response.status_code} - {response.text}")
        response.raise_for_status()


def logout_all_sessions(session_token: str) -> bool:
    """
    Logout all sessions for the current user
    
    Args:
        session_token: Session token from login
        
    Returns:
        True if logout all successful
        
    Raises:
        requests.HTTPError: If logout all fails
    """
    url = f"{get_auth_api_base_url()}/logout-all"
    cookies = {'session_token': session_token}
    
    logger.info("Logging out all user sessions")
    response = requests.post(url, cookies=cookies)
    
    if response.status_code == 204:
        logger.info("All user sessions logged out successfully")
        return True
    else:
        logger.error(f"Logout all failed: {response.status_code} - {response.text}")
        response.raise_for_status()


def get_user_profile(session_token: str) -> Dict[str, Any]:
    """
    Get current user profile using session token
    
    Args:
        session_token: Session token from login
        
    Returns:
        User profile data
        
    Raises:
        requests.HTTPError: If request fails
    """
    url = f"{get_auth_api_base_url()}/profile"
    cookies = {'session_token': session_token}
    
    logger.info("Getting user profile")
    response = requests.get(url, cookies=cookies)
    
    if response.status_code == 200:
        logger.info("User profile retrieved successfully")
        return response.json()
    else:
        logger.error(f"Failed to get user profile: {response.status_code} - {response.text}")
        response.raise_for_status()


def create_api_key(
    session_token: str,
    workspace_id: str,
    name: str,
    scopes: Optional[list] = None
) -> Dict[str, Any]:
    """
    Create an API key for a workspace
    
    Args:
        session_token: Session token for authentication
        workspace_id: Workspace ID
        name: Name for the API key
        scopes: Optional list of scopes for the API key
        
    Returns:
        API key details including the actual key (only returned once!)
        
    Raises:
        requests.HTTPError: If creation fails
    """
    url = f"{get_environment_config().api_url}/v1/api-keys"
    cookies = {'session_token': session_token}
    payload = {
        "workspaceId": workspace_id,
        "name": name,
        "scopes": scopes or []
    }
    
    logger.info(f"Creating API key: {name}")
    response = requests.post(url, json=payload, cookies=cookies)
    
    if response.status_code == 201:
        logger.info(f"API key created successfully: {name}")
        return response.json()
    else:
        logger.error(f"Failed to create API key: {response.status_code} - {response.text}")
        response.raise_for_status()


def list_api_keys(session_token: str, workspace_id: str) -> list:
    """
    List all API keys for a workspace
    
    Args:
        session_token: Session token for authentication
        workspace_id: Workspace ID
        
    Returns:
        List of API keys (without plaintext keys)
        
    Raises:
        requests.HTTPError: If request fails
    """
    url = f"{get_environment_config().api_url}/v1/private/api-keys"
    cookies = {'session_token': session_token}
    params = {"workspace_id": workspace_id}
    
    logger.info(f"Listing API keys for workspace: {workspace_id}")
    response = requests.get(url, params=params, cookies=cookies)
    
    if response.status_code == 200:
        logger.info("API keys retrieved successfully")
        return response.json()
    else:
        logger.error(f"Failed to list API keys: {response.status_code} - {response.text}")
        response.raise_for_status()


def revoke_api_key(session_token: str, api_key_id: str) -> bool:
    """
    Revoke an API key
    
    Args:
        session_token: Session token for authentication
        api_key_id: API key ID to revoke
        
    Returns:
        True if revocation successful
        
    Raises:
        requests.HTTPError: If revocation fails
    """
    url = f"{get_environment_config().api_url}/v1/private/api-keys/{api_key_id}"
    cookies = {'session_token': session_token}
    
    logger.info(f"Revoking API key: {api_key_id}")
    response = requests.delete(url, cookies=cookies)
    
    if response.status_code == 204:
        logger.info("API key revoked successfully")
        return True
    else:
        logger.error(f"Failed to revoke API key: {response.status_code} - {response.text}")
        response.raise_for_status()


def validate_api_key(api_key: str, workspace_name: str) -> bool:
    """
    Validate an API key by making a test API call
    
    Args:
        api_key: The API key to validate
        workspace_name: Workspace name for the API call
        
    Returns:
        True if API key is valid
    """
    url = f"{get_environment_config().api_url}/v1/private/projects"
    headers = {
        "Authorization": api_key,
        "Opik-Workspace": workspace_name
    }
    
    logger.info("Validating API key")
    response = requests.get(url, headers=headers)
    
    if response.status_code == 200:
        logger.info("API key is valid")
        return True
    else:
        logger.warning(f"API key validation failed: {response.status_code}")
        return False


def create_workspace(session_token: str, name: str, display_name: Optional[str] = None) -> Dict[str, Any]:
    """
    Create a new workspace
    
    Args:
        session_token: Session token for authentication
        name: Workspace name (unique identifier)
        display_name: Optional display name
        
    Returns:
        Workspace details
        
    Raises:
        requests.HTTPError: If creation fails
    """
    url = f"{get_environment_config().api_url}/v1/workspaces"
    cookies = {'session_token': session_token}
    payload = {
        "name": name,
        "displayName": display_name or name
    }
    
    logger.info(f"Creating workspace: {name}")
    response = requests.post(url, json=payload, cookies=cookies)
    
    if response.status_code == 201:
        logger.info(f"Workspace created successfully: {name}")
        return response.json()
    else:
        logger.error(f"Failed to create workspace: {response.status_code} - {response.text}")
        response.raise_for_status()


def list_workspaces(session_token: str) -> list:
    """
    List all workspaces for the current user
    
    Args:
        session_token: Session token for authentication
        
    Returns:
        List of workspaces
        
    Raises:
        requests.HTTPError: If request fails
    """
    url = f"{get_environment_config().api_url}/v1/workspaces"
    cookies = {'session_token': session_token}
    
    logger.info("Listing workspaces")
    response = requests.get(url, cookies=cookies)
    
    if response.status_code == 200:
        logger.info("Workspaces retrieved successfully")
        return response.json()
    else:
        logger.error(f"Failed to list workspaces: {response.status_code} - {response.text}")
        response.raise_for_status()


def add_workspace_member(
    session_token: str,
    workspace_id: str,
    user_id: str,
    role_id: str
) -> Dict[str, Any]:
    """
    Add a member to a workspace
    
    Args:
        session_token: Session token for authentication
        workspace_id: Workspace ID
        user_id: User ID to add
        role_id: Role ID for the member
        
    Returns:
        Member details
        
    Raises:
        requests.HTTPError: If request fails
    """
    url = f"{get_environment_config().api_url}/v1/workspaces/{workspace_id}/members"
    cookies = {'session_token': session_token}
    payload = {
        "userId": user_id,
        "roleId": role_id
    }
    
    logger.info(f"Adding member to workspace: {workspace_id}")
    response = requests.post(url, json=payload, cookies=cookies)
    
    if response.status_code == 201:
        logger.info("Member added successfully")
        return response.json()
    else:
        logger.error(f"Failed to add member: {response.status_code} - {response.text}")
        response.raise_for_status()


def get_audit_logs(
    session_token: str,
    operation: Optional[str] = None,
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    page: int = 1,
    size: int = 50
) -> Dict[str, Any]:
    """
    Query audit logs
    
    Args:
        session_token: Session token for authentication
        operation: Optional filter by operation type
        start_date: Optional start date filter (ISO format)
        end_date: Optional end date filter (ISO format)
        page: Page number (1-based)
        size: Page size
        
    Returns:
        Paginated audit log results
        
    Raises:
        requests.HTTPError: If request fails
    """
    url = f"{get_environment_config().api_url}/v1/audit-logs"
    cookies = {'session_token': session_token}
    params = {
        "page": page,
        "size": size
    }
    
    if operation:
        params["operation"] = operation
    if start_date:
        params["startDate"] = start_date
    if end_date:
        params["endDate"] = end_date
    
    logger.info("Querying audit logs")
    response = requests.get(url, params=params, cookies=cookies)
    
    if response.status_code == 200:
        logger.info("Audit logs retrieved successfully")
        return response.json()
    else:
        logger.error(f"Failed to get audit logs: {response.status_code} - {response.text}")
        response.raise_for_status()


def wait_for_audit_log(
    session_token: str,
    operation: str,
    timeout: int = 10,
    initial_delay: float = 1.0
) -> bool:
    """
    Wait for an audit log entry to appear
    
    Args:
        session_token: Session token for authentication
        operation: Operation type to wait for
        timeout: Maximum wait time in seconds
        initial_delay: Initial delay before first check
        
    Returns:
        True if audit log found, False if timeout
    """
    start_time = time.time()
    delay = initial_delay
    
    logger.info(f"Waiting for audit log: {operation}")
    
    # Initial delay to allow async processing
    time.sleep(initial_delay)
    
    while time.time() - start_time < timeout:
        try:
            result = get_audit_logs(session_token, operation=operation)
            if result.get('items') and len(result['items']) > 0:
                logger.info(f"Audit log found: {operation}")
                return True
        except Exception as e:
            logger.warning(f"Error checking audit logs: {e}")
        
        time.sleep(delay)
        delay = min(delay * 1.5, timeout - (time.time() - start_time))
    
    logger.warning(f"Audit log not found within {timeout}s: {operation}")
    return False


def cleanup_test_user(username: str):
    """
    Cleanup test user data (admin operation)
    
    This is a placeholder - actual implementation would require admin API
    
    Args:
        username: Username to cleanup
    """
    logger.info(f"Cleaning up test user: {username}")
    # TODO: Implement admin API call to delete user
    pass


