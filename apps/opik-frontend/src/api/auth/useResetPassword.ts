import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { ResetPasswordRequest } from "./types";

const AUTH_REST_ENDPOINT = "/v1/public/auth";

const resetPassword = async (request: ResetPasswordRequest): Promise<void> => {
  // 后端使用 snake_case 命名，需要转换
  const backendRequest = {
    token: request.token,
    new_password: request.newPassword,
  };
  await axiosInstance.post(`${AUTH_REST_ENDPOINT}/reset-password`, backendRequest);
};

export default function useResetPassword(
  options?: UseMutationOptions<void, Error, ResetPasswordRequest>,
) {
  return useMutation({
    mutationFn: resetPassword,
    ...options,
  });
}

