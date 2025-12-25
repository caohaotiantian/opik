import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { AuditLogListResponse, AuditLogQueryParams } from "./types";

const AUDIT_LOGS_REST_ENDPOINT = "/v1/private/admin/audit-logs";

export const AUDIT_LOGS_KEY = "audit-logs";

const getAuditLogsList = async (
  params: AuditLogQueryParams,
): Promise<AuditLogListResponse> => {
  const { data } = await axiosInstance.get<AuditLogListResponse>(
    AUDIT_LOGS_REST_ENDPOINT,
    { params },
  );
  return data;
};

export default function useAuditLogsList(
  params: AuditLogQueryParams = {},
  options?: Omit<
    UseQueryOptions<AuditLogListResponse, Error>,
    "queryKey" | "queryFn"
  >,
) {
  return useQuery({
    queryKey: [AUDIT_LOGS_KEY, params],
    queryFn: () => getAuditLogsList(params),
    ...options,
  });
}

