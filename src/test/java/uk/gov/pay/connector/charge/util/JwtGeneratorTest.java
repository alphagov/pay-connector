package uk.gov.pay.connector.charge.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JwtGeneratorTest {

    private static final JwtGenerator jwtGenerator = new JwtGenerator();

    @Test
    public void shouldCreateCorrectToken() {
        String secret = "fa2daee2-1fbb-45ff-4444-52805d5cd9e0";
        Map<String, Object> claims = Map.of("key1", "value1", "key2", "value2");

        String token = jwtGenerator.createJwt(claims, secret);

        Jws<Claims> jws = Jwts.parser()
                .verifyWith(new SecretKeySpec(secret.getBytes(), "HmacSHA256"))
                .build()
                .parseSignedClaims(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat(jws.getHeader().get("typ"), is("JWT"));
        assertThat(jws.getPayload().get("key1"), is("value1"));
        assertThat(jws.getPayload().get("key2"), is("value2"));
    }
}
