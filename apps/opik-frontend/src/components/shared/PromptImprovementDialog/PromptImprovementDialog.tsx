import React, {
  useState,
  useCallback,
  useEffect,
  useMemo,
  useRef,
} from "react";
import { useTranslation } from "react-i18next";
import { Wand2, Loader2, Play, ChevronRight, Sparkles } from "lucide-react";
import CodeMirror from "@uiw/react-codemirror";
import { EditorView } from "@codemirror/view";

import {
  Dialog,
  DialogAutoScrollBody,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Description } from "@/components/ui/description";
import { Separator } from "@/components/ui/separator";
import ExplainerDescription from "@/components/shared/ExplainerDescription/ExplainerDescription";
import usePromptImprovement from "@/hooks/usePromptImprovement";
import useProgressSimulation from "@/hooks/useProgressSimulation";
import {
  COMPOSED_PROVIDER_TYPE,
  LLMPromptConfigsType,
} from "@/types/providers";
import { PROVIDERS } from "@/constants/providers";
import { parseContentWithImages, combineContentWithImages } from "@/lib/llm";
import { EXPLAINER_ID, EXPLAINERS_MAP } from "@/constants/explainers";
import ExplainerCallout from "@/components/shared/ExplainerCallout/ExplainerCallout";
import {
  codeMirrorPromptTheme,
  mustachePlugin,
} from "@/constants/codeMirrorPlugins";
import { cn } from "@/lib/utils";
import { parseComposedProviderType } from "@/lib/provider";
import { TFunction } from "i18next";

const getPromptImprovementProgressMessages = (t: TFunction): string[] => [
  t("promptImprovement.progress.analyzing"),
  t("promptImprovement.progress.defining"),
  t("promptImprovement.progress.scoping"),
  t("promptImprovement.progress.applying"),
  t("promptImprovement.progress.synthesizing"),
  t("promptImprovement.progress.polishing"),
];

interface PromptImprovementDialogProps {
  open: boolean;
  setOpen: (open: boolean) => void;
  id: string;
  originalPrompt?: string;
  model: string;
  provider: COMPOSED_PROVIDER_TYPE;
  configs: LLMPromptConfigsType;
  workspaceName: string;
  onAccept: (messageId: string, improvedPrompt: string) => void;
}

const PromptImprovementDialog: React.FC<PromptImprovementDialogProps> = ({
  open,
  setOpen,
  id,
  originalPrompt = "",
  model,
  provider,
  configs,
  workspaceName,
  onAccept,
}) => {
  const { t } = useTranslation();
  const [userInstructions, setUserInstructions] = useState("");
  const [generatedPrompt, setGeneratedPrompt] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isEditorFocused, setIsEditorFocused] = useState(false);
  const editorViewRef = useRef<EditorView | null>(null);

  const { improvePrompt, generatePrompt } = usePromptImprovement({
    workspaceName,
  });

  const progressMessages = useMemo(() => getPromptImprovementProgressMessages(t), [t]);

  const { message: progressMessage } = useProgressSimulation({
    messages: progressMessages,
    isPending: isLoading && !generatedPrompt,
    intervalMs: 2000,
  });

  const { text: originalPromptText, images: originalImages } = useMemo(
    () => parseContentWithImages(originalPrompt),
    [originalPrompt],
  );

  const isGenerateMode = !originalPromptText?.trim();
  const title = isGenerateMode ? t("promptImprovement.generatePrompt") : t("promptImprovement.improvePrompt");
  const hasInstructions = Boolean(userInstructions.trim());

  useEffect(() => {
    if (open) {
      setUserInstructions("");
      setGeneratedPrompt("");
      setError(null);
      setIsLoading(false);
      setIsEditorFocused(false);
    }
  }, [open, originalImages]);

  // Smart auto-scroll: only auto-scroll when user is near the bottom
  // This allows users to scroll up to review content without being forced down
  useEffect(() => {
    if (!isLoading || !generatedPrompt || !editorViewRef.current) return;

    requestAnimationFrame(() => {
      const editor = editorViewRef.current;
      if (!editor) return;

      const scrollDOM = editor.scrollDOM;
      const { scrollTop, scrollHeight, clientHeight } = scrollDOM;

      // Check if user is within 50px of the bottom
      const isNearBottom = scrollHeight - scrollTop - clientHeight < 50;

      // Only auto-scroll if user is near the bottom or just started
      if (isNearBottom || scrollTop === 0) {
        scrollDOM.scrollTop = scrollHeight;
      }
    });
  }, [generatedPrompt, isLoading]);

  const handleGenerateOrImprove = useCallback(async () => {
    if (isGenerateMode && !hasInstructions) return;

    setIsLoading(true);
    setError(null);
    setGeneratedPrompt("");

    try {
      const controller = new AbortController();

      let result;
      if (isGenerateMode) {
        result = await generatePrompt(
          userInstructions,
          model,
          configs,
          (chunk) => {
            setGeneratedPrompt(chunk);
          },
          controller.signal,
        );
      } else {
        result = await improvePrompt(
          originalPromptText,
          userInstructions,
          model,
          configs,
          (chunk) => {
            setGeneratedPrompt(chunk);
          },
          controller.signal,
        );
      }

      if (
        result?.opikError ||
        result?.providerError ||
        result?.pythonProxyError
      ) {
        const errorMsg =
          result.opikError || result.providerError || result.pythonProxyError;
        setError(errorMsg || t("promptImprovement.errors.generationError"));
      } else if (
        result?.choices?.[0]?.finish_reason === "length" ||
        result?.choices?.some((choice) => choice.finish_reason === "length")
      ) {
        setError(t("promptImprovement.errors.tokenLimitExceeded"));
      } else if (!result?.result || !result.result.trim()) {
        setError(t("promptImprovement.errors.emptyResult"));
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error
          ? err.message
          : isGenerateMode
            ? t("promptImprovement.errors.generateFailed")
            : t("promptImprovement.errors.improveFailed");
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  }, [
    hasInstructions,
    userInstructions,
    isGenerateMode,
    generatePrompt,
    improvePrompt,
    originalPromptText,
    model,
    configs,
  ]);

  const hasPrompt = generatedPrompt.trim() && !isLoading && !error;

  const handleSuccessClick = useCallback(() => {
    if (hasPrompt) {
      const finalPrompt = combineContentWithImages(
        generatedPrompt,
        originalImages,
      );
      onAccept(id, finalPrompt);
      setOpen(false);
    }
  }, [hasPrompt, generatedPrompt, originalImages, onAccept, id, setOpen]);

  const instructionsPlaceholder = isGenerateMode
    ? t("promptImprovement.instructionsPlaceholder.generate")
    : t("promptImprovement.instructionsPlaceholder.improve");

  const modelDisplayName = useMemo(() => {
    if (!model) return t("promptImprovement.notConfigured");
    const providerLabel = provider
      ? PROVIDERS[parseComposedProviderType(provider)]?.label
      : "";
    return providerLabel ? `${providerLabel} ${model}` : model;
  }, [model, provider, t]);

  const renderInstructionsSection = (height: string) => (
    <div className="flex flex-1 flex-col gap-2">
      <Textarea
        className={`comet-code ${height} resize-none`}
        placeholder={instructionsPlaceholder}
        value={userInstructions}
        onChange={(e) => setUserInstructions(e.target.value)}
        autoFocus
      />
    </div>
  );

  const renderArrow = () => (
    <div className="pointer-events-none absolute left-1/2 top-1/2 z-10 flex size-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-primary-100">
      <ChevronRight className="size-3.5 text-primary" />
    </div>
  );

  const renderRightSectionTitle = (label: string) => (
    <div className="flex flex-col gap-2">
      <div className="comet-body-accented">{label}</div>
      <Description>
        {t("promptImprovement.generatedPromptDescription", { model: modelDisplayName })}
      </Description>
    </div>
  );

  const renderGeneratedPromptSection = () => {
    const isEmpty = !generatedPrompt && !isLoading;
    const isLoadingEmpty = isLoading && !generatedPrompt;
    const isStreaming = isLoading && generatedPrompt;
    const isEditable = !isLoading && Boolean(generatedPrompt) && !error;

    return (
      <div className="flex flex-col gap-2">
        <div className="relative h-[320px]">
          {isEmpty && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 rounded-md border border-dashed p-12">
              <Sparkles className="size-4 text-light-slate" />
              <div className="comet-body-s text-center text-muted-slate">
                {t("promptImprovement.emptyPromptPlaceholder")}
              </div>
            </div>
          )}

          {isLoadingEmpty && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 rounded-md border">
              <Loader2 className="size-6 animate-spin text-primary" />
              {progressMessage && (
                <div className="comet-body-s text-muted-slate">
                  {progressMessage}
                </div>
              )}
            </div>
          )}

          {(isStreaming || isEditable) && (
            <div
              className={cn(
                "rounded-md border px-3 py-2 transition-colors min-h-[320px]",
                isEditorFocused && isEditable && "border-primary",
              )}
            >
              <CodeMirror
                onCreateEditor={(view) => {
                  editorViewRef.current = view;
                }}
                onFocus={() => setIsEditorFocused(true)}
                onBlur={() => setIsEditorFocused(false)}
                theme={codeMirrorPromptTheme}
                value={generatedPrompt}
                onChange={(value) => setGeneratedPrompt(value)}
                placeholder="The generated prompt will be shown here, ready for you to review and edit"
                editable={isEditable}
                basicSetup={{
                  foldGutter: false,
                  allowMultipleSelections: false,
                  lineNumbers: false,
                  highlightActiveLine: false,
                }}
                extensions={[EditorView.lineWrapping, mustachePlugin]}
                maxHeight="302px"
              />
            </div>
          )}
        </div>
      </div>
    );
  };

  const renderGenerateButtons = () => (
    <>
      <Button
        variant={hasPrompt ? "outline" : "default"}
        onClick={handleGenerateOrImprove}
        disabled={(isGenerateMode && !hasInstructions) || isLoading}
        className="w-full"
      >
        <Sparkles className="mr-2 size-4 shrink-0" />
        {hasPrompt
          ? isGenerateMode
            ? t("promptImprovement.buttons.regenerate")
            : t("promptImprovement.buttons.rerun")
          : isGenerateMode
            ? t("promptImprovement.buttons.generate")
            : t("promptImprovement.buttons.improve")}
      </Button>
      <Button
        variant={!hasPrompt ? "outline" : "default"}
        onClick={handleSuccessClick}
        disabled={!hasPrompt}
      >
        <Play className="mr-2 size-4 shrink-0" />
        {t("promptImprovement.buttons.usePrompt")}
      </Button>
    </>
  );

  const renderImproveContent = () => {
    return (
      <div className="relative grid grid-cols-2 gap-x-14 gap-y-3">
        {renderArrow()}
        <div className="flex flex-col gap-2">
          <div className="comet-body-accented">{t("promptImprovement.sections.initialPrompt")}</div>
          <Description>
            {t("promptImprovement.sections.initialPromptDescription")}
          </Description>
        </div>
        {renderRightSectionTitle(t("promptImprovement.sections.improvedPrompt"))}
        <div className="flex flex-col gap-2">
          <div className="comet-code h-[120px] overflow-y-auto whitespace-pre-wrap break-words rounded-md border bg-primary-foreground p-3 text-light-slate">
            {originalPromptText}
          </div>
          <div className="mt-1 flex flex-col">
            <div className="comet-title-xs">{t("promptImprovement.sections.instructions")}</div>
            <Description>
              {t("promptImprovement.sections.instructionsDescription")}
            </Description>
          </div>
          {renderInstructionsSection("min-h-[120px]")}
        </div>
        {renderGeneratedPromptSection()}
        {renderGenerateButtons()}
      </div>
    );
  };

  const renderGenerateContent = () => {
    return (
      <div className="relative grid grid-cols-2 gap-x-14 gap-y-3 pb-4">
        {renderArrow()}
        <div className="flex flex-col gap-2">
          <div className="comet-body-accented">{t("promptImprovement.sections.instructions")}</div>
          <Description>
            {t("promptImprovement.sections.instructionsDescription")}
          </Description>
        </div>
        {renderRightSectionTitle(t("promptImprovement.sections.generatedPrompt"))}
        {renderInstructionsSection("h-[320px]")}
        {renderGeneratedPromptSection()}
        {renderGenerateButtons()}
      </div>
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent
        className="max-w-[960px]"
        onClick={(e) => e.stopPropagation()}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Wand2 className="size-5" />
            {title}
          </DialogTitle>
          <ExplainerDescription
            {...EXPLAINERS_MAP[
              isGenerateMode
                ? EXPLAINER_ID.prompt_generation_learn_more
                : EXPLAINER_ID.prompt_improvement_learn_more
            ]}
          />
        </DialogHeader>
        <DialogAutoScrollBody>
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertTitle>{error}</AlertTitle>
            </Alert>
          )}
          {isGenerateMode ? renderGenerateContent() : renderImproveContent()}
          {!isGenerateMode && (
            <>
              <Separator orientation="horizontal" className="my-6" />
              <ExplainerCallout
                {...EXPLAINERS_MAP[EXPLAINER_ID.prompt_improvement_optimizer]}
                isDismissable={false}
              />
            </>
          )}
        </DialogAutoScrollBody>
      </DialogContent>
    </Dialog>
  );
};

export default PromptImprovementDialog;
