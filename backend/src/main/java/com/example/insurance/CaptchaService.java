package com.example.insurance;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final ConcurrentHashMap<String, Entry> memory = new ConcurrentHashMap<>();
  private final StringRedisTemplate redis;
  private final boolean redisEnabled;

  private record Entry(String code, Instant expiresAt) {}

  public CaptchaService(
      StringRedisTemplate redis, @Value("${app.redis-enabled:false}") boolean redisEnabled) {
    this.redis = redis;
    this.redisEnabled = redisEnabled;
  }

  public Map<String, Object> create() {
    String id = UUID.randomUUID().toString(), code = String.format("%04d", RANDOM.nextInt(10000));
    if (redisEnabled)
      redis.opsForValue().set("insurance:captcha:" + id, code, Duration.ofMinutes(2));
    else {
      memory.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
      memory.put(id, new Entry(code, Instant.now().plusSeconds(120)));
    }
    String svg =
        "<svg xmlns='http://www.w3.org/2000/svg' width='140' height='44'><rect width='140'"
            + " height='44' rx='8' fill='#edf6fa'/><path d='M0 10L140 31M0 35L140 8'"
            + " stroke='#b3cbd4'/><text x='25' y='31' font-family='monospace' font-size='29'"
            + " letter-spacing='6' fill='#21556a'>"
            + code
            + "</text></svg>";
    return Map.of(
        "captchaId",
        id,
        "svg",
        "data:image/svg+xml;base64,"
            + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)),
        "expiresIn",
        120);
  }

  public void consume(String id, String supplied) {
    String expected;
    if (redisEnabled) expected = redis.opsForValue().getAndDelete("insurance:captcha:" + id);
    else {
      Entry entry = memory.remove(id);
      expected = entry != null && entry.expiresAt().isAfter(Instant.now()) ? entry.code() : null;
    }
    if (expected == null || !expected.equals(supplied.trim())) throw Api.bad("验证码错误或已过期，请刷新后重试");
  }
}
