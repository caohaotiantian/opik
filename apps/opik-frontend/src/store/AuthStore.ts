import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User, WorkspaceInfo } from "@/api/auth/types";
import axiosInstance from "@/api/api";

/**
 * 认证状态管理
 * 管理用户登录状态、当前用户信息、工作空间列表等
 */

// 同步设置工作空间 Header，确保所有 API 请求都携带正确的工作空间信息
const setWorkspaceHeader = (workspaceName: string) => {
  if (workspaceName) {
    axiosInstance.defaults.headers.common["Comet-Workspace"] = workspaceName;
  } else {
    delete axiosInstance.defaults.headers.common["Comet-Workspace"];
  }
};

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
  loginSuccess: (
    user: User,
    workspaces: WorkspaceInfo[],
    defaultWorkspaceId: string,
  ) => void;
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
  authEnabled: false,
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
        set((state) => {
          // 找到对应的工作空间并设置 Header
          const workspace = state.workspaces.find((w) => w.id === workspaceId);
          if (workspace) {
            setWorkspaceHeader(workspace.name);
          }
          return { currentWorkspaceId: workspaceId };
        }),

      setDefaultWorkspaceId: (workspaceId) =>
        set({ defaultWorkspaceId: workspaceId }),

      setAuthEnabled: (enabled) => set({ authEnabled: enabled }),

      loginSuccess: (user, workspaces, defaultWorkspaceId) => {
        // 立即设置工作空间 Header，确保后续 API 请求能正确携带工作空间信息
        const defaultWorkspace = workspaces.find(
          (w) => w.id === defaultWorkspaceId,
        );
        if (defaultWorkspace) {
          setWorkspaceHeader(defaultWorkspace.name);
        } else if (workspaces.length > 0) {
          setWorkspaceHeader(workspaces[0].name);
        }

        return set({
          isAuthenticated: true,
          currentUser: user,
          workspaces,
          defaultWorkspaceId,
          currentWorkspaceId: defaultWorkspaceId,
        });
      },

      logout: () => {
        // 清除工作空间 Header
        setWorkspaceHeader("");
        return set({
          isAuthenticated: false,
          currentUser: null,
          workspaces: [],
          currentWorkspaceId: null,
        });
      },

      reset: () => {
        // 清除工作空间 Header
        setWorkspaceHeader("");
        return set(initialState);
      },
    }),
    {
      name: "opik-auth-storage",
      partialize: (state) => ({
        // 持久化认证相关状态，确保页面刷新后能正确恢复
        currentWorkspaceId: state.currentWorkspaceId,
        defaultWorkspaceId: state.defaultWorkspaceId,
        workspaces: state.workspaces,
        isAuthenticated: state.isAuthenticated,
        currentUser: state.currentUser,
      }),
      // 在恢复状态后立即设置 workspace header
      onRehydrateStorage: () => (state) => {
        if (state && state.workspaces && state.currentWorkspaceId) {
          const workspace = state.workspaces.find(
            (w) => w.id === state.currentWorkspaceId,
          );
          if (workspace) {
            setWorkspaceHeader(workspace.name);
          }
        }
      },
    },
  ),
);

// 选择器 hooks
export const useIsAuthenticated = () =>
  useAuthStore((state) => state.isAuthenticated);

export const useCurrentUser = () => useAuthStore((state) => state.currentUser);

export const useWorkspaces = () => useAuthStore((state) => state.workspaces);

export const useCurrentWorkspaceId = () =>
  useAuthStore((state) => state.currentWorkspaceId);

export const useDefaultWorkspaceId = () =>
  useAuthStore((state) => state.defaultWorkspaceId);

export const useAuthEnabled = () => useAuthStore((state) => state.authEnabled);

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
