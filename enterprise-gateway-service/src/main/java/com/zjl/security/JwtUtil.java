package com.zjl.security;

import com.zjl.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与解析服务
 */
@Service
public class JwtUtil {

    /**
     * JWT 签名密钥
     */
    private final SecretKey secretKey;
    /**
     * token 有效期（秒）
     */
    private final long ttlSeconds;

    /**
     * 构造器：从配置读取 secret 与有效期
     *
     * @param properties 安全配置
     */
    public JwtUtil(AppSecurityProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = properties.getJwt().getTtlSeconds();
    }

    /**
     * 签发 JWT
     *
     * @param userId 用户 id
     * @param username 用户名
     * @param extraClaims 额外 claims（如 authorities）
     * @return JWT 字符串
     */
    public String issueToken(Long userId, String username, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        // 过期时间 = 当前时间 + 配置的有效期
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                // sub 存放用户 id
                .subject(String.valueOf(userId))
                // 签发时间
                .issuedAt(Date.from(now))
                // 过期时间
                .expiration(Date.from(exp))
                // 自定义 claim：用户名
                .claim("username", username)
                // 额外 claims（如角色、权限等）
                .claims(extraClaims)
                // HS256 签名
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 JWT（签名与过期时间）
     *
     * @param token JWT 字符串
     * @return claims
     */
    public Claims parse(String token) {
        return Jwts.parser()
                // 设置签名密钥用于验签
                .verifyWith(secretKey)
                .build()
                // 解析并校验签名与过期时间
                .parseSignedClaims(token)
                // 获取 claims 体
                .getPayload();
    }
}

