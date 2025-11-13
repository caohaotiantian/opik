import React from "react";
import { useTranslation } from "react-i18next";

import SliderInputControl from "@/components/shared/SliderInputControl/SliderInputControl";
import PromptModelSettingsTooltipContent from "@/components/pages-shared/llm/PromptModelSettings/providerConfigs/PromptModelConfigsTooltipContent";
import {
  LLMOpenAIConfigsType,
  PROVIDER_MODEL_TYPE,
  ReasoningEffort,
} from "@/types/providers";
import { DEFAULT_OPEN_AI_CONFIGS } from "@/constants/llm";
import { isReasoningModel } from "@/lib/modelUtils";
import isUndefined from "lodash/isUndefined";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import ExplainerIcon from "@/components/shared/ExplainerIcon/ExplainerIcon";

interface OpenAIModelSettingsProps {
  configs: Partial<LLMOpenAIConfigsType>;
  model?: PROVIDER_MODEL_TYPE | "";
  onChange: (configs: Partial<LLMOpenAIConfigsType>) => void;
}

const OpenAIModelConfigs = ({
  configs,
  model,
  onChange,
}: OpenAIModelSettingsProps) => {
  const { t } = useTranslation();
  // Reasoning models (GPT-5, O1, O3, O4-mini) require temperature = 1.0
  const isReasoning = isReasoningModel(model);

  return (
    <div className="flex w-72 flex-col gap-6">
      {!isUndefined(configs.temperature) && (
        <SliderInputControl
          value={configs.temperature}
          onChange={(v) => onChange({ temperature: v })}
          id="temperature"
          min={isReasoning ? 1 : 0}
          max={1}
          step={0.01}
          defaultValue={isReasoning ? 1 : DEFAULT_OPEN_AI_CONFIGS.TEMPERATURE}
          label={t("modelConfig.temperature")}
          tooltip={
            <PromptModelSettingsTooltipContent
              text={
                isReasoning
                  ? t("modelConfig.temperatureReasoningTooltip")
                  : t("modelConfig.temperatureTooltip")
              }
            />
          }
        />
      )}

      {!isUndefined(configs.maxCompletionTokens) && (
        <SliderInputControl
          value={configs.maxCompletionTokens}
          onChange={(v) => onChange({ maxCompletionTokens: v })}
          id="maxCompletionTokens"
          min={0}
          max={128000}
          step={1}
          defaultValue={DEFAULT_OPEN_AI_CONFIGS.MAX_COMPLETION_TOKENS}
          label={t("modelConfig.maxOutputTokens")}
          tooltip={
            <PromptModelSettingsTooltipContent text={t("modelConfig.maxOutputTokensTooltip")} />
          }
        />
      )}

      {!isUndefined(configs.topP) && (
        <SliderInputControl
          value={configs.topP}
          onChange={(v) => onChange({ topP: v })}
          id="topP"
          min={0}
          max={1}
          step={0.01}
          defaultValue={DEFAULT_OPEN_AI_CONFIGS.TOP_P}
          label={t("modelConfig.topP")}
          tooltip={
            <PromptModelSettingsTooltipContent text={t("modelConfig.topPTooltip")} />
          }
        />
      )}

      {!isUndefined(configs.frequencyPenalty) && (
        <SliderInputControl
          value={configs.frequencyPenalty}
          onChange={(v) => onChange({ frequencyPenalty: v })}
          id="frequencyPenalty"
          min={0}
          max={1}
          step={0.01}
          defaultValue={DEFAULT_OPEN_AI_CONFIGS.FREQUENCY_PENALTY}
          label={t("modelConfig.frequencyPenalty")}
          tooltip={
            <PromptModelSettingsTooltipContent text={t("modelConfig.frequencyPenaltyTooltip")} />
          }
        />
      )}

      {!isUndefined(configs.presencePenalty) && (
        <SliderInputControl
          value={configs.presencePenalty}
          onChange={(v) => onChange({ presencePenalty: v })}
          id="presencePenalty"
          min={0}
          max={1}
          step={0.01}
          defaultValue={DEFAULT_OPEN_AI_CONFIGS.PRESENCE_PENALTY}
          label={t("modelConfig.presencePenalty")}
          tooltip={
            <PromptModelSettingsTooltipContent text={t("modelConfig.presencePenaltyTooltip")} />
          }
        />
      )}

      {isReasoning && (
        <div className="space-y-2">
          <div className="flex items-center space-x-2">
            <Label htmlFor="reasoningEffort" className="text-sm font-medium">
              {t("modelConfig.reasoningEffort")}
            </Label>
            <ExplainerIcon description={t("modelConfig.reasoningEffortTooltip")} />
          </div>
          <Select
            value={configs.reasoningEffort || "medium"}
            onValueChange={(value: ReasoningEffort) =>
              onChange({ reasoningEffort: value })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder={t("modelConfig.selectReasoningEffort")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="minimal">{t("modelConfig.minimal")}</SelectItem>
              <SelectItem value="low">{t("modelConfig.low")}</SelectItem>
              <SelectItem value="medium">{t("modelConfig.medium")}</SelectItem>
              <SelectItem value="high">{t("modelConfig.high")}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      )}
    </div>
  );
};

export default OpenAIModelConfigs;
