import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { Role, RoleListResponse } from "./types";

const ROLES_REST_ENDPOINT = "/v1/private/roles";

export const ROLES_KEY = "roles";

interface UseRolesListParams {
  scope?: "system" | "workspace" | "project";
}

// 后端返回的角色格式
interface BackendRole {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  scope: string;
  isSystem: boolean;
  permissions: string[];
}

interface BackendRoleListResponse {
  content: BackendRole[];
  total: number;
}

const getRolesList = async (
  params: UseRolesListParams,
): Promise<RoleListResponse> => {
  const { data } = await axiosInstance.get<BackendRoleListResponse>(
    ROLES_REST_ENDPOINT,
    { params },
  );
  
  // 转换后端角色格式为前端格式
  const roles: Role[] = data.content.map((role) => ({
    id: role.id,
    name: role.name,
    displayName: role.displayName,
    description: role.description,
    scope: role.scope as "system" | "workspace" | "project",
    permissions: role.permissions,
    builtin: role.isSystem,
  }));
  
  return { content: roles };
};

export default function useRolesList(
  params: UseRolesListParams = {},
  options?: Omit<
    UseQueryOptions<RoleListResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [ROLES_KEY, params],
    queryFn: () => getRolesList(params),
    staleTime: 10 * 60 * 1000, // 10 minutes
    ...options,
  });
}

