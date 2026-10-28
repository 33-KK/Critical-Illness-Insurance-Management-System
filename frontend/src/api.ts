import { ElMessage } from "element-plus";
export type Row = Record<string, any>;
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T = any>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("anlan-token");
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  let response: Response;
  try {
    response = await fetch("/api" + path, { ...options, headers });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "无法连接服务，请确认后端已启动后重试。");
  }
  if (response.status === 401 && path !== "/auth/login")
    window.dispatchEvent(new Event("auth-expired"));
  let result: any;
  try {
    result = await response.json();
  } catch {
    throw new ApiError(response.status, "INVALID_RESPONSE", "服务器响应异常，请稍后重试。");
  }
  if (!response.ok || result.code !== "OK") {
    throw new ApiError(response.status, result.code, result.message || "操作失败，请稍后重试。");
  }
  return result.data as T;
}
export const get = <T = any>(path: string) => api<T>(path);
export const post = <T = any>(path: string, data?: any) =>
  api<T>(path, {
    method: "POST",
    body: data === undefined ? undefined : JSON.stringify(data),
  });
export const put = <T = any>(path: string, data: any) =>
  api<T>(path, { method: "PUT", body: JSON.stringify(data) });
export const remove = (path: string) => api(path, { method: "DELETE" });
export function query(values: Record<string, any>) {
  const p = new URLSearchParams();
  Object.entries(values).forEach(([k, v]) => {
    if (v !== "" && v !== undefined && v !== null) p.set(k, String(v));
  });
  return p.size ? "?" + p.toString() : "";
}
export function showError(error: unknown) {
  ElMessage.error(error instanceof Error ? error.message : "操作失败，请稍后重试。");
}
export function money(value: any) {
  return Number(value || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}
export function dateTime(value: any) {
  return value ? String(value).replace("T", " ").slice(0, 19) : "—";
}
export const uuid = () => crypto.randomUUID();
export async function downloadFile(id: number, name: string) {
  const response = await fetch(`/api/files/${id}/download`, {
    headers: {
      Authorization: `Bearer ${localStorage.getItem("anlan-token") || ""}`,
    },
  });
  if (!response.ok) {
    const e = await response.json().catch(() => ({ message: "下载失败" }));
    if (response.status === 401) window.dispatchEvent(new Event("auth-expired"));
    throw new Error(e.message || "下载失败");
  }
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
