import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Search,
  Users,
  Shield,
  Ban,
  CheckCircle,
  MoreHorizontal,
} from "lucide-react";
import { format } from "date-fns";

import { useAdminUsersList, useUpdateUserStatus } from "@/api/admin";
import type { AdminUser, UserStatus } from "@/api/admin";

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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tag } from "@/components/ui/tag";
import { useToast } from "@/components/ui/use-toast";
import Loader from "@/components/shared/Loader/Loader";
import ConfirmDialog from "@/components/shared/ConfirmDialog/ConfirmDialog";

const UsersManagementPage = () => {
  const { t } = useTranslation();
  const { toast } = useToast();

  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<UserStatus | "all">("all");
  const [page, setPage] = useState(0);
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null);
  const [actionType, setActionType] = useState<"suspend" | "activate" | null>(null);

  const { data, isLoading } = useAdminUsersList({
    search: searchQuery || undefined,
    status: statusFilter === "all" ? undefined : statusFilter,
    page,
    size: 20,
  });

  const updateStatusMutation = useUpdateUserStatus();

  const handleStatusChange = async () => {
    if (!selectedUser || !actionType) return;

    const newStatus: UserStatus = actionType === "suspend" ? "suspended" : "active";

    try {
      await updateStatusMutation.mutateAsync({
        userId: selectedUser.id,
        request: { status: newStatus },
      });

      toast({
        title:
          actionType === "suspend"
            ? t("admin.users.suspended", "用户已暂停")
            : t("admin.users.activated", "用户已激活"),
      });

      setSelectedUser(null);
      setActionType(null);
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("admin.users.statusUpdateFailed", "状态更新失败"),
      });
    }
  };

  const getStatusBadge = (status: UserStatus) => {
    const config: Record<UserStatus, { variant: "green" | "red" | "gray" }> = {
      active: { variant: "green" },
      suspended: { variant: "red" },
      deleted: { variant: "gray" },
    };

    const { variant } = config[status];
    return (
      <Tag variant={variant} className="flex items-center gap-1">
        {status === "active" && <CheckCircle className="h-3 w-3" />}
        {status === "suspended" && <Ban className="h-3 w-3" />}
        {t(`admin.users.status.${status}`, status)}
      </Tag>
    );
  };

  const getInitials = (name?: string, email?: string) => {
    if (name) {
      return name
        .split(" ")
        .map((n) => n[0])
        .join("")
        .toUpperCase()
        .slice(0, 2);
    }
    return email?.slice(0, 2).toUpperCase() || "??";
  };

  if (isLoading) {
    return <Loader />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Users className="h-6 w-6" />
          {t("admin.users.title", "用户管理")}
        </h1>
        <p className="text-muted-foreground">
          {t("admin.users.description", "管理系统中的所有用户")}
        </p>
      </div>

      {/* 筛选栏 */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder={t("admin.users.searchPlaceholder", "搜索用户名或邮箱...")}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={statusFilter}
              onValueChange={(v) => setStatusFilter(v as UserStatus | "all")}
            >
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder={t("admin.users.filterByStatus", "按状态筛选")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("common.all", "全部")}</SelectItem>
                <SelectItem value="active">{t("admin.users.status.active", "活跃")}</SelectItem>
                <SelectItem value="suspended">{t("admin.users.status.suspended", "已暂停")}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* 用户列表 */}
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.users.listTitle", "用户列表")}</CardTitle>
          <CardDescription>
            {t("admin.users.listDescription", "共 {{count}} 名用户", {
              count: data?.total || 0,
            })}
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("admin.users.user", "用户")}</TableHead>
                <TableHead>{t("admin.users.role", "角色")}</TableHead>
                <TableHead>{t("admin.users.status", "状态")}</TableHead>
                <TableHead>{t("admin.users.workspaces", "工作空间")}</TableHead>
                <TableHead>{t("admin.users.lastLogin", "最后登录")}</TableHead>
                <TableHead>{t("admin.users.createdAt", "注册时间")}</TableHead>
                <TableHead className="w-[80px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data?.content?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-muted-foreground">
                    {t("admin.users.empty", "暂无用户")}
                  </TableCell>
                </TableRow>
              ) : (
                data?.content?.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <Avatar className="h-8 w-8">
                          <AvatarImage src={user.avatarUrl} />
                          <AvatarFallback>
                            {getInitials(user.fullName, user.email)}
                          </AvatarFallback>
                        </Avatar>
                        <div>
                          <div className="font-medium">{user.username}</div>
                          <div className="text-sm text-muted-foreground">
                            {user.email}
                          </div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      {user.systemAdmin ? (
                        <Tag variant="primary" className="flex items-center gap-1 w-fit">
                          <Shield className="h-3 w-3" />
                          {t("admin.users.systemAdmin", "系统管理员")}
                        </Tag>
                      ) : (
                        <span className="text-muted-foreground">
                          {t("admin.users.normalUser", "普通用户")}
                        </span>
                      )}
                    </TableCell>
                    <TableCell>{getStatusBadge(user.status)}</TableCell>
                    <TableCell>{user.workspaceCount ?? 0}</TableCell>
                    <TableCell>
                      {user.lastLoginAt
                        ? format(new Date(user.lastLoginAt), "yyyy-MM-dd HH:mm")
                        : t("admin.users.neverLoggedIn", "从未登录")}
                    </TableCell>
                    <TableCell>
                      {format(new Date(user.createdAt), "yyyy-MM-dd")}
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          {user.status === "active" ? (
                            <DropdownMenuItem
                              onClick={() => {
                                setSelectedUser(user);
                                setActionType("suspend");
                              }}
                              className="text-destructive"
                            >
                              <Ban className="mr-2 h-4 w-4" />
                              {t("admin.users.suspend", "暂停用户")}
                            </DropdownMenuItem>
                          ) : (
                            <DropdownMenuItem
                              onClick={() => {
                                setSelectedUser(user);
                                setActionType("activate");
                              }}
                            >
                              <CheckCircle className="mr-2 h-4 w-4" />
                              {t("admin.users.activate", "激活用户")}
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
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
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            {t("common.previous", "上一页")}
          </Button>
          <span className="text-sm text-muted-foreground">
            {t("common.pageInfo", "第 {{current}} 页，共 {{total}} 页", {
              current: page + 1,
              total: Math.ceil(data.total / 20),
            })}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage((p) => p + 1)}
            disabled={(page + 1) * 20 >= data.total}
          >
            {t("common.next", "下一页")}
          </Button>
        </div>
      )}

      {/* 状态更改确认对话框 */}
      <ConfirmDialog
        open={!!selectedUser && !!actionType}
        setOpen={() => {
          setSelectedUser(null);
          setActionType(null);
        }}
        onConfirm={handleStatusChange}
        title={
          actionType === "suspend"
            ? t("admin.users.suspendTitle", "暂停用户")
            : t("admin.users.activateTitle", "激活用户")
        }
        description={
          actionType === "suspend"
            ? t(
                "admin.users.suspendDescription",
                "确定要暂停用户 {{username}}？暂停后该用户将无法登录系统。",
                { username: selectedUser?.username },
              )
            : t(
                "admin.users.activateDescription",
                "确定要激活用户 {{username}}？激活后该用户将可以正常登录系统。",
                { username: selectedUser?.username },
              )
        }
        confirmText={
          actionType === "suspend"
            ? t("admin.users.confirmSuspend", "确认暂停")
            : t("admin.users.confirmActivate", "确认激活")
        }
        confirmButtonVariant={actionType === "suspend" ? "destructive" : "default"}
      />
    </div>
  );
};

export default UsersManagementPage;
