import { useQuery, UseQueryOptions, keepPreviousData } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import {
  ApiKeyPageResponse,
  ApiKeysListParams,
  BackendApiKeyPage,
  transformApiKeyPage,
} from "./types";

const API_KEYS_REST_ENDPOINT = "/v1/api-keys";

export const API_KEYS_KEY = "api-keys";

const getApiKeysList = async (
  params: ApiKeysListParams,
): Promise<ApiKeyPageResponse> => {
  // Convert camelCase params to snake_case for backend
  const backendParams: Record<string, string | number | undefined> = {
    page: params.page,
    size: params.size,
    search: params.search || undefined,
    status: params.status || undefined,
    sort_by: params.sortBy,
    sort_dir: params.sortDir,
  };

  // Remove undefined values
  Object.keys(backendParams).forEach(key => {
    if (backendParams[key] === undefined) {
      delete backendParams[key];
    }
  });

  const { data } = await axiosInstance.get<BackendApiKeyPage>(
    API_KEYS_REST_ENDPOINT,
    { params: backendParams },
  );

  return transformApiKeyPage(data);
};

export default function useApiKeysList(
  params: ApiKeysListParams = {},
  options?: Omit<
    UseQueryOptions<ApiKeyPageResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [API_KEYS_KEY, params],
    queryFn: () => getApiKeysList(params),
    placeholderData: keepPreviousData,
    ...options,
  });
}

