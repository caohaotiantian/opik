/**
 * Admin API Types
 * 管理员相关的类型定义
 */

// 用户状态
export type UserStatus = "active" | "suspended" | "deleted";

// 用户信息（管理员视图）
export interface AdminUser {
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
  workspaceCount?: number;
}

// 用户列表响应
export interface AdminUserListResponse {
  content: AdminUser[];
  page: number;
  size: number;
  total: number;
}

// 用户查询参数
export interface AdminUserQueryParams {
  search?: string;
  status?: UserStatus;
  systemAdmin?: boolean;
  page?: number;
  size?: number;
}

// 创建用户请求
export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  fullName?: string;
  systemAdmin?: boolean;
}

// 更新用户状态请求
export interface UpdateUserStatusRequest {
  status: UserStatus;
}

// 审计日志
export interface AuditLog {
  id: string;
  timestamp: string;
  workspaceId: string;
  workspaceName?: string;
  userId: string;
  username: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  resourceName?: string;
  ipAddress?: string;
  userAgent?: string;
  requestId?: string;
  result: "success" | "failure" | "error";
  errorMessage?: string;
  metadata?: Record<string, unknown>;
}

// 审计日志列表响应
export interface AuditLogListResponse {
  content: AuditLog[];
  page: number;
  size: number;
  total: number;
}

// 审计日志查询参数
export interface AuditLogQueryParams {
  workspaceId?: string;
  userId?: string;
  username?: string;
  action?: string;
  resourceType?: string;
  result?: "success" | "failure" | "error";
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

// 审计日志统计
export interface AuditLogStats {
  totalLogs: number;
  successCount: number;
  failureCount: number;
  errorCount: number;
  topActions: { action: string; count: number }[];
  topUsers: { userId: string; username: string; count: number }[];
  topResourceTypes: { resourceType: string; count: number }[];
}

// 导出审计日志请求
export interface ExportAuditLogsRequest {
  format: "csv" | "json";
  filters?: Omit<AuditLogQueryParams, "page" | "size">;
}
