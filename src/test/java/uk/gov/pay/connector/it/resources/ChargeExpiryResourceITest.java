package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargeExpiryResourceITest extends ChargingITestBase {

    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String PROVIDER_NAME = "worldpay";

    public ChargeExpiryResourceITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldExpireChargesBeforeAndAfterAuthorisationAndShouldHaveTheRightEvents() {
        String extChargeId1 = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String extChargeId2 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(2))
                .body("expiry-failed", is(0));

        asList(extChargeId1, extChargeId2).forEach(chargeId ->
                connectorRestApiClient
                        .withAccountId(accountId)
                        .withChargeId(chargeId)
                        .getCharge()
                        .statusCode(OK.getStatusCode())
                        .contentType(JSON)
                        .body(JSON_CHARGE_KEY, is(chargeId))
                        .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus())));

        List<String> events1 = databaseTestHelper.getInternalEvents(extChargeId1);
        List<String> events2 = databaseTestHelper.getInternalEvents(extChargeId2);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events2));
    }

    @Test
    public void shouldExpireChargesOnlyAfterTheExpiryWindow() {
        String shouldExpireChargeId = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String shouldntExpireChargeId = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(89), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(shouldExpireChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(shouldExpireChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(shouldntExpireChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(shouldntExpireChargeId))
                .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()));

        List<String> events1 = databaseTestHelper.getInternalEvents(shouldExpireChargeId);
        List<String> events2 = databaseTestHelper.getInternalEvents(shouldntExpireChargeId);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(Collections.singletonList(CREATED.getValue()), is(events2));
    }

    @Test
    public void shouldExpireChargesEvenIfOnGatewayCancellationError() {
        String extChargeId1 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        addCharge(CAPTURE_SUBMITTED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId()); //should not get picked

        worldpayMockClient.mockCancelError();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(0))
                .body("expiry-failed", is(1));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId1)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId1))
                .body(JSON_STATE_KEY, is(EXPIRE_CANCEL_FAILED.toExternal().getStatus()));

        List<String> events = databaseTestHelper.getInternalEvents(extChargeId1);

        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRE_CANCEL_FAILED.getValue()), is(events));

    }


    @Test
    public void shouldExpireSuccessAndFailForMatchingCharges() {
        final String gatewayTransactionId1 = RandomIdGenerator.newId();
        String extChargeId1 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusMinutes(90), gatewayTransactionId1);
        String extChargeId2 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        addCharge(CAPTURE_SUBMITTED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId()); //should ignore

        worldpayMockClient.mockCancelError();
        worldpayMockClient.mockCancelSuccessOnlyFor(gatewayTransactionId1);

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(1));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId1)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId1))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(extChargeId2)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId2))
                .body(JSON_STATE_KEY, is(EXPIRE_CANCEL_FAILED.toExternal().getStatus()));

        List<String> events1 = databaseTestHelper.getInternalEvents(extChargeId1);
        List<String> events2 = databaseTestHelper.getInternalEvents(extChargeId2);

        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRE_CANCEL_FAILED.getValue()), is(events2));

    }

    @Test
    public void shouldPreserveCardDetailsIfChargeExpires() {
        String externalChargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));

        worldpayMockClient.mockCancelSuccess();

        Map<String, Object> chargeCardDetails = databaseTestHelper.getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails.isEmpty(), is(false));

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode());

        chargeCardDetails = databaseTestHelper.getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, is(notNullValue()));
        assertThat(chargeCardDetails.get("card_brand"), is(notNullValue()));
        assertThat(chargeCardDetails.get("last_digits_card_number"), is(notNullValue()));
        assertThat(chargeCardDetails.get("first_digits_card_number"), is(notNullValue()));
        assertThat(chargeCardDetails.get("expiry_date"), is(notNullValue()));
        assertThat(chargeCardDetails.get("cardholder_name"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_line1"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_line2"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_postcode"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_country"), is(notNullValue()));
    }

    @Test
    public void shouldNotExpireChargesWhenAwaitingCaptureDelayIsLessThan48Hours() {
        String chargeToBeExpiredCreatedStatus = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String chargeToBeExpiredAwaitingCaptureRequest = addCharge(AWAITING_CAPTURE_REQUEST, "ref", ZonedDateTime.now().minusHours(48L).plusMinutes(1L), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeToBeExpiredCreatedStatus)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredCreatedStatus))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeToBeExpiredAwaitingCaptureRequest)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredAwaitingCaptureRequest))
                .body(JSON_STATE_KEY, is(AWAITING_CAPTURE_REQUEST.toExternal().getStatus()));

        List<String> events1 = databaseTestHelper.getInternalEvents(chargeToBeExpiredCreatedStatus);
        List<String> events2 = databaseTestHelper.getInternalEvents(chargeToBeExpiredAwaitingCaptureRequest);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(AWAITING_CAPTURE_REQUEST.getValue()), is(events2));
    }

    @Test
    public void shouldExpireChargesWhenAwaitingCaptureDelayIsMoreThan48Hours() {
        String chargeToBeExpiredCreatedStatus = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String chargeToBeExpiredAwaitingCaptureRequest = addCharge(AWAITING_CAPTURE_REQUEST, "ref", ZonedDateTime.now().minusHours(48L).minusMinutes(1L), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(2))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeToBeExpiredCreatedStatus)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredCreatedStatus))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeToBeExpiredAwaitingCaptureRequest)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredAwaitingCaptureRequest))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        List<String> events1 = databaseTestHelper.getInternalEvents(chargeToBeExpiredCreatedStatus);
        List<String> events2 = databaseTestHelper.getInternalEvents(chargeToBeExpiredAwaitingCaptureRequest);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(AWAITING_CAPTURE_REQUEST.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events2));
    }
}
