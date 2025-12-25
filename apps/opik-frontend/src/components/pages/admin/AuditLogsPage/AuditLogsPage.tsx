import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Search,
  FileText,
  Download,
  CheckCircle,
  XCircle,
  AlertCircle,
  Loader2,
} from "lucide-react";
import { format } from "date-fns";

import { useAuditLogsList, useExportAuditLogs } from "@/api/admin";
import type { AuditLog, AuditLogQueryParams } from "@/api/admin";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tag } from "@/components/ui/tag";
import { useToast } from "@/components/ui/use-toast";
import Loader from "@/components/shared/Loader/Loader";

const AuditLogsPage = () => {
  const { t } = useTranslation();
  const { toast } = useToast();

  const [filters, setFilters] = useState<AuditLogQueryParams>({
    page: 0,
    size: 20,
  });
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const { data, isLoading } = useAuditLogsList(filters);
  const exportMutation = useExportAuditLogs();

  const handleExport = async (exportFormat: "csv" | "json") => {
    try {
      const blob = await exportMutation.mutateAsync({
        format: exportFormat,
        filters: {
          workspaceId: filters.workspaceId,
          userId: filters.userId,
          action: filters.action,
          resourceType: filters.resourceType,
          result: filters.result,
          startTime: filters.startTime,
          endTime: filters.endTime,
        },
      });

      // 下载文件
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `audit-logs-${format(new Date(), "yyyyMMdd-HHmmss")}.${exportFormat}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);

      toast({
        title: t("admin.auditLogs.exported", "导出成功"),
      });
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("admin.auditLogs.exportFailed", "导出失败"),
      });
    }
  };

  const getResultBadge = (result: "success" | "failure" | "error") => {
    const config: Record<"success" | "failure" | "error", { variant: "green" | "red" | "yellow"; icon: React.ReactNode }> = {
      success: {
        variant: "green",
        icon: <CheckCircle className="h-3 w-3" />,
      },
      failure: {
        variant: "red",
        icon: <XCircle className="h-3 w-3" />,
      },
      error: {
        variant: "yellow",
        icon: <AlertCircle className="h-3 w-3" />,
      },
    };

    const { variant, icon } = config[result];
    return (
      <Tag variant={variant} className="flex items-center gap-1 w-fit">
        {icon}
        {t(`admin.auditLogs.result.${result}`, result)}
      </Tag>
    );
  };

  const formatMetadata = (metadata?: Record<string, unknown>) => {
    if (!metadata || Object.keys(metadata).length === 0) return null;
    return JSON.stringify(metadata, null, 2);
  };

  if (isLoading) {
    return <Loader />;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <FileText className="h-6 w-6" />
            {t("admin.auditLogs.title", "审计日志")}
          </h1>
          <p className="text-muted-foreground">
            {t("admin.auditLogs.description", "查看系统操作的审计记录")}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => handleExport("csv")}
            disabled={exportMutation.isPending}
          >
            {exportMutation.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Download className="mr-2 h-4 w-4" />
            )}
            {t("admin.auditLogs.exportCsv", "导出 CSV")}
          </Button>
          <Button
            variant="outline"
            onClick={() => handleExport("json")}
            disabled={exportMutation.isPending}
          >
            <Download className="mr-2 h-4 w-4" />
            {t("admin.auditLogs.exportJson", "导出 JSON")}
          </Button>
        </div>
      </div>

      {/* 筛选栏 */}
      <Card>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder={t("admin.auditLogs.searchUser", "搜索用户名...")}
                value={filters.username || ""}
                onChange={(e) =>
                  setFilters({ ...filters, username: e.target.value || undefined, page: 0 })
                }
                className="pl-9"
              />
            </div>
            <Select
              value={filters.resourceType || "all"}
              onValueChange={(v) =>
                setFilters({
                  ...filters,
                  resourceType: v === "all" ? undefined : v,
                  page: 0,
                })
              }
            >
              <SelectTrigger>
                <SelectValue placeholder={t("admin.auditLogs.filterByResource", "资源类型")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("common.all", "全部")}</SelectItem>
                <SelectItem value="user">{t("admin.auditLogs.resourceType.user", "用户")}</SelectItem>
                <SelectItem value="workspace">{t("admin.auditLogs.resourceType.workspace", "工作空间")}</SelectItem>
                <SelectItem value="project">{t("admin.auditLogs.resourceType.project", "项目")}</SelectItem>
                <SelectItem value="api_key">{t("admin.auditLogs.resourceType.api_key", "API Key")}</SelectItem>
                <SelectItem value="session">{t("admin.auditLogs.resourceType.session", "会话")}</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={filters.result || "all"}
              onValueChange={(v) =>
                setFilters({
                  ...filters,
                  result: v === "all" ? undefined : (v as "success" | "failure" | "error"),
                  page: 0,
                })
              }
            >
              <SelectTrigger>
                <SelectValue placeholder={t("admin.auditLogs.filterByResult", "结果")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("common.all", "全部")}</SelectItem>
                <SelectItem value="success">{t("admin.auditLogs.result.success", "成功")}</SelectItem>
                <SelectItem value="failure">{t("admin.auditLogs.result.failure", "失败")}</SelectItem>
                <SelectItem value="error">{t("admin.auditLogs.result.error", "错误")}</SelectItem>
              </SelectContent>
            </Select>
            <Input
              type="date"
              value={filters.startTime?.split("T")[0] || ""}
              onChange={(e) =>
                setFilters({
                  ...filters,
                  startTime: e.target.value ? `${e.target.value}T00:00:00Z` : undefined,
                  page: 0,
                })
              }
              placeholder={t("admin.auditLogs.startDate", "开始日期")}
            />
          </div>
        </CardContent>
      </Card>

      {/* 日志列表 */}
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.auditLogs.listTitle", "日志记录")}</CardTitle>
          <CardDescription>
            {t("admin.auditLogs.listDescription", "共 {{count}} 条记录", {
              count: data?.total || 0,
            })}
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("admin.auditLogs.timestamp", "时间")}</TableHead>
                <TableHead>{t("admin.auditLogs.user", "用户")}</TableHead>
                <TableHead>{t("admin.auditLogs.action", "操作")}</TableHead>
                <TableHead>{t("admin.auditLogs.resource", "资源")}</TableHead>
                <TableHead>{t("admin.auditLogs.result", "结果")}</TableHead>
                <TableHead>{t("admin.auditLogs.ip", "IP 地址")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data?.content?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground">
                    {t("admin.auditLogs.empty", "暂无日志记录")}
                  </TableCell>
                </TableRow>
              ) : (
                data?.content?.map((log) => (
                  <TableRow
                    key={log.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => setSelectedLog(log)}
                  >
                    <TableCell className="font-mono text-sm">
                      {format(new Date(log.timestamp), "yyyy-MM-dd HH:mm:ss")}
                    </TableCell>
                    <TableCell>
                      <div className="font-medium">{log.username}</div>
                    </TableCell>
                    <TableCell>{log.action}</TableCell>
                    <TableCell>
                      <div className="flex flex-col">
                        <span className="text-xs text-muted-foreground">
                          {log.resourceType}
                        </span>
                        <span className="truncate max-w-[200px]">
                          {log.resourceName || log.resourceId}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>{getResultBadge(log.result)}</TableCell>
                    <TableCell className="font-mono text-sm">
                      {log.ipAddress}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 分页 */}
      {data && data.total > 20 && (
        <div className="flex items-center justify-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setFilters({ ...filters, page: Math.max(0, (filters.page || 0) - 1) })}
            disabled={filters.page === 0}
          >
            {t("common.previous", "上一页")}
          </Button>
          <span className="text-sm text-muted-foreground">
            {t("common.pageInfo", "第 {{current}} 页，共 {{total}} 页", {
              current: (filters.page || 0) + 1,
              total: Math.ceil(data.total / 20),
            })}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setFilters({ ...filters, page: (filters.page || 0) + 1 })}
            disabled={((filters.page || 0) + 1) * 20 >= data.total}
          >
            {t("common.next", "下一页")}
          </Button>
        </div>
      )}

      {/* 日志详情对话框 */}
      <Dialog open={!!selectedLog} onOpenChange={() => setSelectedLog(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("admin.auditLogs.detailTitle", "日志详情")}</DialogTitle>
            <DialogDescription>
              {selectedLog && format(new Date(selectedLog.timestamp), "yyyy-MM-dd HH:mm:ss")}
            </DialogDescription>
          </DialogHeader>
          {selectedLog && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.user", "用户")}
                  </label>
                  <p className="font-medium">{selectedLog.username}</p>
                </div>
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.action", "操作")}
                  </label>
                  <p className="font-medium">{selectedLog.action}</p>
                </div>
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.resourceType", "资源类型")}
                  </label>
                  <p className="font-medium">{selectedLog.resourceType}</p>
                </div>
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.resourceId", "资源 ID")}
                  </label>
                  <p className="font-mono text-sm">{selectedLog.resourceId || "-"}</p>
                </div>
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.result", "结果")}
                  </label>
                  <div className="mt-1">{getResultBadge(selectedLog.result)}</div>
                </div>
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.ip", "IP 地址")}
                  </label>
                  <p className="font-mono text-sm">{selectedLog.ipAddress || "-"}</p>
                </div>
              </div>

              {selectedLog.errorMessage && (
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.errorMessage", "错误信息")}
                  </label>
                  <p className="mt-1 rounded bg-destructive/10 p-2 text-sm text-destructive">
                    {selectedLog.errorMessage}
                  </p>
                </div>
              )}

              {selectedLog.userAgent && (
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.userAgent", "User Agent")}
                  </label>
                  <p className="mt-1 text-sm text-muted-foreground break-all">
                    {selectedLog.userAgent}
                  </p>
                </div>
              )}

              {formatMetadata(selectedLog.metadata) && (
                <div>
                  <label className="text-sm font-medium text-muted-foreground">
                    {t("admin.auditLogs.metadata", "元数据")}
                  </label>
                  <pre className="mt-1 rounded bg-muted p-2 text-xs overflow-auto max-h-40">
                    {formatMetadata(selectedLog.metadata)}
                  </pre>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default AuditLogsPage;
