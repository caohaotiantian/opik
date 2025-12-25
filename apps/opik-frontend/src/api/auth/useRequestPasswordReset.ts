import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { RequestPasswordResetRequest } from "./types";

const AUTH_REST_ENDPOINT = "/v1/public/auth";

const requestPasswordReset = async (
  request: RequestPasswordResetRequest,
): Promise<void> => {
  await axiosInstance.post(`${AUTH_REST_ENDPOINT}/forgot-password`, request);
};

export default function useRequestPasswordReset(
  options?: UseMutationOptions<void, Error, RequestPasswordResetRequest>,
) {
  return useMutation({
    mutationFn: requestPasswordReset,
    ...options,
  });
}

