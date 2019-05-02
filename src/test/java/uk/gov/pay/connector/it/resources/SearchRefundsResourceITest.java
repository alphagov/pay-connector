package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZonedDateTime;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SearchRefundsResourceITest extends ChargingITestBase {

    private static final String PROVIDER_NAME = "sandbox";

    private static final String INVALID_ACCOUNT_ID = "999999999";

    public SearchRefundsResourceITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturnAllRefundsForGetRefundsByAccountId() {
        long chargeId = nextLong();
        long chargeId2 = nextLong();
        databaseTestHelper.addGatewayAccount("123", "SANDBOX", credentials);
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId("charge1")
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId2)
                .withExternalChargeId("charge2")
                .withGatewayAccountId("123")
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        
        String refundExternalId1 = randomAlphanumeric(10);
        String refundExternalId2 = randomAlphanumeric(10);
        String refundDate1 = "2016-02-03T00:00:00.000Z";
        String refundDate2 = "2016-02-02T00:00:00.000Z";
        
        databaseTestHelper.addRefund(refundExternalId1, "refund-1-provider-reference", 1L, REFUND_SUBMITTED, chargeId, randomAlphanumeric(10), ZonedDateTime.parse(refundDate1));
        databaseTestHelper.addRefund(refundExternalId2, "refund-2-provider-reference", 2L, REFUNDED, chargeId, randomAlphanumeric(10), ZonedDateTime.parse(refundDate2));
        databaseTestHelper.addRefund("shouldnotberetrieved", "refund-1-provider-reference", 1L, REFUND_SUBMITTED, chargeId2, randomAlphanumeric(10), ZonedDateTime.parse(refundDate1));
        databaseTestHelper.addToken(chargeId, "tokenId");

        connectorRestApiClient.withAccountId(accountId)
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getRefunds()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].charge_id", is("charge1"))
                .body("results[0].refund_id", is(refundExternalId1))
                .body("results[0].status", is("submitted"))
                .body("results[0].amount_submitted", is(1))
                .body("results[0].links.size()", is(2))
                .body("results[0].created_date", is(refundDate1))
                .body("results[1].charge_id", is("charge1"))
                .body("results[1].refund_id", is(refundExternalId2))
                .body("results[1].status", is("success"))
                .body("results[1].amount_submitted", is(2))
                .body("results[1].links.size()", is(2))
                .body("results[1].created_date", is(refundDate2))
                .body("total", is(2))
                .body("count", is(2))
                .body("page", is(1))
                .body("results", hasSize(2));
    }

    @Test
    public void shouldReturnNoRefundsWhenNoneFound() {
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(nextLong())
                .withExternalChargeId("charge2")
                .withGatewayAccountId(accountId)
                .withAmount(AMOUNT)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        connectorRestApiClient.withAccountId(accountId)
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getRefunds()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0))
                .body("total", is(0))
                .body("count", is(0))
                .body("page", is(1))
                .body("results", hasSize(0));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenAccountDoesNotExist() {
        connectorRestApiClient.withAccountId(INVALID_ACCOUNT_ID)
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getRefunds()
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", is(format("Gateway account with id %s does not exist", INVALID_ACCOUNT_ID)));
    }
}
