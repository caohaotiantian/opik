import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { LoginRequest, LoginResponse } from "./types";

const AUTH_REST_ENDPOINT = "/v1/public/auth";

// 后端响应格式（snake_case）
interface BackendLoginResponse {
  user_id: string;
  username: string;
  email: string;
  full_name?: string;
  default_workspace_id: string;
  default_workspace_name: string;
  system_admin: boolean;
}

const login = async (request: LoginRequest): Promise<LoginResponse> => {
  // 后端使用 snake_case 命名，需要转换
  const backendRequest = {
    username_or_email: request.usernameOrEmail,
    password: request.password,
  };
  const { data } = await axiosInstance.post<BackendLoginResponse>(
    `${AUTH_REST_ENDPOINT}/login`,
    backendRequest,
  );
  
  // 将 snake_case 转换为 camelCase
  return {
    userId: data.user_id,
    username: data.username,
    email: data.email,
    fullName: data.full_name,
    defaultWorkspaceId: data.default_workspace_id,
    defaultWorkspaceName: data.default_workspace_name,
    systemAdmin: data.system_admin,
  };
};

export default function useLogin(
  options?: UseMutationOptions<LoginResponse, Error, LoginRequest>,
) {
  return useMutation({
    mutationFn: login,
    ...options,
  });
}

