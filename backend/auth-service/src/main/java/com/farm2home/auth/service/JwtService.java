package com.farm2home.auth.service;

import com.farm2home.auth.domain.entity.Role;
import com.farm2home.auth.domain.entity.User;
import com.farm2home.common.core.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    public String generateAccessToken(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(user.getMobile())
                .claim(SecurityConstants.CLAIM_USER_ID, user.getId().toString())
                .claim(SecurityConstants.CLAIM_ROLES, roles)
                .claim(SecurityConstants.CLAIM_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * The {@code jti} (RFC 7519 JWT ID) is what makes every refresh token unique, independent of
     * {@code iat}/{@code exp} - jjwt encodes those as integer-second {@code NumericDate} values,
     * so two refresh tokens minted for the same user within the same wall-clock second would
     * otherwise be byte-identical (same subject, same userId, same type, same truncated
     * iat/exp) and therefore hash to the same value in {@code auth.refresh_tokens.token_hash}
     * (AuthServiceImpl hashes the whole compact token string for storage/lookup) - silently
     * breaking the single-use/revocation guarantee, since a lookup by
     * (hash, revoked=false) could then match a different, still-live row than the one the
     * caller actually presented. A fresh {@link UUID} per call guarantees a distinct payload -
     * and therefore a distinct signature and compact string - on every issuance, including
     * concurrent ones for the same user in the same second. Not added to the access token: it is
     * never persisted or looked up by hash, so it has no uniqueness requirement to satisfy.
     */
    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getMobile())
                .claim(SecurityConstants.CLAIM_USER_ID, user.getId().toString())
                .claim(SecurityConstants.CLAIM_TYPE, SecurityConstants.TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractMobile(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractMobile(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
