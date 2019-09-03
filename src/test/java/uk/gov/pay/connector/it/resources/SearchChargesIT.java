package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SearchChargesIT {

    @DropwizardTestContext
    private TestContext testContext;
    private DatabaseTestHelper databaseTestHelper;
    private String accountId;
    private RestAssuredClient connectorRestApiClient;

    @Before
    public void setupGatewayAccount() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(nextLong());
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("sandbox")
                .withServiceName("a cool service")
                .build());
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);
    }

    @Test
    public void searchChargesReturnsExpectedGatewayTransactionId() {
        addCharge("txId-1234", ChargeStatus.CAPTURED);
        
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("payment_states", "captured")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("results[0].gateway_transaction_id", Is.is("txId-1234"));
    }
    
    @Test
    public void searchCharges_doesNotMatchOnPartialReferenceMatch() {
        addCharge(ServicePaymentReference.of("partial_reference"));

        connectorRestApiClient
                .withQueryParam("reference", "ref")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void searchCharges_doesReturnOnExactReferenceMatch() {
        addCharge(ServicePaymentReference.of("reference"));

        connectorRestApiClient
                .withQueryParam("reference", "reference")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));
    }
    
    @Test
    public void searchCharges_doesReturnOnCaseInsensitiveExactReferenceMatch() {
        addCharge(ServicePaymentReference.of("rEfeReNcE"));

        connectorRestApiClient
                .withQueryParam("reference", "refERenCe")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));
    }

    private void addCharge(String gatewayTransactionId, ChargeStatus chargeStatus) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(chargeStatus)
                .withTransactionId(gatewayTransactionId)
                .build());
    }
    
    private void addCharge(ServicePaymentReference reference) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withReference(reference)
                .withAmount(100)
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withTransactionId("gateway transaction id")
                .build());
    }
}
