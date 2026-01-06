import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { AuditLog, AuditLogListResponse, AuditLogQueryParams } from "./types";

const AUDIT_LOGS_REST_ENDPOINT = "/v1/private/admin/audit-logs";

export const AUDIT_LOGS_KEY = "audit-logs";

// 后端返回的审计日志格式（snake_case）
interface BackendAuditLog {
  id: string;
  workspace_id: string;
  user_id: string;
  username: string;
  action: string;
  resource_type: string;
  resource_id?: string;
  resource_name?: string;
  operation?: string;
  status?: string;
  ip_address?: string;
  user_agent?: string;
  request_path?: string;
  request_method?: string;
  changes?: string;
  error_message?: string;
  duration_ms?: number;
  timestamp: string;
  created_at: string;
  created_by?: string;
}

// 后端分页响应格式
interface BackendAuditLogPage {
  content: BackendAuditLog[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  first: boolean;
  last: boolean;
}

// 转换后端审计日志为前端格式
const transformAuditLog = (log: BackendAuditLog): AuditLog => ({
  id: log.id,
  timestamp: log.timestamp,
  workspaceId: log.workspace_id,
  userId: log.user_id,
  username: log.username,
  action: log.action,
  resourceType: log.resource_type,
  resourceId: log.resource_id,
  resourceName: log.resource_name,
  ipAddress: log.ip_address,
  userAgent: log.user_agent,
  result:
    (log.status?.toLowerCase() as "success" | "failure" | "error") || "success",
  errorMessage: log.error_message,
});

const getAuditLogsList = async (
  params: AuditLogQueryParams,
): Promise<AuditLogListResponse> => {
  // 转换 camelCase 参数为 snake_case
  const backendParams: Record<string, string | number | undefined> = {
    workspace_id: params.workspaceId,
    user_id: params.userId,
    resource_type: params.resourceType,
    status: params.result?.toUpperCase(), // 后端使用 status 而非 result
    start_time: params.startTime,
    end_time: params.endTime,
    page: params.page,
    size: params.size,
  };

  // 移除 undefined 值
  Object.keys(backendParams).forEach((key) => {
    if (backendParams[key] === undefined) {
      delete backendParams[key];
    }
  });

  const { data } = await axiosInstance.get<BackendAuditLogPage>(
    AUDIT_LOGS_REST_ENDPOINT,
    { params: backendParams },
  );

  return {
    content: data.content.map(transformAuditLog),
    page: data.page,
    size: data.size,
    total: data.total_elements,
  };
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
