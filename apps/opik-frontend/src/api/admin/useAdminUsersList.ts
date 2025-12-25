import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { AdminUser, AdminUserListResponse, AdminUserQueryParams, UserStatus } from "./types";

const ADMIN_USERS_REST_ENDPOINT = "/v1/private/admin/users";

export const ADMIN_USERS_KEY = "admin-users";

// 后端返回的用户数据格式（snake_case）
interface BackendAdminUser {
  id: string;
  username: string;
  email: string;
  full_name?: string;
  avatar_url?: string;
  status: string;
  system_admin: boolean;
  email_verified: boolean;
  locale?: string;
  created_at: string;
  last_login_at?: string;
  workspace_count?: number;
}

interface BackendAdminUserListResponse {
  content: BackendAdminUser[];
  page: number;
  size: number;
  total: number;
}

const transformUser = (user: BackendAdminUser): AdminUser => ({
  id: user.id,
  username: user.username,
  email: user.email,
  fullName: user.full_name,
  avatarUrl: user.avatar_url,
  status: (user.status?.toLowerCase() as UserStatus) || "active",
  systemAdmin: user.system_admin ?? false,
  emailVerified: user.email_verified ?? false,
  locale: user.locale,
  createdAt: user.created_at,
  lastLoginAt: user.last_login_at,
  workspaceCount: user.workspace_count,
});

const getAdminUsersList = async (
  params: AdminUserQueryParams,
): Promise<AdminUserListResponse> => {
  const { data } = await axiosInstance.get<BackendAdminUserListResponse>(
    ADMIN_USERS_REST_ENDPOINT,
    { params },
  );
  return {
    content: data.content.map(transformUser),
    page: data.page,
    size: data.size,
    total: data.total,
  };
};

export default function useAdminUsersList(
  params: AdminUserQueryParams = {},
  options?: Omit<
    UseQueryOptions<AdminUserListResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [ADMIN_USERS_KEY, params],
    queryFn: () => getAdminUsersList(params),
    ...options,
  });
}

