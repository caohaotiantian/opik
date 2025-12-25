import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { User, Mail, Lock, Save, Loader2, Eye, EyeOff, Check } from "lucide-react";

import { useChangePassword, useUpdateProfile } from "@/api/auth";
import { useCurrentUser } from "@/store/AuthStore";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Card,
  CardContent,
  CardDescription,
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
  FormDescription,
} from "@/components/ui/form";
import { Separator } from "@/components/ui/separator";
import { useToast } from "@/components/ui/use-toast";

// 个人信息表单验证
const profileSchema = z.object({
  email: z.string().email("请输入有效的邮箱地址"),
  fullName: z.string().optional(),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

// 密码修改表单验证
const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "请输入当前密码"),
    newPassword: z
      .string()
      .min(8, "密码至少8个字符")
      .max(128, "密码最多128个字符")
      .regex(/[a-z]/, "密码必须包含小写字母")
      .regex(/[A-Z]/, "密码必须包含大写字母")
      .regex(/[0-9]/, "密码必须包含数字")
      .regex(/[!@#$%^&*(),.?":{}|<>]/, "密码必须包含特殊字符"),
    confirmPassword: z.string().min(1, "请确认新密码"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "两次输入的密码不一致",
    path: ["confirmPassword"],
  });

type PasswordFormValues = z.infer<typeof passwordSchema>;

const ProfileSettingsPage = () => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const currentUser = useCurrentUser();

  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const updateProfileMutation = useUpdateProfile();
  const changePasswordMutation = useChangePassword();

  // 个人信息表单
  const profileForm = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      email: "",
      fullName: "",
    },
  });

  // 当 currentUser 加载完成后，更新表单值
  useEffect(() => {
    if (currentUser) {
      profileForm.reset({
        email: currentUser.email || "",
        fullName: currentUser.fullName || "",
      });
    }
  }, [currentUser, profileForm]);

  // 密码修改表单
  const passwordForm = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: {
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    },
  });

  // 提交个人信息更新
  const onProfileSubmit = async (values: ProfileFormValues) => {
    try {
      await updateProfileMutation.mutateAsync({
        email: values.email,
        fullName: values.fullName,
      });

      toast({
        title: t("settings.profileUpdated", "个人信息已更新"),
        description: t("settings.profileUpdatedDescription", "您的个人信息已成功更新"),
      });
    } catch (error) {
      const errorMessage =
        (error as { response?: { data?: { message?: string } } })?.response?.data
          ?.message || t("settings.profileUpdateFailed", "更新失败，请稍后重试");

      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: errorMessage,
      });
    }
  };

  // 提交密码修改
  const onPasswordSubmit = async (values: PasswordFormValues) => {
    try {
      await changePasswordMutation.mutateAsync({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });

      toast({
        title: t("settings.passwordChanged", "密码已修改"),
        description: t("settings.passwordChangedDescription", "您的密码已成功修改"),
      });

      // 清空表单
      passwordForm.reset();
    } catch (error) {
      const errorMessage =
        (error as { response?: { data?: { message?: string } } })?.response?.data
          ?.message || t("settings.passwordChangeFailed", "密码修改失败");

      toast({
        variant: "destructive",
        title: t("common.error", "错误"),
        description: errorMessage,
      });
    }
  };

  // 密码强度检查
  const newPassword = passwordForm.watch("newPassword");
  const passwordStrength = {
    hasLength: newPassword?.length >= 8,
    hasLowercase: /[a-z]/.test(newPassword || ""),
    hasUppercase: /[A-Z]/.test(newPassword || ""),
    hasNumber: /[0-9]/.test(newPassword || ""),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(newPassword || ""),
  };

  const strengthCount = Object.values(passwordStrength).filter(Boolean).length;
  const strengthLabel =
    strengthCount <= 2
      ? t("auth.passwordWeak", "弱")
      : strengthCount <= 4
        ? t("auth.passwordMedium", "中")
        : t("auth.passwordStrong", "强");
  const strengthColor =
    strengthCount <= 2
      ? "text-red-500"
      : strengthCount <= 4
        ? "text-yellow-500"
        : "text-green-500";

  // 如果用户信息未加载，显示加载状态
  if (!currentUser) {
    return (
      <div className="container max-w-2xl py-6 flex items-center justify-center min-h-[400px]">
        <div className="flex flex-col items-center gap-2">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          <p className="text-muted-foreground">{t("common.loading", "加载中...")}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="container max-w-2xl py-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <User className="h-6 w-6" />
          {t("settings.title", "个人设置")}
        </h1>
        <p className="text-muted-foreground">
          {t("settings.description", "管理您的账户信息和安全设置")}
        </p>
      </div>

      {/* 个人信息卡片 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Mail className="h-5 w-5" />
            {t("settings.profileTitle", "个人信息")}
          </CardTitle>
          <CardDescription>
            {t("settings.profileDescription", "更新您的邮箱和显示名称")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...profileForm}>
            <form onSubmit={profileForm.handleSubmit(onProfileSubmit)} className="space-y-4">
              <div className="space-y-2">
                <FormLabel>{t("settings.username", "用户名")}</FormLabel>
                <Input value={currentUser?.username || ""} disabled className="bg-muted" />
                <FormDescription>
                  {t("settings.usernameHint", "用户名创建后不可修改")}
                </FormDescription>
              </div>

              <FormField
                control={profileForm.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("settings.email", "邮箱")}</FormLabel>
                    <FormControl>
                      <Input placeholder="your@email.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={profileForm.control}
                name="fullName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("settings.fullName", "显示名称")}</FormLabel>
                    <FormControl>
                      <Input
                        placeholder={t("settings.fullNamePlaceholder", "输入您的显示名称")}
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      {t("settings.fullNameHint", "此名称将显示在界面和通知中")}
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <Button type="submit" disabled={updateProfileMutation.isPending}>
                {updateProfileMutation.isPending ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : (
                  <Save className="mr-2 h-4 w-4" />
                )}
                {t("settings.saveChanges", "保存更改")}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>

      <Separator />

      {/* 密码修改卡片 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Lock className="h-5 w-5" />
            {t("settings.passwordTitle", "修改密码")}
          </CardTitle>
          <CardDescription>
            {t("settings.passwordDescription", "定期更新密码以确保账户安全")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...passwordForm}>
            <form onSubmit={passwordForm.handleSubmit(onPasswordSubmit)} className="space-y-4">
              <FormField
                control={passwordForm.control}
                name="currentPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("settings.currentPassword", "当前密码")}</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Input
                          type={showCurrentPassword ? "text" : "password"}
                          placeholder={t("settings.currentPasswordPlaceholder", "输入当前密码")}
                          {...field}
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                          onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                        >
                          {showCurrentPassword ? (
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

              <FormField
                control={passwordForm.control}
                name="newPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("settings.newPassword", "新密码")}</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Input
                          type={showNewPassword ? "text" : "password"}
                          placeholder={t("settings.newPasswordPlaceholder", "输入新密码")}
                          {...field}
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                          onClick={() => setShowNewPassword(!showNewPassword)}
                        >
                          {showNewPassword ? (
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

              {/* 密码强度指示器 */}
              {newPassword && (
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                      <div
                        className={`h-full transition-all ${
                          strengthCount <= 2
                            ? "bg-red-500 w-1/5"
                            : strengthCount <= 4
                              ? "bg-yellow-500 w-3/5"
                              : "bg-green-500 w-full"
                        }`}
                      />
                    </div>
                    <span className={`text-sm font-medium ${strengthColor}`}>
                      {strengthLabel}
                    </span>
                  </div>
                  <ul className="text-xs space-y-1">
                    <li className={`flex items-center gap-1 ${passwordStrength.hasLength ? "text-green-600" : "text-muted-foreground"}`}>
                      {passwordStrength.hasLength && <Check className="h-3 w-3" />}
                      {t("auth.pwdReq8Chars", "至少8个字符")}
                    </li>
                    <li className={`flex items-center gap-1 ${passwordStrength.hasLowercase ? "text-green-600" : "text-muted-foreground"}`}>
                      {passwordStrength.hasLowercase && <Check className="h-3 w-3" />}
                      {t("auth.pwdReqLower", "包含小写字母")}
                    </li>
                    <li className={`flex items-center gap-1 ${passwordStrength.hasUppercase ? "text-green-600" : "text-muted-foreground"}`}>
                      {passwordStrength.hasUppercase && <Check className="h-3 w-3" />}
                      {t("auth.pwdReqUpper", "包含大写字母")}
                    </li>
                    <li className={`flex items-center gap-1 ${passwordStrength.hasNumber ? "text-green-600" : "text-muted-foreground"}`}>
                      {passwordStrength.hasNumber && <Check className="h-3 w-3" />}
                      {t("auth.pwdReqNumber", "包含数字")}
                    </li>
                    <li className={`flex items-center gap-1 ${passwordStrength.hasSpecial ? "text-green-600" : "text-muted-foreground"}`}>
                      {passwordStrength.hasSpecial && <Check className="h-3 w-3" />}
                      {t("auth.pwdReqSpecial", "包含特殊字符")}
                    </li>
                  </ul>
                </div>
              )}

              <FormField
                control={passwordForm.control}
                name="confirmPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("settings.confirmPassword", "确认新密码")}</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Input
                          type={showConfirmPassword ? "text" : "password"}
                          placeholder={t("settings.confirmPasswordPlaceholder", "再次输入新密码")}
                          {...field}
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                          onClick={() => setShowConfirmPassword(!showConfirmPassword)}
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

              <Button type="submit" disabled={changePasswordMutation.isPending}>
                {changePasswordMutation.isPending ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : (
                  <Lock className="mr-2 h-4 w-4" />
                )}
                {t("settings.changePassword", "修改密码")}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
};

export default ProfileSettingsPage;

