"""
E2E Tests for User Authentication Flow

Tests:
1. User registration
2. User login/logout
3. Session management
4. Profile access
"""
import pytest
import logging
import allure
from tests.Authentication.auth_helpers import (
    register_user,
    login_user,
    logout_user,
    get_user_profile,
)

logger = logging.getLogger(__name__)


class TestUserRegistration:
    """Tests for user registration"""
    
    @pytest.mark.sanity
    @pytest.mark.authentication
    @allure.title("User registration - successful")
    @allure.description("Test successful user registration with valid data")
    def test_user_registration_success(self, random_username, random_email, test_password):
        """
        Test successful user registration
        
        Steps:
        1. Register new user with valid data
        2. Verify response contains user ID
        3. Verify response contains correct username
        4. Verify response contains correct email
        """
        logger.info(f"Testing user registration for: {random_username}")
        
        # Register user
        response = register_user(
            username=random_username,
            email=random_email,
            password=test_password,
            full_name=f"Test User {random_username}"
        )
        
        # Verify response
        assert 'id' in response, "Response should contain user ID"
        assert response['username'] == random_username, f"Username mismatch: expected {random_username}, got {response.get('username')}"
        assert response['email'] == random_email, f"Email mismatch: expected {random_email}, got {response.get('email')}"
        assert 'password' not in response, "Response should not contain password"
        
        logger.info(f"User registration successful: {random_username}")
    
    @pytest.mark.authentication
    @allure.title("User registration - duplicate username")
    @allure.description("Test that duplicate username is rejected")
    def test_user_registration_duplicate_username(self, registered_user, random_email):
        """
        Test that duplicate username registration is rejected
        
        Steps:
        1. Create a registered user (via fixture)
        2. Attempt to register another user with same username but different email
        3. Verify that registration fails with 409 Conflict
        """
        logger.info(f"Testing duplicate username registration: {registered_user['username']}")
        
        # Attempt to register with same username
        with pytest.raises(Exception) as exc_info:
            register_user(
                username=registered_user['username'],
                email=random_email,
                password="DifferentPass123!"
            )
        
        # Verify it's a conflict error (409)
        assert '409' in str(exc_info.value) or 'conflict' in str(exc_info.value).lower(), \
            "Should fail with conflict error for duplicate username"
        
        logger.info("Duplicate username correctly rejected")
    
    @pytest.mark.authentication
    @allure.title("User registration - duplicate email")
    @allure.description("Test that duplicate email is rejected")
    def test_user_registration_duplicate_email(self, registered_user, random_username):
        """
        Test that duplicate email registration is rejected
        
        Steps:
        1. Create a registered user (via fixture)
        2. Attempt to register another user with same email but different username
        3. Verify that registration fails with 409 Conflict
        """
        logger.info(f"Testing duplicate email registration: {registered_user['email']}")
        
        # Attempt to register with same email
        with pytest.raises(Exception) as exc_info:
            register_user(
                username=random_username,
                email=registered_user['email'],
                password="DifferentPass123!"
            )
        
        # Verify it's a conflict error (409)
        assert '409' in str(exc_info.value) or 'conflict' in str(exc_info.value).lower(), \
            "Should fail with conflict error for duplicate email"
        
        logger.info("Duplicate email correctly rejected")
    
    @pytest.mark.authentication
    @allure.title("User registration - invalid email format")
    @allure.description("Test that invalid email format is rejected")
    def test_user_registration_invalid_email(self, random_username, test_password):
        """
        Test that invalid email format is rejected
        
        Steps:
        1. Attempt to register with invalid email format
        2. Verify that registration fails with 400 Bad Request
        """
        logger.info("Testing invalid email format")
        
        invalid_emails = [
            "not-an-email",
            "@example.com",
            "user@",
            "user@@example.com",
        ]
        
        for invalid_email in invalid_emails:
            logger.info(f"Testing invalid email: {invalid_email}")
            
            with pytest.raises(Exception) as exc_info:
                register_user(
                    username=f"{random_username}_{invalid_emails.index(invalid_email)}",
                    email=invalid_email,
                    password=test_password
                )
            
            # Verify it's a validation error (400)
            assert '400' in str(exc_info.value) or 'bad request' in str(exc_info.value).lower(), \
                f"Should fail with bad request for invalid email: {invalid_email}"
        
        logger.info("Invalid email formats correctly rejected")
    
    @pytest.mark.authentication
    @allure.title("User registration - weak password")
    @allure.description("Test that weak password is rejected")
    def test_user_registration_weak_password(self, random_username, random_email):
        """
        Test that weak password is rejected
        
        Steps:
        1. Attempt to register with weak passwords
        2. Verify that registration fails with 400 Bad Request
        """
        logger.info("Testing weak password rejection")
        
        weak_passwords = [
            "123",  # Too short
            "password",  # No uppercase, no number, no special
            "PASSWORD",  # No lowercase, no number, no special
            "Password1",  # No special character
        ]
        
        for weak_password in weak_passwords:
            logger.info(f"Testing weak password: {weak_password}")
            
            with pytest.raises(Exception) as exc_info:
                register_user(
                    username=f"{random_username}_{weak_passwords.index(weak_password)}",
                    email=f"{random_username}_{weak_passwords.index(weak_password)}@example.com",
                    password=weak_password
                )
            
            # Verify it's a validation error (400)
            assert '400' in str(exc_info.value) or 'bad request' in str(exc_info.value).lower(), \
                f"Should fail with bad request for weak password: {weak_password}"
        
        logger.info("Weak passwords correctly rejected")


class TestUserLogin:
    """Tests for user login/logout"""
    
    @pytest.mark.sanity
    @pytest.mark.authentication
    @allure.title("User login - successful")
    @allure.description("Test successful user login with valid credentials")
    def test_user_login_success(self, registered_user):
        """
        Test successful user login
        
        Steps:
        1. Create a registered user (via fixture)
        2. Login with valid credentials
        3. Verify response contains user info
        4. Verify session token is returned
        """
        logger.info(f"Testing user login for: {registered_user['username']}")
        
        # Login
        user_info, session_token = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        # Verify response
        assert user_info['username'] == registered_user['username'], \
            f"Username mismatch in login response"
        assert session_token, "Session token should be returned"
        assert len(session_token) > 0, "Session token should not be empty"
        
        logger.info(f"User login successful: {registered_user['username']}")
    
    @pytest.mark.authentication
    @allure.title("User login - wrong password")
    @allure.description("Test that login with wrong password is rejected")
    def test_user_login_wrong_password(self, registered_user):
        """
        Test that login with wrong password is rejected
        
        Steps:
        1. Create a registered user (via fixture)
        2. Attempt to login with wrong password
        3. Verify that login fails with 401 Unauthorized
        """
        logger.info(f"Testing login with wrong password: {registered_user['username']}")
        
        # Attempt login with wrong password
        with pytest.raises(Exception) as exc_info:
            login_user(
                username=registered_user['username'],
                password="WrongPassword123!"
            )
        
        # Verify it's an authentication error (401)
        assert '401' in str(exc_info.value) or 'unauthorized' in str(exc_info.value).lower(), \
            "Should fail with unauthorized error for wrong password"
        
        logger.info("Wrong password correctly rejected")
    
    @pytest.mark.authentication
    @allure.title("User login - non-existent user")
    @allure.description("Test that login with non-existent username is rejected")
    def test_user_login_nonexistent_user(self, test_password):
        """
        Test that login with non-existent username is rejected
        
        Steps:
        1. Attempt to login with non-existent username
        2. Verify that login fails with 401 Unauthorized
        """
        logger.info("Testing login with non-existent user")
        
        # Attempt login with non-existent user
        with pytest.raises(Exception) as exc_info:
            login_user(
                username="nonexistent_user_12345",
                password=test_password
            )
        
        # Verify it's an authentication error (401)
        assert '401' in str(exc_info.value) or 'unauthorized' in str(exc_info.value).lower(), \
            "Should fail with unauthorized error for non-existent user"
        
        logger.info("Non-existent user correctly rejected")


class TestUserProfile:
    """Tests for user profile access"""
    
    @pytest.mark.sanity
    @pytest.mark.authentication
    @allure.title("Get user profile - authenticated")
    @allure.description("Test getting user profile with valid session")
    def test_get_user_profile_authenticated(self, logged_in_user):
        """
        Test getting user profile with valid session
        
        Steps:
        1. Create a logged-in user (via fixture)
        2. Get user profile using session token
        3. Verify profile contains correct user data
        """
        logger.info(f"Testing get user profile: {logged_in_user['username']}")
        
        # Get profile
        profile = get_user_profile(logged_in_user['session_token'])
        
        # Verify profile
        assert profile['username'] == logged_in_user['username'], \
            "Profile username should match logged-in user"
        assert profile['email'] == logged_in_user['email'], \
            "Profile email should match logged-in user"
        assert 'id' in profile, "Profile should contain user ID"
        assert 'password' not in profile, "Profile should not contain password"
        
        logger.info("User profile retrieved successfully")
    
    @pytest.mark.authentication
    @allure.title("Get user profile - unauthenticated")
    @allure.description("Test that profile access without session is rejected")
    def test_get_user_profile_unauthenticated(self):
        """
        Test that profile access without session is rejected
        
        Steps:
        1. Attempt to get profile without session token
        2. Verify that request fails with 401 Unauthorized
        """
        logger.info("Testing get user profile without authentication")
        
        # Attempt to get profile without session
        with pytest.raises(Exception) as exc_info:
            get_user_profile("")  # Empty session token
        
        # Verify it's an authentication error (401)
        assert '401' in str(exc_info.value) or '403' in str(exc_info.value) or \
               'unauthorized' in str(exc_info.value).lower() or 'forbidden' in str(exc_info.value).lower(), \
            "Should fail with authentication error without valid session"
        
        logger.info("Unauthenticated access correctly rejected")
    
    @pytest.mark.authentication
    @allure.title("Get user profile - invalid session")
    @allure.description("Test that profile access with invalid session is rejected")
    def test_get_user_profile_invalid_session(self):
        """
        Test that profile access with invalid session is rejected
        
        Steps:
        1. Attempt to get profile with invalid session token
        2. Verify that request fails with 401 Unauthorized
        """
        logger.info("Testing get user profile with invalid session")
        
        # Attempt to get profile with invalid session
        with pytest.raises(Exception) as exc_info:
            get_user_profile("invalid_session_token_12345")
        
        # Verify it's an authentication error (401)
        assert '401' in str(exc_info.value) or '403' in str(exc_info.value) or \
               'unauthorized' in str(exc_info.value).lower() or 'forbidden' in str(exc_info.value).lower(), \
            "Should fail with authentication error for invalid session"
        
        logger.info("Invalid session correctly rejected")


class TestUserLogout:
    """Tests for user logout"""
    
    @pytest.mark.sanity
    @pytest.mark.authentication
    @allure.title("User logout - successful")
    @allure.description("Test successful user logout")
    def test_user_logout_success(self, logged_in_user):
        """
        Test successful user logout
        
        Steps:
        1. Create a logged-in user (via fixture)
        2. Logout using session token
        3. Verify logout is successful
        4. Verify session is invalidated (profile access should fail)
        """
        logger.info(f"Testing user logout: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Logout
        result = logout_user(session_token)
        assert result is True, "Logout should return True"
        
        logger.info("User logged out successfully")
        
        # Verify session is invalidated
        logger.info("Verifying session is invalidated")
        with pytest.raises(Exception) as exc_info:
            get_user_profile(session_token)
        
        # Should fail with authentication error
        assert '401' in str(exc_info.value) or '403' in str(exc_info.value) or \
               'unauthorized' in str(exc_info.value).lower() or 'forbidden' in str(exc_info.value).lower(), \
            "Should fail to access profile after logout"
        
        logger.info("Session correctly invalidated after logout")


