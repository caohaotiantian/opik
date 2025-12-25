import { useMutation, UseMutationOptions, useQueryClient } from "@tanstack/react-query";
import axiosInstance, { WORKSPACES_MANAGEMENT_REST_ENDPOINT } from "@/api/api";
import { UpdateMemberRoleRequest } from "./types";
import { MEMBERS_KEY } from "./useMembersList";

interface UpdateMemberRoleParams {
  workspaceId: string;
  memberId: string;
  request: UpdateMemberRoleRequest;
}

const updateMemberRole = async ({
  workspaceId,
  memberId,
  request,
}: UpdateMemberRoleParams): Promise<void> => {
  // 后端使用 userId 而非 memberId，且期望 role_id (snake_case)
  await axiosInstance.put(
    `${WORKSPACES_MANAGEMENT_REST_ENDPOINT}${workspaceId}/members/${memberId}`,
    {
      role_id: request.roleId,
    },
  );
};

export default function useUpdateMemberRole(
  options?: UseMutationOptions<void, Error, UpdateMemberRoleParams>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: updateMemberRole,
    onSuccess: (_data, variables, context) => {
      queryClient.invalidateQueries({
        queryKey: [MEMBERS_KEY, variables.workspaceId],
      });
      options?.onSuccess?.(undefined, variables, context);
    },
    ...options,
  });
}

