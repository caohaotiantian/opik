import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import axiosInstance from "@/api/api";
import { ExportAuditLogsRequest } from "./types";

const AUDIT_LOGS_REST_ENDPOINT = "/v1/private/admin/audit-logs";

const exportAuditLogs = async (request: ExportAuditLogsRequest): Promise<Blob> => {
  const { data } = await axiosInstance.post<Blob>(
    `${AUDIT_LOGS_REST_ENDPOINT}/export`,
    request,
    {
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

