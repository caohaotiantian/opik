import React from "react";
import { useNavigate } from "@tanstack/react-router";
import { Check, ChevronsUpDown, Building2 } from "lucide-react";
import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
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
import useAuthStore, { useWorkspaces, useCurrentWorkspaceId } from "@/store/AuthStore";
import useAppStore from "@/store/AppStore";

interface WorkspaceSelectorProps {
  expanded?: boolean;
}

const WorkspaceSelector: React.FC<WorkspaceSelectorProps> = ({ expanded = true }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [open, setOpen] = React.useState(false);
  
  const workspaces = useWorkspaces();
  const currentWorkspaceId = useCurrentWorkspaceId();
  const { setCurrentWorkspaceId } = useAuthStore();
  const setActiveWorkspaceName = useAppStore((state) => state.setActiveWorkspaceName);
  
  const currentWorkspace = workspaces.find((w) => w.id === currentWorkspaceId);
  
  // Don't show selector if there's only one workspace
  if (workspaces.length <= 1) {
    return null;
  }
  
  const handleSelectWorkspace = (workspaceId: string) => {
    const workspace = workspaces.find((w) => w.id === workspaceId);
    if (workspace) {
      setCurrentWorkspaceId(workspaceId);
      setActiveWorkspaceName(workspace.name);
      navigate({ to: `/${workspace.name}/home` });
    }
    setOpen(false);
  };
  
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(
            "w-full justify-between",
            !expanded && "px-2"
          )}
        >
          <div className="flex items-center gap-2 truncate">
            <Building2 className="h-4 w-4 shrink-0" />
            {expanded && (
              <span className="truncate">
                {currentWorkspace?.displayName || currentWorkspace?.name || t("workspace.select", "选择工作空间")}
              </span>
            )}
          </div>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[250px] p-0" align="start">
        <Command>
          <CommandInput placeholder={t("workspace.searchPlaceholder", "搜索工作空间...")} />
          <CommandList>
            <CommandEmpty>{t("workspace.notFound", "未找到工作空间")}</CommandEmpty>
            <CommandGroup heading={t("workspace.workspaces", "工作空间")}>
              {workspaces.map((workspace) => (
                <CommandItem
                  key={workspace.id}
                  value={workspace.name}
                  onSelect={() => handleSelectWorkspace(workspace.id)}
                  className="cursor-pointer"
                >
                  <Check
                    className={cn(
                      "mr-2 h-4 w-4",
                      currentWorkspaceId === workspace.id ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <div className="flex flex-col">
                    <span>{workspace.displayName || workspace.name}</span>
                    {workspace.role && (
                      <span className="text-xs text-muted-foreground">
                        {workspace.role}
                      </span>
                    )}
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};

export default WorkspaceSelector;

