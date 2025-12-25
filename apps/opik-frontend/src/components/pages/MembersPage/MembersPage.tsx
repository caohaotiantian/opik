import { useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { UserPlus, Trash2, Loader2, Shield, Search } from "lucide-react";
import { format } from "date-fns";

import {
  useMembersList,
  useAddMember,
  useUpdateMemberRole,
  useRemoveMember,
  useRolesList,
} from "@/api/members";
import type { WorkspaceMember } from "@/api/members";
import { useCurrentWorkspaceId } from "@/store/AuthStore";
import { useUsersList } from "@/api/users";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
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
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tag } from "@/components/ui/tag";
import { useToast } from "@/components/ui/use-toast";
import Loader from "@/components/shared/Loader/Loader";
import ConfirmDialog from "@/components/shared/ConfirmDialog/ConfirmDialog";

const MembersPage = () => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const workspaceId = useCurrentWorkspaceId();

  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [isRemoveDialogOpen, setIsRemoveDialogOpen] = useState(false);
  const [selectedMember, setSelectedMember] = useState<WorkspaceMember | null>(null);
  const [selectedUserId, setSelectedUserId] = useState("");
  const [selectedUserName, setSelectedUserName] = useState("");
  const [selectedRoleId, setSelectedRoleId] = useState("");
  const [userSearchOpen, setUserSearchOpen] = useState(false);
  const [userSearchQuery, setUserSearchQuery] = useState("");

  const { data: membersData, isLoading: membersLoading } = useMembersList({
    workspaceId: workspaceId || "",
  });

  const { data: rolesData } = useRolesList({ scope: "workspace" });

  // 获取所有用户列表（用于选择）
  const { data: usersData } = useUsersList({ 
    search: userSearchQuery || undefined,
    status: "active",
    size: 50,
  });

  const addMutation = useAddMember();
  const updateRoleMutation = useUpdateMemberRole();
  const removeMutation = useRemoveMember();

  const workspaceRoles = rolesData?.content?.filter(
    (role) => role.scope === "workspace",
  ) || [];

  // 过滤掉已经是成员的用户
  const availableUsers = useMemo(() => {
    const memberUserIds = new Set(membersData?.content?.map((m) => m.userId) || []);
    return usersData?.content?.filter((user) => !memberUserIds.has(user.id)) || [];
  }, [usersData, membersData]);

  const handleAddMember = async () => {
    if (!workspaceId || !selectedUserId || !selectedRoleId) return;

    try {
      await addMutation.mutateAsync({
        workspaceId,
        request: {
          userId: selectedUserId,
          roleId: selectedRoleId,
        },
      });

      setIsAddDialogOpen(false);
      setSelectedUserId("");
      setSelectedUserName("");
      setSelectedRoleId("");

      toast({
        title: t("members.added", "成员已添加"),
        description: t("members.addedDescription", "{{name}} 已被添加到工作空间", {
          name: selectedUserName,
        }),
      });
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("members.addFailed", "添加成员失败"),
      });
    }
  };

  const handleUpdateRole = async (member: WorkspaceMember, newRoleId: string) => {
    if (!workspaceId) return;

    try {
      await updateRoleMutation.mutateAsync({
        workspaceId,
        memberId: member.userId, // 后端使用 userId 而不是 member.id
        request: { roleId: newRoleId },
      });

      toast({
        title: t("members.roleUpdated", "角色已更新"),
      });
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("members.roleUpdateFailed", "更新角色失败"),
      });
    }
  };

  const handleRemoveMember = async () => {
    if (!workspaceId || !selectedMember) return;

    try {
      await removeMutation.mutateAsync({
        workspaceId,
        memberId: selectedMember.userId, // 后端使用 userId 而不是 member.id
      });

      setIsRemoveDialogOpen(false);
      setSelectedMember(null);

      toast({
        title: t("members.removed", "成员已移除"),
      });
    } catch (error) {
      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: t("members.removeFailed", "移除成员失败"),
      });
    }
  };

  const getStatusBadge = (status: string) => {
    const variants: Record<string, "default" | "gray" | "green"> = {
      active: "green",
      invited: "gray",
      suspended: "gray",
    };
    return (
      <Tag variant={variants[status] || "gray"}>
        {t(`members.status.${status}`, status)}
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

  // 获取翻译后的角色显示名称
  const getRoleDisplayName = (roleName: string) => {
    return t(`members.roles.${roleName}`, roleName);
  };

  // 获取翻译后的角色描述
  const getRoleDescription = (roleName: string) => {
    return t(`members.roleDescriptions.${roleName}`, "");
  };

  if (membersLoading) {
    return <Loader />;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">
            {t("members.title", "成员管理")}
          </h1>
          <p className="text-muted-foreground">
            {t("members.description", "管理工作空间的成员和权限")}
          </p>
        </div>
        <Button onClick={() => setIsAddDialogOpen(true)}>
          <UserPlus className="mr-2 h-4 w-4" />
          {t("members.add", "添加成员")}
        </Button>
      </div>

      {/* 成员列表 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5" />
            {t("members.listTitle", "成员列表")}
          </CardTitle>
          <CardDescription>
            {t("members.listDescription", "共 {{count}} 名成员", {
              count: membersData?.total || 0,
            })}
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("members.member", "成员")}</TableHead>
                <TableHead>{t("members.role", "角色")}</TableHead>
                <TableHead>{t("members.status", "状态")}</TableHead>
                <TableHead>{t("members.joinedAt", "加入时间")}</TableHead>
                <TableHead className="w-[100px]">{t("common.actions", "操作")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {membersData?.content?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground">
                    {t("members.empty", "暂无成员")}
                  </TableCell>
                </TableRow>
              ) : (
                membersData?.content?.map((member) => (
                  <TableRow key={member.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <Avatar className="h-8 w-8">
                          <AvatarImage src={member.avatarUrl} />
                          <AvatarFallback>
                            {getInitials(member.fullName, member.email)}
                          </AvatarFallback>
                        </Avatar>
                        <div>
                          <div className="font-medium">
                            {member.fullName || member.username}
                          </div>
                          <div className="text-sm text-muted-foreground">
                            {member.email}
                          </div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Select
                        value={member.roleId}
                        onValueChange={(value) => handleUpdateRole(member, value)}
                        disabled={updateRoleMutation.isPending}
                      >
                        <SelectTrigger className="w-[180px]">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {workspaceRoles.map((role) => (
                            <SelectItem key={role.id} value={role.id}>
                              {getRoleDisplayName(role.name)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>{getStatusBadge(member.status)}</TableCell>
                    <TableCell>
                      {format(new Date(member.joinedAt), "yyyy-MM-dd")}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => {
                          setSelectedMember(member);
                          setIsRemoveDialogOpen(true);
                        }}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 添加成员对话框 */}
      <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("members.addTitle", "添加成员")}</DialogTitle>
            <DialogDescription>
              {t("members.addDescription", "从已注册用户中选择成员添加到工作空间")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label>{t("members.selectUser", "选择用户")}</Label>
              <Popover open={userSearchOpen} onOpenChange={setUserSearchOpen}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    aria-expanded={userSearchOpen}
                    className="w-full justify-between"
                  >
                    {selectedUserName || t("members.selectUserPlaceholder", "搜索并选择用户...")}
                    <Search className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-[400px] p-0">
                  <Command>
                    <CommandInput
                      placeholder={t("members.searchUser", "搜索用户名或邮箱...")}
                      value={userSearchQuery}
                      onValueChange={setUserSearchQuery}
                    />
                    <CommandList>
                      <CommandEmpty>{t("members.noUsersFound", "未找到用户")}</CommandEmpty>
                      <CommandGroup>
                        {availableUsers.map((user) => (
                          <CommandItem
                            key={user.id}
                            value={user.username}
                            onSelect={() => {
                              setSelectedUserId(user.id);
                              setSelectedUserName(user.fullName || user.username);
                              setUserSearchOpen(false);
                            }}
                          >
                            <div className="flex items-center gap-3">
                              <Avatar className="h-8 w-8">
                                <AvatarImage src={user.avatarUrl} />
                                <AvatarFallback>
                                  {(user.fullName || user.username)?.slice(0, 2).toUpperCase()}
                                </AvatarFallback>
                              </Avatar>
                              <div>
                                <div className="font-medium">{user.fullName || user.username}</div>
                                <div className="text-xs text-muted-foreground">{user.email}</div>
                              </div>
                            </div>
                          </CommandItem>
                        ))}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                </PopoverContent>
              </Popover>
            </div>
            <div>
              <Label htmlFor="role">{t("members.role", "角色")}</Label>
              <Select value={selectedRoleId} onValueChange={setSelectedRoleId}>
                <SelectTrigger>
                  <SelectValue placeholder={t("members.selectRole", "选择角色")} />
                </SelectTrigger>
                <SelectContent>
                  {workspaceRoles.map((role) => (
                    <SelectItem key={role.id} value={role.id}>
                      <div>
                        <div className="font-medium">{getRoleDisplayName(role.name)}</div>
                        <div className="text-xs text-muted-foreground">
                          {getRoleDescription(role.name) || role.description}
                        </div>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddDialogOpen(false)}>
              {t("common.cancel", "取消")}
            </Button>
            <Button
              onClick={handleAddMember}
              disabled={!selectedUserId || !selectedRoleId || addMutation.isPending}
            >
              {addMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {t("members.addMember", "添加成员")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 移除成员确认对话框 */}
      <ConfirmDialog
        open={isRemoveDialogOpen}
        setOpen={setIsRemoveDialogOpen}
        onConfirm={handleRemoveMember}
        title={t("members.removeTitle", "移除成员")}
        description={t(
          "members.removeDescription",
          "确定要将 {{name}} 从工作空间中移除？此操作无法撤销。",
          { name: selectedMember?.fullName || selectedMember?.username },
        )}
        confirmText={t("common.remove", "移除")}
        confirmButtonVariant="destructive"
      />
    </div>
  );
};

export default MembersPage;
