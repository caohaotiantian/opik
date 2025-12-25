import { Outlet, Link, useLocation, Navigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import {
  Users,
  FileText,
  Settings,
  Shield,
  ChevronLeft,
  Home,
} from "lucide-react";
import { Suspense } from "react";

import { useIsSystemAdmin, useIsAuthenticated, useCurrentUser } from "@/store/AuthStore";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import Loader from "@/components/shared/Loader/Loader";

/**
 * 管理员页面布局
 * 包含侧边导航栏和内容区域
 */
const AdminLayout = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const isAuthenticated = useIsAuthenticated();
  const isSystemAdmin = useIsSystemAdmin();
  const currentUser = useCurrentUser();

  // 未认证，重定向到登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 非管理员，显示无权限提示
  if (!isSystemAdmin) {
    return (
      <div className="flex h-screen w-full flex-col items-center justify-center gap-4 bg-background">
        <Shield className="h-16 w-16 text-muted-foreground" />
        <h1 className="text-2xl font-bold">{t("admin.accessDenied", "访问被拒绝")}</h1>
        <p className="text-muted-foreground">
          {t("admin.noPermission", "您没有权限访问管理员页面")}
        </p>
        <Button asChild>
          <Link to="/">
            <Home className="mr-2 h-4 w-4" />
            {t("admin.backToHome", "返回首页")}
          </Link>
        </Button>
      </div>
    );
  }

  const navItems = [
    {
      path: "/admin/users",
      icon: Users,
      label: t("admin.nav.users", "用户管理"),
    },
    {
      path: "/admin/audit-logs",
      icon: FileText,
      label: t("admin.nav.auditLogs", "审计日志"),
    },
  ];

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

  return (
    <div className="flex h-screen bg-background">
      {/* 侧边栏 */}
      <aside className="flex w-64 flex-col border-r bg-card">
        {/* Logo 区域 */}
        <div className="flex h-16 items-center border-b px-6">
          <Link to="/" className="flex items-center gap-2">
            <ChevronLeft className="h-5 w-5 text-muted-foreground" />
            <span className="text-lg font-semibold">Opik</span>
          </Link>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 space-y-1 p-4">
          <div className="mb-4">
            <span className="px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {t("admin.nav.title", "管理中心")}
            </span>
          </div>
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                )}
              >
                <Icon className="h-5 w-5" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* 用户信息 */}
        <div className="border-t p-4">
          <div className="flex items-center gap-3">
            <Avatar className="h-9 w-9">
              <AvatarImage src={currentUser?.avatarUrl} />
              <AvatarFallback>
                {getInitials(currentUser?.fullName, currentUser?.email)}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 overflow-hidden">
              <p className="truncate text-sm font-medium">
                {currentUser?.fullName || currentUser?.username}
              </p>
              <p className="truncate text-xs text-muted-foreground">
                {t("admin.systemAdmin", "系统管理员")}
              </p>
            </div>
          </div>
        </div>
      </aside>

      {/* 主内容区域 */}
      <main className="flex-1 overflow-auto">
        <div className="container max-w-7xl py-8">
          <Suspense fallback={<Loader />}>
            <Outlet />
          </Suspense>
        </div>
      </main>
    </div>
  );
};

export default AdminLayout;

