import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { TFunction } from "i18next";
import { JsonParam, useQueryParam } from "use-query-params";

import {
  COLUMN_FEEDBACK_SCORES_ID,
  COLUMN_ID_ID,
  COLUMN_TYPE,
  ColumnData,
} from "@/types/shared";
import { Thread } from "@/types/traces";
import { ThreadStatus } from "@/types/thread";
import {
  METRIC_NAME_TYPE,
  INTERVAL_TYPE,
} from "@/api/projects/useProjectMetric";
import FiltersButton from "@/components/shared/FiltersButton/FiltersButton";
import ThreadsFeedbackScoresSelect from "@/components/pages-shared/traces/TracesOrSpansFeedbackScoresSelect/ThreadsFeedbackScoresSelect";

import MetricContainerChart from "./MetricChart/MetricChartContainer";
import {
  getDurationLabels,
  getIntervalDescriptions,
  renderDurationTooltipValue,
  durationYTickFormatter,
} from "./utils";

const getThreadFilterColumns = (t: TFunction): ColumnData<Thread>[] => [
  {
    id: COLUMN_ID_ID,
    label: t("threads.columns.id"),
    type: COLUMN_TYPE.string,
  },
  {
    id: "first_message",
    label: t("threads.columns.firstMessage"),
    type: COLUMN_TYPE.string,
  },
  {
    id: "last_message",
    label: t("threads.columns.lastMessage"),
    type: COLUMN_TYPE.string,
  },
  {
    id: "number_of_messages",
    label: t("threads.columns.messageCount"),
    type: COLUMN_TYPE.number,
  },
  {
    id: "status",
    label: t("threads.columns.status"),
    type: COLUMN_TYPE.category,
  },
  {
    id: "created_at",
    label: t("threads.columns.createdAt"),
    type: COLUMN_TYPE.time,
  },
  {
    id: "last_updated_at",
    label: t("threads.columns.lastUpdated"),
    type: COLUMN_TYPE.time,
  },
  {
    id: "duration",
    label: t("threads.columns.duration"),
    type: COLUMN_TYPE.duration,
  },
  {
    id: "tags",
    label: t("threads.columns.tags"),
    type: COLUMN_TYPE.list,
  },
  {
    id: "start_time",
    label: t("threads.columns.startTime"),
    type: COLUMN_TYPE.time,
  },
  {
    id: "end_time",
    label: t("threads.columns.endTime"),
    type: COLUMN_TYPE.time,
  },
  {
    id: COLUMN_FEEDBACK_SCORES_ID,
    label: t("threads.columns.feedbackScores"),
    type: COLUMN_TYPE.numberDictionary,
  },
];

interface ThreadMetricsSectionProps {
  projectId: string;
  interval: INTERVAL_TYPE;
  intervalStart: string;
  intervalEnd: string;
  hasThreads: boolean;
}

const ThreadMetricsSection: React.FC<ThreadMetricsSectionProps> = ({
  projectId,
  interval,
  intervalStart,
  intervalEnd,
  hasThreads,
}) => {
  const { t } = useTranslation();
  const intervalDescriptions = useMemo(() => getIntervalDescriptions(t), [t]);
  const durationLabels = useMemo(() => getDurationLabels(t), [t]);
  const THREAD_FILTER_COLUMNS = useMemo(() => getThreadFilterColumns(t), [t]);
  const [threadFilters = [], setThreadFilters] = useQueryParam(
    "threads_metrics_filters",
    JsonParam,
    {
      updateType: "replaceIn",
    },
  );

  const threadFiltersConfig = useMemo(
    () => ({
      rowsMap: {
        [COLUMN_FEEDBACK_SCORES_ID]: {
          keyComponent: ThreadsFeedbackScoresSelect as React.FC<unknown> & {
            placeholder: string;
            value: string;
            onValueChange: (value: string) => void;
          },
          keyComponentProps: {
            projectId,
            placeholder: "Select score",
          },
        },
        status: {
          keyComponentProps: {
            options: [
              { value: ThreadStatus.INACTIVE, label: "Inactive" },
              { value: ThreadStatus.ACTIVE, label: "Active" },
            ],
            placeholder: "Select value",
          },
        },
      },
    }),
    [projectId],
  );

  if (!hasThreads) {
    return null;
  }

  return (
    <div className="pt-6">
      <div className="sticky top-0 z-10 flex items-center justify-between bg-soft-background pb-3 pt-2">
        <h2 className="comet-title-s truncate break-words">{t("metrics.threadMetrics")}</h2>
        <FiltersButton
          columns={THREAD_FILTER_COLUMNS}
          filters={threadFilters}
          onChange={setThreadFilters}
          config={threadFiltersConfig}
        />
      </div>
      <div
        className="grid grid-cols-1 gap-4 md:grid-cols-2"
        style={{ "--chart-height": "230px" } as React.CSSProperties}
      >
        <div>
          <MetricContainerChart
            chartId="threads_feedback_scores_chart"
            key="threads_feedback_scores_chart"
            name={t("metrics.threadsFeedbackScores")}
            description={intervalDescriptions.AVERAGES[interval]}
            metricName={METRIC_NAME_TYPE.THREAD_FEEDBACK_SCORES}
            interval={interval}
            intervalStart={intervalStart}
            intervalEnd={intervalEnd}
            projectId={projectId}
            chartType="line"
            threadFilters={threadFilters}
          />
        </div>
        <div>
          <MetricContainerChart
            chartId="number_of_thread_chart"
            key="number_of_thread_chart"
            name={t("metrics.numberOfThreads")}
            description={intervalDescriptions.TOTALS[interval]}
            metricName={METRIC_NAME_TYPE.THREAD_COUNT}
            interval={interval}
            intervalStart={intervalStart}
            intervalEnd={intervalEnd}
            projectId={projectId}
            chartType="line"
            threadFilters={threadFilters}
          />
        </div>
        <div className="md:col-span-2">
          <MetricContainerChart
            chartId="thread_duration_chart"
            key="thread_duration_chart"
            name={t("metrics.threadDuration")}
            description={intervalDescriptions.QUANTILES[interval]}
            metricName={METRIC_NAME_TYPE.THREAD_DURATION}
            interval={interval}
            intervalStart={intervalStart}
            intervalEnd={intervalEnd}
            projectId={projectId}
            renderValue={renderDurationTooltipValue}
            labelsMap={durationLabels}
            customYTickFormatter={durationYTickFormatter}
            chartType="line"
            threadFilters={threadFilters}
          />
        </div>
      </div>
    </div>
  );
};

export default ThreadMetricsSection;
