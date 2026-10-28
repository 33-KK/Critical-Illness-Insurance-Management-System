package com.example.insurance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurance.Domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
  private final Store db;
  private final ObjectMapper json;

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(x -> x.disable())
        .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            x ->
                x.requestMatchers("/api/auth/login", "/api/auth/captcha", "/actuator/health")
                    .permitAll()
                    .requestMatchers(
                        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            x ->
                x.authenticationEntryPoint(
                        (req, res, e) -> error(res, 401, "UNAUTHORIZED", "请先登录或登录已过期"))
                    .accessDeniedHandler(
                        (req, res, e) -> error(res, 403, "FORBIDDEN", "没有执行此操作的权限")))
        .addFilterBefore(new TokenFilter(db), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private void error(HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setCharacterEncoding("UTF-8");
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(res.getWriter(), new Api<>(code, message, null));
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static class TokenFilter extends OncePerRequestFilter {
    final Store db;

    TokenFilter(Store db) {
      this.db = db;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
      String header = req.getHeader("Authorization");
      if (header != null && header.startsWith("Bearer ") && header.length() < 256) {
        Session session =
            db.sessions.selectOne(
                new QueryWrapper<Session>()
                    .eq("token_hash", hash(header.substring(7)))
                    .gt("expires_at", LocalDateTime.now()));
        if (session != null) {
          User user = db.users.selectById(session.getUserId());
          if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
            Role role = db.roles.selectOne(new QueryWrapper<Role>().eq("code", user.getRole()));
            var authorities =
                role == null
                    ? List.<SimpleGrantedAuthority>of()
                    : Arrays.stream(role.getPermissions().split(","))
                        .filter(x -> !x.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .toList();
            SecurityContextHolder.getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, authorities));
          }
        }
      }
      chain.doFilter(req, res);
    }
  }
}
