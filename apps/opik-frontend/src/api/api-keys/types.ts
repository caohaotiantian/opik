/**
 * API Keys Types
 * API Key 管理相关的类型定义
 */

// API Key 状态
export type ApiKeyStatus = "ACTIVE" | "REVOKED";

// 后端返回的 API Key 响应格式（snake_case）- 对应 ApiKeyResponse.java
export interface BackendApiKeyResponse {
  id: string;
  name: string;
  api_key: string | null; // 明文 key，仅在创建时返回，列表查询时为 null
  workspace_id: string;
  description?: string;
  key_prefix?: string;
  status: ApiKeyStatus;
  expires_at?: string;
  created_at: string;
  last_used_at?: string;
}

// 保持向后兼容的别名
export type BackendApiKey = BackendApiKeyResponse;

// 后端列表响应格式 - 直接返回数组
export type BackendApiKeyList = BackendApiKeyResponse[];

// 后端分页响应格式（用于兼容，实际后端返回数组）
export interface BackendApiKeyPage {
  content: BackendApiKeyResponse[];
  page: number;
  size: number;
  total: number;
}

// 后端创建 API Key 的响应格式 - 直接是 ApiKeyResponse
export type BackendCreateApiKeyResponse = BackendApiKeyResponse;

// 前端使用的 API Key 格式（camelCase）
export interface ApiKeyResponse {
  id: string;
  workspaceId: string;
  name: string;
  description?: string;
  keyPrefix?: string;
  apiKey?: string; // 明文 key，仅在创建时返回
  status: ApiKeyStatus;
  expiresAt?: string;
  lastUsedAt?: string;
  createdAt: string;
}

// 前端分页响应格式
export interface ApiKeyPageResponse {
  content: ApiKeyResponse[];
  page: number;
  size: number;
  total: number;
}

// API Key 列表响应（兼容旧版，直接是数组）
export type ApiKeyListResponse = ApiKeyResponse[];

// 创建 API Key 请求
export interface CreateApiKeyRequest {
  name: string;
  description?: string;
  workspaceId?: string;
  expiresAt?: string;
}

// 创建 API Key 响应
export type CreateApiKeyResponse = ApiKeyResponse;

// 更新 API Key 请求
export interface UpdateApiKeyRequest {
  name?: string;
  description?: string;
}

// 排序方向
export type SortDirection = "asc" | "desc";

// 排序字段
export type ApiKeySortField = "name" | "created_at" | "last_used_at" | "status";

// 列表查询参数
export interface ApiKeysListParams {
  page?: number;
  size?: number;
  search?: string;
  status?: ApiKeyStatus | "";
  sortBy?: ApiKeySortField;
  sortDir?: SortDirection;
  workspaceId?: string;
}

// 转换后端 API Key 格式为前端格式
export const transformApiKey = (
  backendKey: BackendApiKeyResponse,
): ApiKeyResponse => ({
  id: backendKey.id,
  workspaceId: backendKey.workspace_id,
  name: backendKey.name,
  description: backendKey.description,
  keyPrefix: backendKey.key_prefix,
  apiKey: backendKey.api_key || undefined, // api_key 可能为 null
  status: backendKey.status,
  expiresAt: backendKey.expires_at,
  lastUsedAt: backendKey.last_used_at,
  createdAt: backendKey.created_at,
});

// 转换后端数组响应为前端分页格式
export const transformApiKeyList = (
  backendList: BackendApiKeyList,
): ApiKeyPageResponse => ({
  content: backendList.map((key) => transformApiKey(key)),
  page: 1,
  size: backendList.length,
  total: backendList.length,
});

// 转换后端分页响应为前端格式（保持向后兼容）
export const transformApiKeyPage = (
  backendPage: BackendApiKeyPage,
): ApiKeyPageResponse => ({
  content: backendPage.content.map((key) => transformApiKey(key)),
  page: backendPage.page,
  size: backendPage.size,
  total: backendPage.total,
});
