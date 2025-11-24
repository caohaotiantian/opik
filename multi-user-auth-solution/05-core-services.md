# 核心服务设计

## 1. 认证服务 (AuthService)

### 1.1 LocalAuthService 完整实现

```java
package com.comet.opik.infrastructure.auth;

import com.comet.opik.api.Visibility;
import com.comet.opik.domain.*;
import com.comet.opik.infrastructure.authorization.PermissionService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LocalAuthService implements AuthService {
    
    private static final String MISSING_WORKSPACE = "Workspace name is required";
    private static final String INVALID_SESSION = "Invalid or expired session";
    private static final String INVALID_API_KEY = "Invalid or expired API key";
    private static final String USER_NOT_FOUND = "User not found";
    private static final String USER_SUSPENDED = "User account is suspended";
    private static final String WORKSPACE_NOT_FOUND = "Workspace not found";
    private static final String ACCESS_DENIED = "Access denied to workspace";
    private static final String MEMBER_NOT_ACTIVE = "Workspace membership is not active";
    
    private final @NonNull Provider<RequestContext> requestContext;
    private final @NonNull UserService userService;
    private final @NonNull SessionService sessionService;
    private final @NonNull ApiKeyService apiKeyService;
    private final @NonNull WorkspaceService workspaceService;
    private final @NonNull WorkspaceMemberService memberService;
    private final @NonNull PermissionService permissionService;
    private final @NonNull CacheService cacheService;
    
    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        UriInfo uriInfo = contextInfo.uriInfo();
        String path = uriInfo.getRequestUri().getPath();
        String workspaceName = extractWorkspaceName(headers, uriInfo);
        
        if (StringUtils.isBlank(workspaceName)) {
            log.warn("Workspace name is missing");
            throw new ClientErrorException(MISSING_WORKSPACE, Response.Status.FORBIDDEN);
        }
        
        try {
            if (sessionToken != null && StringUtils.isNotBlank(sessionToken.getValue())) {
                authenticateUsingSession(sessionToken, workspaceName, path, contextInfo);
            } else {
                authenticateUsingApiKey(headers, workspaceName, path, contextInfo);
            }
        } catch (ClientErrorException authException) {
            // 处理公开端点访问
            if (isEndpointPublic(contextInfo)) {
                handlePublicAccess(workspaceName);
                return;
            }
            throw authException;
        }
    }
    
    @Override
    public void authenticateSession(Cookie sessionToken) {
        if (sessionToken == null || StringUtils.isBlank(sessionToken.getValue())) {
            log.warn("No session token found");
            throw new ClientErrorException("Please login first", Response.Status.FORBIDDEN);
        }
        
        // 验证 session 是否有效
        sessionService.validateSession(sessionToken.getValue())
            .orElseThrow(() -> new ClientErrorException(INVALID_SESSION, Response.Status.UNAUTHORIZED));
    }
    
    /**
     * 使用 Session Token 认证
     */
    private void authenticateUsingSession(
        Cookie sessionToken,
        String workspaceName,
        String path,
        ContextInfoHolder contextInfo
    ) {
        log.debug("Authenticating using session token for workspace: '{}'", workspaceName);
        
        // 1. 验证 Session
        Session session = sessionService.validateSession(sessionToken.getValue())
            .orElseThrow(() -> {
                log.warn("Invalid session token");
                return new ClientErrorException(INVALID_SESSION, Response.Status.UNAUTHORIZED);
            });
        
        // 2. 获取用户信息
        User user = userService.getUser(session.getUserId())
            .orElseThrow(() -> {
                log.warn("User not found for session: '{}'", session.getUserId());
                return new ClientErrorException(USER_NOT_FOUND, Response.Status.UNAUTHORIZED);
            });
        
        // 3. 检查用户状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("User '{}' is not active: '{}'", user.getUsername(), user.getStatus());
            throw new ClientErrorException(USER_SUSPENDED, Response.Status.FORBIDDEN);
        }
        
        // 4. 获取工作空间
        Workspace workspace = workspaceService.getWorkspaceByName(workspaceName)
            .orElseThrow(() -> {
                log.warn("Workspace not found: '{}'", workspaceName);
                return new ClientErrorException(WORKSPACE_NOT_FOUND, Response.Status.NOT_FOUND);
            });
        
        // 5. 检查用户访问权限 (系统管理员自动通过)
        if (!user.isSystemAdmin()) {
            WorkspaceMember member = memberService.getMember(workspace.getId(), user.getId())
                .orElseThrow(() -> {
                    log.warn("User '{}' is not a member of workspace '{}'", 
                        user.getUsername(), workspaceName);
                    return new ClientErrorException(ACCESS_DENIED, Response.Status.FORBIDDEN);
                });
            
            if (member.getStatus() != MemberStatus.ACTIVE) {
                log.warn("User '{}' membership in workspace '{}' is not active: '{}'",
                    user.getUsername(), workspaceName, member.getStatus());
                throw new ClientErrorException(MEMBER_NOT_ACTIVE, Response.Status.FORBIDDEN);
            }
        }
        
        // 6. 加载用户权限并设置上下文
        setAuthenticatedContext(user, workspace, session.getSessionToken());
        
        // 7. 更新 Session 最后访问时间 (异步)
        sessionService.updateLastAccessedAsync(session.getId());
        
        log.info("User '{}' authenticated successfully for workspace '{}' via session",
            user.getUsername(), workspaceName);
    }
    
    /**
     * 使用 API Key 认证
     */
    private void authenticateUsingApiKey(
        HttpHeaders headers,
        String workspaceName,
        String path,
        ContextInfoHolder contextInfo
    ) {
        log.debug("Authenticating using API key for workspace: '{}'", workspaceName);
        
        // 1. 提取 API Key
        String apiKey = extractApiKey(headers);
        if (StringUtils.isBlank(apiKey)) {
            log.warn("API key not found in Authorization header");
            throw new ClientErrorException("Missing API key", Response.Status.UNAUTHORIZED);
        }
        
        // 2. 检查缓存
        Optional<CachedAuthInfo> cached = cacheService.resolveApiKeyFromCache(apiKey, workspaceName);
        if (cached.isPresent()) {
            log.debug("API key found in cache for workspace: '{}'", workspaceName);
            setAuthenticatedContext(cached.get());
            return;
        }
        
        // 3. 验证 API Key
        ApiKeyInfo apiKeyInfo = apiKeyService.validateApiKey(apiKey)
            .orElseThrow(() -> {
                log.warn("Invalid API key");
                return new ClientErrorException(INVALID_API_KEY, Response.Status.UNAUTHORIZED);
            });
        
        // 4. 检查工作空间匹配
        if (!apiKeyInfo.getWorkspaceName().equals(workspaceName)) {
            log.warn("API key workspace mismatch: expected '{}', got '{}'",
                workspaceName, apiKeyInfo.getWorkspaceName());
            throw new ClientErrorException("API key not valid for this workspace", 
                Response.Status.FORBIDDEN);
        }
        
        // 5. 检查 API Key 状态
        if (apiKeyInfo.getStatus() != ApiKeyStatus.ACTIVE) {
            log.warn("API key is not active: '{}'", apiKeyInfo.getStatus());
            throw new ClientErrorException("API key is revoked or expired", 
                Response.Status.UNAUTHORIZED);
        }
        
        // 6. 检查过期时间
        if (apiKeyInfo.getExpiresAt() != null && apiKeyInfo.getExpiresAt().isBefore(Instant.now())) {
            log.warn("API key has expired");
            throw new ClientErrorException("API key has expired", Response.Status.UNAUTHORIZED);
        }
        
        // 7. 获取用户和工作空间信息
        User user = userService.getUser(apiKeyInfo.getUserId())
            .orElseThrow(() -> new ClientErrorException(USER_NOT_FOUND, Response.Status.UNAUTHORIZED));
        
        Workspace workspace = workspaceService.getWorkspace(apiKeyInfo.getWorkspaceId())
            .orElseThrow(() -> new ClientErrorException(WORKSPACE_NOT_FOUND, Response.Status.NOT_FOUND));
        
        // 8. 设置上下文 (考虑 API Key 的权限范围限制)
        setAuthenticatedContext(user, workspace, apiKey, apiKeyInfo.getPermissions());
        
        // 9. 缓存认证结果
        cacheService.cacheApiKey(apiKey, workspaceName, user, workspace, apiKeyInfo.getPermissions());
        
        // 10. 异步更新 API Key 最后使用时间
        apiKeyService.updateLastUsedAsync(apiKeyInfo.getId());
        
        log.info("User '{}' authenticated successfully for workspace '{}' via API key",
            user.getUsername(), workspaceName);
    }
    
    /**
     * 设置认证上下文
     */
    private void setAuthenticatedContext(User user, Workspace workspace, String credential) {
        setAuthenticatedContext(user, workspace, credential, null);
    }
    
    private void setAuthenticatedContext(
        User user,
        Workspace workspace,
        String credential,
        Set<Permission> apiKeyPermissions
    ) {
        RequestContext context = requestContext.get();
        context.setUserId(user.getId());
        context.setUserName(user.getUsername());
        context.setWorkspaceId(workspace.getId());
        context.setWorkspaceName(workspace.getName());
        context.setApiKey(credential);
        context.setSystemAdmin(user.isSystemAdmin());
        
        // 加载用户权限 (考虑 API Key 权限范围限制)
        if (apiKeyPermissions != null && !apiKeyPermissions.isEmpty()) {
            // API Key 有权限范围限制,使用 API Key 的权限
            context.setPermissions(apiKeyPermissions);
        } else {
            // API Key 无权限限制或 Session 认证,使用用户在工作空间中的完整权限
            Set<Permission> permissions = permissionService.getUserWorkspacePermissions(
                user.getId(), workspace.getId());
            context.setPermissions(permissions);
        }
        
        log.debug("Set authenticated context: userId='{}', workspaceId='{}', permissions={}",
            user.getId(), workspace.getId(), context.getPermissions());
    }
    
    private void setAuthenticatedContext(CachedAuthInfo cached) {
        RequestContext context = requestContext.get();
        context.setUserId(cached.getUserId());
        context.setUserName(cached.getUserName());
        context.setWorkspaceId(cached.getWorkspaceId());
        context.setWorkspaceName(cached.getWorkspaceName());
        context.setSystemAdmin(cached.isSystemAdmin());
        context.setPermissions(cached.getPermissions());
    }
    
    /**
     * 处理公开端点访问
     */
    private void handlePublicAccess(String workspaceName) {
        Workspace workspace = workspaceService.getWorkspaceByName(workspaceName)
            .orElseThrow(() -> new ClientErrorException(WORKSPACE_NOT_FOUND, Response.Status.NOT_FOUND));
        
        RequestContext context = requestContext.get();
        context.setUserId("public");
        context.setUserName("Public");
        context.setWorkspaceId(workspace.getId());
        context.setWorkspaceName(workspace.getName());
        context.setVisibility(Visibility.PUBLIC);
        context.setSystemAdmin(false);
        context.setPermissions(Set.of());
        
        log.info("Public access granted for workspace: '{}'", workspaceName);
    }
    
    /**
     * 提取工作空间名称
     */
    private String extractWorkspaceName(HttpHeaders headers, UriInfo uriInfo) {
        // 1. 从 Header 获取
        String workspaceName = headers.getHeaderString(RequestContext.WORKSPACE_HEADER);
        if (StringUtils.isNotBlank(workspaceName)) {
            return workspaceName.trim();
        }
        
        // 2. 从 Query Parameter 获取
        workspaceName = uriInfo.getQueryParameters().getFirst(RequestContext.WORKSPACE_QUERY_PARAM);
        if (StringUtils.isNotBlank(workspaceName)) {
            return workspaceName.trim();
        }
        
        return null;
    }
    
    /**
     * 提取 API Key
     */
    private String extractApiKey(HttpHeaders headers) {
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(authHeader)) {
            return null;
        }
        
        // 支持 "Bearer xxx" 或直接 "xxx"
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        
        return authHeader.trim();
    }
    
    /**
     * 检查是否为公开端点
     */
    private boolean isEndpointPublic(ContextInfoHolder contextInfo) {
        // 实现公开端点检查逻辑
        // 参考 RemoteAuthService 的 PUBLIC_ENDPOINTS
        return false;
    }
}
```

## 2. 用户服务 (UserService)

### 2.1 UserService 接口

```java
package com.comet.opik.domain;

import com.google.inject.ImplementedBy;

import java.util.Optional;

@ImplementedBy(UserServiceImpl.class)
public interface UserService {
    
    /**
     * 用户注册
     */
    User register(UserRegisterRequest request);
    
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * 用户登出
     */
    void logout(String sessionToken);
    
    /**
     * 获取用户信息
     */
    Optional<User> getUser(String userId);
    
    /**
     * 根据用户名获取用户
     */
    Optional<User> getUserByUsername(String username);
    
    /**
     * 根据邮箱获取用户
     */
    Optional<User> getUserByEmail(String email);
    
    /**
     * 更新用户信息
     */
    User updateUser(String userId, UserUpdateRequest request);
    
    /**
     * 修改密码
     */
    void changePassword(String userId, ChangePasswordRequest request);
    
    /**
     * 请求重置密码
     */
    void requestPasswordReset(String email);
    
    /**
     * 重置密码
     */
    void resetPassword(String token, String newPassword);
    
    /**
     * 修改用户状态
     */
    void updateUserStatus(String userId, UserStatus status);
    
    /**
     * 删除用户
     */
    void deleteUser(String userId);
}
```

### 2.2 UserService 实现

```java
package com.comet.opik.domain;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ConflictException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UserServiceImpl implements UserService {
    
    private final @NonNull UserDAO userDAO;
    private final @NonNull PasswordService passwordService;
    private final @NonNull SessionService sessionService;
    private final @NonNull PasswordResetTokenService tokenService;
    private final @NonNull IdGenerator idGenerator;
    
    @Override
    public User register(UserRegisterRequest request) {
        log.info("Registering new user: '{}'", request.getUsername());
        
        // 1. 验证用户名和邮箱是否已存在
        if (userDAO.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        
        if (userDAO.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }
        
        // 2. 验证密码强度
        if (!passwordService.isPasswordStrong(request.getPassword())) {
            throw new BadRequestException(
                "Password must be at least 8 characters and contain uppercase, lowercase, digit and special character");
        }
        
        // 3. 创建用户
        User user = User.builder()
            .id(idGenerator.generate())
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordService.hashPassword(request.getPassword()))
            .fullName(request.getFullName())
            .status(UserStatus.ACTIVE)
            .isSystemAdmin(false)
            .emailVerified(false)  // 当前版本不需要邮箱验证
            .createdAt(Instant.now())
            .createdBy("system")
            .lastUpdatedBy("system")
            .build();
        
        userDAO.save(user);
        
        log.info("User '{}' registered successfully", user.getUsername());
        return user;
    }
    
    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("User login attempt: '{}'", request.getUsername());
        
        // 1. 查找用户
        User user = userDAO.findByUsername(request.getUsername())
            .orElseThrow(() -> {
                log.warn("Login failed: user not found '{}'", request.getUsername());
                return new BadRequestException("Invalid username or password");
            });
        
        // 2. 验证密码
        if (!passwordService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user '{}'", request.getUsername());
            throw new BadRequestException("Invalid username or password");
        }
        
        // 3. 检查用户状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login failed: user '{}' is not active: '{}'", 
                user.getUsername(), user.getStatus());
            throw new BadRequestException("User account is suspended");
        }
        
        // 4. 创建 Session
        Session session = sessionService.createSession(
            user.getId(),
            request.getIpAddress(),
            request.getUserAgent()
        );
        
        // 5. 更新最后登录时间
        userDAO.updateLastLogin(user.getId(), Instant.now());
        
        log.info("User '{}' logged in successfully", user.getUsername());
        
        return LoginResponse.builder()
            .sessionToken(session.getSessionToken())
            .user(user)
            .expiresAt(session.getExpiresAt())
            .build();
    }
    
    @Override
    public void logout(String sessionToken) {
        log.info("User logout");
        sessionService.invalidateSession(sessionToken);
    }
    
    @Override
    public Optional<User> getUser(String userId) {
        return userDAO.findById(userId);
    }
    
    @Override
    public Optional<User> getUserByUsername(String username) {
        return userDAO.findByUsername(username);
    }
    
    @Override
    public Optional<User> getUserByEmail(String email) {
        return userDAO.findByEmail(email);
    }
    
    @Override
    public User updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user: '{}'", userId);
        
        User user = getUser(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        // 检查邮箱是否被其他用户使用
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userDAO.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already in use");
            }
        }
        
        userDAO.update(
            userId,
            request.getEmail(),
            request.getFullName(),
            request.getAvatarUrl(),
            request.getLocale()
        );
        
        log.info("User '{}' updated successfully", userId);
        return getUser(userId).orElseThrow();
    }
    
    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        log.info("Changing password for user: '{}'", userId);
        
        User user = getUser(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        // 验证旧密码
        if (!passwordService.verifyPassword(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid old password");
        }
        
        // 验证新密码强度
        if (!passwordService.isPasswordStrong(request.getNewPassword())) {
            throw new BadRequestException("New password does not meet strength requirements");
        }
        
        // 更新密码
        String newPasswordHash = passwordService.hashPassword(request.getNewPassword());
        userDAO.updatePassword(userId, newPasswordHash);
        
        log.info("Password changed successfully for user: '{}'", userId);
    }
    
    @Override
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: '{}'", email);
        
        User user = getUserByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        // 创建重置令牌
        String token = tokenService.createResetToken(user.getId());
        
        // TODO: 发送重置邮件 (当前版本暂不实现)
        log.info("Password reset token generated for user: '{}', token: '{}'", 
            user.getUsername(), token);
    }
    
    @Override
    public void resetPassword(String token, String newPassword) {
        log.info("Resetting password with token");
        
        // 验证令牌
        String userId = tokenService.validateResetToken(token)
            .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));
        
        // 验证新密码强度
        if (!passwordService.isPasswordStrong(newPassword)) {
            throw new BadRequestException("Password does not meet strength requirements");
        }
        
        // 更新密码
        String newPasswordHash = passwordService.hashPassword(newPassword);
        userDAO.updatePassword(userId, newPasswordHash);
        
        // 标记令牌为已使用
        tokenService.markTokenAsUsed(token);
        
        log.info("Password reset successfully for user: '{}'", userId);
    }
    
    @Override
    public void updateUserStatus(String userId, UserStatus status) {
        log.info("Updating user status: '{}' to '{}'", userId, status);
        
        User user = getUser(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        userDAO.updateStatus(userId, status);
        
        log.info("User '{}' status updated to '{}'", user.getUsername(), status);
    }
    
    @Override
    public void deleteUser(String userId) {
        log.info("Deleting user: '{}'", userId);
        
        // 软删除: 将状态改为 DELETED
        updateUserStatus(userId, UserStatus.DELETED);
        
        log.info("User '{}' deleted successfully", userId);
    }
}
```

## 3. Session 服务 (SessionService)

### 3.1 SessionService 实现

```java
package com.comet.opik.domain;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionService {
    
    private final @NonNull SessionDAO sessionDAO;
    private final @NonNull RedissonClient redissonClient;
    
    private static final int SESSION_TIMEOUT_HOURS = 24;
    private static final String SESSION_CACHE_PREFIX = "session:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 创建 Session
     */
    public Session createSession(String userId, String ipAddress, String userAgent) {
        log.debug("Creating session for user: '{}'", userId);
        
        String sessionToken = generateSecureToken();
        Instant expiresAt = Instant.now().plus(SESSION_TIMEOUT_HOURS, ChronoUnit.HOURS);
        
        Session session = Session.builder()
            .id(UUID.randomUUID().toString())
            .sessionToken(sessionToken)
            .userId(userId)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .expiresAt(expiresAt)
            .createdAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .build();
        
        // 保存到数据库
        sessionDAO.save(session);
        
        // 缓存到 Redis
        cacheSession(session);
        
        log.info("Session created for user: '{}'", userId);
        return session;
    }
    
    /**
     * 验证 Session
     */
    public Optional<Session> validateSession(String sessionToken) {
        // 1. 从缓存获取
        Session cached = getFromCache(sessionToken);
        if (cached != null) {
            if (cached.getExpiresAt().isAfter(Instant.now())) {
                log.debug("Session found in cache and valid");
                return Optional.of(cached);
            } else {
                log.debug("Session found in cache but expired");
                removeFromCache(sessionToken);
                return Optional.empty();
            }
        }
        
        // 2. 从数据库获取
        Optional<Session> session = sessionDAO.findByToken(sessionToken);
        if (session.isPresent()) {
            if (session.get().getExpiresAt().isAfter(Instant.now())) {
                log.debug("Session found in database and valid");
                cacheSession(session.get());
                return session;
            } else {
                log.debug("Session found in database but expired");
                return Optional.empty();
            }
        }
        
        log.debug("Session not found");
        return Optional.empty();
    }
    
    /**
     * 更新最后访问时间 (异步)
     */
    public void updateLastAccessedAsync(String sessionId) {
        // 使用异步方式更新,不阻塞主流程
        CompletableFuture.runAsync(() -> {
            try {
                sessionDAO.updateLastAccessed(sessionId, Instant.now());
            } catch (Exception e) {
                log.error("Failed to update session last accessed time: '{}'", sessionId, e);
            }
        });
    }
    
    /**
     * 销毁 Session
     */
    public void invalidateSession(String sessionToken) {
        log.debug("Invalidating session");
        
        // 从数据库删除
        sessionDAO.deleteByToken(sessionToken);
        
        // 从缓存删除
        removeFromCache(sessionToken);
        
        log.info("Session invalidated");
    }
    
    /**
     * 生成安全的 Session Token
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    /**
     * 缓存 Session
     */
    private void cacheSession(Session session) {
        String key = SESSION_CACHE_PREFIX + session.getSessionToken();
        long ttlSeconds = session.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redissonClient.getBucket(key).set(session, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Session cached with TTL: '{}' seconds", ttlSeconds);
        }
    }
    
    /**
     * 从缓存获取 Session
     */
    private Session getFromCache(String sessionToken) {
        String key = SESSION_CACHE_PREFIX + sessionToken;
        return (Session) redissonClient.getBucket(key).get();
    }
    
    /**
     * 从缓存删除 Session
     */
    private void removeFromCache(String sessionToken) {
        String key = SESSION_CACHE_PREFIX + sessionToken;
        redissonClient.getBucket(key).delete();
    }
}
```

## 4. API Key 服务 (ApiKeyService)

### 4.1 ApiKeyService 实现

```java
package com.comet.opik.domain;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApiKeyService {
    
    private final @NonNull ApiKeyDAO apiKeyDAO;
    private final @NonNull UserService userService;
    private final @NonNull WorkspaceService workspaceService;
    
    private static final String API_KEY_PREFIX = "opik_";
    private static final int API_KEY_LENGTH = 48;
    private static final int MAX_API_KEYS_PER_USER = 50;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 生成 API Key
     */
    public ApiKeyResponse generateApiKey(ApiKeyCreateRequest request) {
        log.info("Generating API key for user: '{}' in workspace: '{}'",
            request.getUserId(), request.getWorkspaceId());
        
        // 1. 验证用户和工作空间
        userService.getUser(request.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        workspaceService.getWorkspace(request.getWorkspaceId())
            .orElseThrow(() -> new NotFoundException("Workspace not found"));
        
        // 2. 检查用户的 API Key 数量限制
        int userKeyCount = apiKeyDAO.countByUser(request.getUserId());
        if (userKeyCount >= MAX_API_KEYS_PER_USER) {
            throw new BadRequestException(
                "Maximum API keys limit reached. Maximum allowed: " + MAX_API_KEYS_PER_USER);
        }
        
        // 3. 生成 API Key
        String apiKey = generateSecureApiKey();
        String keyHash = hashApiKey(apiKey);
        String keyPrefix = apiKey.substring(0, 12);
        
        // 4. 计算过期时间 (默认不过期)
        Instant expiresAt = request.getExpiryDays() != null
            ? Instant.now().plus(request.getExpiryDays(), ChronoUnit.DAYS)
            : null;
        
        // 5. 创建 API Key 记录
        ApiKey apiKeyEntity = ApiKey.builder()
            .id(UUID.randomUUID().toString())
            .userId(request.getUserId())
            .workspaceId(request.getWorkspaceId())
            .keyHash(keyHash)
            .keyPrefix(keyPrefix)
            .name(request.getName())
            .description(request.getDescription())
            .status(ApiKeyStatus.ACTIVE)
            .permissions(request.getPermissions())  // 权限范围限制
            .expiresAt(expiresAt)
            .createdAt(Instant.now())
            .build();
        
        apiKeyDAO.save(apiKeyEntity);
        
        log.info("API key generated successfully: '{}' for user: '{}'",
            apiKeyEntity.getId(), request.getUserId());
        
        // 6. 返回明文 API Key (只在创建时返回一次)
        return ApiKeyResponse.builder()
            .id(apiKeyEntity.getId())
            .apiKey(apiKey)  // 明文,只返回一次
            .keyPrefix(keyPrefix)
            .name(request.getName())
            .permissions(request.getPermissions())
            .createdAt(apiKeyEntity.getCreatedAt())
            .expiresAt(expiresAt)
            .build();
    }
    
    /**
     * 验证 API Key
     */
    public Optional<ApiKeyInfo> validateApiKey(String apiKey) {
        String keyHash = hashApiKey(apiKey);
        return apiKeyDAO.findByHash(keyHash);
    }
    
    /**
     * 撤销 API Key
     */
    public void revokeApiKey(String apiKeyId) {
        log.info("Revoking API key: '{}'", apiKeyId);
        
        apiKeyDAO.updateStatus(apiKeyId, ApiKeyStatus.REVOKED);
        
        log.info("API key revoked: '{}'", apiKeyId);
    }
    
    /**
     * 更新最后使用时间 (异步)
     */
    public void updateLastUsedAsync(String apiKeyId) {
        CompletableFuture.runAsync(() -> {
            try {
                apiKeyDAO.updateLastUsed(apiKeyId, Instant.now());
            } catch (Exception e) {
                log.error("Failed to update API key last used time: '{}'", apiKeyId, e);
            }
        });
    }
    
    /**
     * 生成安全的 API Key
     */
    private String generateSecureApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return API_KEY_PREFIX + randomPart;
    }
    
    /**
     * 哈希 API Key (SHA-256)
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
```

## 5. 工作空间服务 (WorkspaceService)

### 5.1 WorkspaceService 实现

```java
package com.comet.opik.domain;

import com.comet.opik.infrastructure.authorization.Permission;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ConflictException;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorkspaceService {
    
    private final @NonNull WorkspaceDAO workspaceDAO;
    private final @NonNull WorkspaceMemberService memberService;
    private final @NonNull RoleService roleService;
    private final @NonNull UserService userService;
    
    private static final int DEFAULT_QUOTA = 10;
    
    /**
     * 创建工作空间
     */
    public Workspace createWorkspace(WorkspaceCreateRequest request, String currentUserId) {
        log.info("Creating workspace: '{}' by user: '{}'", request.getName(), currentUserId);
        
        // 1. 验证工作空间名称是否已存在
        if (workspaceDAO.existsByName(request.getName())) {
            throw new ConflictException("Workspace name already exists");
        }
        
        // 2. 验证所有者用户存在
        userService.getUser(currentUserId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        // 3. 创建工作空间
        Workspace workspace = Workspace.builder()
            .id(UUID.randomUUID().toString())
            .name(request.getName())
            .displayName(request.getDisplayName())
            .description(request.getDescription())
            .ownerUserId(currentUserId)
            .quotaLimit(DEFAULT_QUOTA)
            .status(WorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .createdBy(currentUserId)
            .build();
        
        workspaceDAO.save(workspace);
        
        // 4. 将创建者添加为工作空间管理员
        Role workspaceAdminRole = roleService.getBuiltinRole("Workspace Admin", RoleScope.WORKSPACE)
            .orElseThrow(() -> new IllegalStateException("Workspace Admin role not found"));
        
        memberService.addMember(
            workspace.getId(),
            currentUserId,
            workspaceAdminRole.getId()
        );
        
        log.info("Workspace '{}' created successfully", workspace.getName());
        return workspace;
    }
    
    /**
     * 获取工作空间
     */
    public Optional<Workspace> getWorkspace(String workspaceId) {
        return workspaceDAO.findById(workspaceId);
    }
    
    /**
     * 根据名称获取工作空间
     */
    public Optional<Workspace> getWorkspaceByName(String name) {
        return workspaceDAO.findByName(name);
    }
    
    /**
     * 获取用户的工作空间列表
     */
    public List<Workspace> getUserWorkspaces(String userId) {
        return workspaceDAO.findByUserId(userId);
    }
    
    /**
     * 更新工作空间
     */
    public Workspace updateWorkspace(String workspaceId, WorkspaceUpdateRequest request) {
        log.info("Updating workspace: '{}'", workspaceId);
        
        Workspace workspace = getWorkspace(workspaceId)
            .orElseThrow(() -> new NotFoundException("Workspace not found"));
        
        // 检查名称是否被其他工作空间使用
        if (request.getName() != null && !request.getName().equals(workspace.getName())) {
            if (workspaceDAO.existsByName(request.getName())) {
                throw new ConflictException("Workspace name already exists");
            }
        }
        
        workspaceDAO.update(
            workspaceId,
            request.getName(),
            request.getDisplayName(),
            request.getDescription(),
            request.getQuotaLimit()
        );
        
        log.info("Workspace '{}' updated successfully", workspaceId);
        return getWorkspace(workspaceId).orElseThrow();
    }
    
    /**
     * 删除工作空间
     */
    public void deleteWorkspace(String workspaceId) {
        log.info("Deleting workspace: '{}'", workspaceId);
        
        // 软删除: 将状态改为 DELETED
        workspaceDAO.updateStatus(workspaceId, WorkspaceStatus.DELETED);
        
        log.info("Workspace '{}' deleted successfully", workspaceId);
    }
}
```

## 6. 缓存服务 (CacheService)

### 6.1 缓存优化策略

```java
package com.comet.opik.infrastructure.cache;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CacheService {
    
    private final @NonNull RedissonClient redissonClient;
    private final @NonNull RBloomFilter<String> apiKeyBloomFilter;
    
    private static final String CACHE_PREFIX_SESSION = "session:";
    private static final String CACHE_PREFIX_API_KEY = "apikey:";
    private static final String CACHE_PREFIX_PERMISSION = "permission:";
    private static final String LOCK_PREFIX = "lock:";
    
    /**
     * 防止缓存雪崩 - 随机 TTL
     */
    public void set(String key, Object value, int baseTtlSeconds) {
        // TTL 加上随机偏移 (±10%)
        int randomOffset = (int) (baseTtlSeconds * 0.1 * Math.random());
        int actualTtl = baseTtlSeconds + randomOffset;
        
        redissonClient.getBucket(key).set(value, actualTtl, TimeUnit.SECONDS);
    }
    
    /**
     * 防止缓存击穿 - 分布式锁
     */
    public <T> Optional<T> getOrLoad(String key, 
                                     java.util.function.Supplier<Optional<T>> loader,
                                     int ttlSeconds) {
        // 1. 尝试从缓存获取
        Object cached = redissonClient.getBucket(key).get();
        if (cached != null) {
            return Optional.of((T) cached);
        }
        
        // 2. 缓存未命中，加锁查询
        String lockKey = LOCK_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 100ms
            if (lock.tryLock(100, 5000, TimeUnit.MILLISECONDS)) {
                // 双重检查
                cached = redissonClient.getBucket(key).get();
                if (cached != null) {
                    return Optional.of((T) cached);
                }
                
                // 加载数据
                Optional<T> data = loader.get();
                
                // 缓存结果（即使为空也缓存，防止缓存穿透）
                if (data.isPresent()) {
                    set(key, data.get(), ttlSeconds);
                } else {
                    // 空值缓存较短时间
                    set(key, NULL_VALUE, 60);
                }
                
                return data;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to acquire lock for key: {}", key, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        
        // 获取锁失败，直接查询数据库
        return loader.get();
    }
    
    /**
     * 防止缓存穿透 - 布隆过滤器
     */
    public boolean mightContainApiKey(String apiKeyHash) {
        return apiKeyBloomFilter.contains(apiKeyHash);
    }
    
    /**
     * API Key 加入布隆过滤器
     */
    public void addApiKeyToBloomFilter(String apiKeyHash) {
        apiKeyBloomFilter.add(apiKeyHash);
    }
    
    /**
     * 缓存失效
     */
    public void invalidate(String key) {
        redissonClient.getBucket(key).delete();
    }
    
    /**
     * 批量缓存失效
     */
    public void invalidatePattern(String pattern) {
        redissonClient.getKeys().deleteByPattern(pattern);
    }
    
    private static final Object NULL_VALUE = new Object();
}
```

### 6.2 Session 定时清理

```java
package com.comet.opik.domain;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionCleanupService {
    
    private final @NonNull SessionDAO sessionDAO;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    /**
     * 启动定时清理任务
     */
    public void start() {
        // 每小时执行一次清理
        scheduler.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            1,  // 初始延迟 1 小时
            1,  // 每 1 小时
            TimeUnit.HOURS
        );
        
        log.info("Session cleanup scheduler started");
    }
    
    /**
     * 清理过期 Session
     */
    private void cleanupExpiredSessions() {
        try {
            Instant now = Instant.now();
            int deleted = sessionDAO.deleteExpiredSessions(now);
            
            if (deleted > 0) {
                log.info("Cleaned up {} expired sessions", deleted);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
        }
    }
    
    /**
     * 清理用户的旧 Session (保留最新的 N 个)
     */
    public void cleanupOldUserSessions(String userId, int keepCount) {
        try {
            int deleted = sessionDAO.deleteOldUserSessions(userId, keepCount);
            
            if (deleted > 0) {
                log.info("Cleaned up {} old sessions for user: {}", deleted, userId);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup old user sessions", e);
        }
    }
    
    /**
     * 停止定时任务
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Session cleanup scheduler stopped");
    }
}
```

### 6.3 Session 并发控制

```java
// SessionService 增强版本
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionService {
    
    private final @NonNull SessionDAO sessionDAO;
    private final @NonNull RedissonClient redissonClient;
    private final @NonNull SessionCleanupService cleanupService;
    
    private static final int SESSION_TIMEOUT_HOURS = 24;
    private static final int MAX_CONCURRENT_SESSIONS = 5;  // 最大并发 Session 数
    private static final String SESSION_CACHE_PREFIX = "session:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 创建 Session - 带并发限制
     */
    public Session createSession(String userId, String ipAddress, String userAgent) {
        log.debug("Creating session for user: '{}'", userId);
        
        // 1. 检查并发 Session 数量
        int activeSessionCount = sessionDAO.countActiveSessionsByUser(userId);
        if (activeSessionCount >= MAX_CONCURRENT_SESSIONS) {
            // 删除最老的 Session
            cleanupService.cleanupOldUserSessions(userId, MAX_CONCURRENT_SESSIONS - 1);
        }
        
        // 2. 生成 Session Token 和指纹
        String sessionToken = generateSecureToken();
        String fingerprint = generateFingerprint(ipAddress, userAgent);
        Instant expiresAt = Instant.now().plus(SESSION_TIMEOUT_HOURS, ChronoUnit.HOURS);
        
        // 3. 创建 Session
        Session session = Session.builder()
            .id(UUID.randomUUID().toString())
            .sessionToken(sessionToken)
            .userId(userId)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .fingerprint(fingerprint)
            .expiresAt(expiresAt)
            .createdAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .build();
        
        // 4. 保存到数据库
        sessionDAO.save(session);
        
        // 5. 缓存到 Redis
        cacheSession(session);
        
        log.info("Session created for user: '{}', concurrent sessions: {}", 
            userId, activeSessionCount + 1);
        
        return session;
    }
    
    /**
     * 验证 Session - 带指纹检查
     */
    public Optional<Session> validateSession(String sessionToken, 
                                             String currentIp, 
                                             String currentUserAgent) {
        // 1. 从缓存获取
        Session cached = getFromCache(sessionToken);
        if (cached != null) {
            // 检查过期
            if (cached.getExpiresAt().isAfter(Instant.now())) {
                // 检查指纹
                if (verifyFingerprint(cached, currentIp, currentUserAgent)) {
                    return Optional.of(cached);
                } else {
                    // 指纹不匹配，删除 Session
                    log.warn("Session fingerprint mismatch, possible hijacking: userId={}", 
                        cached.getUserId());
                    invalidateSession(sessionToken);
                    return Optional.empty();
                }
            } else {
                removeFromCache(sessionToken);
                return Optional.empty();
            }
        }
        
        // 2. 从数据库获取
        Optional<Session> session = sessionDAO.findByToken(sessionToken);
        if (session.isPresent()) {
            // 检查过期
            if (session.get().getExpiresAt().isAfter(Instant.now())) {
                // 检查指纹
                if (verifyFingerprint(session.get(), currentIp, currentUserAgent)) {
                    cacheSession(session.get());
                    return session;
                } else {
                    log.warn("Session fingerprint mismatch, possible hijacking: userId={}", 
                        session.get().getUserId());
                    invalidateSession(sessionToken);
                    return Optional.empty();
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * 生成 Session 指纹
     */
    private String generateFingerprint(String ipAddress, String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = ipAddress + "|" + (userAgent != null ? userAgent : "");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * 验证 Session 指纹
     */
    private boolean verifyFingerprint(Session session, String currentIp, String currentUserAgent) {
        String currentFingerprint = generateFingerprint(currentIp, currentUserAgent);
        return session.getFingerprint().equals(currentFingerprint);
    }
    
    /**
     * 生成安全的 Session Token
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    // ... 其他方法保持不变 ...
}
```

## 7. 密码服务 (PasswordService)

```java
package com.comet.opik.domain;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

@Slf4j
@Singleton
public class PasswordService {
    
    private static final int BCRYPT_COST = 12;
    
    /**
     * 哈希密码
     */
    public String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
    }
    
    /**
     * 验证密码
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            log.error("Password verification failed", e);
            return false;
        }
    }
    
    /**
     * 检查密码强度
     * 要求:
     * - 至少 8 个字符
     * - 包含大写字母
     * - 包含小写字母
     * - 包含数字
     * - 包含特殊字符
     */
    public boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUppercase = password.matches(".*[A-Z].*");
        boolean hasLowercase = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[@$!%*?&].*");
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecial;
    }
}
```

## 8. 性能监控服务

### 8.1 Metrics 定义

```java
package com.comet.opik.infrastructure.metrics;

import com.codahale.metrics.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

@Singleton
@Getter
public class AuthMetrics {
    
    private final MetricRegistry registry;
    
    // 认证指标
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Timer authenticationTimer;
    private final Histogram authenticationDuration;
    
    // 权限指标
    private final Counter permissionDeniedCounter;
    private final Counter permissionGrantedCounter;
    private final Histogram permissionsPerUserHistogram;
    
    // Session 指标
    private final Counter sessionCreatedCounter;
    private final Counter sessionExpiredCounter;
    private final Counter sessionHijackAttemptCounter;
    
    // API Key 指标
    private final Counter apiKeyGeneratedCounter;
    private final Counter apiKeyRevokedCounter;
    private final Counter apiKeyValidationCounter;
    
    // 缓存指标
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer cacheAccessTimer;
    
    @Inject
    public AuthMetrics(MetricRegistry registry) {
        this.registry = registry;
        
        // 初始化指标
        this.loginSuccessCounter = registry.counter("auth.login.success");
        this.loginFailureCounter = registry.counter("auth.login.failure");
        this.authenticationTimer = registry.timer("auth.authentication.timer");
        this.authenticationDuration = registry.histogram("auth.authentication.duration");
        
        this.permissionDeniedCounter = registry.counter("auth.permission.denied");
        this.permissionGrantedCounter = registry.counter("auth.permission.granted");
        this.permissionsPerUserHistogram = registry.histogram("auth.permission.per_user");
        
        this.sessionCreatedCounter = registry.counter("auth.session.created");
        this.sessionExpiredCounter = registry.counter("auth.session.expired");
        this.sessionHijackAttemptCounter = registry.counter("auth.session.hijack_attempt");
        
        this.apiKeyGeneratedCounter = registry.counter("auth.apikey.generated");
        this.apiKeyRevokedCounter = registry.counter("auth.apikey.revoked");
        this.apiKeyValidationCounter = registry.counter("auth.apikey.validation");
        
        this.cacheHitCounter = registry.counter("cache.hit");
        this.cacheMissCounter = registry.counter("cache.miss");
        this.cacheAccessTimer = registry.timer("cache.access.timer");
        
        // 注册 Gauge
        registry.gauge("auth.session.active", () -> this::getActiveSessionCount);
        registry.gauge("auth.apikey.active", () -> this::getActiveApiKeyCount);
    }
    
    private Long getActiveSessionCount() {
        // 从数据库或缓存获取活跃 Session 数量
        return 0L;  // 占位符
    }
    
    private Long getActiveApiKeyCount() {
        // 从数据库获取活跃 API Key 数量
        return 0L;  // 占位符
    }
}
```

### 8.2 使用示例

```java
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UserService {
    
    private final @NonNull AuthMetrics metrics;
    
    @Timed(name = "user.login.timer")
    @Metered(name = "user.login.rate")
    public LoginResponse login(LoginRequest request) {
        Timer.Context timer = metrics.getAuthenticationTimer().time();
        
        try {
            // ... 登录逻辑 ...
            metrics.getLoginSuccessCounter().inc();
            return response;
        } catch (AuthenticationException e) {
            metrics.getLoginFailureCounter().inc();
            throw e;
        } finally {
            long duration = timer.stop();
            metrics.getAuthenticationDuration().update(duration);
        }
    }
}
```

下一章: [实施计划](./06-implementation-plan.md)

