package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

@Component
@RequiredArgsConstructor
public class Access {
  private final Store db;

  public User user() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof User user))
      throw new Api.Failure(401, "UNAUTHORIZED", "请先登录");
    return user;
  }

  public void require(String permission) {
    if (!has(permission)) throw Api.forbidden();
  }

  public boolean has(String permission) {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals(permission));
  }

  public void requireRole(String role) {
    if (!user().getRole().equals(role)) throw Api.forbidden();
  }

  public Map<String, Object> userView(User u) {
    Org org = db.orgs.selectById(u.getOrgId());
    Role role = db.roles.selectOne(new QueryWrapper<Role>().eq("code", u.getRole()));
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", u.getId());
    view.put("username", u.getUsername());
    view.put("displayName", u.getDisplayName());
    view.put("role", u.getRole());
    view.put("orgId", u.getOrgId());
    view.put("orgName", org == null ? "" : org.getName());
    view.put("phone", u.getPhone());
    view.put("email", u.getEmail());
    view.put("enabled", u.getEnabled());
    view.put(
        "permissions",
        role == null
            ? List.of()
            : Arrays.stream(role.getPermissions().split(",")).filter(s -> !s.isBlank()).toList());
    return view;
  }

  public void log(String action, String target, String detail) {
    Log log = new Log();
    log.setUsername(user().getUsername());
    log.setAction(action);
    log.setTarget(target);
    log.setDetail(detail);
    log.setCreatedAt(LocalDateTime.now());
    var attrs = RequestContextHolder.getRequestAttributes();
    log.setIp(
        attrs instanceof ServletRequestAttributes a ? a.getRequest().getRemoteAddr() : "system");
    db.logs.insert(log);
  }

  public static <T> T found(T value, String label) {
    if (value == null) throw Api.missing(label);
    return value;
  }

  public static void text(String value, String field, int max) {
    if (value == null || value.isBlank() || value.length() > max)
      throw Api.bad(field + "不能为空且长度不能超过 " + max);
  }

  public static void optional(String value, String field, int max) {
    if (value != null && value.length() > max) throw Api.bad(field + "长度不能超过 " + max);
  }
}
