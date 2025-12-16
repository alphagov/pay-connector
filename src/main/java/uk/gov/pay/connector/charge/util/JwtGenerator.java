package uk.gov.pay.connector.charge.util;


import io.jsonwebtoken.Jwts;

import javax.crypto.spec.SecretKeySpec;
import java.util.Map;

public class JwtGenerator {

    public String createJwt(Map<String, Object> claims, String secret) {
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");

        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .claims().add(claims).and()
                .signWith(secret_key, Jwts.SIG.HS256)
                .compact();
    }
}
