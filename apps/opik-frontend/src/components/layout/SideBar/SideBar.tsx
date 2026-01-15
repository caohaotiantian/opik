import React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";

import {
  // Book, // Removed - no longer needed
  Database,
  FlaskConical,
  // GraduationCap, // Removed - no longer needed
  LayoutGrid,
  // MessageCircleQuestion, // Removed - no longer needed
  FileTerminal,
  LucideHome,
  Blocks,
  Bolt,
  Brain,
  ChevronLeft,
  ChevronRight,
  SparklesIcon,
  UserPen,
  Key,
  Users,
  Shield,
  ScrollText,
  LogOut,
  Settings,
} from "lucide-react";
import { keepPreviousData } from "@tanstack/react-query";

import useAppStore from "@/store/AppStore";
import useAuthStore, { useIsSystemAdmin, useAuthEnabled, useCurrentUser } from "@/store/AuthStore";
import { useLogout } from "@/api/auth";
import useProjectsList from "@/api/projects/useProjectsList";
import useDatasetsList from "@/api/datasets/useDatasetsList";
import useExperimentsList from "@/api/datasets/useExperimentsList";
import useRulesList from "@/api/automations/useRulesList";
import useOptimizationsList from "@/api/optimizations/useOptimizationsList";
import { OnChangeFn } from "@/types/shared";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/components/ui/use-toast";
import { cn } from "@/lib/utils";
import Logo from "@/components/layout/Logo/Logo";
import usePluginsStore from "@/store/PluginsStore";
// import ProvideFeedbackDialog from "@/components/layout/SideBar/FeedbackDialog/ProvideFeedbackDialog"; // Removed - no longer needed
import usePromptsList from "@/api/prompts/usePromptsList";
import useAnnotationQueuesList from "@/api/annotation-queues/useAnnotationQueuesList";
// import { useOpenQuickStartDialog } from "@/components/pages-shared/onboarding/QuickstartDialog/QuickstartDialog"; // Removed - no longer needed
// import GitHubStarListItem from "@/components/layout/SideBar/GitHubStarListItem/GitHubStarListItem"; // Removed - no longer needed
import WorkspaceSelector from "@/components/layout/WorkspaceSelector/WorkspaceSelector";
import SidebarMenuItem, {
  MENU_ITEM_TYPE,
  MenuItem,
  MenuItemGroup,
} from "@/components/layout/SideBar/MenuItem/SidebarMenuItem";

const HOME_PATH = "/$workspaceName/home";

// 基础菜单项（所有用户可见）
const useBaseMenuItems = (): MenuItemGroup[] => {
  const { t } = useTranslation();

  return [
    {
      id: "home",
      items: [
        {
          id: "home",
          path: "/$workspaceName/home",
          type: MENU_ITEM_TYPE.router,
          icon: LucideHome,
          label: t("navigation.home"),
        },
      ],
    },
    {
      id: "observability",
      label: t("sections.observability"),
      items: [
        {
          id: "projects",
          path: "/$workspaceName/projects",
          type: MENU_ITEM_TYPE.router,
          icon: LayoutGrid,
          label: t("navigation.projects"),
          count: "projects",
        },
      ],
    },
    {
      id: "evaluation",
      label: t("sections.evaluation"),
      items: [
        {
          id: "experiments",
          path: "/$workspaceName/experiments",
          type: MENU_ITEM_TYPE.router,
          icon: FlaskConical,
          label: t("navigation.experiments"),
          count: "experiments",
        },
        {
          id: "optimizations",
          path: "/$workspaceName/optimizations",
          type: MENU_ITEM_TYPE.router,
          icon: SparklesIcon,
          label: t("navigation.optimizationRuns"),
          count: "optimizations",
        },
        {
          id: "datasets",
          path: "/$workspaceName/datasets",
          type: MENU_ITEM_TYPE.router,
          icon: Database,
          label: t("navigation.datasets"),
          count: "datasets",
        },
        {
          id: "annotation_queues",
          path: "/$workspaceName/annotation-queues",
          type: MENU_ITEM_TYPE.router,
          icon: UserPen,
          label: t("navigation.annotationQueues"),
          count: "annotation_queues",
        },
      ],
    },
    {
      id: "prompt_engineering",
      label: t("sections.promptEngineering"),
      items: [
        {
          id: "prompts",
          path: "/$workspaceName/prompts",
          type: MENU_ITEM_TYPE.router,
          icon: FileTerminal,
          label: t("navigation.promptLibrary"),
          count: "prompts",
        },
        {
          id: "playground",
          path: "/$workspaceName/playground",
          type: MENU_ITEM_TYPE.router,
          icon: Blocks,
          label: t("navigation.playground"),
        },
      ],
    },
    {
      id: "production",
      label: t("sections.production"),
      items: [
        {
          id: "online_evaluation",
          path: "/$workspaceName/online-evaluation",
          type: MENU_ITEM_TYPE.router,
          icon: Brain,
          label: t("navigation.onlineEvaluation"),
          count: "rules",
        },
      ],
    },
    {
      id: "configuration",
      label: t("navigation.configuration"),
      items: [
        {
          id: "configuration",
          path: "/$workspaceName/configuration",
          type: MENU_ITEM_TYPE.router,
          icon: Bolt,
          label: t("navigation.configuration"),
        },
      ],
    },
  ];
};

// 工作空间管理菜单项（认证模式下可见）
const useWorkspaceManagementItems = (): MenuItemGroup => {
  const { t } = useTranslation();

  return {
    id: "workspace_management",
    label: t("sections.workspaceManagement"),
    items: [
      {
        id: "api_keys",
        path: "/$workspaceName/api-keys",
        type: MENU_ITEM_TYPE.router,
        icon: Key,
        label: t("navigation.apiKeys"),
      },
      {
        id: "members",
        path: "/$workspaceName/members",
        type: MENU_ITEM_TYPE.router,
        icon: Users,
        label: t("navigation.members"),
      },
    ],
  };
};

// 管理员菜单项（仅系统管理员可见）
const useAdminMenuItems = (): MenuItemGroup => {
  const { t } = useTranslation();

  return {
    id: "administration",
    label: t("sections.administration"),
    items: [
      {
        id: "admin_users",
        path: "/admin/users",
        type: MENU_ITEM_TYPE.router,
        icon: Shield,
        label: t("navigation.userManagement"),
      },
      {
        id: "admin_audit_logs",
        path: "/admin/audit-logs",
        type: MENU_ITEM_TYPE.router,
        icon: ScrollText,
        label: t("navigation.auditLogs"),
      },
    ],
  };
};

// 组合所有菜单项
const useMenuItems = (authEnabled: boolean, isAdmin: boolean): MenuItemGroup[] => {
  const baseItems = useBaseMenuItems();
  const workspaceManagementItems = useWorkspaceManagementItems();
  const adminItems = useAdminMenuItems();

  const menuItems = [...baseItems];

  // 认证模式下显示工作空间管理
  if (authEnabled) {
    menuItems.push(workspaceManagementItems);
  }

  // 管理员显示管理菜单
  if (authEnabled && isAdmin) {
    menuItems.push(adminItems);
  }

  return menuItems;
};

type SideBarProps = {
  expanded: boolean;
  setExpanded: OnChangeFn<boolean | undefined>;
};

const SideBar: React.FunctionComponent<SideBarProps> = ({
  expanded,
  setExpanded,
}) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { toast } = useToast();

  const { activeWorkspaceName: workspaceName } = useAppStore();
  const LogoComponent = usePluginsStore((state) => state.Logo);
  const SidebarInviteDevButton = usePluginsStore(
    (state) => state.SidebarInviteDevButton,
  );
  
  // 获取认证状态和管理员角色
  const authEnabled = useAuthEnabled();
  const isAdmin = useIsSystemAdmin();
  const currentUser = useCurrentUser();
  const { logout: authLogout } = useAuthStore();
  
  const logoutMutation = useLogout();
  
  const MENU_ITEMS = useMenuItems(authEnabled, isAdmin);
  
  // 处理登出
  const handleLogout = async () => {
    try {
      await logoutMutation.mutateAsync();
      authLogout();
      toast({
        title: t("auth.logoutSuccess", "已登出"),
        description: t("auth.logoutDescription", "您已成功登出系统"),
      });
      navigate({ to: "/login" });
    } catch (error) {
      // 即使后端登出失败，也清除本地状态
      authLogout();
      navigate({ to: "/login" });
    }
  };
  
  // 获取用户名首字母
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

  const { data: projectData } = useProjectsList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const { data: datasetsData } = useDatasetsList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const { data: experimentsData } = useExperimentsList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const { data: promptsData } = usePromptsList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const { data: rulesData } = useRulesList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const { data: optimizationsData } = useOptimizationsList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const { data: annotationQueuesData } = useAnnotationQueuesList(
    {
      workspaceName,
      page: 1,
      size: 1,
    },
    {
      placeholderData: keepPreviousData,
      enabled: expanded,
    },
  );

  const countDataMap: Record<string, number | undefined> = {
    projects: projectData?.total,
    datasets: datasetsData?.total,
    experiments: experimentsData?.total,
    prompts: promptsData?.total,
    rules: rulesData?.total,
    optimizations: optimizationsData?.total,
    annotation_queues: annotationQueuesData?.total,
  };

  const logo = LogoComponent ? (
    <LogoComponent expanded={expanded} />
  ) : (
    <Logo expanded={expanded} />
  );

  const renderItems = (items: MenuItem[]) => {
    return items.map((item) => (
      <SidebarMenuItem
        key={item.id}
        item={item}
        expanded={expanded}
        count={countDataMap[item.count!]}
      />
    ));
  };

  // renderBottomItems function removed - bottom section no longer needed

  const renderGroups = (groups: MenuItemGroup[]) => {
    return groups.map((group) => {
      return (
        <li key={group.id} className={cn(expanded && "mb-1")}>
          <div>
            {group.label && expanded && (
              <div className="comet-body-s truncate pb-1 pl-2.5 pr-3 pt-3 text-light-slate">
                {group.label}
              </div>
            )}

            <ul>{renderItems(group.items)}</ul>
          </div>
        </li>
      );
    });
  };

  const renderExpandCollapseButton = () => {
    return (
      <Button
        variant="outline"
        size="icon-2xs"
        onClick={() => setExpanded((s) => !s)}
        className={cn(
          "absolute -right-3 top-2 hidden rounded-full z-50 lg:group-hover:flex",
        )}
      >
        {expanded ? <ChevronLeft /> : <ChevronRight />}
      </Button>
    );
  };

  // 渲染用户信息区域
  const renderUserSection = () => {
    if (!authEnabled || !currentUser) {
      return null;
    }

    return (
      <div className="mt-auto border-t pt-3">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              className={cn(
                "flex w-full items-center gap-3 rounded-md px-2 py-2 text-left transition-colors hover:bg-muted",
                !expanded && "justify-center"
              )}
            >
              <Avatar className="h-8 w-8 shrink-0">
                <AvatarImage src={currentUser.avatarUrl} />
                <AvatarFallback className="text-xs">
                  {getInitials(currentUser.fullName, currentUser.email)}
                </AvatarFallback>
              </Avatar>
              {expanded && (
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">
                    {currentUser.fullName || currentUser.username}
                  </div>
                  <div className="truncate text-xs text-muted-foreground">
                    {currentUser.email}
                  </div>
                </div>
              )}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align={expanded ? "end" : "center"} className="w-56">
            <div className="px-2 py-1.5">
              <div className="text-sm font-medium">
                {currentUser.fullName || currentUser.username}
              </div>
              <div className="text-xs text-muted-foreground">
                {currentUser.email}
              </div>
            </div>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => navigate({ to: `/${workspaceName}/settings/profile` })}
            >
              <Settings className="mr-2 h-4 w-4" />
              {t("navigation.settings", "设置")}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={handleLogout}
              className="text-destructive focus:text-destructive"
            >
              <LogOut className="mr-2 h-4 w-4" />
              {t("auth.logout", "登出")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    );
  };

  return (
    <>
      <aside className="comet-sidebar-width group h-[calc(100vh-var(--banner-height))] border-r transition-all">
        <div className="comet-header-height relative flex w-full items-center justify-between gap-6 border-b">
          <Link
            to={HOME_PATH}
            className="absolute left-[18px] z-10 block"
            params={{ workspaceName }}
          >
            {logo}
          </Link>
        </div>
        <div className="relative flex h-[calc(100%-var(--header-height))]">
          {renderExpandCollapseButton()}
          <div className="flex min-h-0 grow flex-col overflow-auto px-3 py-4">
            {/* 工作空间选择器 - 仅在认证模式下显示 */}
            {authEnabled && (
              <div className="mb-4">
                <WorkspaceSelector expanded={expanded} />
              </div>
            )}
            <ul className="flex flex-col gap-1 pb-2">
              {renderGroups(MENU_ITEMS)}
            </ul>
            {/* 用户信息和登出区域 */}
            {renderUserSection()}
          </div>
        </div>
      </aside>
    </>
  );
};

export default SideBar;
