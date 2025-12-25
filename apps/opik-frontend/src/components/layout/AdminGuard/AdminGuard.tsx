import { Navigate, Outlet } from "@tanstack/react-router";
import { useIsSystemAdmin, useIsAuthenticated } from "@/store/AuthStore";

/**
 * 管理员守卫组件
 * 保护只有系统管理员才能访问的路由
 */
const AdminGuard = () => {
  const isAuthenticated = useIsAuthenticated();
  const isSystemAdmin = useIsSystemAdmin();

  // 未认证，重定向到登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 非管理员，显示无权限页面或重定向
  if (!isSystemAdmin) {
    return (
      <div className="flex h-screen w-full flex-col items-center justify-center gap-4">
        <h1 className="text-2xl font-bold text-destructive">访问被拒绝</h1>
        <p className="text-muted-foreground">您没有权限访问此页面</p>
        <a href="/" className="text-primary hover:underline">
          返回首页
        </a>
      </div>
    );
  }

  return <Outlet />;
};

export default AdminGuard;

