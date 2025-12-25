import { useMutation, UseMutationOptions, useQueryClient } from "@tanstack/react-query";
import axiosInstance, { WORKSPACES_MANAGEMENT_REST_ENDPOINT } from "@/api/api";
import { AddMemberRequest, WorkspaceMember } from "./types";
import { MEMBERS_KEY } from "./useMembersList";

interface AddMemberParams {
  workspaceId: string;
  request: AddMemberRequest;
}

const addMember = async ({
  workspaceId,
  request,
}: AddMemberParams): Promise<void> => {
  // 后端期望 user_id 和 role_id (snake_case)
  await axiosInstance.post(
    `${WORKSPACES_MANAGEMENT_REST_ENDPOINT}${workspaceId}/members`,
    {
      user_id: request.userId,
      role_id: request.roleId,
    },
  );
};

export default function useAddMember(
  options?: UseMutationOptions<void, Error, AddMemberParams>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: addMember,
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({
        queryKey: [MEMBERS_KEY, variables.workspaceId],
      });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

