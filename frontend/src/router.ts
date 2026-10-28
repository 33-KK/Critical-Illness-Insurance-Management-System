import { createRouter, createWebHistory } from "vue-router";
import { auth, loadUser, has, clearAuth } from "./auth";
import Login from "./views/Login.vue";
// Load business pages when visited so login does not download the chart bundle.
const Dashboard = () => import("./views/Dashboard.vue");
const Patients = () => import("./views/Patients.vue");
const Visits = () => import("./views/Visits.vue");
const VisitCreate = () => import("./views/VisitCreate.vue");
const Transactions = () => import("./views/Transactions.vue");
const ResourcePage = () => import("./views/ResourcePage.vue");
const Roles = () => import("./views/Roles.vue");
const Files = () => import("./views/Files.vue");
const Logs = () => import("./views/Logs.vue");
const Profile = () => import("./views/Profile.vue");
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: Login, meta: { public: true, title: "登录" } },
    { path: "/", redirect: "/dashboard" },
    {
      path: "/dashboard",
      component: Dashboard,
      meta: { title: "工作台", permission: "dashboard:view" },
    },
    {
      path: "/patients",
      component: Patients,
      meta: { title: "参保人档案", permission: "patient:view" },
    },
    {
      path: "/plans",
      component: ResourcePage,
      props: { resource: "plans" },
      meta: { title: "保险方案", permission: "plan:view" },
    },
    {
      path: "/surgeries",
      component: ResourcePage,
      props: { resource: "surgeries" },
      meta: { title: "手术项目", permission: "surgery:view" },
    },
    {
      path: "/visits/new",
      component: VisitCreate,
      meta: { title: "新建就诊", permission: "visit:create" },
    },
    {
      path: "/visits",
      component: Visits,
      meta: { title: "就诊记录", permission: "visit:view" },
    },
    {
      path: "/reviews",
      alias: "/audits",
      component: Visits,
      props: { reviewMode: true },
      meta: { title: "保险审核", permission: "audit:review" },
    },
    {
      path: "/transactions",
      component: Transactions,
      meta: { title: "医保卡流水", permission: "card:ledger" },
    },
    {
      path: "/users",
      alias: "/admin/users",
      component: ResourcePage,
      props: { resource: "users" },
      meta: { title: "用户管理", permission: "system:user" },
    },
    {
      path: "/roles",
      alias: "/admin/roles",
      component: Roles,
      meta: { title: "角色权限", permission: "system:role" },
    },
    {
      path: "/orgs",
      alias: "/admin/orgs",
      component: ResourcePage,
      props: { resource: "orgs" },
      meta: { title: "组织管理", permission: "system:org" },
    },
    {
      path: "/dicts",
      alias: "/admin/dicts",
      component: ResourcePage,
      props: { resource: "dicts" },
      meta: { title: "数据字典", permission: "system:dict" },
    },
    {
      path: "/menus",
      alias: "/admin/menus",
      component: ResourcePage,
      props: { resource: "menus" },
      meta: { title: "菜单管理", permission: "system:menu" },
    },
    {
      path: "/logs",
      alias: "/admin/logs",
      component: Logs,
      meta: { title: "操作日志", permission: "system:log" },
    },
    {
      path: "/files",
      component: Files,
      meta: { title: "文件中心", permission: "file:manage" },
    },
    { path: "/profile", component: Profile, meta: { title: "个人中心" } },
    { path: "/:pathMatch(.*)*", redirect: "/dashboard" },
  ],
});
router.beforeEach(async (to) => {
  document.title = `${to.meta.title || "工作台"} · 安澜医保`;
  if (to.meta.public) {
    if (localStorage.getItem("anlan-token")) return "/dashboard";
    return true;
  }
  if (!localStorage.getItem("anlan-token"))
    return { path: "/login", query: { redirect: to.fullPath } };
  if (!auth.ready) {
    try {
      await loadUser();
    } catch {
      clearAuth();
      return "/login";
    }
  }
  if (!has(to.meta.permission as string)) {
    const first = router
      .getRoutes()
      .find(
        (r) =>
          r.path !== "/" && !r.meta.public && r.meta.permission && has(r.meta.permission as string),
      );
    return first?.path || "/profile";
  }
  document.title = `${to.meta.title} · 安澜医保`;
});
window.addEventListener("auth-expired", () => {
  const redirect = router.currentRoute.value.fullPath;
  clearAuth();
  if (router.currentRoute.value.path !== "/login")
    router.replace({ path: "/login", query: { redirect } });
});
