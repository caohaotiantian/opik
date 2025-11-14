import React from "react";
import { cn } from "@/lib/utils";
import { useTheme } from "@/components/theme-provider";
import { THEME_MODE } from "@/constants/theme";
import imageLogoUrl from "/images/ai-benchmark-logo.png";
import imageLogoInvertedUrl from "/images/ai-benchmark-logo-inverted.png";

type LogoProps = {
  expanded: boolean;
};

const Logo: React.FunctionComponent<LogoProps> = ({ expanded }) => {
  const { themeMode } = useTheme();

  return (
    <img
      className={cn("h-12 object-contain object-left", {
        "w-[48px]": !expanded,
        "w-full": expanded,
      })}
      src={themeMode === THEME_MODE.DARK ? imageLogoInvertedUrl : imageLogoUrl}
      alt="ai-benchmark logo"
    />
  );
};

export default Logo;
