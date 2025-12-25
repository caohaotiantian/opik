import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTranslation } from "react-i18next";
import { Loader2, Mail, ArrowLeft, CheckCircle } from "lucide-react";

import { useRequestPasswordReset } from "@/api/auth";

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
const forgotPasswordSchema = z.object({
  email: z.string().email("请输入有效的邮箱地址"),
});

type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

const ForgotPasswordPage = () => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [emailSent, setEmailSent] = useState(false);
  const [sentEmail, setSentEmail] = useState("");

  const resetMutation = useRequestPasswordReset();

  const form = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: "",
    },
  });

  const onSubmit = async (values: ForgotPasswordFormValues) => {
    try {
      await resetMutation.mutateAsync({ email: values.email });
      setSentEmail(values.email);
      setEmailSent(true);
    } catch (error) {
      // 即使邮箱不存在也显示成功消息（安全考虑，防止邮箱枚举）
      setSentEmail(values.email);
      setEmailSent(true);
    }
  };

  // 重新发送邮件
  const handleResend = async () => {
    try {
      await resetMutation.mutateAsync({ email: sentEmail });
      toast({
        title: t("auth.emailResent", "邮件已重新发送"),
        description: t("auth.checkEmailAgain", "请查看您的邮箱"),
      });
    } catch {
      // 静默失败
    }
  };

  // 邮件发送成功状态
  if (emailSent) {
    return (
      <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
        <CardHeader className="space-y-1 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
            <CheckCircle className="h-8 w-8 text-green-600" />
          </div>
          <CardTitle className="text-2xl font-bold tracking-tight">
            {t("auth.emailSent", "邮件已发送")}
          </CardTitle>
          <CardDescription>
            {t(
              "auth.emailSentDescription",
              "我们已向 {{email}} 发送了密码重置链接，请查看您的邮箱。",
              { email: sentEmail },
            )}
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          <div className="rounded-lg bg-muted p-4 text-sm text-muted-foreground">
            <p className="flex items-center gap-2">
              <Mail className="h-4 w-4" />
              {t("auth.checkSpam", "如果没有收到邮件，请检查垃圾邮件文件夹。")}
            </p>
          </div>
        </CardContent>

        <CardFooter className="flex flex-col gap-4">
          <Button
            variant="outline"
            className="w-full"
            onClick={handleResend}
            disabled={resetMutation.isPending}
          >
            {resetMutation.isPending && (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            )}
            {t("auth.resendEmail", "重新发送邮件")}
          </Button>

          <a
            href="/login"
            className="flex items-center justify-center gap-2 text-sm text-primary hover:underline"
          >
            <ArrowLeft className="h-4 w-4" />
            {t("auth.backToLogin", "返回登录")}
          </a>
        </CardFooter>
      </Card>
    );
  }

  return (
    <Card className="w-full max-w-md bg-white/95 backdrop-blur shadow-2xl">
      <CardHeader className="space-y-1 text-center">
        <CardTitle className="text-2xl font-bold tracking-tight">
          {t("auth.forgotPasswordTitle", "忘记密码")}
        </CardTitle>
        <CardDescription>
          {t(
            "auth.forgotPasswordDescription",
            "输入您的邮箱地址，我们将向您发送重置密码的链接。",
          )}
        </CardDescription>
      </CardHeader>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
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
                      {...field}
                    />
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
              disabled={resetMutation.isPending}
            >
              {resetMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {t("auth.sendResetLink", "发送重置链接")}
            </Button>

            <a
              href="/login"
              className="flex items-center justify-center gap-2 text-sm text-primary hover:underline"
            >
              <ArrowLeft className="h-4 w-4" />
              {t("auth.backToLogin", "返回登录")}
            </a>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
};

export default ForgotPasswordPage;

