import { useState, useMemo } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTranslation } from "react-i18next";
import { Eye, EyeOff, Loader2, Check, X } from "lucide-react";

import { useRegister } from "@/api/auth";
import useAuthStore from "@/store/AuthStore";

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

// 密码强度检查
const checkPasswordStrength = (password: string) => {
  const checks = {
    minLength: password.length >= 8,
    hasLowercase: /[a-z]/.test(password),
    hasUppercase: /[A-Z]/.test(password),
    hasNumber: /[0-9]/.test(password),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(password),
  };

  const score = Object.values(checks).filter(Boolean).length;

  return {
    checks,
    score,
    strength: score <= 2 ? "weak" : score <= 3 ? "medium" : "strong",
  };
};

// 表单验证 schema
const registerSchema = z
  .object({
    username: z
      .string()
      .min(3, "用户名至少3个字符")
      .max(50, "用户名最多50个字符")
      .regex(/^[a-zA-Z0-9_-]+$/, "用户名只能包含字母、数字、下划线和连字符"),
    email: z.string().email("请输入有效的邮箱地址"),
    fullName: z.string().optional(),
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

type RegisterFormValues = z.infer<typeof registerSchema>;

const RegisterPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { loginSuccess } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const registerMutation = useRegister();

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      username: "",
      email: "",
      fullName: "",
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

  const onSubmit = async (values: RegisterFormValues) => {
    try {
      const response = await registerMutation.mutateAsync({
        username: values.username,
        email: values.email,
        password: values.password,
        fullName: values.fullName || undefined,
      });

      // 注册成功后自动登录
      loginSuccess(
        {
          id: response.userId,
          username: response.username,
          email: response.email,
          fullName: response.fullName,
          status: "active",
          systemAdmin: response.systemAdmin,
          emailVerified: true,
          createdAt: new Date().toISOString(),
        },
        [
          {
            id: response.defaultWorkspaceId,
            name: response.defaultWorkspaceName,
            displayName: response.defaultWorkspaceName,
            role: "owner",
          },
        ],
        response.defaultWorkspaceId,
      );

      toast({
        title: t("auth.registerSuccess", "注册成功"),
        description: t("auth.welcomeNew", "欢迎加入，{{name}}", {
          name: response.username,
        }),
      });

      // 重定向到默认工作空间
      navigate({ to: `/${response.defaultWorkspaceName}/home` });
    } catch (error) {
      const errorMessage =
        (error as { response?: { data?: { message?: string } } })?.response
          ?.data?.message || t("auth.registerFailed", "注册失败，请稍后重试");

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

  return (
    <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
      <CardHeader className="space-y-1 text-center">
        <CardTitle className="text-2xl font-bold tracking-tight">
          {t("auth.register", "注册账号")}
        </CardTitle>
        <CardDescription>
          {t("auth.registerDescription", "创建您的 Opik 账号")}
        </CardDescription>
      </CardHeader>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            <FormField
              control={form.control}
              name="username"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.username", "用户名")}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t("auth.usernamePlaceholder", "请输入用户名")}
                      autoComplete="username"
                      data-testid="register-username"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.email", "邮箱")}</FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder={t("auth.emailPlaceholder", "请输入邮箱地址")}
                      autoComplete="email"
                      data-testid="register-email"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="fullName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("auth.fullName", "姓名")}{" "}
                    <span className="text-muted-foreground">
                      ({t("common.optional", "可选")})
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      placeholder={t("auth.fullNamePlaceholder", "请输入您的姓名")}
                      autoComplete="name"
                      data-testid="register-fullname"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.password", "密码")}</FormLabel>
                  <FormControl>
                    <div className="relative">
                      <Input
                        type={showPassword ? "text" : "password"}
                        placeholder={t("auth.passwordPlaceholder", "请输入密码")}
                        autoComplete="new-password"
                        data-testid="register-password"
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
                        <PasswordRequirement
                          met={passwordStrength.checks.hasSpecial}
                          label={t("auth.pwdReqSpecial", "包含特殊字符")}
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
                        data-testid="register-confirm-password"
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

          <CardFooter className="flex flex-col gap-4">
            <Button
              type="submit"
              className="w-full"
              disabled={
                registerMutation.isPending ||
                passwordStrength.strength === "weak"
              }
              data-testid="register-submit"
            >
              {registerMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {t("auth.registerButton", "注册")}
            </Button>

            <div className="text-center text-sm text-muted-foreground">
              {t("auth.hasAccount", "已有账号？")}{" "}
              <a href="/login" className="text-primary hover:underline">
                {t("auth.loginNow", "立即登录")}
              </a>
            </div>
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

export default RegisterPage;

