package uk.gov.pay.connector.charge.util;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Map;

import static java.time.temporal.ChronoUnit.MINUTES;

public class JwtGenerator {

    /**
     * Utility method to create a JWT for Worldpay 3DS Flex DDC based upon the required claims
     * shown in their documentation.
     *
     * @see <a href="https://beta.developer.worldpay.com/docs/wpg/directintegration/3ds2#device-data-collection-ddc-"
     * >Worldpay DDC Documentation</a>
     */
    public String createWorldpay3dsFlexDdcJwt(String issuer, String organisationId, String secret) {
        Map<String, Object> claims = Map.of(
                "jti", RandomIdGenerator.newId(),
                "iat", Instant.now().toEpochMilli(),
                "exp", Instant.now().plus(90, MINUTES).toEpochMilli(),
                "iss", issuer,
                "OrgUnitId", organisationId);

        return createJwt(claims, secret);
    }


    private String createJwt(Map<String, Object> claims, String secret) {
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .addClaims(claims)
                .signWith(secret_key, SignatureAlgorithm.HS256)
                .compact();
    }
}
