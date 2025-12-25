import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { ChangePasswordRequest } from "./types";

const SESSION_REST_ENDPOINT = "/v1/session";

const changePassword = async (request: ChangePasswordRequest): Promise<void> => {
  // 后端使用 snake_case 命名，需要转换
  const backendRequest = {
    current_password: request.currentPassword,
    new_password: request.newPassword,
  };
  await axiosInstance.put(`${SESSION_REST_ENDPOINT}/password`, backendRequest);
};

export default function useChangePassword(
  options?: UseMutationOptions<void, Error, ChangePasswordRequest>,
) {
  return useMutation({
    mutationFn: changePassword,
    ...options,
  });
}

