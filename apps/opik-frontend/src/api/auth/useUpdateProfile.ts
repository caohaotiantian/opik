import { useMutation, UseMutationOptions, useQueryClient } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { UpdateProfileRequest, User } from "./types";
import { CURRENT_USER_KEY } from "./useCurrentUser";

const SESSION_REST_ENDPOINT = "/v1/session";

const updateProfile = async (request: UpdateProfileRequest): Promise<User> => {
  // 转换为后端使用的 snake_case 格式
  const backendRequest = {
    email: request.email,
    full_name: request.fullName,
    avatar_url: request.avatarUrl,
    locale: request.locale,
  };
  
  const { data } = await axiosInstance.put<User>(
    `${SESSION_REST_ENDPOINT}/profile`,
    backendRequest,
  );
  return data;
};

export default function useUpdateProfile(
  options?: UseMutationOptions<User, Error, UpdateProfileRequest>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: updateProfile,
    onSuccess: (data, variables, context) => {
      // Invalidate current user query to refresh data
      queryClient.invalidateQueries({ queryKey: [CURRENT_USER_KEY] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

