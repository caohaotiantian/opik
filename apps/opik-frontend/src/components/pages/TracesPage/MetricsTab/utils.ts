import { TFunction } from "i18next";
import { INTERVAL_TYPE } from "@/api/projects/useProjectMetric";
import { ChartTooltipRenderValueArguments } from "@/components/shared/ChartTooltipContent/ChartTooltipContent";
import { formatDuration } from "@/lib/date";

/**
 * Get duration labels for percentile charts
 */
export const getDurationLabels = (t: TFunction) => ({
  "duration.p50": t("metrics.percentiles.p50"),
  "duration.p90": t("metrics.percentiles.p90"),
  "duration.p99": t("metrics.percentiles.p99"),
});

/**
 * Get interval descriptions for different chart types and intervals
 */
export const getIntervalDescriptions = (t: TFunction) => ({
  TOTALS: {
    [INTERVAL_TYPE.HOURLY]: t("metrics.intervals.hourlyTotals"),
    [INTERVAL_TYPE.DAILY]: t("metrics.intervals.dailyTotals"),
    [INTERVAL_TYPE.WEEKLY]: t("metrics.intervals.weeklyTotals"),
  },
  AVERAGES: {
    [INTERVAL_TYPE.HOURLY]: t("metrics.intervals.hourlyAverages"),
    [INTERVAL_TYPE.DAILY]: t("metrics.intervals.dailyAverages"),
    [INTERVAL_TYPE.WEEKLY]: t("metrics.intervals.weeklyAverages"),
  },
  QUANTILES: {
    [INTERVAL_TYPE.HOURLY]: t("metrics.intervals.hourlyQuantiles"),
    [INTERVAL_TYPE.DAILY]: t("metrics.intervals.dailyQuantiles"),
    [INTERVAL_TYPE.WEEKLY]: t("metrics.intervals.weeklyQuantiles"),
  },
  COST: {
    [INTERVAL_TYPE.HOURLY]: t("metrics.intervals.hourlyCost"),
    [INTERVAL_TYPE.DAILY]: t("metrics.intervals.dailyCost"),
    [INTERVAL_TYPE.WEEKLY]: t("metrics.intervals.weeklyCost"),
  },
});

/**
 * Renders duration tooltip values in formatted duration string
 */
export const renderDurationTooltipValue = ({
  value,
}: ChartTooltipRenderValueArguments) => formatDuration(value as number, false);

/**
 * Formats Y-axis tick values for duration charts
 */
export const durationYTickFormatter = (value: number) =>
  formatDuration(value, false);
