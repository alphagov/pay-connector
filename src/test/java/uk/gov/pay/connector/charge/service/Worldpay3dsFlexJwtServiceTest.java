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
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.Auth3dsDetailsEntityFixture.anAuth3dsDetailsEntity;

@RunWith(MockitoJUnitRunner.class)
public class Worldpay3dsFlexJwtServiceTest {

    @Mock
    private ConnectorConfiguration mockConfiguration; 
    
    @Mock
    private ChargeSweepConfig mockChargeSweepConfig;
    
    @Mock 
    private LinksConfig mockLinksConfig;

    @Mock
    private Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity;

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
    public static final String WORLDPAY_CHALLENGE_ACS_URL = "http://www.example.com";
    public static final String WORLDPAY_CHALLENGE_PAYLOAD = "a-payload";
    public static final String WORLDPAY_CHALLENGE_TRANSACTION_ID = "a-transaction-id";
    public static final String CHARGE_EXTERNAL_ID = "a-charge-id";

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
        var worldpay3dsFlexCredentials = new Worldpay3dsFlexCredentials("me", "myOrg", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0");
        int paymentCreationTimeEpochSeconds19August2029 = 1881821916;
        int expectedTokenExpirationTimeEpochSeconds = paymentCreationTimeEpochSeconds19August2029 + TOKEN_EXPIRY_DURATION_SECONDS;
        var paymentCreationZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) paymentCreationTimeEpochSeconds19August2029), UTC);

        String token = worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, worldpay3dsFlexCredentials, paymentCreationZonedDateTime);

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(VALID_CREDENTIALS.get("jwt_mac_id").getBytes(), "HmacSHA256"))
                .parseClaimsJws(token);

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat((Map<String, Object>)jws.getHeader(), hasEntry("typ", "JWT"));
        assertThat(jws.getBody(), hasKey("jti"));
        assertThat(jws.getBody(), hasKey("iat"));
        assertThat(jws.getBody(), hasEntry("exp", expectedTokenExpirationTimeEpochSeconds));
        assertThat(jws.getBody(), hasEntry("iss", "me"));
        assertThat(jws.getBody(), hasEntry("OrgUnitId", "myOrg"));
    }

    @Test
    public void shouldNotReturnChallengeTokenIfChargeInWrongState() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = anAuth3dsDetailsEntity()
                .withWorldpayChallengeAcsUrl("http://www.example.com")
                .withWorldpayChallengePayload("a-payload")
                .withWorldpayChallengeTransactionId("a-transaction-id")
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
                .build();

        Optional<String> maybeToken = worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);

        assertThat(maybeToken, is(Optional.empty()));
    }

    @Test
    public void shouldNotReturnChallengeTokenIfChallengeDataNotPresent() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = anAuth3dsDetailsEntity().build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
                .build();

        Optional<String> maybeToken = worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);

        assertThat(maybeToken, is(Optional.empty()));
    }

    @Test
    public void shouldCreateCorrectChallengeToken() {
        when(worldpay3dsFlexCredentialsEntity.getIssuer()).thenReturn("me");
        when(worldpay3dsFlexCredentialsEntity.getOrganisationalUnitId()).thenReturn("myOrg");
        when(worldpay3dsFlexCredentialsEntity.getJwtMacKey()).thenReturn("fa2daee2-1fbb-45ff-4444-52805d5cd9e0");

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(WORLDPAY.getName())
                .withWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity)
                .build();
        ChargeEntity chargeEntity = createValidChargeEntityForChallengeToken(gatewayAccountEntity);

        Optional<String> maybeToken = worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);
        
        assertThat(maybeToken.isPresent(), is(true));

        Jws<Claims> jws = Jwts.parser()
                .setSigningKey(new SecretKeySpec(VALID_CREDENTIALS.get("jwt_mac_id").getBytes(), "HmacSHA256"))
                .parseClaimsJws(maybeToken.get());

        assertThat(jws.getHeader().getAlgorithm(), is("HS256"));
        assertThat((Map<String, Object>)jws.getHeader(), hasEntry("typ", "JWT"));
        assertThat(jws.getBody(), hasKey("jti"));
        assertThat(jws.getBody(), hasKey("iat"));
        assertThat(jws.getBody(), hasEntry("iss", VALID_CREDENTIALS.get("issuer")));
        assertThat(jws.getBody(), hasEntry("OrgUnitId", VALID_CREDENTIALS.get("organisational_unit_id")));
        assertThat(jws.getBody(), hasEntry("ReturnUrl", format("%s/card_details/%s/3ds_required_in", FRONTEND_URL, CHARGE_EXTERNAL_ID)));
        assertThat(jws.getBody(), hasEntry("ObjectifyPayload", true));
        assertThat(jws.getBody(), hasEntry(is("Payload"), instanceOf(Map.class)));
        Map<String, Object> payload = (Map<String, Object>) jws.getBody().get("Payload");
        assertThat(payload, hasEntry("ACSUrl", WORLDPAY_CHALLENGE_ACS_URL));
        assertThat(payload, hasEntry("Payload", WORLDPAY_CHALLENGE_PAYLOAD));
        assertThat(payload, hasEntry("TransactionId", WORLDPAY_CHALLENGE_TRANSACTION_ID));
    }

    @Test
    public void generateDdcToken_shouldThrowExceptionForMissingIssuer() {
        var worldpay3dsFlexCredentials = new Worldpay3dsFlexCredentials(null, "myOrg", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0");
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), null);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex JWT for account 1 because the " +
                "following credential is unavailable: issuer");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, worldpay3dsFlexCredentials, ZonedDateTime.now());
    }

    @Test
    public void generateDdcToken_shouldThrowExceptionForMissingOrgId() {
        var worldpay3dsFlexCredentials = new Worldpay3dsFlexCredentials("me", null, "fa2daee2-1fbb-45ff-4444-52805d5cd9e0");
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), null);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage(
                "Cannot generate Worldpay 3ds Flex JWT for account 1 because the following credential is " +
                        "unavailable: organisational_unit_id");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, worldpay3dsFlexCredentials, ZonedDateTime.now());
    }

    @Test
    public void generateDdcToken_shouldThrowExceptionForMissingJwtMacId() {
        var worldpay3dsFlexCredentials = new Worldpay3dsFlexCredentials("me", "myOrg", null);
        var gatewayAccount = new GatewayAccount(1L, WORLDPAY.getName(), null);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex JWT for account 1 because the " +
                "following credential is unavailable: jwt_mac_key");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, worldpay3dsFlexCredentials, ZonedDateTime.now());
    }

    @Test
    public void generateDdcToken_shouldThrowExceptionForNonWorldpayAccount() {
        var worldpay3dsFlexCredentials = mock(Worldpay3dsFlexCredentials.class);
        var gatewayAccount = new GatewayAccount(1L, SMARTPAY.getName(), VALID_CREDENTIALS);

        expectedException.expect(Worldpay3dsFlexJwtPaymentProviderException.class);
        expectedException.expectMessage("Cannot provide a Worldpay 3ds flex JWT for account 1 because the " +
                "Payment Provider is not Worldpay.");

        worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, worldpay3dsFlexCredentials, ZonedDateTime.now());
    }

    @Test
    public void generateChallengeToken_shouldThrowExceptionForMissingIssuer() {
        when(worldpay3dsFlexCredentialsEntity.getIssuer()).thenReturn(null);
        when(worldpay3dsFlexCredentialsEntity.getOrganisationalUnitId()).thenReturn("myOrg");
        when(worldpay3dsFlexCredentialsEntity.getJwtMacKey()).thenReturn("fa2daee2-1fbb-45ff-4444-52805d5cd9e0");
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(WORLDPAY.getName())
                .withWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity)
                .build();
        ChargeEntity chargeEntity = createValidChargeEntityForChallengeToken(gatewayAccountEntity);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex JWT for account 1 because the " +
                "following credential is unavailable: issuer");

        worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);
    }

    @Test
    public void generateChallengeToken_shouldThrowExceptionForMissingOrgId() {
        when(worldpay3dsFlexCredentialsEntity.getIssuer()).thenReturn("me");
        when(worldpay3dsFlexCredentialsEntity.getOrganisationalUnitId()).thenReturn(null);
        when(worldpay3dsFlexCredentialsEntity.getJwtMacKey()).thenReturn("fa2daee2-1fbb-45ff-4444-52805d5cd9e0");
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(WORLDPAY.getName())
                .withWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity)
                .build();
        ChargeEntity chargeEntity = createValidChargeEntityForChallengeToken(gatewayAccountEntity);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage(
                "Cannot generate Worldpay 3ds Flex JWT for account 1 because the following credential is " +
                        "unavailable: organisational_unit_id");

        worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);
    }

    @Test
    public void generateChallengeToken_shouldThrowExceptionForMissingJwtMacId() {
        when(worldpay3dsFlexCredentialsEntity.getIssuer()).thenReturn("me");
        when(worldpay3dsFlexCredentialsEntity.getOrganisationalUnitId()).thenReturn("myOrg");
        when(worldpay3dsFlexCredentialsEntity.getJwtMacKey()).thenReturn(null);
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(WORLDPAY.getName())
                .withWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity)
                .build();
        ChargeEntity chargeEntity = createValidChargeEntityForChallengeToken(gatewayAccountEntity);

        expectedException.expect(Worldpay3dsFlexJwtCredentialsException.class);
        expectedException.expectMessage("Cannot generate Worldpay 3ds Flex JWT for account 1 because the " +
                "following credential is unavailable: jwt_mac_key");

        worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);
    }

    @Test
    public void generateChallengeToken_shouldThrowExceptionForNonWorldpayAccount() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(SMARTPAY.getName())
                .withCredentials(VALID_CREDENTIALS)
                .build();
        ChargeEntity chargeEntity = createValidChargeEntityForChallengeToken(gatewayAccountEntity);

        expectedException.expect(Worldpay3dsFlexJwtPaymentProviderException.class);
        expectedException.expectMessage("Cannot provide a Worldpay 3ds flex JWT for account 1 because the " +
                "Payment Provider is not Worldpay.");

        worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(chargeEntity);
    }

    private ChargeEntity createValidChargeEntityForChallengeToken(GatewayAccountEntity gatewayAccountEntity) {
        Auth3dsDetailsEntity auth3dsDetailsEntity = anAuth3dsDetailsEntity()
                .withWorldpayChallengeAcsUrl(WORLDPAY_CHALLENGE_ACS_URL)
                .withWorldpayChallengePayload(WORLDPAY_CHALLENGE_PAYLOAD)
                .withWorldpayChallengeTransactionId(WORLDPAY_CHALLENGE_TRANSACTION_ID)
                .build();

        return ChargeEntityFixture.aValidChargeEntity()
                .withExternalId(CHARGE_EXTERNAL_ID)
                .withStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
    }
}
