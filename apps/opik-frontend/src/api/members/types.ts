/**
 * Workspace Members API Types
 * 工作空间成员管理相关的类型定义
 */

// 成员状态
export type MemberStatus = "active" | "suspended" | "invited";

// 工作空间成员
export interface WorkspaceMember {
  id: string;
  userId: string;
  username: string;
  email: string;
  fullName?: string;
  avatarUrl?: string;
  roleId: string;
  roleName: string;
  roleDisplayName: string;
  status: MemberStatus;
  joinedAt: string;
}

// 成员列表响应
export interface MemberListResponse {
  content: WorkspaceMember[];
  page: number;
  size: number;
  total: number;
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

// 角色列表响应
export interface RoleListResponse {
  content: Role[];
}

