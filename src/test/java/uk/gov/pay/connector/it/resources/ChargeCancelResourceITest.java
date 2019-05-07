package uk.gov.pay.connector.it.resources;

import junitparams.Parameters;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargeCancelResourceITest extends ChargingITestBase {

    public ChargeCancelResourceITest() {
        super("worldpay");
    }

    @Test
    @Parameters({"CREATED", "ENTERING_CARD_DETAILS"})
    public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth(ChargeStatus status) {
        String chargeId = addCharge(status, "ref", ZonedDateTime.now().minusHours(1), "irrelevant");
        cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

        List<String> events = databaseTestHelper.getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(status.getValue(), SYSTEM_CANCELLED.getValue()));
    }

    @Test
    public void shouldPreserveCardDetailsIfCancelled() {
        String externalChargeId = addCharge(ChargeStatus.AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));

        worldpayMockClient.mockCancelSuccess();

        Map<String, Object> cardDetails = databaseTestHelper.getChargeCardDetailsByChargeId(chargeId);
        assertThat(cardDetails.isEmpty(), is(false));

        cancelChargeAndCheckApiStatus(externalChargeId, SYSTEM_CANCELLED, 204);

        cardDetails = databaseTestHelper.getChargeCardDetailsByChargeId(chargeId);
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
        worldpayMockClient.mockCancelSuccess();

        cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

        List<String> events = databaseTestHelper.getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                SYSTEM_CANCEL_READY.getValue(),
                SYSTEM_CANCELLED.getValue()));
    }

    @Test
    public void shouldRespondWith204WithLockingStatus_IfCancelFailedAfterAuth() {

        String chargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
        worldpayMockClient.mockCancelError();

        cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCEL_ERROR, 204);

        List<String> events = databaseTestHelper.getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                SYSTEM_CANCEL_READY.getValue(),
                SYSTEM_CANCEL_ERROR.getValue()));
    }

    @Test
    @Parameters({
            "AUTHORISATION_REJECTED",
            "AUTHORISATION_ERROR",
            "CAPTURE_READY",
            "CAPTURED",
            "CAPTURE_SUBMITTED",
            "CAPTURE_ERROR",
            "EXPIRED",
            "EXPIRE_CANCEL_FAILED",
            "SYSTEM_CANCEL_ERROR",
            "SYSTEM_CANCELLED",
            "USER_CANCEL_READY",
            "USER_CANCELLED",
            "USER_CANCEL_ERROR"
    })
    public void respondWith400_whenNotCancellableState(ChargeStatus notCancellableState) {
        String chargeId = createNewInPastChargeWithStatus(notCancellableState);
        String expectedMessage = "Charge not in correct state to be processed, " + chargeId;
        connectorRestApiClient
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(BAD_REQUEST.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
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
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
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
                .body("message", contains("Charge with id [" + unknownChargeId + "] not found."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void respondWith404_IfAccountIdIsMissing() {
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
    public void respondWith404_IfAccountIdIsNonNumeric() {
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
    public void respondWith404_IfChargeIdDoNotBelongToAccount() {
        String chargeId = createNewInPastChargeWithStatus(CREATED);
        String expectedMessage = format("Charge with id [%s] not found.", chargeId);

        connectorRestApiClient
                .withAccountId("12345")
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        return addCharge(status, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
    }
}
