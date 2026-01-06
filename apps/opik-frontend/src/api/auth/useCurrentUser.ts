import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { CurrentUserResponse, User, WorkspaceInfo } from "./types";

const SESSION_REST_ENDPOINT = "/v1/session";

export const CURRENT_USER_KEY = "current-user";

// 后端响应格式（snake_case）
interface BackendUserResponse {
  user_id?: string;
  id?: string;
  username: string;
  email: string;
  full_name?: string;
  avatar_url?: string;
  status: string;
  system_admin?: boolean;
  email_verified?: boolean;
  locale?: string;
  created_at?: string;
  last_login_at?: string;
}

interface BackendWorkspaceInfo {
  id: string;
  name: string;
  display_name?: string;
  displayName?: string;
  role: string;
}

interface BackendCurrentUserResponse {
  user?: BackendUserResponse;
  // 简单响应格式（直接返回用户信息）
  user_id?: string;
  username?: string;
  email?: string;
  full_name?: string;
  default_workspace_name?: string;
  system_admin?: boolean;
  // 完整响应格式（也用于简单格式）
  workspaces?: BackendWorkspaceInfo[];
  default_workspace_id?: string;
}

const transformUser = (backendUser: BackendUserResponse): User => ({
  id: backendUser.user_id || backendUser.id || "",
  username: backendUser.username,
  email: backendUser.email,
  fullName: backendUser.full_name,
  avatarUrl: backendUser.avatar_url,
  status: (backendUser.status as "active" | "suspended" | "deleted") || "active",
  systemAdmin: backendUser.system_admin ?? false,
  emailVerified: backendUser.email_verified ?? false,
  locale: backendUser.locale,
  createdAt: backendUser.created_at || new Date().toISOString(),
  lastLoginAt: backendUser.last_login_at,
});

const transformWorkspace = (ws: BackendWorkspaceInfo): WorkspaceInfo => ({
  id: ws.id,
  name: ws.name,
  displayName: ws.display_name || ws.displayName || ws.name,
  role: ws.role,
});

const getCurrentUser = async (): Promise<CurrentUserResponse> => {
  const { data } = await axiosInstance.get<BackendCurrentUserResponse>(
    `${SESSION_REST_ENDPOINT}/current-user`,
  );
  
  // 处理两种可能的响应格式
  if (data.user) {
    // 完整格式：{ user: {...}, workspaces: [...], default_workspace_id: "..." }
    return {
      user: transformUser(data.user),
      workspaces: (data.workspaces || []).map(transformWorkspace),
      defaultWorkspaceId: data.default_workspace_id || "",
    };
  } else {
    // 简单格式：直接返回用户信息
    return {
      user: {
        id: data.user_id || "",
        username: data.username || "",
        email: data.email || "",
        fullName: data.full_name,
        status: "active",
        systemAdmin: data.system_admin ?? false,
        emailVerified: true,
        createdAt: new Date().toISOString(),
      },
      workspaces: data.default_workspace_id && data.default_workspace_name ? [{
        id: data.default_workspace_id,
        name: data.default_workspace_name,
        displayName: data.default_workspace_name,
        role: "owner",
      }] : [],
      defaultWorkspaceId: data.default_workspace_id || "",
    };
  }
};

export default function useCurrentUser(
  options?: Omit<
    UseQueryOptions<CurrentUserResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [CURRENT_USER_KEY],
    queryFn: getCurrentUser,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: false,
    ...options,
  });
}

