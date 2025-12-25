import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";

const AUTH_REST_ENDPOINT = "/v1/public/auth";

const logout = async (): Promise<void> => {
  await axiosInstance.post(`${AUTH_REST_ENDPOINT}/logout`);
};

export default function useLogout(options?: UseMutationOptions<void, Error>) {
  return useMutation({
    mutationFn: logout,
    ...options,
  });
}

