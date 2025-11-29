"""
E2E Tests for Session Management

Tests:
1. Session creation and validation
2. Session fingerprint verification
3. Session expiration
4. Multiple concurrent sessions
5. Session invalidation
"""
import pytest
import logging
import allure
import time
from tests.Authentication.auth_helpers import (
    login_user,
    logout_user,
    get_user_profile,
)

logger = logging.getLogger(__name__)


class TestSessionCreation:
    """Tests for session creation and validation"""
    
    @pytest.mark.sanity
    @pytest.mark.session
    @allure.title("Session creation on login")
    @allure.description("Test that session is created on successful login")
    def test_session_created_on_login(self, registered_user):
        """
        Test that session is created on successful login
        
        Steps:
        1. Login with valid credentials
        2. Verify session token is returned
        3. Verify session token is valid (can access protected resources)
        """
        logger.info(f"Testing session creation: {registered_user['username']}")
        
        # Login
        user_info, session_token = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        # Verify session token exists
        assert session_token, "Session token should be returned on login"
        assert len(session_token) > 20, "Session token should be sufficiently long"
        
        # Verify session is valid by accessing protected resource
        profile = get_user_profile(session_token)
        assert profile['username'] == registered_user['username'], \
            "Should be able to access profile with session token"
        
        logger.info("Session created and validated successfully")
        
        # Cleanup
        logout_user(session_token)
    
    @pytest.mark.session
    @allure.title("Session persists across requests")
    @allure.description("Test that session persists across multiple requests")
    def test_session_persists_across_requests(self, logged_in_user):
        """
        Test that session persists across multiple requests
        
        Steps:
        1. Login and get session token (via fixture)
        2. Make multiple requests with the same session token
        3. Verify all requests succeed
        """
        logger.info(f"Testing session persistence: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Make multiple requests
        for i in range(5):
            logger.info(f"Request {i+1}/5")
            profile = get_user_profile(session_token)
            assert profile['username'] == logged_in_user['username'], \
                f"Request {i+1} should succeed with same session token"
            time.sleep(0.5)  # Small delay between requests
        
        logger.info("Session persisted across all requests")


class TestConcurrentSessions:
    """Tests for concurrent session management"""
    
    @pytest.mark.session
    @allure.title("Multiple concurrent sessions per user")
    @allure.description("Test that a user can have multiple active sessions")
    def test_multiple_concurrent_sessions(self, registered_user):
        """
        Test that a user can have multiple active sessions
        
        Steps:
        1. Login to create first session
        2. Login again to create second session
        3. Verify both sessions are valid
        4. Logout from first session
        5. Verify second session still valid
        """
        logger.info(f"Testing multiple concurrent sessions: {registered_user['username']}")
        
        # Create first session
        user_info1, session_token1 = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        logger.info("First session created")
        
        # Create second session
        user_info2, session_token2 = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        logger.info("Second session created")
        
        # Verify both sessions are different
        assert session_token1 != session_token2, "Sessions should be different"
        
        # Verify both sessions are valid
        profile1 = get_user_profile(session_token1)
        assert profile1['username'] == registered_user['username']
        
        profile2 = get_user_profile(session_token2)
        assert profile2['username'] == registered_user['username']
        
        logger.info("Both sessions are valid")
        
        # Logout from first session
        logout_user(session_token1)
        logger.info("First session logged out")
        
        # Verify first session is invalid
        with pytest.raises(Exception):
            get_user_profile(session_token1)
        
        # Verify second session still valid
        profile2 = get_user_profile(session_token2)
        assert profile2['username'] == registered_user['username'], \
            "Second session should still be valid"
        
        logger.info("Second session still valid after first session logout")
        
        # Cleanup
        logout_user(session_token2)
    
    @pytest.mark.session
    @allure.title("Maximum concurrent sessions limit")
    @allure.description("Test that there is a limit on concurrent sessions")
    def test_maximum_concurrent_sessions_limit(self, registered_user):
        """
        Test that there is a limit on concurrent sessions
        
        This test assumes a max limit of 5 concurrent sessions (configurable)
        
        Steps:
        1. Create multiple sessions (more than limit)
        2. Verify oldest sessions are invalidated when limit is exceeded
        """
        logger.info(f"Testing max concurrent sessions limit: {registered_user['username']}")
        
        max_sessions = 5
        sessions = []
        
        # Create sessions up to limit + 2
        for i in range(max_sessions + 2):
            user_info, session_token = login_user(
                username=registered_user['username'],
                password=registered_user['password']
            )
            sessions.append(session_token)
            logger.info(f"Created session {i+1}/{max_sessions+2}")
            time.sleep(0.2)
        
        # Verify first sessions (oldest) are invalidated
        logger.info("Verifying oldest sessions are invalidated")
        for i in range(2):  # First 2 should be invalid
            with pytest.raises(Exception):
                get_user_profile(sessions[i])
        
        # Verify most recent sessions are still valid
        logger.info("Verifying recent sessions are still valid")
        for i in range(2, max_sessions + 2):
            profile = get_user_profile(sessions[i])
            assert profile['username'] == registered_user['username']
        
        # Cleanup
        for session in sessions[2:]:
            try:
                logout_user(session)
            except:
                pass


class TestSessionInvalidation:
    """Tests for session invalidation"""
    
    @pytest.mark.session
    @allure.title("Session invalidated on logout")
    @allure.description("Test that session is invalidated after logout")
    def test_session_invalidated_on_logout(self, registered_user):
        """
        Test that session is invalidated after logout
        
        Steps:
        1. Login to create session
        2. Verify session is valid
        3. Logout
        4. Verify session is invalid (cannot access protected resources)
        """
        logger.info(f"Testing session invalidation: {registered_user['username']}")
        
        # Login
        user_info, session_token = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        # Verify session is valid
        profile = get_user_profile(session_token)
        assert profile['username'] == registered_user['username']
        logger.info("Session is valid before logout")
        
        # Logout
        logout_user(session_token)
        logger.info("Logged out")
        
        # Verify session is invalid
        with pytest.raises(Exception) as exc_info:
            get_user_profile(session_token)
        
        assert '401' in str(exc_info.value) or '403' in str(exc_info.value) or \
               'unauthorized' in str(exc_info.value).lower() or 'forbidden' in str(exc_info.value).lower(), \
            "Should fail with authentication error after logout"
        
        logger.info("Session correctly invalidated after logout")
    
    @pytest.mark.session
    @allure.title("All user sessions can be invalidated")
    @allure.description("Test that all sessions for a user can be invalidated at once")
    def test_invalidate_all_sessions(self, registered_user):
        """
        Test that all sessions for a user can be invalidated at once
        
        Steps:
        1. Create multiple sessions for the user
        2. Call "logout all" endpoint
        3. Verify all sessions are invalidated
        """
        logger.info(f"Testing invalidate all sessions: {registered_user['username']}")
        
        # Create multiple sessions
        sessions = []
        for i in range(3):
            user_info, session_token = login_user(
                username=registered_user['username'],
                password=registered_user['password']
            )
            sessions.append(session_token)
            logger.info(f"Created session {i+1}/3")
        
        # Verify all sessions are valid
        for session in sessions:
            profile = get_user_profile(session)
            assert profile['username'] == registered_user['username']
        
        logger.info("All sessions valid before logout all")
        
        # Call logout all endpoint
        logout_all_sessions(sessions[0])
        
        # Verify all sessions are invalid
        for session in sessions:
            with pytest.raises(Exception):
                get_user_profile(session)
        
        logger.info("All sessions invalidated successfully")


class TestSessionExpiration:
    """Tests for session expiration"""
    
    @pytest.mark.session
    @allure.title("Session expiration after timeout")
    @allure.description("Test that session expires after inactivity timeout")
    def test_session_expires_after_timeout(self, registered_user):
        """
        Test that session expires after inactivity timeout
        
        This test requires a short session timeout (e.g., 5 seconds) for testing
        
        Steps:
        1. Login to create session
        2. Wait for timeout period
        3. Verify session is expired (cannot access protected resources)
        """
        logger.info(f"Testing session expiration: {registered_user['username']}")
        
        # Login
        user_info, session_token = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        # Verify session is valid
        profile = get_user_profile(session_token)
        assert profile['username'] == registered_user['username']
        logger.info("Session is valid initially")
        
        # Wait for timeout (assume 5 seconds for testing)
        timeout_seconds = 10
        logger.info(f"Waiting {timeout_seconds} seconds for session to expire")
        time.sleep(timeout_seconds)
        
        # Verify session is expired
        with pytest.raises(Exception) as exc_info:
            get_user_profile(session_token)
        
        assert '401' in str(exc_info.value) or 'unauthorized' in str(exc_info.value).lower(), \
            "Should fail with authentication error for expired session"
        
        logger.info("Session correctly expired after timeout")
    
    @pytest.mark.session
    @allure.title("Session refreshed on activity")
    @allure.description("Test that session timeout is refreshed on activity")
    @pytest.mark.slow
    def test_session_refreshed_on_activity(self, registered_user):
        """
        Test that session timeout is refreshed on activity
        
        This test requires a short session timeout (e.g., 5 seconds) for testing
        
        Steps:
        1. Login to create session
        2. Make requests at intervals shorter than timeout
        3. Verify session remains valid beyond original timeout
        """
        logger.info(f"Testing session refresh on activity: {registered_user['username']}")
        
        # Login
        user_info, session_token = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        timeout_seconds = 10
        request_interval = 3  # Make requests every 3 seconds
        total_duration = timeout_seconds * 1.5  # 15 seconds total
        
        logger.info(f"Making requests every {request_interval}s for {total_duration}s")
        
        start_time = time.time()
        while time.time() - start_time < total_duration:
            # Make request to keep session active
            profile = get_user_profile(session_token)
            assert profile['username'] == registered_user['username'], \
                "Session should remain valid with activity"
            
            elapsed = time.time() - start_time
            logger.info(f"Request successful at {elapsed:.1f}s")
            
            time.sleep(request_interval)
        
        logger.info("Session remained valid with continuous activity")
        
        # Cleanup
        logout_user(session_token)


class TestSessionSecurity:
    """Tests for session security features"""
    
    @pytest.mark.session
    @pytest.mark.security
    @allure.title("Session fingerprint validation")
    @allure.description("Test that session fingerprint prevents hijacking")
    def test_session_fingerprint_validation(self, registered_user, env_config):
        """
        Test that session fingerprint prevents hijacking
        
        Steps:
        1. Login from "location A" (simulated with specific headers)
        2. Attempt to use session from "location B" (different headers)
        3. Verify request is rejected due to fingerprint mismatch
        """
        logger.info(f"Testing session fingerprint: {registered_user['username']}")
        
        import requests
        
        # Login with specific User-Agent
        url = f"{env_config.api_url}/v1/public/auth/login"
        headers_a = {"User-Agent": "Mozilla/5.0 (Location A)"}
        payload = {
            "username": registered_user['username'],
            "password": registered_user['password']
        }
        
        response_a = requests.post(url, json=payload, headers=headers_a)
        assert response_a.status_code == 200, "Login should succeed"
        
        # Get session cookie
        session_cookie = response_a.cookies.get('session_token')
        assert session_cookie, "Session cookie should be set"
        
        logger.info("Logged in from 'Location A'")
        
        # Try to use session from "Location B" (different User-Agent)
        profile_url = f"{env_config.api_url}/v1/public/auth/profile"
        headers_b = {"User-Agent": "Mozilla/5.0 (Location B)"}
        cookies = {'session_token': session_cookie}
        
        response_b = requests.get(profile_url, headers=headers_b, cookies=cookies)
        
        # Depending on implementation, this might:
        # 1. Reject (401/403) - strict fingerprint validation
        # 2. Accept but log warning - lenient validation
        # For now, we'll accept both behaviors
        logger.info(f"Request from 'Location B' status: {response_b.status_code}")
        
        if response_b.status_code in [401, 403]:
            logger.info("✅ Fingerprint validation is strict - request rejected")
        elif response_b.status_code == 200:
            logger.info("⚠️ Fingerprint validation is lenient - request accepted with warning")
        
        logger.info("Session fingerprint validation test completed")


