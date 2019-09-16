package uk.gov.pay.connector.charge.service;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ChargeSweepConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtCredentialsException;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtPaymentProviderException;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.model.domain.Auth3dsDetailsEntityFixture.anAuth3dsDetailsEntity;

@RunWith(MockitoJUnitRunner.class)
public class Worldpay3dsFlexJwtServiceTest {

    @Mock
    private ConnectorConfiguration mockConfiguration; 
    
    @Mock
    private ChargeSweepConfig mockChargeSweepConfig;
    
    @Mock 
    private LinksConfig mockLinksConfig;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    
    private static final int TOKEN_EXPIRY_DURATION_SECONDS = 5400;
    
    private static final Map<String, String> VALID_CREDENTIALS = Map.of(
            "issuer", "me",
            "organisational_unit_id", "myOrg",
            "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
    );
    
    private static final String FRONTEND_URL = "http://frontend.pymt.service.gov.uk";

    private static Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;
    
    @Before
    public void setUp() {
        when(mockLinksConfig.getFrontendUrl()).thenReturn(FRONTEND_URL);
        when(mockChargeSweepConfig.getDefaultChargeExpiryThreshold()).thenReturn(TOKEN_EXPIRY_DURATION_SECONDS);
        when(mockConfiguration.getLinks()).thenReturn(mockLinksConfig);
        when(mockConfiguration.getChargeSweepConfig()).thenReturn(mockChargeSweepConfig);
        
        worldpay3dsFlexJwtService = new Worldpay3dsFlexJwtService(new JwtGenerator(), mockConfiguration);
    }

    @Test
    public void shouldCreateCorrectTokenForWorldpay3dsFlexDdc() {
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), VALID_CREDENTIALS);
        int paymentCreationTimeEpochSeconds19August2029 = 1881821916;
        int expectedTokenExpirationTimeEpochSeconds = paymentCreationTimeEpochSeconds19August2029 + TOKEN_EXPIRY_DURATION_SECONDS;
        var paymentCreationZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) paymentCreationTimeEpochSeconds19August2029), UTC);

        String token = worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, paymentCreationZonedDateTime);

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(VALID_CREDENTIALS.get("jwt_mac_id").getBytes(), "HmacSHA256"))
                .parseClaimsJws(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat((Map<String, Object>)jws.getHeader(), hasEntry("typ", "JWT"));
        assertThat(jws.getBody(), hasKey("jti"));
        assertThat(jws.getBody(), hasKey("iat"));
        assertThat(jws.getBody(), hasEntry("exp", expectedTokenExpirationTimeEpochSeconds));
        assertThat(jws.getBody(), hasEntry("iss", VALID_CREDENTIALS.get("issuer")));
        assertThat(jws.getBody(), hasEntry("OrgUnitId", VALID_CREDENTIALS.get("organisational_unit_id")));
    }

    @Test
    public void shouldCreateCorrectChallengeToken() {
        
        String worldpayChallengeAcsUrl = "http://www.example.com";
        String worldpayChallengePayload = "a-payload";
        String worldpayChallengeTransactionId = "a-transaction-id";
        String chargeExternalId = "a-charge-id";

        Auth3dsDetailsEntity auth3dsDetailsEntity = anAuth3dsDetailsEntity()
                .withWorldpayChallengeAcsUrl(worldpayChallengeAcsUrl)
                .withWorldpayChallengePayload(worldpayChallengePayload)
                .withWorldpayChallengeTransactionId(worldpayChallengeTransactionId)
                .build();

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(WORLDPAY.getName(), VALID_CREDENTIALS,
                GatewayAccountEntity.Type.TEST);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withExternalId(chargeExternalId)
                .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        String token = worldpay3dsFlexJwtService.generateChallengeToken(chargeEntity);

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(VALID_CREDENTIALS.get("jwt_mac_id").getBytes(), "HmacSHA256"))
                .parseClaimsJws(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat((Map<String, Object>)jws.getHeader(), hasEntry("typ", "JWT"));
        assertThat(jws.getBody(), hasKey("jti"));
        assertThat(jws.getBody(), hasKey("iat"));
        assertThat(jws.getBody(), hasEntry("iss", VALID_CREDENTIALS.get("issuer")));
        assertThat(jws.getBody(), hasEntry("OrgUnitId", VALID_CREDENTIALS.get("organisational_unit_id")));
        assertThat(jws.getBody(), hasEntry("ReturnUrl", format("%s/card_details/%s/3ds_required_in", FRONTEND_URL, chargeExternalId)));
        assertThat(jws.getBody(), hasEntry("ObjectifyPayload", true));
        assertThat(jws.getBody(), hasEntry(is("Payload"), instanceOf(Map.class)));
        Map<String, Object> payload = (Map<String, Object>) jws.getBody().get("Payload");
        assertThat(payload, hasEntry("ACSUrl", worldpayChallengeAcsUrl));
        assertThat(payload, hasEntry("Payload", worldpayChallengePayload));
        assertThat(payload, hasEntry("TransactionId", worldpayChallengeTransactionId));
    }

    @Test
    public void shouldThrowExceptionForMissingIssuer() {
        var credentialsMissingIssuer = Map.of(
                "organisational_unit_id", "myOrg",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), credentialsMissingIssuer);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex JWT for account 1 because the " +
                "following credential is unavailable: issuer");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }

    @Test
    public void shouldThrowExceptionForMissingOrgId() {
        var credentialsMissingOrgId = Map.of(
                "issuer", "me",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), credentialsMissingOrgId);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage(
                "Cannot generate Worldpay 3ds Flex JWT for account 1 because the following credential is " +
                        "unavailable: organisational_unit_id");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }

    @Test
    public void shouldThrowExceptionForMissingJwtMacId() {
        var credentialsMissingJwtMacId = Map.of(
                "issuer", "me",
                "organisational_unit_id", "myOrg"
        );
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), credentialsMissingJwtMacId);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex JWT for account 1 because the " +
                "following credential is unavailable: jwt_mac_id");

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

        expectedException.expect(Worldpay3dsFlexJwtPaymentProviderException.class);
        expectedException.expectMessage("Cannot provide a Worldpay 3ds flex JWT for account 1 because the " +
                "Payment Provider is not Worldpay.");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, ZonedDateTime.now());
    }
}
