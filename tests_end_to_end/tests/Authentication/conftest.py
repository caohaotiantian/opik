"""
Fixtures for Authentication E2E tests
"""
import pytest
import logging
import random
import string
from tests.Authentication.auth_helpers import (
    register_user,
    login_user,
    logout_user,
    cleanup_test_user,
)

logger = logging.getLogger(__name__)


def get_random_string(length: int = 8) -> str:
    """Generate a random string"""
    letters = string.ascii_lowercase + string.digits
    return ''.join(random.choice(letters) for _ in range(length))


@pytest.fixture
def random_username():
    """Generate a random username for testing"""
    return f"testuser_{get_random_string(8)}"


@pytest.fixture
def random_email():
    """Generate a random email for testing"""
    return f"test_{get_random_string(8)}@example.com"


@pytest.fixture
def test_password():
    """Standard test password"""
    return "SecureTestPass123!"


@pytest.fixture
def registered_user(random_username, random_email, test_password):
    """
    Create and register a test user, cleanup after test
    
    Returns:
        dict: User registration details including username, email, password
    """
    logger.info(f"Creating registered test user: {random_username}")
    
    try:
        user_data = register_user(
            username=random_username,
            email=random_email,
            password=test_password,
            full_name=f"Test User {random_username}"
        )
        
        user_info = {
            'id': user_data.get('id'),
            'username': random_username,
            'email': random_email,
            'password': test_password,
            'full_name': user_data.get('fullName'),
        }
        
        logger.info(f"Registered user created: {random_username}")
        yield user_info
        
    finally:
        # Cleanup
        logger.info(f"Cleaning up test user: {random_username}")
        cleanup_test_user(random_username)


@pytest.fixture
def logged_in_user(registered_user):
    """
    Create a registered user and log them in
    
    Returns:
        dict: User info with session_token added
    """
    logger.info(f"Logging in user: {registered_user['username']}")
    
    user_info, session_token = login_user(
        username=registered_user['username'],
        password=registered_user['password']
    )
    
    result = {
        **registered_user,
        'session_token': session_token,
        'user_info': user_info
    }
    
    logger.info(f"User logged in: {registered_user['username']}")
    yield result
    
    # Logout after test
    try:
        logger.info(f"Logging out user: {registered_user['username']}")
        logout_user(session_token)
    except Exception as e:
        logger.warning(f"Error during logout: {e}")


@pytest.fixture
def two_users(random_username, random_email, test_password):
    """
    Create two registered test users
    
    Returns:
        list: List of two user dicts
    """
    username1 = f"{random_username}_1"
    username2 = f"{random_username}_2"
    email1 = f"user1_{random_email}"
    email2 = f"user2_{random_email}"
    
    logger.info(f"Creating two test users: {username1}, {username2}")
    
    users = []
    try:
        # Create first user
        user1_data = register_user(
            username=username1,
            email=email1,
            password=test_password,
            full_name=f"Test User 1"
        )
        
        users.append({
            'id': user1_data.get('id'),
            'username': username1,
            'email': email1,
            'password': test_password,
        })
        
        # Create second user
        user2_data = register_user(
            username=username2,
            email=email2,
            password=test_password,
            full_name=f"Test User 2"
        )
        
        users.append({
            'id': user2_data.get('id'),
            'username': username2,
            'email': email2,
            'password': test_password,
        })
        
        logger.info("Two test users created")
        yield users
        
    finally:
        # Cleanup
        for user in users:
            logger.info(f"Cleaning up test user: {user['username']}")
            cleanup_test_user(user['username'])


