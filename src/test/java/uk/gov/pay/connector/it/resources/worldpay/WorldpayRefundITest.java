package uk.gov.pay.connector.it.resources.worldpay;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.resources.ApiPaths.REFUNDS_API_PATH;

public class WorldpayRefundITest extends CardResourceITestBase {

    public WorldpayRefundITest() {
        super("worldpay");
    }

    @Test
    @Ignore
    public void shouldBeAbleToRequestARefund_partialAmount() {

        int chargeAmount = 1000;
        int refundAmount = 100;
        String transactionId = "worldpayTransactionRefund1";

        long chargeId = nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, chargeAmount, CAPTURED, "returnUrl", transactionId);

        String paymentUrl = "http://localhost:" + app.getLocalPort() + "/v1/api/accounts/" + accountId + "/charges/" + externalChargeId;

        worldpay.mockRefundResponse();

        ValidatableResponse response = givenSetup()
                .body("{\"amount\": " + refundAmount + "}")
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(100))
                .body("status", is("submitted"))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS));

        String refundId = response.extract().path("refund_id");
        response.body("_links", hasSize(2))
                .body("_links", containsLink("self", "GET", paymentUrl + "/refunds/" + refundId))
                .body("_links", containsLink("payment_url", "GET", paymentUrl))
                .extract().path("refund_id");
    }

    @Test
    @Ignore
    public void shouldBeAbleToRequestARefund_fullAmount() {

        int chargeAmount = 1000;
        int refundAmount = 1000;
        String transactionId = "worldpayTransactionRefund1";

        long chargeId = nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, chargeAmount, CAPTURED, "returnUrl", transactionId);

        worldpay.mockRefundResponse();

        givenSetup()
                .body("{\"amount\": " + refundAmount + "}")
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200)
                .body("amount", is(1000))
                .body("status", is("submitted"));
    }

    @Test
    @Ignore
    public void shouldBeAbleToRequestARefund_multiplePartialAmounts() {

        int chargeAmount = 1000;
        int refundAmount1 = 100;
        int refundAmount2 = 200;
        String transactionId = "worldpayTransactionRefund1";

        long chargeId = nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, chargeAmount, CAPTURED, "returnUrl", transactionId);

        givenSetup()
                .body("{\"amount\": " + refundAmount1 + "}")
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200);

        givenSetup()
                .body("{\"amount\": " + refundAmount2 + "}")
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200);

        givenSetup()
                .get(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200)
                .body(hasSize(2));
        //TODO Verify content. WIP

    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {

        int chargeAmount = 1000;
        int refundAmount = 1100;
        long chargeId = nextInt();
        String externalChargeId = "charge-" + chargeId;

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, chargeAmount, CAPTURED, "returnUrl", "worldpayTransactionRefund1");

        givenSetup()
                .body("{\"amount\": " + refundAmount + "}")
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(400);

    }

    @Test
    public void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {

        int chargeAmount = 1000;
        int refundAmount1 = 900;
        int refundAmount2 = 500;
        String transactionId = "worldpayTransactionRefund1";

        long chargeId = nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, chargeAmount, CAPTURED, "returnUrl", transactionId);

        givenSetup()
                .body("{\"amount\": " + refundAmount1 + "}")
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200);

        givenSetup()
                .body("{\"amount\": " + refundAmount2 + "}")
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(400);

        givenSetup()
                .get(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(200)
                .body(hasSize(1));
        //TODO Verify content. WIP
    }
}
