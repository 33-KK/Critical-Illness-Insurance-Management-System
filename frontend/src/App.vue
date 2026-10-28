<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import {
  ShieldPlus,
  LayoutDashboard,
  Users,
  HeartPulse,
  Stethoscope,
  ClipboardList,
  ClipboardCheck,
  WalletCards,
  UserRoundCog,
  KeyRound,
  Building2,
  BookOpen,
  PanelLeft,
  ScrollText,
  FolderOpen,
  ChevronDown,
  ChevronRight,
  X,
} from "lucide-vue-next";
import { auth, has, clearAuth, roleLabels } from "./auth";
import { post } from "./api";
const route = useRoute(),
  router = useRouter(),
  collapsed = ref(false),
  mobileOpen = ref(false),
  systemExpanded = ref(false),
  smallScreen = ref(false),
  navigationToggle = ref<HTMLButtonElement | null>(null),
  navigationClose = ref<HTMLButtonElement | null>(null);
const navigationMedia = window.matchMedia("(max-width: 760px)");
function syncNavigationSize() {
  smallScreen.value = navigationMedia.matches;
  if (smallScreen.value) collapsed.value = false;
  else mobileOpen.value = false;
}
onMounted(() => {
  syncNavigationSize();
  navigationMedia.addEventListener("change", syncNavigationSize);
});
onBeforeUnmount(() => navigationMedia.removeEventListener("change", syncNavigationSize));
watch(mobileOpen, async (open) => {
  await nextTick();
  if (open) navigationClose.value?.focus();
  else if (smallScreen.value) navigationToggle.value?.focus();
});
const menuIcons = {
  LayoutDashboard,
  Users,
  HeartPulse,
  Stethoscope,
  ClipboardList,
  ClipboardCheck,
  WalletCards,
  UserRoundCog,
  KeyRound,
  Building2,
  BookOpen,
  PanelLeft,
  ScrollText,
  FolderOpen,
  DataAnalysis: LayoutDashboard,
  User: Users,
  DocumentChecked: HeartPulse,
  FirstAidKit: Stethoscope,
  Tickets: ClipboardList,
  CircleCheck: ClipboardCheck,
  Wallet: WalletCards,
  UserFilled: UserRoundCog,
  Lock: KeyRound,
  OfficeBuilding: Building2,
  Collection: BookOpen,
  Menu: PanelLeft,
  List: ScrollText,
  Folder: FolderOpen,
};
const groups = [
  {
    name: "总览",
    items: [
      {
        path: "/dashboard",
        name: "工作台",
        icon: LayoutDashboard,
        p: "dashboard:view",
      },
    ],
  },
  {
    name: "医保业务",
    items: [
      { path: "/patients", name: "参保人档案", icon: Users, p: "patient:view" },
      { path: "/plans", name: "保险方案", icon: HeartPulse, p: "plan:view" },
      {
        path: "/surgeries",
        name: "手术项目",
        icon: Stethoscope,
        p: "surgery:view",
      },
      {
        path: "/visits",
        name: "就诊记录",
        icon: ClipboardList,
        p: "visit:view",
      },
      {
        path: "/reviews",
        name: "保险审核",
        icon: ClipboardCheck,
        p: "audit:review",
      },
      {
        path: "/transactions",
        name: "医保卡流水",
        icon: WalletCards,
        p: "card:ledger",
      },
    ],
  },
  {
    name: "系统管理",
    items: [
      {
        path: "/users",
        name: "用户管理",
        icon: UserRoundCog,
        p: "system:user",
      },
      { path: "/roles", name: "角色权限", icon: KeyRound, p: "system:role" },
      { path: "/orgs", name: "组织管理", icon: Building2, p: "system:org" },
      { path: "/dicts", name: "数据字典", icon: BookOpen, p: "system:dict" },
      { path: "/menus", name: "菜单管理", icon: PanelLeft, p: "system:menu" },
      { path: "/logs", name: "操作日志", icon: ScrollText, p: "system:log" },
    ],
  },
  {
    name: "工作空间",
    items: [{ path: "/files", name: "文件中心", icon: FolderOpen, p: "file:manage" }],
  },
];
const visibleGroups = computed(() => {
  const definitions = groups.flatMap((group) =>
    group.items.map((item) => ({ ...item, group: group.name })),
  );
  const items = auth.menus
    .filter((menu) => menu.enabled !== false && has(menu.permission))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || a.id - b.id)
    .map((menu) => {
      const definition =
        definitions.find((item) => item.p === menu.permission) ||
        definitions.find((item) => item.path === menu.path);
      return {
        ...menu,
        path: String(menu.path),
        name: String(menu.name),
        icon: menuIcons[menu.icon as keyof typeof menuIcons] || definition?.icon || FolderOpen,
        group: definition?.group || "其他功能",
      };
    });
  return [...new Set(items.map((item) => item.group))].map((name) => ({
    name,
    items: items.filter((item) => item.group === name),
  }));
});
const canonical = (path: string) =>
  path === "/audits" ? "/reviews" : path.startsWith("/admin/") ? path.slice(6) : path;
const active = (path: string) =>
  canonical(route.path) === canonical(path) ||
  (canonical(path) === "/visits" && route.path === "/visits/new");
watch(
  () => route.path,
  () => {
    mobileOpen.value = false;
    if (
      visibleGroups.value.some(
        (group) => group.name === "系统管理" && group.items.some((item) => active(item.path)),
      )
    )
      systemExpanded.value = true;
  },
  { immediate: true },
);
function toggleNavigation() {
  if (smallScreen.value) mobileOpen.value = !mobileOpen.value;
  else collapsed.value = !collapsed.value;
}
async function logout() {
  try {
    await ElMessageBox.confirm("退出后需重新登录才能继续处理业务。", "退出登录", {
      confirmButtonText: "退出登录",
      cancelButtonText: "取消",
    });
    await post("/auth/logout").catch(() => {});
    clearAuth();
    router.replace("/login");
  } catch {}
}
</script>
<template>
  <router-view v-if="route.meta.public" />
  <div
    v-else
    class="app-shell"
    :class="{ 'sidebar-collapsed': collapsed, 'mobile-nav-open': mobileOpen }"
    @keydown.esc="mobileOpen = false"
  >
    <button
      v-if="mobileOpen"
      class="navigation-backdrop"
      aria-label="关闭导航"
      @click="mobileOpen = false"
    ></button>
    <aside
      id="main-navigation"
      class="sidebar"
      aria-label="主导航"
      :inert="smallScreen && !mobileOpen"
      :aria-hidden="smallScreen && !mobileOpen ? true : undefined"
    >
      <router-link class="brand" to="/dashboard">
        <span class="brand-mark"><ShieldPlus :size="26" :stroke-width="1.8" /></span>
        <span class="brand-copy">
          <strong>安澜医保</strong>
          <small>大病保险业务管理平台</small>
        </span>
      </router-link>
      <button
        ref="navigationClose"
        class="mobile-nav-close icon-button"
        aria-label="关闭导航"
        @click="mobileOpen = false"
      >
        <X :size="20" />
      </button>
      <nav class="sidebar-scroll">
        <div class="nav-group" v-for="group in visibleGroups" :key="group.name">
          <button
            v-if="group.name === '系统管理' && !collapsed"
            class="nav-label nav-group-toggle"
            :aria-expanded="systemExpanded"
            aria-controls="system-navigation-items"
            @click="systemExpanded = !systemExpanded"
          >
            <span>{{ group.name }}</span>
            <ChevronDown v-if="systemExpanded" :size="14" />
            <ChevronRight v-else :size="14" />
          </button>
          <div v-else class="nav-label">{{ group.name }}</div>
          <div
            :id="group.name === '系统管理' ? 'system-navigation-items' : undefined"
            v-show="group.name !== '系统管理' || systemExpanded || collapsed"
          >
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ active: active(item.path) }"
              :aria-current="active(item.path) ? 'page' : undefined"
              :title="item.name"
            >
              <component :is="item.icon" :size="18" :stroke-width="1.7" />
              <span>{{ item.name }}</span>
            </router-link>
          </div>
        </div>
      </nav>
      <div class="sidebar-bottom">
        <ShieldPlus :size="17" />
        <div>
          <strong>医保业务协作</strong>
          <span>参保 · 就诊 · 审核 · 结算</span>
        </div>
      </div>
    </aside>
    <div class="main-shell" :inert="smallScreen && mobileOpen">
      <header class="topbar">
        <div class="topbar-left">
          <button
            ref="navigationToggle"
            class="icon-button"
            title="收起或展开导航"
            aria-label="收起或展开导航"
            aria-controls="main-navigation"
            :aria-expanded="smallScreen ? mobileOpen : !collapsed"
            @click="toggleNavigation"
          >
            <PanelLeft :size="20" />
          </button>
          <router-link to="/dashboard" class="breadcrumb-root">业务中心</router-link>
          <ChevronRight class="breadcrumb-divider" :size="14" />
          <span class="breadcrumb-current">{{ route.meta.title }}</span>
        </div>
        <div class="topbar-right">
          <span class="demo-environment">本地演示</span>
          <div class="topbar-rule"></div>
          <el-dropdown
            trigger="click"
            @command="(c: string) => (c === 'logout' ? logout() : router.push('/profile'))"
          >
            <button class="user-menu">
              <span class="avatar">{{ auth.user?.displayName?.slice(-2) || "安澜" }}</span>
              <span class="user-menu-copy">
                <strong>{{ auth.user?.displayName }}</strong>
                <small>{{ roleLabels[auth.user?.role || ""] || auth.user?.role }}</small>
              </span>
              <ChevronDown :size="14" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="page-content"><router-view :key="route.path" /></main>
      <footer class="app-footer">
        <span>安澜医保 · 大病保险业务管理平台</span>
        <span>参保管理与报销服务工作空间</span>
      </footer>
    </div>
  </div>
</template>
