import { useState, useMemo } from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTranslation } from "react-i18next";
import { Eye, EyeOff, Loader2, Check, X, CheckCircle } from "lucide-react";

import { useResetPassword } from "@/api/auth";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { useToast } from "@/components/ui/use-toast";
import { cn } from "@/lib/utils";

// 密码强度类型
type PasswordStrengthLevel = "weak" | "medium" | "strong";

// 密码强度检查
const checkPasswordStrength = (password: string): {
  checks: {
    minLength: boolean;
    hasLowercase: boolean;
    hasUppercase: boolean;
    hasNumber: boolean;
    hasSpecial: boolean;
  };
  score: number;
  strength: PasswordStrengthLevel;
} => {
  const checks = {
    minLength: password.length >= 8,
    hasLowercase: /[a-z]/.test(password),
    hasUppercase: /[A-Z]/.test(password),
    hasNumber: /[0-9]/.test(password),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(password),
  };

  const score = Object.values(checks).filter(Boolean).length;

  const strength: PasswordStrengthLevel =
    score <= 2 ? "weak" : score <= 3 ? "medium" : "strong";

  return {
    checks,
    score,
    strength,
  };
};

// 表单验证 schema
const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(8, "密码至少8个字符")
      .max(128, "密码最多128个字符")
      .regex(/[a-z]/, "密码必须包含小写字母")
      .regex(/[A-Z]/, "密码必须包含大写字母")
      .regex(/[0-9]/, "密码必须包含数字")
      .regex(/[!@#$%^&*(),.?":{}|<>]/, "密码必须包含特殊字符"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "两次输入的密码不一致",
    path: ["confirmPassword"],
  });

type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

const ResetPasswordPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { token } = useParams({ strict: false }) as { token?: string };
  const { toast } = useToast();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [resetSuccess, setResetSuccess] = useState(false);

  const resetMutation = useResetPassword();

  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
    mode: "onChange",
  });

  const password = form.watch("password");
  const passwordStrength = useMemo(
    () => checkPasswordStrength(password || ""),
    [password],
  );

  const onSubmit = async (values: ResetPasswordFormValues) => {
    if (!token) {
      toast({
        variant: "destructive",
        title: t("auth.error", "错误"),
        description: t("auth.invalidResetToken", "无效的重置链接"),
      });
      return;
    }

    try {
      await resetMutation.mutateAsync({
        token,
        newPassword: values.password,
      });
      setResetSuccess(true);
    } catch (error) {
      const errorMessage =
        (error as { response?: { data?: { message?: string } } })?.response
          ?.data?.message || t("auth.resetFailed", "密码重置失败，请稍后重试");

      toast({
        variant: "destructive",
        title: t("auth.error", "错误"),
        description: errorMessage,
      });
    }
  };

  const strengthColors = {
    weak: "bg-red-500",
    medium: "bg-yellow-500",
    strong: "bg-green-500",
  };

  const strengthLabels = {
    weak: t("auth.passwordWeak", "弱"),
    medium: t("auth.passwordMedium", "中"),
    strong: t("auth.passwordStrong", "强"),
  };

  // 重置成功状态
  if (resetSuccess) {
    return (
      <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
        <CardHeader className="space-y-1 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
            <CheckCircle className="h-8 w-8 text-green-600" />
          </div>
          <CardTitle className="text-2xl font-bold tracking-tight">
            {t("auth.passwordResetSuccess", "密码重置成功")}
          </CardTitle>
          <CardDescription>
            {t(
              "auth.passwordResetSuccessDescription",
              "您的密码已成功重置，现在可以使用新密码登录了。",
            )}
          </CardDescription>
        </CardHeader>

        <CardFooter>
          <Button
            className="w-full"
            onClick={() => navigate({ to: "/login" })}
          >
            {t("auth.goToLogin", "前往登录")}
          </Button>
        </CardFooter>
      </Card>
    );
  }

  // 无效的 token
  if (!token) {
    return (
      <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
        <CardHeader className="space-y-1 text-center">
          <CardTitle className="text-2xl font-bold tracking-tight text-destructive">
            {t("auth.invalidLink", "无效的链接")}
          </CardTitle>
          <CardDescription>
            {t(
              "auth.invalidLinkDescription",
              "该密码重置链接无效或已过期，请重新申请。",
            )}
          </CardDescription>
        </CardHeader>

        <CardFooter>
          <Button
            className="w-full"
            onClick={() => navigate({ to: "/forgot-password" })}
          >
            {t("auth.requestNewLink", "重新申请")}
          </Button>
        </CardFooter>
      </Card>
    );
  }

  return (
    <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
      <CardHeader className="space-y-1 text-center">
        <CardTitle className="text-2xl font-bold tracking-tight">
          {t("auth.resetPasswordTitle", "重置密码")}
        </CardTitle>
        <CardDescription>
          {t("auth.resetPasswordDescription", "请输入您的新密码")}
        </CardDescription>
      </CardHeader>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.newPassword", "新密码")}</FormLabel>
                  <FormControl>
                    <div className="relative">
                      <Input
                        type={showPassword ? "text" : "password"}
                        placeholder={t("auth.newPasswordPlaceholder", "请输入新密码")}
                        autoComplete="new-password"
                        {...field}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                        onClick={() => setShowPassword(!showPassword)}
                      >
                        {showPassword ? (
                          <EyeOff className="h-4 w-4 text-muted-foreground" />
                        ) : (
                          <Eye className="h-4 w-4 text-muted-foreground" />
                        )}
                      </Button>
                    </div>
                  </FormControl>

                  {/* 密码强度指示器 */}
                  {password && (
                    <div className="mt-2 space-y-2">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                          <div
                            className={cn(
                              "h-full transition-all duration-300",
                              strengthColors[passwordStrength.strength],
                            )}
                            style={{
                              width: `${(passwordStrength.score / 5) * 100}%`,
                            }}
                          />
                        </div>
                        <span
                          className={cn(
                            "text-xs font-medium",
                            passwordStrength.strength === "weak" &&
                              "text-red-500",
                            passwordStrength.strength === "medium" &&
                              "text-yellow-600",
                            passwordStrength.strength === "strong" &&
                              "text-green-600",
                          )}
                        >
                          {strengthLabels[passwordStrength.strength]}
                        </span>
                      </div>

                      <div className="grid grid-cols-2 gap-1 text-xs">
                        <PasswordRequirement
                          met={passwordStrength.checks.minLength}
                          label={t("auth.pwdReq8Chars", "至少8个字符")}
                        />
                        <PasswordRequirement
                          met={passwordStrength.checks.hasLowercase}
                          label={t("auth.pwdReqLower", "包含小写字母")}
                        />
                        <PasswordRequirement
                          met={passwordStrength.checks.hasUppercase}
                          label={t("auth.pwdReqUpper", "包含大写字母")}
                        />
                        <PasswordRequirement
                          met={passwordStrength.checks.hasNumber}
                          label={t("auth.pwdReqNumber", "包含数字")}
                        />
                      </div>
                    </div>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.confirmPassword", "确认密码")}</FormLabel>
                  <FormControl>
                    <div className="relative">
                      <Input
                        type={showConfirmPassword ? "text" : "password"}
                        placeholder={t(
                          "auth.confirmPasswordPlaceholder",
                          "请再次输入密码",
                        )}
                        autoComplete="new-password"
                        {...field}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                        onClick={() =>
                          setShowConfirmPassword(!showConfirmPassword)
                        }
                      >
                        {showConfirmPassword ? (
                          <EyeOff className="h-4 w-4 text-muted-foreground" />
                        ) : (
                          <Eye className="h-4 w-4 text-muted-foreground" />
                        )}
                      </Button>
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>

          <CardFooter>
            <Button
              type="submit"
              className="w-full"
              disabled={
                resetMutation.isPending ||
                passwordStrength.strength === "weak"
              }
            >
              {resetMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {t("auth.resetPassword", "重置密码")}
            </Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

// 密码要求提示组件
const PasswordRequirement = ({
  met,
  label,
}: {
  met: boolean;
  label: string;
}) => (
  <div className="flex items-center gap-1">
    {met ? (
      <Check className="h-3 w-3 text-green-500" />
    ) : (
      <X className="h-3 w-3 text-muted-foreground" />
    )}
    <span className={cn(met ? "text-green-600" : "text-muted-foreground")}>
      {label}
    </span>
  </div>
);

export default ResetPasswordPage;

