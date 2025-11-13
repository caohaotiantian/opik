import React from "react";
import { Info, SquareArrowOutUpRight, X } from "lucide-react";
import { useTranslation } from "react-i18next";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { buildDocsUrl, cn } from "@/lib/utils";
import { Explainer } from "@/types/shared";
import useLocalStorageState from "use-local-storage-state";

type ExplainerCalloutProps = {
  className?: string;
  Icon?: React.ComponentType<React.SVGProps<SVGSVGElement>>;
  isDismissable?: boolean;
} & Explainer;

const ExplainerCallout: React.FC<ExplainerCalloutProps> = ({
  id,
  title,
  description,
  translationKey,
  docLink,
  docHash,
  className,
  Icon = Info,
  isDismissable = true,
}) => {
  const { t } = useTranslation();
  const [isShown, setIsShown] = useLocalStorageState<boolean>(
    `explainer-callout-${id}`,
    {
      defaultValue: true,
    },
  );

  const translatedDescription = translationKey ? t(translationKey) : description;

  if (!isShown) return null;

  return (
    <Alert
      variant="callout"
      size="sm"
      className={cn(isDismissable ? "pr-10" : "pr-4", className)}
    >
      <Icon />
      {title && <AlertTitle size="sm">{title}</AlertTitle>}
      <AlertDescription size="sm">
        {translatedDescription}
        {docLink && (
          <Button variant="link" size="3xs" asChild>
            <a
              href={buildDocsUrl(docLink, docHash)}
              target="_blank"
              rel="noreferrer"
            >
              {t("common.readMore")}
              <SquareArrowOutUpRight className="ml-0.5 size-3 shrink-0" />
            </a>
          </Button>
        )}
      </AlertDescription>
      {isDismissable && (
        <Button
          variant="minimal"
          size="icon-sm"
          onClick={() => setIsShown(false)}
          className="absolute right-1 top-1 !p-0"
        >
          <X />
        </Button>
      )}
    </Alert>
  );
};

export default ExplainerCallout;
