import { QueryFunctionContext, useQuery } from "@tanstack/react-query";
import { AxiosError } from "axios";
import api, {
  WORKSPACE_CONFIG_REST_ENDPOINT,
  WORKSPACE_CONFIG_KEY,
  QueryConfig,
} from "@/api/api";
import { WorkspaceConfig } from "@/types/workspaces";

type UseWorkspaceConfigResponse = WorkspaceConfig;
type UseWorkspaceConfigParams = {
  workspaceName: string;
};

const getWorkspaceConfig = async ({ signal }: QueryFunctionContext) => {
  try {
    const { data } = await api.get(WORKSPACE_CONFIG_REST_ENDPOINT, {
      signal,
    });

    return data;
  } catch (error) {
    // 404 is expected when configuration doesn't exist yet
    if (error instanceof AxiosError && error.response?.status === 404) {
      return null;
    }
    throw error;
  }
};

export default function useWorkspaceConfig(
  params: UseWorkspaceConfigParams,
  options?: QueryConfig<UseWorkspaceConfigResponse>,
) {
  return useQuery({
    queryKey: [WORKSPACE_CONFIG_KEY, params],
    queryFn: (context) => getWorkspaceConfig(context),
    retry: false, // Don't retry on 404
    ...options,
  });
}
