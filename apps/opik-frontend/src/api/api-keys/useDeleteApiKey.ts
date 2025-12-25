import { useMutation, UseMutationOptions, useQueryClient } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { API_KEYS_KEY } from "./useApiKeysList";

const API_KEYS_REST_ENDPOINT = "/v1/api-keys";

const deleteApiKey = async (id: string): Promise<void> => {
  await axiosInstance.delete(`${API_KEYS_REST_ENDPOINT}/${id}`);
};

export default function useDeleteApiKey(
  options?: UseMutationOptions<void, Error, string>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteApiKey,
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: [API_KEYS_KEY] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

