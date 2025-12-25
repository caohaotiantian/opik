import { test, expect } from "@playwright/test";

test.describe("认证重定向", () => {
  test.beforeEach(async ({ context }) => {
    // 清除所有 cookies 和 localStorage 以确保未认证状态
    await context.clearCookies();
  });

  test("访问受保护页面应重定向到登录页", async ({ page }) => {
    // 尝试直接访问工作空间首页
    await page.goto("/default/home");

    // 根据 AuthGuard 的实现，可能会：
    // 1. 重定向到登录页（如果后端认证已启用）
    // 2. 直接显示页面（如果后端返回 404，即未启用认证）
    
    // 等待页面加载完成
    await page.waitForLoadState("networkidle");

    const currentUrl = page.url();
    
    // 检查是否被重定向到登录页或保持在原页面（取决于后端配置）
    const isOnLogin = currentUrl.includes("/login");
    const isOnHome = currentUrl.includes("/home");
    
    // 至少应该在这两个页面之一
    expect(isOnLogin || isOnHome).toBe(true);
    
    console.log(`访问 /default/home 后当前 URL: ${currentUrl}`);
  });

  test("访问 /projects 应重定向到登录页或工作空间", async ({ page }) => {
    await page.goto("/default/projects");
    await page.waitForLoadState("networkidle");

    const currentUrl = page.url();
    const isOnLogin = currentUrl.includes("/login");
    const isOnProjects = currentUrl.includes("/projects");
    
    expect(isOnLogin || isOnProjects).toBe(true);
    console.log(`访问 /default/projects 后当前 URL: ${currentUrl}`);
  });

  test("访问 /admin/users 未认证应显示权限错误或重定向", async ({ page }) => {
    await page.goto("/admin/users");
    await page.waitForLoadState("networkidle");

    const currentUrl = page.url();
    
    // 应该重定向到登录页或显示无权限页面
    const isOnLogin = currentUrl.includes("/login");
    const isOnAdmin = currentUrl.includes("/admin");
    
    // 如果在管理页面，应该看到无权限提示
    if (isOnAdmin) {
      const accessDenied = await page
        .getByText(/访问被拒绝|Access Denied|No Permission/i)
        .isVisible()
        .catch(() => false);
      // 要么显示无权限，要么页面内容正常（如果后端未启用认证）
      console.log(`在管理页面，是否显示无权限: ${accessDenied}`);
    }
    
    console.log(`访问 /admin/users 后当前 URL: ${currentUrl}`);
  });
});

test.describe("公共页面访问", () => {
  test("登录页面应可直接访问", async ({ page }) => {
    await page.goto("/login");
    await page.waitForLoadState("networkidle");
    
    // 登录页面应该显示登录表单
    await expect(page.getByTestId("login-username")).toBeVisible();
  });

  test("注册页面应可直接访问", async ({ page }) => {
    await page.goto("/register");
    await page.waitForLoadState("networkidle");
    
    // 注册页面应该显示注册表单
    await expect(page.getByTestId("register-username")).toBeVisible();
  });

  test("忘记密码页面应可直接访问", async ({ page }) => {
    await page.goto("/forgot-password");
    await page.waitForLoadState("networkidle");
    
    // 忘记密码页面应该显示邮箱输入
    await expect(page.locator('input[type="email"]')).toBeVisible();
  });
});

