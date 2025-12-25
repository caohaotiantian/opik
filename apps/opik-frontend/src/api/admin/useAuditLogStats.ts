import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { AuditLogStats, AuditLogQueryParams } from "./types";

const AUDIT_LOGS_REST_ENDPOINT = "/v1/private/admin/audit-logs";

export const AUDIT_LOG_STATS_KEY = "audit-log-stats";

const getAuditLogStats = async (
  params: Omit<AuditLogQueryParams, "page" | "size">,
): Promise<AuditLogStats> => {
  const { data } = await axiosInstance.get<AuditLogStats>(
    `${AUDIT_LOGS_REST_ENDPOINT}/stats`,
    { params },
  );
  return data;
};

export default function useAuditLogStats(
  params: Omit<AuditLogQueryParams, "page" | "size"> = {},
  options?: Omit<UseQueryOptions<AuditLogStats, Error>, "queryKey" | "queryFn">,
) {
  return useQuery({
    queryKey: [AUDIT_LOG_STATS_KEY, params],
    queryFn: () => getAuditLogStats(params),
    ...options,
  });
}

