import {
  useMutation,
  UseMutationOptions,
  useQueryClient,
} from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { AdminUser, UpdateUserStatusRequest } from "./types";
import { ADMIN_USERS_KEY } from "./useAdminUsersList";

const ADMIN_USERS_REST_ENDPOINT = "/v1/private/admin/users";

interface UpdateUserStatusParams {
  userId: string;
  request: UpdateUserStatusRequest;
}

const updateUserStatus = async ({
  userId,
  request,
}: UpdateUserStatusParams): Promise<AdminUser> => {
  const { data } = await axiosInstance.put<AdminUser>(
    `${ADMIN_USERS_REST_ENDPOINT}/${userId}/status`,
    request,
  );
  return data;
};

export default function useUpdateUserStatus(
  options?: UseMutationOptions<AdminUser, Error, UpdateUserStatusParams>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: updateUserStatus,
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: [ADMIN_USERS_KEY] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}
