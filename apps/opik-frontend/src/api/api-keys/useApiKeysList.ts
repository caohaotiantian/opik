import {
  useQuery,
  UseQueryOptions,
  keepPreviousData,
} from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import {
  ApiKeyPageResponse,
  ApiKeysListParams,
  BackendApiKeyList,
  transformApiKeyList,
} from "./types";

const API_KEYS_REST_ENDPOINT = "/v1/private/api-keys";

export const API_KEYS_KEY = "api-keys";

const getApiKeysList = async (
  params: ApiKeysListParams,
): Promise<ApiKeyPageResponse> => {
  // 后端只支持 workspace_id 参数，不支持分页和排序
  const backendParams: Record<string, string | undefined> = {
    workspace_id: params.workspaceId,
  };

  // Remove undefined values
  Object.keys(backendParams).forEach((key) => {
    if (backendParams[key] === undefined) {
      delete backendParams[key];
    }
  });

  // 后端返回的是数组而非分页对象
  const { data } = await axiosInstance.get<BackendApiKeyList>(
    API_KEYS_REST_ENDPOINT,
    { params: backendParams },
  );

  // 前端进行客户端搜索和过滤
  let filteredData = data;

  // 搜索过滤
  if (params.search) {
    const searchLower = params.search.toLowerCase();
    filteredData = filteredData.filter(
      (key) =>
        key.name.toLowerCase().includes(searchLower) ||
        (key.description &&
          key.description.toLowerCase().includes(searchLower)),
    );
  }

  // 状态过滤
  if (params.status) {
    filteredData = filteredData.filter((key) => key.status === params.status);
  }

  // 客户端排序
  if (params.sortBy) {
    const sortDir = params.sortDir === "desc" ? -1 : 1;
    filteredData = [...filteredData].sort((a, b) => {
      const aVal = a[params.sortBy as keyof typeof a];
      const bVal = b[params.sortBy as keyof typeof b];
      if (!aVal && !bVal) return 0;
      if (!aVal) return sortDir;
      if (!bVal) return -sortDir;
      return String(aVal).localeCompare(String(bVal)) * sortDir;
    });
  }

  return transformApiKeyList(filteredData);
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
