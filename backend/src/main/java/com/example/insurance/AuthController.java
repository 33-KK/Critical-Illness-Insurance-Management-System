package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurance.Domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final Store db;
  private final Access access;
  private final PasswordEncoder encoder;
  private final CaptchaService captcha;
  private static final SecureRandom RANDOM = new SecureRandom();
  private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

  private record Attempts(int count, Instant until) {}

  @Value("${app.session-hours:8}")
  private int hours;

  @GetMapping("/captcha")
  @Operation(
      summary = "获取一次性验证码",
      tags = "身份认证",
      description = "返回 captchaId 和 SVG data URL；登录时提交验证码，使用后立即失效。")
  @SecurityRequirements
  public Api<?> captcha() {
    return Api.ok(captcha.create());
  }

  @PostMapping("/login")
  @Operation(
      summary = "登录",
      tags = "身份认证",
      description = "校验账号、密码和一次性验证码，返回随机 opaque token 与安全用户视图。后续请求使用 Authorization: Bearer token。")
  @SecurityRequirements
  @Transactional
  public Api<?> login(@Valid @RequestBody Requests.Login input, HttpServletRequest request) {
    String key = request.getRemoteAddr() + ":" + input.username();
    Attempts current = attempts.get(key);
    if (current != null && current.until().isAfter(Instant.now()) && current.count() >= 8)
      throw new Api.Failure(429, "LOGIN_LIMIT", "尝试次数过多，请稍后再试");
    captcha.consume(input.captchaId(), input.captchaCode());
    // Serialize session creation with password changes and account updates.
    User user = db.users.lockByUsername(input.username());
    if (user == null
        || !Boolean.TRUE.equals(user.getEnabled())
        || !encoder.matches(input.password(), user.getPasswordHash())) {
      attempts.compute(
          key,
          (k, v) ->
              new Attempts(
                  v != null && v.until().isAfter(Instant.now()) ? v.count() + 1 : 1,
                  Instant.now().plusSeconds(300)));
      throw new Api.Failure(401, "BAD_CREDENTIALS", "账号或密码错误，或账号已停用");
    }
    attempts.remove(key);
    attempts.entrySet().removeIf(e -> e.getValue().until().isBefore(Instant.now()));
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.sessions.delete(new QueryWrapper<Session>().lt("expires_at", LocalDateTime.now()));
    Session session = new Session();
    session.setUserId(user.getId());
    session.setTokenHash(SecurityConfig.hash(token));
    session.setExpiresAt(LocalDateTime.now().plusHours(hours));
    db.sessions.insert(session);
    Log log = new Log();
    log.setUsername(user.getUsername());
    log.setAction("LOGIN");
    log.setTarget("身份认证");
    log.setDetail("登录成功");
    log.setIp(request.getRemoteAddr());
    log.setCreatedAt(LocalDateTime.now());
    db.logs.insert(log);
    return Api.ok(Map.of("token", token, "user", access.userView(user)));
  }

  @GetMapping("/me")
  @Operation(summary = "获取当前用户", tags = "身份认证", description = "返回当前用户资料和权限，不返回密码摘要或会话摘要。")
  public Api<?> me() {
    return Api.ok(access.userView(access.user()));
  }

  @PostMapping("/logout")
  @Operation(summary = "退出登录", tags = "身份认证", description = "撤销本次登录会话。")
  public Api<?> logout(HttpServletRequest req) {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer "))
      db.sessions.delete(
          new QueryWrapper<Session>().eq("token_hash", SecurityConfig.hash(header.substring(7))));
    return Api.ok();
  }

  @PutMapping("/profile")
  @Operation(summary = "修改个人资料", tags = "个人中心")
  @Transactional
  public Api<?> profile(@Valid @RequestBody Requests.Profile input) {
    Long id = access.user().getId();
    // The authenticated principal is a snapshot: never write its role, enabled flag or hash back.
    db.users.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, id)
            .set(User::getDisplayName, input.displayName())
            .set(User::getPhone, input.phone())
            .set(User::getEmail, input.email()));
    access.log("PROFILE_UPDATE", String.valueOf(id), "更新个人资料");
    return Api.ok(access.userView(Access.found(db.users.selectById(id), "用户")));
  }

  @PutMapping("/password")
  @Operation(
      summary = "修改个人密码",
      tags = "个人中心",
      description = "验证原密码后更新 BCrypt 摘要，并撤销当前用户所有会话，需要重新登录。")
  @Transactional
  public Api<?> password(@Valid @RequestBody Requests.Password input) {
    User user = Access.found(db.users.lockById(access.user().getId()), "用户");
    if (!encoder.matches(input.oldPassword(), user.getPasswordHash())) throw Api.bad("原密码不正确");
    db.users.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, user.getId())
            .set(User::getPasswordHash, encoder.encode(input.newPassword())));
    db.sessions.delete(new QueryWrapper<Session>().eq("user_id", user.getId()));
    access.log("PASSWORD_CHANGE", String.valueOf(user.getId()), "修改密码并撤销所有会话");
    return Api.ok();
  }
}
