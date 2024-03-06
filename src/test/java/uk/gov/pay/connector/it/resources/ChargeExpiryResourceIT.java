package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

public class ChargeExpiryResourceIT extends NewChargingITestBase {
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String PROVIDER_NAME = "worldpay";

    public ChargeExpiryResourceIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldExpireChargesAndHaveTheCorrectEvents() {
        String extChargeId1 = addCharge(CREATED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String extChargeId2 = addCharge(ENTERING_CARD_DETAILS, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String extChargeId3 = addCharge(AUTHORISATION_READY, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String extChargeId4 = addCharge(AUTHORISATION_3DS_REQUIRED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String extChargeId5 = addCharge(AUTHORISATION_3DS_READY, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String extChargeId6 = addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(6))
                .body("expiry-failed", is(0));

        asList(extChargeId1, extChargeId6).forEach(chargeId ->
                connectorRestApiClient
                        .withAccountId(accountId)
                        .withChargeId(chargeId)
                        .getCharge()
                        .statusCode(OK.getStatusCode())
                        .contentType(JSON)
                        .body(JSON_CHARGE_KEY, is(chargeId))
                        .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus())));

        String chargeStatus1 = databaseTestHelper.getChargeStatusByExternalId(extChargeId1);
        String chargeStatus2 = databaseTestHelper.getChargeStatusByExternalId(extChargeId2);
        String chargeStatus3 = databaseTestHelper.getChargeStatusByExternalId(extChargeId3);
        String chargeStatus4 = databaseTestHelper.getChargeStatusByExternalId(extChargeId4);
        String chargeStatus5 = databaseTestHelper.getChargeStatusByExternalId(extChargeId5);
        String chargeStatus6 = databaseTestHelper.getChargeStatusByExternalId(extChargeId6);

        assertThat(chargeStatus1, is(EXPIRED.getValue()));
        assertThat(chargeStatus2, is(EXPIRED.getValue()));
        assertThat(chargeStatus3, is(EXPIRED.getValue()));
        assertThat(chargeStatus4, is(EXPIRED.getValue()));
        assertThat(chargeStatus5, is(EXPIRED.getValue()));
        assertThat(chargeStatus6, is(EXPIRED.getValue()));

        List<String> events1 = databaseTestHelper.getInternalEvents(extChargeId1);
        List<String> events2 = databaseTestHelper.getInternalEvents(extChargeId2);
        List<String> events3 = databaseTestHelper.getInternalEvents(extChargeId3);
        List<String> events4 = databaseTestHelper.getInternalEvents(extChargeId4);
        List<String> events5 = databaseTestHelper.getInternalEvents(extChargeId5);
        List<String> events6 = databaseTestHelper.getInternalEvents(extChargeId6);

        // pre-authorisation states won't enter the EXPIRE_CANCEL_READY state
        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(ENTERING_CARD_DETAILS.getValue(), EXPIRED.getValue()), is(events2));
        assertThat(asList(AUTHORISATION_READY.getValue(), EXPIRED.getValue()), is(events3));

        assertThat(asList(AUTHORISATION_3DS_REQUIRED.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events4));
        assertThat(asList(AUTHORISATION_3DS_READY.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events5));
        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events6));
    }

    @Test
    public void shouldExpireChargesOnlyAfterTheExpiryWindow() {
        String shouldExpireCreatedChargeId = addCharge(CREATED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String shouldntExpireCreatedChargeId = addCharge(CREATED, "ref", Instant.now().minus(89, MINUTES), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(shouldExpireCreatedChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(shouldExpireCreatedChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        List<String> events1 = databaseTestHelper.getInternalEvents(shouldExpireCreatedChargeId);
        List<String> events2 = databaseTestHelper.getInternalEvents(shouldntExpireCreatedChargeId);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(Collections.singletonList(CREATED.getValue()), is(events2));
    }

    @Test
    public void shouldOnlyExpireChargesUpdatedBeforeThreshold() {
        String chargeExternalIdForChargeThatShouldBeExpired = RandomIdGenerator.newId();
        databaseTestHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalIdForChargeThatShouldBeExpired)
                .withGatewayAccountId(accountId)
                .withStatus(CREATED)
                .withCreatedDate(Instant.now().minus(90, MINUTES))
                .withUpdatedDate(Instant.now().minus(40, MINUTES))
                .build());
        String chargeExternalIdForChargeThatShouldNotBeExpired = RandomIdGenerator.newId();
        databaseTestHelper.addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalIdForChargeThatShouldNotBeExpired)
                .withGatewayAccountId(accountId)
                .withStatus(CREATED)
                .withCreatedDate(Instant.now().minus(90, MINUTES))
                .withUpdatedDate(Instant.now().minus(2, MINUTES))
                .build());

        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeExternalIdForChargeThatShouldBeExpired)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeExternalIdForChargeThatShouldBeExpired))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeExternalIdForChargeThatShouldNotBeExpired)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeExternalIdForChargeThatShouldNotBeExpired))
                .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()));
    }

    @Test
    public void shouldExpireChargesEvenIfOnGatewayCancellationError() {
        String extChargeId1 = addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        addCharge(CAPTURE_SUBMITTED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId()); //should not get picked

        worldpayMockClient.mockCancelError();
        worldpayMockClient.mockAuthorisationQuerySuccess();

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
        String extChargeId1 = addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(90, MINUTES), gatewayTransactionId1);
        String extChargeId2 = addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        addCharge(CAPTURE_SUBMITTED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId()); //should ignore

        worldpayMockClient.mockCancelError();
        worldpayMockClient.mockCancelSuccessOnlyFor(gatewayTransactionId1);
        worldpayMockClient.mockAuthorisationQuerySuccess();

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
        String externalChargeId = addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));

        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess();

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
    public void shouldNotExpireChargesWhenAwaitingCaptureDelayIsLessThan120Hours() {
        String chargeToBeExpiredCreatedStatus = addCharge(CREATED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String chargeToBeExpiredAwaitingCaptureRequest = addCharge(AWAITING_CAPTURE_REQUEST, "ref",
                Instant.now().minus(120, HOURS).plus(1, MINUTES), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess();

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
        assertThat(Collections.singletonList(AWAITING_CAPTURE_REQUEST.getValue()), is(events2));
    }

    @Test
    public void shouldExpireChargesWhenAwaitingCaptureDelayIsMoreThan120Hours() {
        String chargeToBeExpiredCreatedStatus = addCharge(CREATED, "ref", Instant.now().minus(90, MINUTES), RandomIdGenerator.newId());
        String chargeToBeExpiredAwaitingCaptureRequest = addCharge(AWAITING_CAPTURE_REQUEST, "ref",
                Instant.now().minus(120, HOURS).minus(1, MINUTES), RandomIdGenerator.newId());

        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess();

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
