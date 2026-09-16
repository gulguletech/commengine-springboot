package com.commengine.security;

import com.commengine.config.AppProperties;
import com.commengine.util.DurationParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Ported from utils/jwt.js: signAuthToken / signTempToken / verifyToken. */
@Service
public class JwtService {

    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        // jsonwebtoken (Node) uses the raw secret string as an HMAC key regardless of length;
        // jjwt requires >= 256 bits for HS256, so pad/hash short secrets defensively.
        byte[] raw = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = raw.length >= 32 ? Keys.hmacShaKeyFor(raw) : Keys.hmacShaKeyFor(padKey(raw));
    }

    private static byte[] padKey(byte[] raw) {
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, 32));
        return padded;
    }

    public String signAuthToken(String userId) {
        long seconds = DurationParser.toSeconds(props.getJwt().getExpiresIn());
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("userId", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(seconds)))
                .signWith(key)
                .compact();
    }

    public String signTempToken(String phone) {
        long seconds = DurationParser.toSeconds(props.getJwt().getTempExpiresIn());
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("phone", phone)
                .claim("temp", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(seconds)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException if the token is invalid, malformed, or expired. */
    public Claims verify(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
