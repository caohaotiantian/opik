import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance, { WORKSPACES_MANAGEMENT_REST_ENDPOINT } from "@/api/api";
import { MemberListResponse, WorkspaceMember } from "./types";

export const MEMBERS_KEY = "workspace-members";

interface UseMembersListParams {
  workspaceId: string;
  page?: number;
  size?: number;
}

// 后端返回的成员数据格式（snake_case）- WorkspaceMemberResponse
interface BackendWorkspaceMember {
  id: string;
  user_id: string;
  username: string;
  email: string;
  full_name?: string;
  avatar_url?: string;
  role_id: string;
  role_name: string;
  role_display_name: string;
  status: string;
  joined_at: string;
}

const transformMember = (member: BackendWorkspaceMember): WorkspaceMember => ({
  id: member.id,
  userId: member.user_id,
  username: member.username || "Unknown",
  email: member.email || "",
  fullName: member.full_name,
  avatarUrl: member.avatar_url,
  roleId: member.role_id,
  roleName: member.role_name || "",
  roleDisplayName: member.role_display_name || member.role_name || "",
  status: (member.status?.toLowerCase() as "active" | "suspended" | "invited") || "active",
  joinedAt: member.joined_at || new Date().toISOString(),
});

const getMembersList = async (
  params: UseMembersListParams,
): Promise<MemberListResponse> => {
  const { workspaceId, ...queryParams } = params;
  // 后端返回的是数组而非分页对象
  const { data } = await axiosInstance.get<BackendWorkspaceMember[]>(
    `${WORKSPACES_MANAGEMENT_REST_ENDPOINT}${workspaceId}/members`,
    { params: queryParams },
  );
  // 转换数据格式并转换为分页格式以兼容前端
  return {
    content: data.map(transformMember),
    page: 1,
    size: data.length,
    total: data.length,
  };
};

export default function useMembersList(
  params: UseMembersListParams,
  options?: Omit<
    UseQueryOptions<MemberListResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [MEMBERS_KEY, params.workspaceId, params],
    queryFn: () => getMembersList(params),
    enabled: !!params.workspaceId,
    ...options,
  });
}

