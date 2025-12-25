import React, { useEffect } from "react";
import useAppStore from "@/store/AppStore";
import { DEFAULT_WORKSPACE_NAME } from "@/constants/user";
import { useWorkspaceNameFromURL } from "@/hooks/useWorkspaceNameFromURL";
import { Navigate } from "@tanstack/react-router";
import { useAuthEnabled, useWorkspaces, useCurrentWorkspaceId } from "@/store/AuthStore";

type WorkspacePreloaderProps = {
  children: React.ReactNode;
};

const WorkspacePreloader: React.FunctionComponent<WorkspacePreloaderProps> = ({
  children,
}) => {
  const setActiveWorkspaceName = useAppStore(
    (state) => state.setActiveWorkspaceName,
  );
  const workspaceNameFromURL = useWorkspaceNameFromURL();
  const authEnabled = useAuthEnabled();
  const workspaces = useWorkspaces();
  const currentWorkspaceId = useCurrentWorkspaceId();

  useEffect(() => {
    if (authEnabled) {
      // In authenticated mode, use the user's current workspace
      const currentWorkspace = workspaces.find(w => w.id === currentWorkspaceId);
      if (currentWorkspace) {
        setActiveWorkspaceName(currentWorkspace.name);
      } else if (workspaces.length > 0) {
        // Fall back to first available workspace
        setActiveWorkspaceName(workspaces[0].name);
      }
    } else {
      // Backward compatibility: use default workspace when auth is disabled
      setActiveWorkspaceName(DEFAULT_WORKSPACE_NAME);
    }
  }, [setActiveWorkspaceName, authEnabled, workspaces, currentWorkspaceId]);

  // In authenticated mode, redirect to user's workspace if URL doesn't match
  if (authEnabled) {
    const currentWorkspace = workspaces.find(w => w.id === currentWorkspaceId);
    const targetWorkspaceName = currentWorkspace?.name || workspaces[0]?.name;
    
    if (targetWorkspaceName && workspaceNameFromURL && workspaceNameFromURL !== targetWorkspaceName) {
      return (
        <Navigate
          to="/$workspaceName"
          params={{ workspaceName: targetWorkspaceName }}
        />
      );
    }
  } else {
    // Backward compatibility mode
    if (workspaceNameFromURL && workspaceNameFromURL !== DEFAULT_WORKSPACE_NAME) {
      return (
        <Navigate
          to="/$workspaceName"
          params={{ workspaceName: DEFAULT_WORKSPACE_NAME }}
        />
      );
    }
  }

  return children;
};

export default WorkspacePreloader;
