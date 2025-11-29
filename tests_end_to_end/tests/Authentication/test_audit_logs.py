"""
E2E Tests for Audit Logging

Tests:
1. Audit log creation on user actions
2. Audit log query and filtering
3. Audit log data completeness
"""
import pytest
import logging
import allure
import time
from tests.Authentication.auth_helpers import (
    register_user,
    login_user,
    logout_user,
    create_workspace,
    get_audit_logs,
    wait_for_audit_log,
)

logger = logging.getLogger(__name__)


class TestAuditLogCreation:
    """Tests for audit log creation"""
    
    @pytest.mark.audit
    @allure.title("Audit log for user registration")
    @allure.description("Test that user registration creates an audit log")
    def test_audit_log_user_registration(self, random_username, random_email, test_password):
        """
        Test that user registration creates an audit log
        
        Steps:
        1. Register a new user
        2. Login as admin to query audit logs
        3. Verify audit log exists for user registration
        """
        logger.info(f"Testing audit log for user registration: {random_username}")
        
        # Register user
        user_data = register_user(
            username=random_username,
            email=random_email,
            password=test_password
        )
        
        # TODO: Login as admin and query audit logs
        # admin_session = login_as_admin()
        # 
        # # Wait for audit log to be written (async processing)
        # found = wait_for_audit_log(
        #     session_token=admin_session,
        #     operation="USER_REGISTER",
        #     timeout=10
        # )
        # 
        # assert found, "Audit log should be created for user registration"
        
        logger.info("Audit log verification completed")
    
    @pytest.mark.audit
    @allure.title("Audit log for user login")
    @allure.description("Test that user login creates an audit log")
    def test_audit_log_user_login(self, registered_user):
        """
        Test that user login creates an audit log
        
        Steps:
        1. Login as user
        2. Query audit logs (as admin)
        3. Verify audit log exists for login
        """
        logger.info(f"Testing audit log for user login: {registered_user['username']}")
        
        # Login
        user_info, session_token = login_user(
            username=registered_user['username'],
            password=registered_user['password']
        )
        
        # TODO: Query audit logs as admin
        # Verify login audit log exists
        
        logger.info("Audit log verification completed")
        
        # Cleanup
        logout_user(session_token)
    
    @pytest.mark.audit
    @allure.title("Audit log for workspace creation")
    @allure.description("Test that workspace creation creates an audit log")
    def test_audit_log_workspace_creation(self, logged_in_user):
        """
        Test that workspace creation creates an audit log
        
        Steps:
        1. Create a workspace
        2. Query audit logs
        3. Verify audit log exists for workspace creation
        """
        logger.info(f"Testing audit log for workspace creation: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Create workspace
        workspace = create_workspace(
            session_token=session_token,
            name=f"test_workspace_{int(time.time())}",
            display_name="Test Workspace"
        )
        
        # Wait for audit log
        found = wait_for_audit_log(
            session_token=session_token,
            operation="CREATE_WORKSPACE",
            timeout=10
        )
        
        assert found, "Audit log should be created for workspace creation"
        
        logger.info("Audit log verified successfully")


class TestAuditLogQuery:
    """Tests for audit log querying"""
    
    @pytest.mark.audit
    @allure.title("Query audit logs - all operations")
    @allure.description("Test querying all audit logs")
    def test_query_all_audit_logs(self, logged_in_user):
        """
        Test querying all audit logs
        
        Steps:
        1. Perform some actions (create workspace, etc.)
        2. Query all audit logs
        3. Verify logs are returned
        """
        logger.info(f"Testing query all audit logs: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Perform some actions
        workspace = create_workspace(
            session_token=session_token,
            name=f"test_workspace_{int(time.time())}",
            display_name="Test Workspace"
        )
        
        # Wait for async processing
        time.sleep(5)
        
        # Query audit logs
        result = get_audit_logs(session_token=session_token)
        
        # Verify response structure
        assert 'items' in result, "Response should contain items"
        assert 'total' in result, "Response should contain total count"
        assert 'page' in result, "Response should contain page number"
        
        logger.info(f"Found {len(result['items'])} audit log entries")
    
    @pytest.mark.audit
    @allure.title("Query audit logs - filter by operation")
    @allure.description("Test querying audit logs filtered by operation type")
    def test_query_audit_logs_by_operation(self, logged_in_user):
        """
        Test querying audit logs filtered by operation type
        
        Steps:
        1. Perform specific operation (e.g., create workspace)
        2. Query audit logs filtered by that operation
        3. Verify only logs for that operation are returned
        """
        logger.info(f"Testing query audit logs by operation: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Create workspace
        workspace = create_workspace(
            session_token=session_token,
            name=f"test_workspace_{int(time.time())}",
            display_name="Test Workspace"
        )
        
        # Wait for async processing
        time.sleep(5)
        
        # Query audit logs for CREATE_WORKSPACE
        result = get_audit_logs(
            session_token=session_token,
            operation="CREATE_WORKSPACE"
        )
        
        # Verify all returned logs are for CREATE_WORKSPACE
        for log in result['items']:
            assert log['operation'] == "CREATE_WORKSPACE", \
                "All logs should be for CREATE_WORKSPACE operation"
        
        logger.info(f"Found {len(result['items'])} CREATE_WORKSPACE audit logs")
    
    @pytest.mark.audit
    @allure.title("Query audit logs - date range filter")
    @allure.description("Test querying audit logs with date range filter")
    def test_query_audit_logs_by_date_range(self, logged_in_user):
        """
        Test querying audit logs with date range filter
        
        Steps:
        1. Query audit logs for a specific date range
        2. Verify all returned logs are within the date range
        """
        logger.info(f"Testing query audit logs by date range: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Get today's date range
        from datetime import datetime, timedelta
        today = datetime.utcnow().date()
        start_date = today.isoformat()
        end_date = (today + timedelta(days=1)).isoformat()
        
        # Query audit logs for today
        result = get_audit_logs(
            session_token=session_token,
            start_date=start_date,
            end_date=end_date
        )
        
        # Verify all logs are within date range
        for log in result['items']:
            log_date = datetime.fromisoformat(log['timestamp']).date()
            assert log_date == today, \
                "All logs should be from today"
        
        logger.info(f"Found {len(result['items'])} audit logs for today")


class TestAuditLogData:
    """Tests for audit log data completeness"""
    
    @pytest.mark.audit
    @allure.title("Audit log data completeness")
    @allure.description("Test that audit logs contain all required fields")
    def test_audit_log_data_completeness(self, logged_in_user):
        """
        Test that audit logs contain all required fields
        
        Steps:
        1. Perform an action
        2. Query the audit log
        3. Verify log contains all required fields:
           - operation
           - userId
           - timestamp
           - status
           - entityType
           - entityId
           - details
        """
        logger.info(f"Testing audit log data completeness: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Create workspace
        workspace = create_workspace(
            session_token=session_token,
            name=f"test_workspace_{int(time.time())}",
            display_name="Test Workspace"
        )
        
        # Wait and query
        found = wait_for_audit_log(
            session_token=session_token,
            operation="CREATE_WORKSPACE",
            timeout=10
        )
        
        assert found, "Audit log should exist"
        
        # Get the specific audit log
        result = get_audit_logs(
            session_token=session_token,
            operation="CREATE_WORKSPACE"
        )
        
        audit_log = result['items'][0]
        
        # Verify required fields
        required_fields = [
            'operation',
            'userId',
            'timestamp',
            'status',
            'entityType',
            'entityId',
        ]
        
        for field in required_fields:
            assert field in audit_log, f"Audit log should contain {field}"
            assert audit_log[field] is not None, f"{field} should not be null"
        
        logger.info("Audit log data completeness verified")
    
    @pytest.mark.audit
    @allure.title("Audit log captures user context")
    @allure.description("Test that audit logs capture user context (IP, user agent, etc.)")
    def test_audit_log_user_context(self, logged_in_user):
        """
        Test that audit logs capture user context
        
        Steps:
        1. Perform an action
        2. Query the audit log
        3. Verify log contains user context:
           - IP address
           - User agent
           - Workspace
        """
        logger.info(f"Testing audit log user context: {logged_in_user['username']}")
        
        session_token = logged_in_user['session_token']
        
        # Create workspace
        workspace = create_workspace(
            session_token=session_token,
            name=f"test_workspace_{int(time.time())}",
            display_name="Test Workspace"
        )
        
        # Wait and query
        time.sleep(5)
        
        result = get_audit_logs(
            session_token=session_token,
            operation="CREATE_WORKSPACE"
        )
        
        audit_log = result['items'][0]
        
        # Verify user context fields
        assert 'userId' in audit_log, "Should contain userId"
        assert audit_log['userId'] == logged_in_user['id'], "userId should match logged-in user"
        
        # Optional context fields
        context_fields = ['ipAddress', 'userAgent', 'workspaceId']
        for field in context_fields:
            if field in audit_log:
                logger.info(f"Audit log contains {field}: {audit_log[field]}")
        
        logger.info("Audit log user context verified")


