import { useState } from "react";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTranslation } from "react-i18next";
import { Eye, EyeOff, Loader2 } from "lucide-react";

import { useLogin } from "@/api/auth";
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

// 表单验证 schema
const loginSchema = z.object({
  username: z.string().min(1, "请输入用户名"),
  password: z.string().min(1, "请输入密码"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

const LoginPage = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as { redirect?: string };
  const { toast } = useToast();
  const { loginSuccess } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);

  const loginMutation = useLogin();

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: "",
      password: "",
    },
  });

  const onSubmit = async (values: LoginFormValues) => {
    try {
      const response = await loginMutation.mutateAsync({
        usernameOrEmail: values.username,
        password: values.password,
      });

      // 更新认证状态
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
        title: t("auth.loginSuccess", "登录成功"),
        description: t("auth.welcomeBack", "欢迎回来，{{name}}", {
          name: response.username,
        }),
      });

      // 重定向到之前的页面或默认工作空间
      const redirectTo =
        search?.redirect || `/${response.defaultWorkspaceName}/home`;
      navigate({ to: redirectTo });
    } catch (error) {
      const errorMessage =
        (error as { response?: { data?: { message?: string } } })?.response
          ?.data?.message || t("auth.loginFailed", "登录失败，请检查用户名和密码");
      
      toast({
        variant: "destructive",
        title: t("auth.error", "错误"),
        description: errorMessage,
      });
    }
  };

  return (
    <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
      <CardHeader className="space-y-1 text-center">
        <CardTitle className="text-2xl font-bold tracking-tight">
          {t("auth.login", "登录")}
        </CardTitle>
        <CardDescription>
          {t("auth.loginDescription", "输入您的账号信息登录系统")}
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
                      data-testid="login-username"
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
                        autoComplete="current-password"
                        data-testid="login-password"
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
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex items-center justify-end">
              <a
                href="/forgot-password"
                className="text-sm text-primary hover:underline"
              >
                {t("auth.forgotPassword", "忘记密码？")}
              </a>
            </div>
          </CardContent>

          <CardFooter className="flex flex-col gap-4">
            <Button
              type="submit"
              className="w-full"
              disabled={loginMutation.isPending}
              data-testid="login-submit"
            >
              {loginMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {t("auth.loginButton", "登录")}
            </Button>

            <div className="text-center text-sm text-muted-foreground">
              {t("auth.noAccount", "还没有账号？")}{" "}
              <a href="/register" className="text-primary hover:underline">
                {t("auth.registerNow", "立即注册")}
              </a>
            </div>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export default LoginPage;

