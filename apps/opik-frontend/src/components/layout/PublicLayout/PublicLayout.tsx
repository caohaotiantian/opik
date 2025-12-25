import { Outlet } from "@tanstack/react-router";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Globe } from "lucide-react";
import { cn } from "@/lib/utils";
import Loader from "@/components/shared/Loader/Loader";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/**
 * 公开页面布局
 * 用于登录、注册等无需认证的页面
 */
const PublicLayout = () => {
  const { i18n } = useTranslation();

  const languages = [
    { code: "en", label: "English", flag: "🇺🇸" },
    { code: "zh", label: "中文", flag: "🇨🇳" },
  ];

  const currentLanguage = languages.find((lang) => lang.code === i18n.language) || languages[0];

  const handleLanguageChange = (langCode: string) => {
    i18n.changeLanguage(langCode);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 h-80 w-80 rounded-full bg-primary/10 blur-3xl" />
        <div className="absolute -bottom-40 -left-40 h-80 w-80 rounded-full bg-primary/5 blur-3xl" />
      </div>

      {/* 语言切换器 - 右上角 */}
      <div className="absolute top-4 right-4 z-10">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm" className="text-slate-300 hover:text-white hover:bg-slate-700">
              <Globe className="mr-2 h-4 w-4" />
              <span>{currentLanguage.flag} {currentLanguage.label}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {languages.map((lang) => (
              <DropdownMenuItem
                key={lang.code}
                onClick={() => handleLanguageChange(lang.code)}
                className={cn(
                  "cursor-pointer",
                  i18n.language === lang.code && "bg-accent"
                )}
              >
                <span className="mr-2">{lang.flag}</span>
                {lang.label}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* 主内容 */}
      <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
        {/* Logo */}
        <div className="mb-8">
          <OpikLogo className="h-12 w-auto" />
        </div>

        {/* 页面内容 */}
        <Suspense fallback={<Loader />}>
          <Outlet />
        </Suspense>

        {/* 底部版权 */}
        <div className="mt-8 text-center text-sm text-slate-500">
          © {new Date().getFullYear()} Opik. All rights reserved.
        </div>
      </div>
    </div>
  );
};

/**
 * Opik Logo 组件
 */
const OpikLogo = ({ className }: { className?: string }) => {
  return (
    <svg
      viewBox="0 0 100 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn("text-white", className)}
    >
      {/* Opik logo SVG path */}
      <text
        x="50%"
        y="50%"
        dominantBaseline="middle"
        textAnchor="middle"
        fill="currentColor"
        fontSize="24"
        fontWeight="bold"
        fontFamily="system-ui, -apple-system, sans-serif"
      >
        Opik
      </text>
    </svg>
  );
};

export default PublicLayout;

