import React from "react";
import { Link } from "@tanstack/react-router";
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
} from "lucide-react";
import { keepPreviousData } from "@tanstack/react-query";

import useAppStore from "@/store/AppStore";
import useProjectsList from "@/api/projects/useProjectsList";
import useDatasetsList from "@/api/datasets/useDatasetsList";
import useExperimentsList from "@/api/datasets/useExperimentsList";
import useRulesList from "@/api/automations/useRulesList";
import useOptimizationsList from "@/api/optimizations/useOptimizationsList";
import { OnChangeFn } from "@/types/shared";
import { Button } from "@/components/ui/button";
// import { Separator } from "@/components/ui/separator"; // Removed - no longer needed
import { cn } from "@/lib/utils"; // buildDocsUrl removed - no longer needed
import Logo from "@/components/layout/Logo/Logo";
import usePluginsStore from "@/store/PluginsStore";
// import ProvideFeedbackDialog from "@/components/layout/SideBar/FeedbackDialog/ProvideFeedbackDialog"; // Removed - no longer needed
import usePromptsList from "@/api/prompts/usePromptsList";
import useAnnotationQueuesList from "@/api/annotation-queues/useAnnotationQueuesList";
// import { useOpenQuickStartDialog } from "@/components/pages-shared/onboarding/QuickstartDialog/QuickstartDialog"; // Removed - no longer needed
// import GitHubStarListItem from "@/components/layout/SideBar/GitHubStarListItem/GitHubStarListItem"; // Removed - no longer needed
import SidebarMenuItem, {
  MENU_ITEM_TYPE,
  MenuItem,
  MenuItemGroup,
} from "@/components/layout/SideBar/MenuItem/SidebarMenuItem";

const HOME_PATH = "/$workspaceName/home";

const useMenuItems = (): MenuItemGroup[] => {
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

type SideBarProps = {
  expanded: boolean;
  setExpanded: OnChangeFn<boolean | undefined>;
};

const SideBar: React.FunctionComponent<SideBarProps> = ({
  expanded,
  setExpanded,
}) => {
  const { t } = useTranslation();
  // const [openProvideFeedback, setOpenProvideFeedback] = useState(false); // Removed - no longer needed
  // const { open: openQuickstart } = useOpenQuickStartDialog(); // Removed - no longer needed

  const { activeWorkspaceName: workspaceName } = useAppStore();
  const LogoComponent = usePluginsStore((state) => state.Logo);
  const SidebarInviteDevButton = usePluginsStore(
    (state) => state.SidebarInviteDevButton,
  );
  
  const MENU_ITEMS = useMenuItems();

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
            <ul className="flex flex-col gap-1 pb-2">
              {renderGroups(MENU_ITEMS)}
            </ul>
            {/* Bottom section with Separator, GitHub Star, Documentation, Quickstart Guide, and Provide Feedback removed as per user request */}
          </div>
        </div>
      </aside>

      {/* ProvideFeedbackDialog removed as per user request */}
    </>
  );
};

export default SideBar;
