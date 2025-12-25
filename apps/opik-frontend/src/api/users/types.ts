/**
 * Users API Types
 * 用户相关类型定义
 */

export type UserStatus = "active" | "suspended" | "deleted";

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
  lastLoginAt?: string;
  createdAt: string;
}

export interface UserListResponse {
  content: User[];
  total: number;
  page: number;
  size: number;
}

export interface UserQueryParams {
  search?: string;
  status?: UserStatus;
  page?: number;
  size?: number;
}

