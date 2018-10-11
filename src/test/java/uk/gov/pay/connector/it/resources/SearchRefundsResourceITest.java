package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.hasSize;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;

public class SearchRefundsResourceITest extends ChargingITestBase {

    private static final String PROVIDER_NAME = "sandbox";

    private RestAssuredClient api;
    private static final String INVALID_ACCOUNT_ID = "999999999";

    public SearchRefundsResourceITest() {
        super(PROVIDER_NAME);
    }

    @Before
    public void setup() {
        api = new RestAssuredClient(app.getLocalPort(), accountId);
    }

    @Test
    public void shouldReturnAllRefundsForGetRefundsByAccountId() {
        String returnUrl = "http://return.url/return-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId = nextLong();

        app.getDatabaseTestHelper().addCharge(chargeId, "charge1", accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null,
                ServicePaymentReference.of("ref"), null, email);

        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-1-provider-reference", 1L, RefundStatus.REFUND_SUBMITTED.getValue(), chargeId, now().minusHours(2));
        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-2-provider-reference", 2L, RefundStatus.REFUNDED.getValue(), chargeId, now().minusHours(3));
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        api.withAccountId(accountId)
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getRefunds()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", Is.is(2))
                .body("total", Is.is(2))
                .body("count", Is.is(2))
                .body("page", Is.is(1))
                .body("results", hasSize(2));
    }

    @Test
    public void shouldReturnNoRefundsWhenNoneFound() {
        String returnUrl = "http://return.url/return-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId = nextLong();

        app.getDatabaseTestHelper().addCharge(chargeId, "charge2", accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null,
                ServicePaymentReference.of("ref"), null, email);

        api.withAccountId(accountId)
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getRefunds()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", Is.is(0))
                .body("total", Is.is(0))
                .body("count", Is.is(0))
                .body("page", Is.is(1))
                .body("results", hasSize(0));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenAccountDoesNotExist() {
        api.withAccountId(INVALID_ACCOUNT_ID)
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getRefunds()
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", Is.is(format("Gateway account with id %s does not exist", INVALID_ACCOUNT_ID)));
    }
}
