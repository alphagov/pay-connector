package uk.gov.pay.connector.charge.resource;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesFrontendResourceWorldpayJwtIT {

    @DropwizardTestContext
    private TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;
    private RestAssuredClient connectorRestApi;

    
    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        connectorRestApi = new RestAssuredClient(testContext.getPort());
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void shouldGetCorrectDdcToken() {
        var chargeExternalId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, nextLong(), chargeExternalId, ChargeStatus.CREATED);

        connectorRestApi
                .withChargeId(chargeExternalId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_OK)
                .body("jwt", is(notNullValue()));
    }

    @Test
    public void shouldReturn409WhenCredentialsAreMissingForGatewayAccount() {
        var chargeExternalId = "mySecondChargeId";
        var gatewayAccountId = "202";
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, null, nextLong(), chargeExternalId,
                ChargeStatus.CREATED);

        connectorRestApi
                .withChargeId(chargeExternalId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_CONFLICT)
                .body("message", is(List.of("Cannot generate Worldpay 3ds Flex JWT for account 202 because credentials are unavailable")));
    }

    @Test
    public void shouldReturn409WhenThePaymentProviderIsNotWorldpayForDdcToken() {
        var chargeExternalId = "myThirdChargeId";
        var gatewayAccountId = "303";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, SMARTPAY, validCredentials, nextLong(), chargeExternalId,
                ChargeStatus.CREATED);

        connectorRestApi
                .withChargeId(chargeExternalId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_CONFLICT)
                .body("message", is(List.of("Cannot provide a Worldpay 3ds flex JWT for account 303 because the Payment Provider is not Worldpay.")));
    }

    @Test
    public void shouldReturnChallengeJwt() {
        long chargeId = nextLong();
        var chargeExternalId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, chargeId, chargeExternalId,
                ChargeStatus.AUTHORISATION_3DS_REQUIRED);
        
        databaseTestHelper.updateCharge3dsFlexChallengeDetails(chargeId,
                "http://example.com",
                "a-transaction-id", 
                "a-payload", 
                "2.1.0");

        connectorRestApi
                .withAccountId(gatewayAccountId)
                .withChargeId(chargeExternalId)
                .getFrontendCharge()
                .statusCode(HttpStatus.SC_OK)
                .body("$", hasKey("auth_3ds_data"))
                .body("auth_3ds_data", hasKey("worldpayChallengeJwt"));
    }

    @Test
    public void shouldOmitChallengeJwtWhenChargeNotInAppropriateState() {
        long chargeId = nextLong();
        var chargeExternalId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, chargeId, chargeExternalId,
                ChargeStatus.CREATED);

        databaseTestHelper.updateCharge3dsFlexChallengeDetails(chargeId,
                "http://example.com",
                "a-transaction-id",
                "a-payload",
                "2.1.0");

        connectorRestApi
                .withAccountId(gatewayAccountId)
                .withChargeId(chargeExternalId)
                .getFrontendCharge()
                .statusCode(HttpStatus.SC_OK)
                .body("$", hasKey("auth_3ds_data"))
                .body("auth_3ds_data", not(hasKey("worldpayChallengeJwt")));
    }

    private void setUpChargeAndAccount(String gatewayAccountId,
                                       PaymentGatewayName paymentProvider,
                                       Map<String, String> credentials,
                                       Long chargeId,
                                       String chargeExternalId,
                                       ChargeStatus chargeStatus) {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway(paymentProvider.getName())
                .build());
        
        if (credentials != null) {
            databaseTestHelper.insertWorldpay3dsFlexCredential(
                    Long.valueOf(gatewayAccountId),
                    credentials.get("jwt_mac_id"),
                    credentials.get("issuer"),
                    credentials.get("organisational_unit_id"),
                    2L);
        }

        databaseTestHelper.addCharge(
                anAddChargeParams()
                        .withChargeId(chargeId)
                        .withGatewayAccountId(gatewayAccountId)
                        .withExternalChargeId(chargeExternalId)
                        .withStatus(chargeStatus)
                        .build()
        );
    }

}
