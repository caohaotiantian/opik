import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { RegisterRequest, RegisterResponse } from "./types";

const AUTH_REST_ENDPOINT = "/v1/public/auth";

// 后端响应格式（snake_case）
interface BackendRegisterResponse {
  user_id: string;
  username: string;
  email: string;
  full_name?: string;
  default_workspace_id: string;
  default_workspace_name: string;
  system_admin: boolean;
}

const register = async (request: RegisterRequest): Promise<RegisterResponse> => {
  // 后端使用 snake_case 命名，需要转换
  const backendRequest = {
    username: request.username,
    email: request.email,
    password: request.password,
    full_name: request.fullName,
  };
  const { data } = await axiosInstance.post<BackendRegisterResponse>(
    `${AUTH_REST_ENDPOINT}/register`,
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

export default function useRegister(
  options?: UseMutationOptions<RegisterResponse, Error, RegisterRequest>,
) {
  return useMutation({
    mutationFn: register,
    ...options,
  });
}

