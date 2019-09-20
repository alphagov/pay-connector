package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

import static org.apache.commons.lang3.RandomUtils.nextLong;
import static uk.gov.pay.connector.pact.util.GatewayAccountUtil.setUpGatewayAccount;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "pact-broker-test.cloudapps.digital", tags = {"${PACT_CONSUMER_TAG}", "test"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"frontend"})
public class FrontendContractTest {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @TestTarget
    public static Target target;
    private static DatabaseTestHelper dbHelper;

    @BeforeClass
    public static void setUp() {
        target = new HttpTarget(app.getLocalPort());
        dbHelper = app.getDatabaseTestHelper();
    }

    @Before
    public void refreshDatabase() {
        dbHelper.truncateAllData();
    }
    
    @State(("an unused token testToken exists with external charge id chargeExternalId associated with it"))
    public void anUnusedTokenExists() {
        long gatewayAccountId = 666L;
        setUpGatewayAccount(dbHelper, gatewayAccountId);

        var params = anAddChargeParams()
                .withExternalChargeId("chargeExternalId")
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .build();
        dbHelper.addCharge(params);
        
        dbHelper.addToken(params.getChargeId(), "testToken", false);
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
                .withCreatedDate(ZonedDateTime.now())
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
                .withCredentials(Map.of(
                        "jwt_mac_id", "1A4rIZWXzXxqH7hZQUJ5aIUlFgPJFKLfrOGKxASaaV3YWMrcS616L7H86UhnTg5u",
                        "organisational_unit_id", "a-org-unit-id",
                        "issuer", "an-issuer"
                ))
                .insert();

        String chargeExternalId = "testChargeId";

        dbHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
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
                .withCredentials(Map.of(
                        "jwt_mac_id", "1A4rIZWXzXxqH7hZQUJ5aIUlFgPJFKLfrOGKxASaaV3YWMrcS616L7H86UhnTg5u",
                        "organisational_unit_id", "a-org-unit-id",
                        "issuer", "an-issuer"
                ))
                .insert();

        Long chargeId = nextLong();
        String chargeExternalId = "testChargeId";

        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .build());

        dbHelper.updateCharge3dsFlexChallengeDetails(chargeId,
                "http://example.com",
                "a-transaction-id",
                "a-payload",
                "2.1.0");
    }
}
