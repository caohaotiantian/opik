import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { UserListResponse, UserQueryParams } from "./types";

// 用户列表 API（不需要管理员权限）
const USERS_REST_ENDPOINT = "/v1/private/users/available";

export const USERS_KEY = "users";

const getUsersList = async (
  params: UserQueryParams,
): Promise<UserListResponse> => {
  const { data } = await axiosInstance.get<UserListResponse>(
    USERS_REST_ENDPOINT,
    { params },
  );
  return data;
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

