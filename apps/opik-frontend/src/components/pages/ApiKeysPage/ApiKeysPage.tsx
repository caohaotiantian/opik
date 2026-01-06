import { useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  Plus,
  Key,
  Copy,
  Trash2,
  Loader2,
  Eye,
  EyeOff,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
} from "lucide-react";
import { format } from "date-fns";

import { useApiKeysList, useCreateApiKey, useDeleteApiKey } from "@/api/api-keys";
import type {
  ApiKeyResponse,
  CreateApiKeyRequest,
  ApiKeyStatus,
  ApiKeySortField,
  SortDirection,
} from "@/api/api-keys";
import { useCurrentWorkspaceId } from "@/store/AuthStore";

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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Tag } from "@/components/ui/tag";
import { useToast } from "@/components/ui/use-toast";
import Loader from "@/components/shared/Loader/Loader";
import ConfirmDialog from "@/components/shared/ConfirmDialog/ConfirmDialog";
import SearchInput from "@/components/shared/SearchInput/SearchInput";
import DataTablePagination from "@/components/shared/DataTablePagination/DataTablePagination";
import { cn } from "@/lib/utils";

const DEFAULT_PAGE_SIZE = 10;

const ApiKeysPage = () => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const workspaceId = useCurrentWorkspaceId();

  // 对话框状态
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
  const [selectedApiKey, setSelectedApiKey] = useState<ApiKeyResponse | null>(null);
  const [newApiKeyResult, setNewApiKeyResult] = useState<ApiKeyResponse | null>(null);
  const [showPlainKey, setShowPlainKey] = useState(false);

  // 表单状态
  const [formData, setFormData] = useState<CreateApiKeyRequest>({
    name: "",
    description: "",
  });

  // 分页、搜索、筛选、排序状态
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<ApiKeyStatus | "">("");
  const [sortBy, setSortBy] = useState<ApiKeySortField>("created_at");
  const [sortDir, setSortDir] = useState<SortDirection>("desc");

  // 构建查询参数
  const queryParams = useMemo(
    () => ({
      page,
      size,
      search: search || undefined,
      status: statusFilter || undefined,
      sortBy,
      sortDir,
      workspaceId: workspaceId || undefined, // 传递工作空间 ID
    }),
    [page, size, search, statusFilter, sortBy, sortDir, workspaceId],
  );

  const { data, isLoading, isError } = useApiKeysList(queryParams);
  const createMutation = useCreateApiKey();
  const deleteMutation = useDeleteApiKey();

  // 处理排序点击
  const handleSort = (field: ApiKeySortField) => {
    if (sortBy === field) {
      // 同一列，切换排序方向
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      // 不同列，设置新的排序字段和默认降序
      setSortBy(field);
      setSortDir("desc");
    }
    setPage(1); // 重置到第一页
  };

  // 渲染排序图标
  const renderSortIcon = (field: ApiKeySortField) => {
    if (sortBy !== field) {
      return <ArrowUpDown className="ml-1 h-3 w-3 opacity-50" />;
    }
    return sortDir === "asc" ? (
      <ArrowUp className="ml-1 h-3 w-3" />
    ) : (
      <ArrowDown className="ml-1 h-3 w-3" />
    );
  };

  // 处理搜索
  const handleSearch = (value: string) => {
    setSearch(value);
    setPage(1); // 重置到第一页
  };

  // 处理状态筛选
  const handleStatusFilter = (value: string) => {
    setStatusFilter(value as ApiKeyStatus | "");
    setPage(1); // 重置到第一页
  };

  const handleCreate = async () => {
    try {
      const result = await createMutation.mutateAsync({
        ...formData,
        workspaceId: workspaceId || undefined,
      });
      setNewApiKeyResult(result);
      setIsCreateDialogOpen(false);
      setFormData({ name: "", description: "" });

      toast({
        title: t("apiKeys.created", "API Key 已创建"),
        description: t("apiKeys.createdDescription", "请立即复制保存，此密钥仅显示一次"),
      });
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("apiKeys.createFailed", "创建 API Key 失败"),
      });
    }
  };

  const handleDelete = async () => {
    if (!selectedApiKey) return;

    try {
      await deleteMutation.mutateAsync(selectedApiKey.id);
      setIsDeleteDialogOpen(false);
      setSelectedApiKey(null);

      toast({
        title: t("apiKeys.deleted", "API Key 已删除"),
      });
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("apiKeys.deleteFailed", "删除 API Key 失败"),
      });
    }
  };

  const copyToClipboard = async (text: string) => {
    await navigator.clipboard.writeText(text);
    toast({
      title: t("common.copied", "已复制"),
      description: t("apiKeys.keyCopied", "API Key 已复制到剪贴板"),
    });
  };

  const getStatusBadge = (status: string) => {
    const variants: Record<string, "default" | "gray"> = {
      ACTIVE: "default",
      REVOKED: "gray",
    };
    const displayText: Record<string, string> = {
      ACTIVE: "active",
      REVOKED: "revoked",
    };
    return (
      <Tag variant={variants[status] || "gray"}>
        {t(`apiKeys.status.${displayText[status] || status}`, displayText[status] || status)}
      </Tag>
    );
  };

  if (isLoading && !data) {
    return <Loader />;
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center p-8 text-destructive">
        {t("common.loadError", "加载失败")}
      </div>
    );
  }

  const apiKeys = data?.content ?? [];
  const total = data?.total ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">
            {t("apiKeys.title", "API Keys")}
          </h1>
          <p className="text-muted-foreground">
            {t("apiKeys.description", "管理您的 API 访问密钥")}
          </p>
        </div>
        <Button onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t("apiKeys.create", "创建 API Key")}
        </Button>
      </div>

      {/* 新创建的 API Key 显示 */}
      {newApiKeyResult && newApiKeyResult.apiKey && (
        <Card className="border-green-200 bg-green-50">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-green-700">
              <Key className="h-5 w-5" />
              {t("apiKeys.newKeyCreated", "新 API Key 已创建")}
            </CardTitle>
            <CardDescription className="text-green-600">
              {t("apiKeys.copyWarning", "请立即复制此密钥，它将不会再次显示。")}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <Input
                value={showPlainKey ? newApiKeyResult.apiKey : "••••••••••••••••"}
                readOnly
                className="font-mono"
              />
              <Button
                variant="outline"
                size="icon"
                onClick={() => setShowPlainKey(!showPlainKey)}
              >
                {showPlainKey ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </Button>
              <Button
                variant="outline"
                size="icon"
                onClick={() => copyToClipboard(newApiKeyResult.apiKey!)}
              >
                <Copy className="h-4 w-4" />
              </Button>
            </div>
            <Button
              variant="ghost"
              size="sm"
              className="mt-2"
              onClick={() => setNewApiKeyResult(null)}
            >
              {t("common.dismiss", "关闭")}
            </Button>
          </CardContent>
        </Card>
      )}

      {/* 搜索和筛选 */}
      <div className="flex items-center gap-4">
        <SearchInput
          searchText={search}
          setSearchText={handleSearch}
          placeholder={t("apiKeys.searchPlaceholder", "搜索 API Key 名称...")}
          className="w-64"
        />
        <Select value={statusFilter} onValueChange={handleStatusFilter}>
          <SelectTrigger className="w-40">
            <SelectValue placeholder={t("apiKeys.allStatus", "全部状态")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">{t("apiKeys.allStatus", "全部状态")}</SelectItem>
            <SelectItem value="ACTIVE">{t("apiKeys.status.active", "活跃")}</SelectItem>
            <SelectItem value="REVOKED">{t("apiKeys.status.revoked", "已撤销")}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* API Key 列表 */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead
                  className="cursor-pointer select-none hover:bg-muted/50"
                  onClick={() => handleSort("name")}
                >
                  <div className="flex items-center">
                    {t("apiKeys.name", "名称")}
                    {renderSortIcon("name")}
                  </div>
                </TableHead>
                <TableHead>{t("apiKeys.keyPrefix", "密钥前缀")}</TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:bg-muted/50"
                  onClick={() => handleSort("status")}
                >
                  <div className="flex items-center">
                    {t("apiKeys.status", "状态")}
                    {renderSortIcon("status")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:bg-muted/50"
                  onClick={() => handleSort("last_used_at")}
                >
                  <div className="flex items-center">
                    {t("apiKeys.lastUsed", "最后使用")}
                    {renderSortIcon("last_used_at")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:bg-muted/50"
                  onClick={() => handleSort("created_at")}
                >
                  <div className="flex items-center">
                    {t("apiKeys.createdAt", "创建时间")}
                    {renderSortIcon("created_at")}
                  </div>
                </TableHead>
                <TableHead className="w-[100px]">{t("common.actions", "操作")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {apiKeys.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground">
                    {search || statusFilter
                      ? t("apiKeys.noResults", "未找到匹配的 API Key")
                      : t("apiKeys.empty", "暂无 API Key")}
                  </TableCell>
                </TableRow>
              ) : (
                apiKeys.map((apiKey) => {
                  const isRevoked = apiKey.status === "REVOKED";
                  return (
                    <TableRow key={apiKey.id}>
                      <TableCell>
                        <div>
                          <div className="font-medium">{apiKey.name}</div>
                          {apiKey.description && (
                            <div className="text-sm text-muted-foreground">
                              {apiKey.description}
                            </div>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <code className="text-sm">{apiKey.id.substring(0, 8)}...</code>
                      </TableCell>
                      <TableCell>{getStatusBadge(apiKey.status)}</TableCell>
                      <TableCell>
                        {apiKey.lastUsedAt
                          ? format(new Date(apiKey.lastUsedAt), "yyyy-MM-dd HH:mm")
                          : t("apiKeys.neverUsed", "从未使用")}
                      </TableCell>
                      <TableCell>
                        {format(new Date(apiKey.createdAt), "yyyy-MM-dd HH:mm")}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setSelectedApiKey(apiKey);
                            setIsDeleteDialogOpen(true);
                          }}
                          disabled={isRevoked}
                          className={cn(
                            isRevoked && "opacity-30 cursor-not-allowed",
                          )}
                          title={
                            isRevoked
                              ? t("apiKeys.alreadyRevoked", "此 API Key 已被撤销")
                              : t("apiKeys.revokeKey", "撤销此 API Key")
                          }
                        >
                          <Trash2
                            className={cn(
                              "h-4 w-4",
                              isRevoked ? "text-muted-foreground" : "text-destructive",
                            )}
                          />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </CardContent>

        {/* 分页 */}
        {total > 0 && (
          <div className="border-t p-4">
            <DataTablePagination
              page={page}
              pageChange={setPage}
              size={size}
              sizeChange={setSize}
              total={total}
            />
          </div>
        )}
      </Card>

      {/* 创建对话框 */}
      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("apiKeys.createTitle", "创建 API Key")}</DialogTitle>
            <DialogDescription>
              {t("apiKeys.createDescription", "创建一个新的 API Key 用于访问 Opik API")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label htmlFor="name">{t("apiKeys.name", "名称")}</Label>
              <Input
                id="name"
                value={formData.name}
                onChange={(e) =>
                  setFormData({ ...formData, name: e.target.value })
                }
                placeholder={t("apiKeys.namePlaceholder", "例如：Production API Key")}
              />
            </div>
            <div>
              <Label htmlFor="description">
                {t("apiKeys.descriptionLabel", "描述")}{" "}
                <span className="text-muted-foreground">
                  ({t("common.optional", "可选")})
                </span>
              </Label>
              <Textarea
                id="description"
                value={formData.description}
                onChange={(e) =>
                  setFormData({ ...formData, description: e.target.value })
                }
                placeholder={t("apiKeys.descriptionPlaceholder", "描述此 API Key 的用途")}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>
              {t("common.cancel", "取消")}
            </Button>
            <Button
              onClick={handleCreate}
              disabled={!formData.name || createMutation.isPending}
            >
              {createMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {t("common.create", "创建")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认对话框 */}
      <ConfirmDialog
        open={isDeleteDialogOpen}
        setOpen={setIsDeleteDialogOpen}
        onConfirm={handleDelete}
        title={t("apiKeys.deleteTitle", "撤销 API Key")}
        description={t(
          "apiKeys.deleteDescription",
          "确定要撤销 API Key \"{{name}}\"？此操作无法撤销，使用此密钥的应用将无法继续访问 API。",
          { name: selectedApiKey?.name },
        )}
        confirmText={t("apiKeys.revoke", "撤销")}
        confirmButtonVariant="destructive"
      />
    </div>
  );
};

export default ApiKeysPage;
