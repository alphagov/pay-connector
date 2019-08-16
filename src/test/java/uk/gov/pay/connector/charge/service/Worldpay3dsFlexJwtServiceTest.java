package uk.gov.pay.connector.charge.service;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexDdcJwtCredentialsException;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexDdcJwtPaymentProviderException;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;

import javax.crypto.spec.SecretKeySpec;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

public class Worldpay3dsFlexJwtServiceTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final JwtGenerator jwtGenerator = new JwtGenerator();
    private static Worldpay3dsFlexJwtService worldpay3dsFlexJwtService = new Worldpay3dsFlexJwtService(jwtGenerator);


    @Test
    public void shouldCreateCorrectTokenForWorldpay3dsFlexDdc() {
        var validCredentials = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.toString(), validCredentials);

        String token = worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount);

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(validCredentials.get("jwt_mac_id").getBytes(), "HmacSHA256"))
                .parseClaimsJws(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat(jws.getHeader().get("typ"), is("JWT"));
        assertThat(jws.getBody().get("jti"), is (notNullValue()));
        assertThat(jws.getBody().get("iat"), is (notNullValue()));
        assertThat(jws.getBody().get("exp"), is (notNullValue()));
        assertThat(jws.getBody().get("iss"), is(validCredentials.get("issuer")));
        assertThat(jws.getBody().get("OrgUnitId"), is(validCredentials.get("organisational_unit_id")));
    }

    @Test
    public void shouldThrowExceptionForMissingIssuer() {
        var credentialsMissingIssuer = Map.of(
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.toString(), credentialsMissingIssuer);

        expectedException.expect(Worldpay3dsFlexDdcJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex DDC JWT for account 1 because the " +
                "following credentials are unavailable: [issuer]");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount);
    }

    @Test
    public void shouldThrowExceptionForMissingOrgId() {
        var credentialsMissingOrgId = Map.of(
                "issuer", "me",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.toString(), credentialsMissingOrgId);

        expectedException.expect(Worldpay3dsFlexDdcJwtCredentialsException.class);
        expectedException.expectMessage(
                "Cannot generate Worldpay 3ds Flex DDC JWT for account 1 because the following credentials are " +
                        "unavailable: [organisational_unit_id]");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount);
    }

    @Test
    public void shouldThrowExceptionForMissingJwtMacId() {
        var credentialsMissingJwtMacId = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.toString(), credentialsMissingJwtMacId);

        expectedException.expect(Worldpay3dsFlexDdcJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex DDC JWT for account 1 because the " +
                "following credentials are unavailable: [jwt_mac_id]");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount);
    }

    @Test
    public void shouldThrowExceptionForNonWorldpayAccount() {
        var validCredentials = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, SMARTPAY.toString(), validCredentials);

        expectedException.expect(Worldpay3dsFlexDdcJwtPaymentProviderException.class);
        expectedException.expectMessage("Cannot provide a Worldpay 3ds flex DDC JWT for account 1 because the " +
                "Payment Provider is not Worldpay.");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount);
    }
}
