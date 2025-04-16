package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.RandomUtils.nextLong;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.pact.util.GatewayAccountUtil.setUpGatewayAccount;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "${PACT_BROKER_HOST:pact-broker.deploy.payments.service.gov.uk}", tags = {"${PACT_CONSUMER_TAG}", "test-fargate"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"frontend"})
public class FrontendContractTest {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(app.getWireMockPort());
    
    @TestTarget
    public static Target target;
    private static DatabaseTestHelper dbHelper;
    
    private StripeMockClient stripeMockClient;
    private WorldpayMockClient worldpayMockClient;
    
    @BeforeClass
    public static void setUp() {
        target = new HttpTarget(app.getLocalPort());
        dbHelper = app.getDatabaseTestHelper();
    }

    @Before
    public void refreshDatabase() {
        dbHelper.truncateAllData();
        stripeMockClient = new StripeMockClient(wireMockRule);
        worldpayMockClient = new WorldpayMockClient(wireMockRule);
    }
    
    @State("an unused token testToken exists with external charge id chargeExternalId associated with it")
    public void anUnusedTokenExists() {
        long gatewayAccountId = 666L;
        setUpGatewayAccount(dbHelper, gatewayAccountId);

        var params = anAddChargeParams()
                .withExternalChargeId("chargeExternalId")
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .build();
        dbHelper.addCharge(params);
        
        dbHelper.addToken(params.chargeId(), "testToken", false);
    }
    
    @State("a sandbox account exists with a charge with id testChargeId that is in state ENTERING_CARD_DETAILS.")
    public void aChargeExistsAwaitingAuthorisation() {
        long gatewayAccountId = 666L;
        setUpGatewayAccount(dbHelper, gatewayAccountId);
        
        String chargeExternalId = "testChargeId";

        dbHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withAmount(100)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .withReturnUrl("aReturnUrl")
                .withTransactionId(chargeExternalId)
                .withReference(ServicePaymentReference.of("aReference"))
                .withDescription("Test description")
                .withCreatedDate(Instant.now())
                .withEmail("test@test.com")
                .withDelayedCapture(false)
                .build());
    }

    @State("a sandbox account exists with a charge with id testChargeId and description DECLINED that is in state ENTERING_CARD_DETAILS.")
    public void aChargeExistsAwaitingAuthorisationWithDescriptionDeclined() {
        long gatewayAccountId = 666L;
        setUpGatewayAccount(dbHelper, gatewayAccountId);

        String chargeExternalId = "testChargeId";

        dbHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withAmount(100)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .withReturnUrl("aReturnUrl")
                .withTransactionId(chargeExternalId)
                .withReference(ServicePaymentReference.of("aReference"))
                .withDescription("DECLINED")
                .withCreatedDate(Instant.now())
                .withEmail("test@test.com")
                .withDelayedCapture(false)
                .build());
    }
    
    @State("a sandbox account exists with a charge with id testChargeId and description ERROR that is in state ENTERING_CARD_DETAILS.")
    public void aChargeExistsAwaitingAuthorisationWithDescriptionError() {
        long gatewayAccountId = 666L;
        setUpGatewayAccount(dbHelper, gatewayAccountId);

        String chargeExternalId = "testChargeId";

        dbHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withAmount(100)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .withReturnUrl("aReturnUrl")
                .withTransactionId(chargeExternalId)
                .withReference(ServicePaymentReference.of("aReference"))
                .withDescription("ERROR")
                .withCreatedDate(Instant.now())
                .withEmail("test@test.com")
                .withDelayedCapture(false)
                .build());
    }

    @State("a Worldpay account exists with 3DS flex credentials and a charge with id testChargeId")
    public void aWorldpayChargeExists() {
        long gatewayAccountId = 666L;
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(gatewayAccountId)
                .withPaymentProvider("worldpay")
                .withCardTypeEntities(Collections.singletonList(dbHelper.getVisaDebitCard()))
                .insert();

        String chargeExternalId = "testChargeId";

        dbHelper.insertWorldpay3dsFlexCredential(gatewayAccountId, "1A4rIZWXzXxqH7hZQUJ5aIUlFgPJFKLfrOGKxASaaV3YWMrcS616L7H86UhnTg5u", "an-issuer", "a-org-unit-id", 2L);

        dbHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withPaymentProvider("worldpay")
                .build());
    }

    @State("a Worldpay account exists with 3DS flex credentials and a charge with id testChargeId that is in state AUTHORISATION_3DS_REQUIRED")
    public void aWorldpayChargeExistsWith3dsCredentialsInStateAuthorisation3dsRequired() {
        long gatewayAccountId = 666L;
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(gatewayAccountId)
                .withPaymentProvider("worldpay")
                .withCardTypeEntities(Collections.singletonList(dbHelper.getVisaDebitCard()))
                .insert();

        Long chargeId = nextLong();
        String chargeExternalId = "testChargeId";

        dbHelper.insertWorldpay3dsFlexCredential(gatewayAccountId, "1A4rIZWXzXxqH7hZQUJ5aIUlFgPJFKLfrOGKxASaaV3YWMrcS616L7H86UhnTg5u", "an-issuer", "a-org-unit-id", 2L);

        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .withPaymentProvider("worldpay")
                .build());

        dbHelper.updateCharge3dsFlexChallengeDetails(chargeId,
                "http://example.com",
                "a-transaction-id",
                "a-payload",
                "2.1.0");
    }
    
    @State("a Worldpay account exists with a charge with id testChargeId that is in state ENTERING_CARD_DETAILS.")
    public void aWorldpayChargeExistsAwaitingAuthorisation() {
        worldpayMockClient.mockAuthorisationSuccess();
        long gatewayAccountId = 666L;
        int gatewayCredentialId = RandomUtils.nextInt();
        createGatewayAccount(gatewayAccountId, gatewayCredentialId, PaymentGatewayName.WORLDPAY);
        createChargeEnteringCardDetails("testChargeId", gatewayAccountId, gatewayCredentialId, PaymentGatewayName.WORLDPAY);
    }
    
    @State("a Stripe account exists with a charge with id testChargeId that is in state ENTERING_CARD_DETAILS.")
    public void aStripeChargeExistsAwaitingAuthorisation() {
        stripeMockClient.mockCreatePaymentIntent();
        long gatewayAccountId = 666L;
        int gatewayCredentialId = RandomUtils.nextInt();
        createGatewayAccount(gatewayAccountId, gatewayCredentialId, PaymentGatewayName.STRIPE);
        createChargeEnteringCardDetails("testChargeId", gatewayAccountId, gatewayCredentialId, PaymentGatewayName.STRIPE);
    }
    
    private void createGatewayAccount(Long gatewayAccountId, int gatewayCredentialId, PaymentGatewayName paymentProvider) {
        Map<String, Object> credentials;
        switch(paymentProvider) {
            case WORLDPAY: 
                credentials = Map.of(
                    ONE_OFF_CUSTOMER_INITIATED, Map.of(
                            CREDENTIALS_MERCHANT_CODE, "merchant-id",
                            CREDENTIALS_USERNAME, "test-user",
                            CREDENTIALS_PASSWORD, "test-password")
                );
                break;
            case STRIPE: 
                credentials = Map.of("stripe_account_id", RandomUtils.nextInt());
                break;
            default:
                throw new RuntimeException("This provider state only supports Worldpay and Stripe accounts");
        }
        createGatewayAccount(gatewayAccountId, gatewayCredentialId, paymentProvider, credentials);
    }
    
    private void createGatewayAccount(Long gatewayAccountId, int gatewayCredentialId, PaymentGatewayName paymentProvider, Map<String, Object> credentials) {
        AddGatewayAccountCredentialsParams accountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withId(gatewayCredentialId)
                .withPaymentProvider(paymentProvider.getName())
                .withGatewayAccountId(gatewayAccountId)
                .withState(ACTIVE)
                .withCredentials(credentials)
                .build();

        CardTypeEntity visaCreditCard = dbHelper.getVisaCreditCard();
        DatabaseFixtures.withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(gatewayAccountId)
                .withPaymentProvider(paymentProvider.getName())
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .withServiceId("external-service-id")
                .withAllowAuthApi(true)
                .withCardTypeEntities(List.of(visaCreditCard))
                .insert();
    }
     
    private void createChargeEnteringCardDetails(String externalChargeId, Long gatewayAccountId, int gatewayCredentialId, PaymentGatewayName paymentProvider) {
        dbHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(externalChargeId)
                .withPaymentProvider(paymentProvider.getName())
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withAmount(100)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .withGatewayCredentialId(Long.valueOf(gatewayCredentialId))
                .build());
    }
    
}
