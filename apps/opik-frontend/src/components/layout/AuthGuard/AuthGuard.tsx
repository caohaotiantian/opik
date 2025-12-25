import { Navigate, Outlet, useLocation } from "@tanstack/react-router";
import { useCurrentUser as useCurrentUserQuery } from "@/api/auth";
import useAuthStore, { useIsAuthenticated, useAuthEnabled } from "@/store/AuthStore";
import Loader from "@/components/shared/Loader/Loader";
import { useEffect } from "react";

/**
 * 认证守卫组件
 * 保护需要登录才能访问的路由
 */
const AuthGuard = () => {
  const location = useLocation();
  const isAuthenticated = useIsAuthenticated();
  const authEnabled = useAuthEnabled();
  const { loginSuccess, setAuthEnabled } = useAuthStore();

  // 获取当前用户信息
  const { data, isLoading, isError, error } = useCurrentUserQuery({
    enabled: authEnabled,
    retry: false,
  });

  // 更新认证状态
  useEffect(() => {
    if (data) {
      loginSuccess(data.user, data.workspaces, data.defaultWorkspaceId);
    }
  }, [data, loginSuccess]);

  // 如果认证未启用（向后兼容模式）
  if (!authEnabled) {
    return <Outlet />;
  }

  // 加载中
  if (isLoading) {
    return (
      <div className="flex h-screen w-full items-center justify-center">
        <Loader />
      </div>
    );
  }

  // 认证失败，检查是否是401错误（未认证）
  if (isError) {
    // 检查是否是认证未启用的后端
    const axiosError = error as { response?: { status: number } };
    if (axiosError?.response?.status === 404) {
      // 认证端点不存在，说明后端未启用认证
      setAuthEnabled(false);
      return <Outlet />;
    }

    // 401 或其他错误，重定向到登录页
    return (
      <Navigate
        to="/login"
        search={{ redirect: location.pathname }}
        replace
      />
    );
  }

  // 未认证，重定向到登录页
  if (!isAuthenticated && !data) {
    return (
      <Navigate
        to="/login"
        search={{ redirect: location.pathname }}
        replace
      />
    );
  }

  return <Outlet />;
};

export default AuthGuard;

