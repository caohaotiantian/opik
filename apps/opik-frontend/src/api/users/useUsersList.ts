import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { User, UserListResponse, UserQueryParams } from "./types";

// 用户列表 API（不需要管理员权限）
const USERS_REST_ENDPOINT = "/v1/private/users/available";

export const USERS_KEY = "users";

// 后端返回的用户数据格式（snake_case）
interface BackendUser {
  id: string;
  username: string;
  email: string;
  full_name?: string;
  avatar_url?: string;
  status: string;
  system_admin: boolean;
  email_verified: boolean;
  locale?: string;
  last_login_at?: string;
  created_at: string;
}

interface BackendUserListResponse {
  content: BackendUser[];
  total: number;
  page: number;
  size: number;
}

const transformUser = (user: BackendUser): User => ({
  id: user.id,
  username: user.username,
  email: user.email,
  fullName: user.full_name,
  avatarUrl: user.avatar_url,
  status:
    (user.status?.toLowerCase() as "active" | "suspended" | "deleted") ||
    "active",
  systemAdmin: user.system_admin ?? false,
  emailVerified: user.email_verified ?? false,
  locale: user.locale,
  lastLoginAt: user.last_login_at,
  createdAt: user.created_at,
});

const getUsersList = async (
  params: UserQueryParams,
): Promise<UserListResponse> => {
  const { data } = await axiosInstance.get<BackendUserListResponse>(
    USERS_REST_ENDPOINT,
    { params },
  );
  return {
    content: data.content.map(transformUser),
    total: data.total,
    page: data.page,
    size: data.size,
  };
};

export default function useUsersList(
  params: UserQueryParams = {},
  options?: Omit<
    UseQueryOptions<UserListResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [USERS_KEY, params],
    queryFn: () => getUsersList(params),
    ...options,
  });
}
