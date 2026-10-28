import { reactive } from "vue";
import { get, post } from "./api";
import type { Row } from "./api";
export const auth = reactive<{
  user: Row | null;
  ready: boolean;
  menus: Row[];
}>({ user: null, ready: false, menus: [] });
export const has = (permission?: string) =>
  !permission || !!auth.user?.permissions?.includes(permission);
export async function loadUser() {
  auth.user = await get("/auth/me");
  auth.menus = await get("/menus");
  auth.ready = true;
}
export async function login(data: Row) {
  const r = await post("/auth/login", data);
  localStorage.setItem("anlan-token", r.token);
  auth.user = r.user;
  try {
    auth.menus = await get("/menus");
    auth.ready = true;
  } catch (error) {
    clearAuth();
    throw error;
  }
}
export function clearAuth() {
  localStorage.removeItem("anlan-token");
  auth.user = null;
  auth.ready = false;
  auth.menus = [];
}
export const roleLabels: Record<string, string> = {
  ADMIN: "系统管理员",
  DOCTOR: "临床医生",
  REVIEWER: "保险审核员",
};
