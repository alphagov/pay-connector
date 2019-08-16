package uk.gov.pay.connector.charge.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class JwtGeneratorTest {

    private static final JwtGenerator jwtGenerator = new JwtGenerator();

    @Test
    public void shouldCreateCorrectTokenForWorldpay3dsFlexDdc() {
        String secret = "fa2daee2-1fbb-45ff-4444-52805d5cd9e0";
        String issuer = "ME";
        String orgId = "myOrg";

        String token = jwtGenerator.createWorldpay3dsFlexDdcJwt(issuer, orgId, secret);

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256"))
                .parseClaimsJws(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat(jws.getHeader().get("typ"), is("JWT"));
        assertThat(jws.getBody().get("jti"), is (notNullValue()));
        assertThat(jws.getBody().get("iat"), is (notNullValue()));
        assertThat(jws.getBody().get("exp"), is (notNullValue()));
        assertThat(jws.getBody().get("iss"), is(issuer));
        assertThat(jws.getBody().get("OrgUnitId"), is(orgId));
    }
}
