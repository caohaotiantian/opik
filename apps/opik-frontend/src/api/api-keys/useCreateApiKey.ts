import {
  useMutation,
  UseMutationOptions,
  useQueryClient,
} from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import {
  CreateApiKeyRequest,
  CreateApiKeyResponse,
  BackendCreateApiKeyResponse,
  transformApiKey,
} from "./types";
import { API_KEYS_KEY } from "./useApiKeysList";

const API_KEYS_REST_ENDPOINT = "/v1/private/api-keys";

const createApiKey = async (
  request: CreateApiKeyRequest,
): Promise<CreateApiKeyResponse> => {
  // 后端使用 snake_case 命名，需要转换
  const backendRequest = {
    name: request.name,
    description: request.description,
    workspace_id: request.workspaceId,
    expires_at: request.expiresAt,
  };
  // 后端直接返回 ApiKeyResponse（api_key 字段包含明文密钥）
  const { data } = await axiosInstance.post<BackendCreateApiKeyResponse>(
    API_KEYS_REST_ENDPOINT,
    backendRequest,
  );
  // 转换后端响应格式为前端格式
  return transformApiKey(data);
};

export default function useCreateApiKey(
  options?: UseMutationOptions<
    CreateApiKeyResponse,
    Error,
    CreateApiKeyRequest
  >,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createApiKey,
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: [API_KEYS_KEY] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}
