import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User, WorkspaceInfo } from "@/api/auth/types";

/**
 * 认证状态管理
 * 管理用户登录状态、当前用户信息、工作空间列表等
 */

interface AuthState {
  // 是否已认证
  isAuthenticated: boolean;
  // 当前用户信息
  currentUser: User | null;
  // 用户的工作空间列表
  workspaces: WorkspaceInfo[];
  // 当前选中的工作空间ID
  currentWorkspaceId: string | null;
  // 默认工作空间ID
  defaultWorkspaceId: string | null;
  // 认证模式是否启用
  authEnabled: boolean;
}

interface AuthActions {
  // 设置认证状态
  setAuthenticated: (authenticated: boolean) => void;
  // 设置当前用户
  setCurrentUser: (user: User | null) => void;
  // 设置工作空间列表
  setWorkspaces: (workspaces: WorkspaceInfo[]) => void;
  // 设置当前工作空间
  setCurrentWorkspaceId: (workspaceId: string | null) => void;
  // 设置默认工作空间
  setDefaultWorkspaceId: (workspaceId: string | null) => void;
  // 设置认证模式
  setAuthEnabled: (enabled: boolean) => void;
  // 登录成功后设置状态
  loginSuccess: (user: User, workspaces: WorkspaceInfo[], defaultWorkspaceId: string) => void;
  // 登出
  logout: () => void;
  // 重置状态
  reset: () => void;
}

type AuthStore = AuthState & AuthActions;

const initialState: AuthState = {
  isAuthenticated: false,
  currentUser: null,
  workspaces: [],
  currentWorkspaceId: null,
  defaultWorkspaceId: null,
  // 默认启用认证模式，只有当后端明确返回认证未启用时才禁用
  authEnabled: true,
};

const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      ...initialState,

      setAuthenticated: (authenticated) =>
        set({ isAuthenticated: authenticated }),

      setCurrentUser: (user) => set({ currentUser: user }),

      setWorkspaces: (workspaces) => set({ workspaces }),

      setCurrentWorkspaceId: (workspaceId) =>
        set({ currentWorkspaceId: workspaceId }),

      setDefaultWorkspaceId: (workspaceId) =>
        set({ defaultWorkspaceId: workspaceId }),

      setAuthEnabled: (enabled) => set({ authEnabled: enabled }),

      loginSuccess: (user, workspaces, defaultWorkspaceId) =>
        set({
          isAuthenticated: true,
          currentUser: user,
          workspaces,
          defaultWorkspaceId,
          currentWorkspaceId: defaultWorkspaceId,
        }),

      logout: () =>
        set({
          isAuthenticated: false,
          currentUser: null,
          workspaces: [],
          currentWorkspaceId: null,
        }),

      reset: () => set(initialState),
    }),
    {
      name: "opik-auth-storage",
      partialize: (state) => ({
        // 只持久化部分状态
        currentWorkspaceId: state.currentWorkspaceId,
        defaultWorkspaceId: state.defaultWorkspaceId,
      }),
    },
  ),
);

// 选择器 hooks
export const useIsAuthenticated = () =>
  useAuthStore((state) => state.isAuthenticated);

export const useCurrentUser = () =>
  useAuthStore((state) => state.currentUser);

export const useWorkspaces = () =>
  useAuthStore((state) => state.workspaces);

export const useCurrentWorkspaceId = () =>
  useAuthStore((state) => state.currentWorkspaceId);

export const useDefaultWorkspaceId = () =>
  useAuthStore((state) => state.defaultWorkspaceId);

export const useAuthEnabled = () =>
  useAuthStore((state) => state.authEnabled);

export const useIsSystemAdmin = () =>
  useAuthStore((state) => state.currentUser?.systemAdmin ?? false);

// 获取当前工作空间信息
export const useCurrentWorkspace = () =>
  useAuthStore((state) => {
    const { workspaces, currentWorkspaceId } = state;
    return workspaces.find((w) => w.id === currentWorkspaceId) ?? null;
  });

// 获取当前用户在当前工作空间的角色
export const useCurrentWorkspaceRole = () =>
  useAuthStore((state) => {
    const { workspaces, currentWorkspaceId } = state;
    const workspace = workspaces.find((w) => w.id === currentWorkspaceId);
    return workspace?.role ?? null;
  });

export default useAuthStore;

