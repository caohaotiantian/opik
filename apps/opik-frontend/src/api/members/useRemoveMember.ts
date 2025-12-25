import { useMutation, UseMutationOptions, useQueryClient } from "@tanstack/react-query";
import axiosInstance, { WORKSPACES_MANAGEMENT_REST_ENDPOINT } from "@/api/api";
import { MEMBERS_KEY } from "./useMembersList";

interface RemoveMemberParams {
  workspaceId: string;
  memberId: string;
}

const removeMember = async ({
  workspaceId,
  memberId,
}: RemoveMemberParams): Promise<void> => {
  // memberId 实际上是 userId
  await axiosInstance.delete(
    `${WORKSPACES_MANAGEMENT_REST_ENDPOINT}${workspaceId}/members/${memberId}`,
  );
};

export default function useRemoveMember(
  options?: UseMutationOptions<void, Error, RemoveMemberParams>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: removeMember,
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({
        queryKey: [MEMBERS_KEY, variables.workspaceId],
      });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

