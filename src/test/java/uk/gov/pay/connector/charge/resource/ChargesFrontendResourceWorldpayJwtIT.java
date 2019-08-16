package uk.gov.pay.connector.charge.resource;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.util.List;
import java.util.Map;

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

    @Test
    public void shouldGetCorrectDdcToken() {
        var chargeId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, chargeId);

        connectorRestApi
                .withChargeId(chargeId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_OK)
                .body("jwt", is(notNullValue()));
    }

    @Test
    public void shouldReturn409WhenCredentialsAreMissingForDdcToken() {
        var chargeId = "mySecondChargeId";
        var gatewayAccountId = "202";
        var credentialsMissingIssuer = Map.of(
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, credentialsMissingIssuer, chargeId);

        connectorRestApi
                .withChargeId(chargeId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_CONFLICT)
                .body("message", is(List.of("Cannot generate Worldpay 3ds Flex DDC JWT for account 202 because the following credentials are unavailable: [issuer]")));
    }

    @Test
    public void shouldReturn409WhenThePaymentProviderIsNotWorldpayForDdcToken() {
        var chargeId = "myThirdChargeId";
        var gatewayAccountId = "303";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, SMARTPAY, validCredentials, chargeId);

        connectorRestApi
                .withChargeId(chargeId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_CONFLICT)
                .body("message", is(List.of("Cannot provide a Worldpay 3ds flex DDC JWT for account 303 because the Payment Provider is not Worldpay.")));
    }
    
    private void setUpChargeAndAccount(String gatewayAccountId, PaymentGatewayName paymentProvider, Map<String, String> credentials, String chargeId) {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway(paymentProvider.toString())
                .withCredentials(credentials)
                .build());

        databaseTestHelper.addCharge(
                anAddChargeParams()
                        .withGatewayAccountId(gatewayAccountId)
                        .withExternalChargeId(chargeId)
                        .build()
        );
    }

}
