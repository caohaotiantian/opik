import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { ExportAuditLogsRequest } from "./types";

const AUDIT_LOGS_REST_ENDPOINT = "/v1/private/admin/audit-logs";

const exportAuditLogs = async (
  request: ExportAuditLogsRequest,
): Promise<Blob> => {
  // 后端使用 GET 方法，参数通过 query string 传递
  const params: Record<string, string | undefined> = {
    format: request.format,
    workspace_id: request.filters?.workspaceId,
    user_id: request.filters?.userId,
    resource_type: request.filters?.resourceType,
    status: request.filters?.result?.toUpperCase(), // 后端使用 status
    start_time: request.filters?.startTime,
    end_time: request.filters?.endTime,
  };

  // 移除 undefined 值
  Object.keys(params).forEach((key) => {
    if (params[key] === undefined) {
      delete params[key];
    }
  });

  const { data } = await axiosInstance.get<Blob>(
    `${AUDIT_LOGS_REST_ENDPOINT}/export`,
    {
      params,
      responseType: "blob",
    },
  );
  return data;
};

export default function useExportAuditLogs(
  options?: UseMutationOptions<Blob, Error, ExportAuditLogsRequest>,
) {
  return useMutation({
    mutationFn: exportAuditLogs,
    ...options,
  });
}
