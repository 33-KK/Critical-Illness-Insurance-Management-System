export interface Field {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  placeholder?: string;
  options?: { label: string; value: any }[];
  min?: number;
  max?: number;
  precision?: number;
  full?: boolean;
}
export interface Column {
  key: string;
  label: string;
  width?: number;
  type?: string;
  minWidth?: number;
}
export interface ResourceSchema {
  title: string;
  description: string;
  singular: string;
  endpoint: string;
  permission: string;
  deletable: boolean;
  columns: Column[];
  fields: Field[];
  defaults: Record<string, any>;
}
const enabled: Field = { key: "enabled", label: "状态", type: "switch" };
export const schemas: Record<string, ResourceSchema> = {
  plans: {
    title: "保险方案",
    description: "维护大病保险方案与各类门诊报销比例，规则调整不会改变历史就诊单。",
    singular: "保险方案",
    endpoint: "/plans",
    permission: "plan:manage",
    deletable: true,
    columns: [
      { key: "code", label: "方案编码", width: 140 },
      { key: "name", label: "方案名称", minWidth: 170 },
      { key: "rateA", label: "A 类报销", type: "percent", width: 120 },
      { key: "rateB", label: "B 类报销", type: "percent", width: 120 },
      { key: "rateC", label: "C 类报销", type: "percent", width: 120 },
      { key: "description", label: "方案说明", minWidth: 180 },
      { key: "enabled", label: "状态", type: "status", width: 95 },
    ],
    fields: [
      {
        key: "code",
        label: "方案编码",
        required: true,
        placeholder: "如 PLAN-001",
      },
      { key: "name", label: "方案名称", required: true },
      {
        key: "rateA",
        label: "A 类门诊报销率（%）",
        type: "number",
        required: true,
        min: 0,
        max: 100,
        precision: 2,
      },
      {
        key: "rateB",
        label: "B 类门诊报销率（%）",
        type: "number",
        required: true,
        min: 0,
        max: 100,
        precision: 2,
      },
      {
        key: "rateC",
        label: "C 类门诊报销率（%）",
        type: "number",
        required: true,
        min: 0,
        max: 100,
        precision: 2,
      },
      enabled,
      { key: "description", label: "方案说明", type: "textarea", full: true },
    ],
    defaults: {
      code: "",
      name: "",
      rateA: 80,
      rateB: 70,
      rateC: 60,
      description: "",
      enabled: true,
    },
  },
  surgeries: {
    title: "手术项目",
    description: "建立统一的手术项目与价格档案，为就诊费用核算提供标准依据。",
    singular: "手术项目",
    endpoint: "/surgeries",
    permission: "surgery:manage",
    deletable: true,
    columns: [
      { key: "code", label: "项目编码", width: 140 },
      { key: "name", label: "手术名称", minWidth: 190 },
      { key: "category", label: "项目分类", width: 135 },
      { key: "price", label: "标准单价", type: "money", width: 150 },
      { key: "description", label: "项目说明", minWidth: 200 },
      { key: "enabled", label: "状态", type: "status", width: 95 },
    ],
    fields: [
      { key: "code", label: "项目编码", required: true },
      { key: "name", label: "手术名称", required: true },
      {
        key: "category",
        label: "项目分类",
        required: true,
        placeholder: "如心血管外科",
      },
      {
        key: "price",
        label: "标准单价（元）",
        type: "number",
        required: true,
        min: 0,
        max: 10000000,
        precision: 2,
      },
      enabled,
      { key: "description", label: "项目说明", type: "textarea", full: true },
    ],
    defaults: {
      code: "",
      name: "",
      category: "",
      price: 0,
      description: "",
      enabled: true,
    },
  },
  users: {
    title: "用户管理",
    description: "管理团队账户、角色归属与组织信息，按职责分配工作权限。",
    singular: "用户",
    endpoint: "/admin/users",
    permission: "system:user",
    deletable: false,
    columns: [
      { key: "username", label: "登录账号", width: 135 },
      { key: "displayName", label: "用户姓名", minWidth: 130 },
      { key: "role", label: "角色", type: "status", width: 120 },
      { key: "orgName", label: "所属组织", minWidth: 180 },
      { key: "phone", label: "联系电话", width: 145 },
      { key: "email", label: "邮箱", minWidth: 190 },
      { key: "enabled", label: "状态", type: "status", width: 95 },
    ],
    fields: [
      { key: "username", label: "登录账号", required: true },
      { key: "displayName", label: "用户姓名", required: true },
      {
        key: "password",
        label: "登录密码",
        type: "password",
        placeholder: "新增必填，编辑留空则不修改",
      },
      {
        key: "role",
        label: "用户角色",
        type: "select",
        required: true,
        options: [
          { label: "系统管理员", value: "ADMIN" },
          { label: "临床医生", value: "DOCTOR" },
          { label: "保险审核员", value: "REVIEWER" },
        ],
      },
      { key: "orgId", label: "所属组织", type: "select", required: true },
      { key: "phone", label: "联系电话" },
      { key: "email", label: "电子邮箱" },
      enabled,
    ],
    defaults: {
      username: "",
      displayName: "",
      password: "",
      role: "DOCTOR",
      orgId: undefined,
      phone: "",
      email: "",
      enabled: true,
    },
  },
  orgs: {
    title: "组织管理",
    description: "维护医院、保险机构与系统组织，建立清晰的业务协作关系。",
    singular: "组织",
    endpoint: "/admin/orgs",
    permission: "system:org",
    deletable: true,
    columns: [
      { key: "name", label: "组织名称", minWidth: 210 },
      { key: "type", label: "组织类型", type: "orgType", width: 140 },
      { key: "parentId", label: "上级组织", type: "parent", width: 170 },
      { key: "contactPerson", label: "联系人", width: 130 },
      { key: "phone", label: "联系电话", width: 160 },
      { key: "enabled", label: "状态", type: "status", width: 95 },
    ],
    fields: [
      { key: "name", label: "组织名称", required: true },
      {
        key: "type",
        label: "组织类型",
        type: "select",
        required: true,
        options: [
          { label: "系统机构", value: "SYSTEM" },
          { label: "医疗机构", value: "HOSPITAL" },
          { label: "保险机构", value: "INSURER" },
        ],
      },
      { key: "parentId", label: "上级组织", type: "select" },
      { key: "contactPerson", label: "联系人" },
      { key: "phone", label: "联系电话" },
      enabled,
    ],
    defaults: {
      name: "",
      type: "HOSPITAL",
      parentId: null,
      contactPerson: "",
      phone: "",
      enabled: true,
    },
  },
  dicts: {
    title: "数据字典",
    description: "统一维护业务字典与展示名称，保持系统数据含义一致。",
    singular: "字典项",
    endpoint: "/admin/dicts",
    permission: "system:dict",
    deletable: true,
    columns: [
      { key: "type", label: "字典类型", minWidth: 170 },
      { key: "code", label: "字典编码", minWidth: 160 },
      { key: "label", label: "显示名称", minWidth: 180 },
      { key: "sortOrder", label: "排序", width: 100 },
      { key: "enabled", label: "状态", type: "status", width: 95 },
    ],
    fields: [
      {
        key: "type",
        label: "字典类型",
        required: true,
        placeholder: "如 GENDER",
      },
      { key: "code", label: "字典编码", required: true },
      { key: "label", label: "显示名称", required: true },
      {
        key: "sortOrder",
        label: "显示顺序",
        type: "number",
        min: 0,
        max: 9999,
        precision: 0,
      },
      enabled,
    ],
    defaults: { type: "", code: "", label: "", sortOrder: 0, enabled: true },
  },
  menus: {
    title: "菜单管理",
    description: "管理导航菜单、路由与访问权限，用户仅能看到有权访问的业务入口。",
    singular: "菜单",
    endpoint: "/admin/menus",
    permission: "system:menu",
    deletable: true,
    columns: [
      { key: "name", label: "菜单名称", minWidth: 160 },
      { key: "path", label: "页面路径", minWidth: 150 },
      { key: "permission", label: "访问权限", minWidth: 160 },
      { key: "icon", label: "图标标识", width: 140 },
      { key: "sortOrder", label: "排序", width: 80 },
      { key: "enabled", label: "状态", type: "status", width: 95 },
    ],
    fields: [
      { key: "name", label: "菜单名称", required: true },
      {
        key: "path",
        label: "页面路径",
        required: true,
        placeholder: "如 /patients",
      },
      { key: "icon", label: "图标标识", placeholder: "如 Users" },
      { key: "permission", label: "访问权限", type: "select" },
      { key: "parentId", label: "上级菜单", type: "select" },
      {
        key: "sortOrder",
        label: "显示顺序",
        type: "number",
        min: 0,
        max: 9999,
        precision: 0,
      },
      enabled,
    ],
    defaults: {
      name: "",
      path: "",
      icon: "",
      permission: "",
      parentId: null,
      sortOrder: 0,
      enabled: true,
    },
  },
};
