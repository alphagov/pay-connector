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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

public class Worldpay3dsFlexJwtServiceTest {

    private static final int tokenExpiryDurationSeconds = 5400;
    private static Worldpay3dsFlexJwtService worldpay3dsFlexJwtService = new Worldpay3dsFlexJwtService(new JwtGenerator(), tokenExpiryDurationSeconds);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldCreateCorrectTokenForWorldpay3dsFlexDdc() {
        var validCredentials = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), validCredentials);
        int paymentCreationTimeEpochSeconds19August2029 = 1881821916;
        int expectedTokenExpirationTimeEpochSeconds = paymentCreationTimeEpochSeconds19August2029 + tokenExpiryDurationSeconds;
        var paymentCreationZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) paymentCreationTimeEpochSeconds19August2029), UTC);

        String token = worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, paymentCreationZonedDateTime);

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(validCredentials.get("jwt_mac_id").getBytes(), "HmacSHA256"))
                .parseClaimsJws(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat(jws.getHeader().get("typ"), is("JWT"));
        assertThat(jws.getBody().get("jti"), is(notNullValue()));
        assertThat(jws.getBody().get("iat"), is(notNullValue()));
        assertThat(jws.getBody().get("exp"), is(expectedTokenExpirationTimeEpochSeconds));
        assertThat(jws.getBody().get("iss"), is(validCredentials.get("issuer")));
        assertThat(jws.getBody().get("OrgUnitId"), is(validCredentials.get("organisational_unit_id")));
    }

    @Test
    public void shouldThrowExceptionForMissingIssuer() {
        var credentialsMissingIssuer = Map.of(
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), credentialsMissingIssuer);

        expectedException.expect(Worldpay3dsFlexDdcJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex DDC JWT for account 1 because the " +
                "following credentials are unavailable: [issuer]");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }

    @Test
    public void shouldThrowExceptionForMissingOrgId() {
        var credentialsMissingOrgId = Map.of(
                "issuer", "me",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), credentialsMissingOrgId);

        expectedException.expect(Worldpay3dsFlexDdcJwtCredentialsException.class);
        expectedException.expectMessage(
                "Cannot generate Worldpay 3ds Flex DDC JWT for account 1 because the following credentials are " +
                        "unavailable: [organisational_unit_id]");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }

    @Test
    public void shouldThrowExceptionForMissingJwtMacId() {
        var credentialsMissingJwtMacId = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), credentialsMissingJwtMacId);

        expectedException.expect(Worldpay3dsFlexDdcJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex DDC JWT for account 1 because the " +
                "following credentials are unavailable: [jwt_mac_id]");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }

    @Test
    public void shouldThrowExceptionForNonWorldpayAccount() {
        var validCredentials = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, SMARTPAY.getName(), validCredentials);

        expectedException.expect(Worldpay3dsFlexDdcJwtPaymentProviderException.class);
        expectedException.expectMessage("Cannot provide a Worldpay 3ds flex DDC JWT for account 1 because the " +
                "Payment Provider is not Worldpay.");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }
}
