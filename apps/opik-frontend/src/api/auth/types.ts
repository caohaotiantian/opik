/**
 * Auth API Types
 * 认证相关的类型定义
 */

// 用户状态枚举
export type UserStatus = "active" | "suspended" | "deleted";

// 登录请求
export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

// 登录响应
export interface LoginResponse {
  userId: string;
  username: string;
  email: string;
  fullName?: string;
  defaultWorkspaceId: string;
  defaultWorkspaceName: string;
  systemAdmin: boolean;
}

// 注册请求
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName?: string;
}

// 注册响应 (与登录响应相同)
export type RegisterResponse = LoginResponse;

// 用户信息
export interface User {
  id: string;
  username: string;
  email: string;
  fullName?: string;
  avatarUrl?: string;
  status: UserStatus;
  systemAdmin: boolean;
  emailVerified: boolean;
  locale?: string;
  createdAt: string;
  lastLoginAt?: string;
}

// 当前用户响应
export interface CurrentUserResponse {
  user: User;
  workspaces: WorkspaceInfo[];
  defaultWorkspaceId: string;
}

// 工作空间简要信息
export interface WorkspaceInfo {
  id: string;
  name: string;
  displayName: string;
  role: string;
}

// 更新用户资料请求
export interface UpdateProfileRequest {
  email?: string;
  fullName?: string;
  avatarUrl?: string;
  locale?: string;
}

// 修改密码请求
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// 请求密码重置
export interface RequestPasswordResetRequest {
  email: string;
}

// 重置密码请求
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

// API Key 状态
export type ApiKeyStatus = "active" | "revoked" | "expired";

// API Key
export interface ApiKey {
  id: string;
  name: string;
  description?: string;
  keyPrefix: string;
  status: ApiKeyStatus;
  scopes?: string[];
  expiresAt?: string;
  lastUsedAt?: string;
  createdAt: string;
}

// API Key 创建响应（包含明文key，仅返回一次）
export interface CreateApiKeyResponse {
  apiKey: ApiKey;
  plainApiKey: string;
}

// 创建 API Key 请求
export interface CreateApiKeyRequest {
  name: string;
  description?: string;
  scopes?: string[];
  expiryDays?: number;
}

// 分页响应
export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  total: number;
}

// 角色
export interface Role {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  scope: "system" | "workspace" | "project";
  permissions: string[];
  builtin: boolean;
}

// 工作空间成员
export interface WorkspaceMember {
  id: string;
  userId: string;
  username: string;
  email: string;
  fullName?: string;
  roleId: string;
  roleName: string;
  status: "active" | "suspended" | "invited";
  joinedAt: string;
}

// 添加成员请求
export interface AddMemberRequest {
  userId?: string;
  email?: string;
  roleId: string;
}

// 更新成员角色请求
export interface UpdateMemberRoleRequest {
  roleId: string;
}

// 审计日志
export interface AuditLog {
  id: string;
  timestamp: string;
  workspaceId: string;
  userId: string;
  username: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  resourceName?: string;
  ipAddress?: string;
  userAgent?: string;
  result: "success" | "failure" | "error";
  errorMessage?: string;
  metadata?: Record<string, unknown>;
}

// 审计日志查询参数
export interface AuditLogQueryParams {
  workspaceId?: string;
  userId?: string;
  action?: string;
  resourceType?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

// 审计日志导出请求
export interface ExportAuditLogsRequest {
  format: "csv" | "json";
  filters?: Omit<AuditLogQueryParams, "page" | "size">;
}

