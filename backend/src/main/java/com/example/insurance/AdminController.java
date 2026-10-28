package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurance.Domain.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminController {
  private final Store db;
  private final Access access;
  private final PasswordEncoder encoder;

  @GetMapping("/admin/users")
  @Operation(summary = "查询用户", tags = "用户管理", description = "返回安全用户视图，不返回密码摘要。")
  public Api<?> users(@RequestParam(required = false) String keyword) {
    access.require("system:user");
    QueryWrapper<User> q = new QueryWrapper<>();
    if (keyword != null && !keyword.isBlank())
      q.and(w -> w.like("username", keyword).or().like("display_name", keyword));
    return Api.ok(db.users.selectList(q.orderByAsc("id")).stream().map(access::userView).toList());
  }

  @PostMapping("/admin/users")
  @Operation(summary = "创建用户", tags = "用户管理")
  @Transactional
  public Api<?> userCreate(@Valid @RequestBody Requests.UserWrite input) {
    access.require("system:user");
    if (input.password() == null) throw Api.bad("新用户必须设置密码");
    User user = new User();
    applyUser(user, input);
    db.users.insert(user);
    access.log("USER_CREATE", user.getUsername(), "创建用户，角色 " + user.getRole());
    return Api.ok(access.userView(user));
  }

  @PutMapping("/admin/users/{id}")
  @Operation(summary = "更新用户", tags = "用户管理")
  @Transactional
  public Api<?> userUpdate(@PathVariable Long id, @Valid @RequestBody Requests.UserWrite input) {
    access.require("system:user");
    User user = Access.found(db.users.lockById(id), "用户");
    if (id.equals(access.user().getId())
        && (!input.enabled() || !input.role().equals(user.getRole())))
      throw Api.bad("不能停用自己或更改自己的角色");
    applyUser(user, input);
    LambdaUpdateWrapper<User> update =
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, id)
            .set(User::getUsername, user.getUsername())
            .set(User::getDisplayName, user.getDisplayName())
            .set(User::getRole, user.getRole())
            .set(User::getOrgId, user.getOrgId())
            .set(User::getPhone, user.getPhone())
            .set(User::getEmail, user.getEmail())
            .set(User::getEnabled, user.getEnabled());
    if (input.password() != null) update.set(User::getPasswordHash, user.getPasswordHash());
    db.users.update(null, update);
    if (!user.getEnabled() || input.password() != null)
      db.sessions.delete(new QueryWrapper<Session>().eq("user_id", id));
    access.log("USER_UPDATE", user.getUsername(), "更新用户");
    return Api.ok(access.userView(user));
  }

  private void applyUser(User u, Requests.UserWrite i) {
    Org org = Access.found(db.orgs.selectById(i.orgId()), "组织");
    if (!Boolean.TRUE.equals(org.getEnabled())) throw Api.bad("组织已停用");
    Access.found(db.roles.selectOne(new QueryWrapper<Role>().eq("code", i.role())), "角色");
    u.setUsername(i.username());
    u.setDisplayName(i.displayName());
    u.setRole(i.role());
    u.setOrgId(i.orgId());
    u.setPhone(i.phone());
    u.setEmail(i.email());
    u.setEnabled(i.enabled());
    if (i.password() != null) u.setPasswordHash(encoder.encode(i.password()));
  }

  @PutMapping("/admin/users/{id}/password")
  @Operation(summary = "重置用户密码", tags = "用户管理", description = "重置后撤销该用户全部会话。")
  @Transactional
  public Api<?> password(@PathVariable Long id, @Valid @RequestBody Requests.ResetPassword input) {
    access.require("system:user");
    User user = Access.found(db.users.lockById(id), "用户");
    db.users.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, id)
            .set(User::getPasswordHash, encoder.encode(input.password())));
    db.sessions.delete(new QueryWrapper<Session>().eq("user_id", id));
    access.log("PASSWORD_RESET", user.getUsername(), "重置密码并撤销会话");
    return Api.ok();
  }

  @GetMapping("/admin/roles")
  @Operation(summary = "查询角色", tags = "角色权限")
  public Api<?> roles() {
    access.require("system:role");
    return Api.ok(db.roles.selectList(null).stream().map(this::roleView).toList());
  }

  private Map<String, Object> roleView(Role role) {
    return Map.of(
        "id",
        role.getId(),
        "code",
        role.getCode(),
        "name",
        role.getName(),
        "permissions",
        role.getPermissions().isBlank()
            ? List.of()
            : Arrays.asList(role.getPermissions().split(",")));
  }

  @PutMapping("/admin/roles/{id}")
  @Operation(summary = "更新角色权限", tags = "角色权限")
  @Transactional
  public Api<?> role(@PathVariable Long id, @Valid @RequestBody Requests.RoleWrite input) {
    access.require("system:role");
    Role role = Access.found(db.roles.selectById(id), "角色");
    if (!Permissions.CODES.containsAll(input.permissions())) throw Api.bad("包含未知权限代码");
    if ("ADMIN".equals(role.getCode())
        && !input
            .permissions()
            .containsAll(List.of("system:user", "system:role", "system:org", "system:menu")))
      throw Api.bad("管理员必须保留用户、角色、组织、菜单管理权限，避免系统失去管理入口");
    role.setName(input.name());
    role.setPermissions(String.join(",", new LinkedHashSet<>(input.permissions())));
    db.roles.updateById(role);
    access.log("ROLE_UPDATE", role.getCode(), "更新角色权限");
    return Api.ok(roleView(role));
  }

  @GetMapping("/admin/permissions")
  @Operation(summary = "查询可配置权限", tags = "角色权限")
  public Api<?> permissions() {
    access.require("system:role");
    return Api.ok(Permissions.ALL);
  }

  @GetMapping("/admin/orgs")
  @Operation(summary = "查询组织", tags = "组织管理")
  public Api<?> orgs() {
    access.require("system:org");
    return Api.ok(db.orgs.selectList(new QueryWrapper<Org>().orderByAsc("id")));
  }

  @PostMapping("/admin/orgs")
  @Operation(summary = "创建组织", tags = "组织管理")
  @Transactional
  public Api<?> orgCreate(@RequestBody Org org) {
    access.require("system:org");
    validateOrg(org);
    org.setId(null);
    db.orgs.insert(org);
    access.log("ORG_CREATE", org.getName(), "创建组织");
    return Api.ok(org);
  }

  @PutMapping("/admin/orgs/{id}")
  @Operation(summary = "更新组织", tags = "组织管理")
  @Transactional
  public Api<?> orgUpdate(@PathVariable Long id, @RequestBody Org org) {
    access.require("system:org");
    Access.found(db.orgs.selectById(id), "组织");
    org.setId(id);
    validateOrg(org);
    db.orgs.updateById(org);
    access.log("ORG_UPDATE", org.getName(), "更新组织");
    return Api.ok(org);
  }

  @DeleteMapping("/admin/orgs/{id}")
  @Operation(summary = "删除未引用组织", tags = "组织管理", description = "已经被用户或下级组织引用时返回 409。")
  @Transactional
  public Api<?> orgDelete(@PathVariable Long id) {
    access.require("system:org");
    Org org = Access.found(db.orgs.selectById(id), "组织");
    if (db.users.selectCount(new QueryWrapper<User>().eq("org_id", id)) > 0
        || db.orgs.selectCount(new QueryWrapper<Org>().eq("parent_id", id)) > 0)
      throw Api.conflict("组织已被用户或下级组织引用，请先停用");
    db.orgs.deleteById(id);
    access.log("ORG_DELETE", org.getName(), "删除未引用组织");
    return Api.ok();
  }

  private void validateOrg(Org org) {
    Access.text(org.getName(), "组织名称", 100);
    if (!Set.of("SYSTEM", "HOSPITAL", "INSURER").contains(Objects.toString(org.getType(), "")))
      throw Api.bad("组织类型不正确");
    Access.optional(org.getContactPerson(), "联系人", 100);
    Access.optional(org.getPhone(), "手机号", 30);
    Set<Long> seen = new HashSet<>();
    Long parent = org.getParentId();
    while (parent != null && parent != 0) {
      if (parent.equals(org.getId()) || !seen.add(parent)) throw Api.bad("组织父级不能形成循环");
      parent = Access.found(db.orgs.selectById(parent), "上级组织").getParentId();
    }
    if (org.getEnabled() == null) org.setEnabled(true);
  }

  @GetMapping("/admin/dicts")
  @Operation(summary = "查询数据字典", tags = "数据字典")
  public Api<?> dicts(
      @RequestParam(required = false) String keyword, @RequestParam(required = false) String type) {
    access.require("system:dict");
    QueryWrapper<Dict> q = new QueryWrapper<>();
    if (type != null && !type.isBlank()) q.eq("type", type);
    if (keyword != null && !keyword.isBlank())
      q.and(w -> w.like("type", keyword).or().like("label", keyword).or().like("code", keyword));
    return Api.ok(db.dicts.selectList(q.orderByAsc("type", "sort_order", "id")));
  }

  @PostMapping("/admin/dicts")
  @Operation(summary = "创建字典项", tags = "数据字典")
  @Transactional
  public Api<?> dictCreate(@RequestBody Dict dict) {
    access.require("system:dict");
    validateDict(dict);
    dict.setId(null);
    db.dicts.insert(dict);
    access.log("DICT_CREATE", dict.getType() + ":" + dict.getCode(), "创建字典");
    return Api.ok(dict);
  }

  @PutMapping("/admin/dicts/{id}")
  @Operation(summary = "更新字典项", tags = "数据字典")
  @Transactional
  public Api<?> dictUpdate(@PathVariable Long id, @RequestBody Dict dict) {
    access.require("system:dict");
    Access.found(db.dicts.selectById(id), "字典");
    validateDict(dict);
    dict.setId(id);
    db.dicts.updateById(dict);
    access.log("DICT_UPDATE", dict.getCode(), "更新字典");
    return Api.ok(dict);
  }

  @DeleteMapping("/admin/dicts/{id}")
  @Operation(summary = "删除字典项", tags = "数据字典")
  @Transactional
  public Api<?> dictDelete(@PathVariable Long id) {
    access.require("system:dict");
    Access.found(db.dicts.selectById(id), "字典");
    db.dicts.deleteById(id);
    access.log("DICT_DELETE", id.toString(), "删除字典");
    return Api.ok();
  }

  private void validateDict(Dict d) {
    Access.text(d.getType(), "字典类型", 50);
    Access.text(d.getCode(), "字典编码", 50);
    Access.text(d.getLabel(), "字典名称", 100);
    if (d.getEnabled() == null) d.setEnabled(true);
    if (d.getSortOrder() == null) d.setSortOrder(0);
  }

  @GetMapping("/admin/menus")
  @Operation(summary = "查询菜单配置", tags = "菜单管理")
  public Api<?> menus() {
    access.require("system:menu");
    return Api.ok(db.menus.selectList(new QueryWrapper<Menu>().orderByAsc("sort_order", "id")));
  }

  @GetMapping("/menus")
  @Operation(summary = "查询当前用户可见菜单", tags = "菜单管理")
  public Api<?> visibleMenus() {
    access.user();
    return Api.ok(
        db
            .menus
            .selectList(new QueryWrapper<Menu>().eq("enabled", true).orderByAsc("sort_order", "id"))
            .stream()
            .filter(
                m ->
                    m.getPermission() == null
                        || m.getPermission().isBlank()
                        || access.has(m.getPermission()))
            .toList());
  }

  @PostMapping("/admin/menus")
  @Operation(summary = "创建菜单", tags = "菜单管理")
  @Transactional
  public Api<?> menuCreate(@RequestBody Menu menu) {
    access.require("system:menu");
    validateMenu(menu);
    menu.setId(null);
    db.menus.insert(menu);
    access.log("MENU_CREATE", menu.getName(), "创建菜单");
    return Api.ok(menu);
  }

  @PutMapping("/admin/menus/{id}")
  @Operation(summary = "更新菜单", tags = "菜单管理")
  @Transactional
  public Api<?> menuUpdate(@PathVariable Long id, @RequestBody Menu menu) {
    access.require("system:menu");
    Access.found(db.menus.selectById(id), "菜单");
    menu.setId(id);
    validateMenu(menu);
    db.menus.updateById(menu);
    access.log("MENU_UPDATE", menu.getName(), "更新菜单");
    return Api.ok(menu);
  }

  @DeleteMapping("/admin/menus/{id}")
  @Operation(summary = "删除菜单", tags = "菜单管理")
  @Transactional
  public Api<?> menuDelete(@PathVariable Long id) {
    access.require("system:menu");
    Access.found(db.menus.selectById(id), "菜单");
    if (db.menus.selectCount(new QueryWrapper<Menu>().eq("parent_id", id)) > 0)
      throw Api.conflict("请先删除下级菜单");
    db.menus.deleteById(id);
    access.log("MENU_DELETE", id.toString(), "删除菜单");
    return Api.ok();
  }

  private void validateMenu(Menu m) {
    Access.text(m.getName(), "菜单名称", 100);
    Access.text(m.getPath(), "路径", 100);
    if (!m.getPath().startsWith("/") || m.getPath().startsWith("//")) throw Api.bad("菜单必须使用站内路径");
    Access.optional(m.getIcon(), "图标", 50);
    if (m.getPermission() != null
        && !m.getPermission().isBlank()
        && !Permissions.CODES.contains(m.getPermission())) throw Api.bad("未知权限代码");
    Set<Long> seen = new HashSet<>();
    Long parent = m.getParentId();
    while (parent != null && parent != 0) {
      if (parent.equals(m.getId()) || !seen.add(parent)) throw Api.bad("菜单父级不能形成循环");
      parent = Access.found(db.menus.selectById(parent), "上级菜单").getParentId();
    }
    if (m.getEnabled() == null) m.setEnabled(true);
    if (m.getSortOrder() == null) m.setSortOrder(0);
  }

  @GetMapping("/admin/logs")
  @Operation(summary = "查询系统操作日志", tags = "系统日志")
  public Api<?> logs(
      @RequestParam(required = false) String keyword, @RequestParam(required = false) String type) {
    access.require("system:log");
    QueryWrapper<Log> q = new QueryWrapper<>();
    if (type != null && !type.isBlank()) q.eq("action", type);
    if (keyword != null && !keyword.isBlank())
      q.and(
          w ->
              w.like("username", keyword)
                  .or()
                  .like("action", keyword)
                  .or()
                  .like("target", keyword)
                  .or()
                  .like("detail", keyword));
    return Api.ok(db.logs.selectList(q.orderByDesc("id")));
  }
}
