package com.example.insurance;

import java.util.*;

public final class Permissions {
  private Permissions() {}

  public static final List<Map<String, String>> ALL =
      List.of(
          permission("dashboard:view", "首页统计", "业务"),
          permission("patient:view", "参保人查询", "业务"),
          permission("patient:manage", "参保人维护", "业务"),
          permission("card:recharge", "医保卡充值与补缴", "资金"),
          permission("card:ledger", "资金流水", "资金"),
          permission("plan:view", "保险查询", "业务"),
          permission("plan:manage", "保险维护", "业务"),
          permission("surgery:view", "手术查询", "业务"),
          permission("surgery:manage", "手术维护", "业务"),
          permission("visit:view", "就诊查询", "业务"),
          permission("visit:create", "就诊登记", "业务"),
          permission("audit:review", "报销审核", "业务"),
          permission("system:user", "用户管理", "系统"),
          permission("system:role", "角色权限", "系统"),
          permission("system:org", "组织管理", "系统"),
          permission("system:dict", "数据字典", "系统"),
          permission("system:menu", "菜单管理", "系统"),
          permission("system:log", "系统日志", "系统"),
          permission("file:manage", "文件管理", "系统"));

  private static Map<String, String> permission(String code, String name, String group) {
    return Map.of("code", code, "name", name, "group", group);
  }

  public static final Set<String> CODES =
      ALL.stream().map(m -> m.get("code")).collect(java.util.stream.Collectors.toUnmodifiableSet());
}
