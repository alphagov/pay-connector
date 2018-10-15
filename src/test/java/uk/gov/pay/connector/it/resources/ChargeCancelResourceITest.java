package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_READY;

public class ChargeCancelResourceITest extends ChargingITestBase {

    public ChargeCancelResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth() {

        asList(CREATED, ENTERING_CARD_DETAILS).forEach(status -> {
            String chargeId = addCharge(status, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
            cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

            List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
            assertThat(events.size(), is(2));
            assertThat(events, hasItems(status.getValue(), SYSTEM_CANCELLED.getValue()));
        });
    }

    @Test
    public void shouldPreserveCardDetailsIfCancelled() {
        String externalChargeId = addCharge(ChargeStatus.AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));

        worldpay.mockCancelSuccess();

        Map<String, Object> cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(cardDetails.isEmpty(), is(false));

        cancelChargeAndCheckApiStatus(externalChargeId, SYSTEM_CANCELLED, 204);

        cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(cardDetails, is(notNullValue()));
        assertThat(cardDetails.get("card_brand"), is(notNullValue()));
        assertThat(cardDetails.get("last_digits_card_number"), is(notNullValue()));
        assertThat(cardDetails.get("first_digits_card_number"), is(notNullValue()));
        assertThat(cardDetails.get("expiry_date"), is(notNullValue()));
        assertThat(cardDetails.get("cardholder_name"), is(notNullValue()));
        assertThat(cardDetails.get("address_line1"), is(notNullValue()));
        assertThat(cardDetails.get("address_line2"), is(notNullValue()));
        assertThat(cardDetails.get("address_postcode"), is(notNullValue()));
        assertThat(cardDetails.get("address_country"), is(notNullValue()));

    }

    @Test
    public void shouldRespondWith204WithLockingStatus_IfCancelledAfterAuth() {
        String chargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "transaction-id");
        worldpay.mockCancelSuccess();

        cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                SYSTEM_CANCEL_READY.getValue(),
                SYSTEM_CANCELLED.getValue()));
    }

    @Test
    public void shouldRespondWith204WithLockingStatus_IfCancelFailedAfterAuth() {

        String chargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
        worldpay.mockCancelError();

        cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCEL_ERROR, 204);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                SYSTEM_CANCEL_READY.getValue(),
                SYSTEM_CANCEL_ERROR.getValue()));
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        List<ChargeStatus> nonCancellableStatuses = asList(
                AUTHORISATION_REJECTED,
                AUTHORISATION_ERROR,
                CAPTURE_READY,
                CAPTURED,
                CAPTURE_SUBMITTED,
                CAPTURE_ERROR,
                EXPIRED,
                EXPIRE_CANCEL_FAILED,
                SYSTEM_CANCEL_ERROR,
                SYSTEM_CANCELLED,
                USER_CANCEL_READY,
                USER_CANCELLED,
                USER_CANCEL_ERROR
        );

        nonCancellableStatuses.forEach(notCancellableState -> {
            String chargeId = createNewInPastChargeWithStatus(notCancellableState);
            String expectedMessage = "Charge not in correct state to be processed, " + chargeId;
            connectorRestApiClient
                    .withChargeId(chargeId)
                    .postChargeCancellation()
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .and()
                    .contentType(JSON)
                    .body("message", is(expectedMessage));
        });
    }

    @Test
    public void respondWith202_whenCancelAlreadyInProgress() {
        String chargeId = createNewInPastChargeWithStatus(SYSTEM_CANCEL_READY);
        String expectedMessage = "System Cancellation for charge already in progress, " + chargeId;
        connectorRestApiClient
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(ACCEPTED.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith404_whenPaymentNotFound() {
        String unknownChargeId = "2344363244";
        connectorRestApiClient
                .withChargeId(unknownChargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is("Charge with id [" + unknownChargeId + "] not found."));
    }

    @Test
    public void respondWith400__IfAccountIdIsMissing() {
        String chargeId = createNewInPastChargeWithStatus(CREATED);
        String expectedMessage = "HTTP 404 Not Found";

        connectorRestApiClient
                .withAccountId("")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith404__IfAccountIdIsNonNumeric() {
        String chargeId = createNewInPastChargeWithStatus(CREATED);
        String expectedMessage = "HTTP 404 Not Found";

        connectorRestApiClient
                .withAccountId("ABSDCEFG")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith404__IfChargeIdDoNotBelongToAccount() {
        String chargeId = createNewInPastChargeWithStatus(CREATED);
        String expectedMessage = format("Charge with id [%s] not found.", chargeId);

        connectorRestApiClient
                .withAccountId("12345")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        return addCharge(status, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
    }
}
