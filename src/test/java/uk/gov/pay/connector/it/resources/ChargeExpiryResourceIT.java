package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.AddChargeParameters;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
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
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextLong;
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
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

public class ChargeExpiryResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());
    
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    
    @Test
    void shouldExpireChargesAndHaveTheCorrectEvents() {
        String extChargeId1 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CREATED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String extChargeId2 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(ENTERING_CARD_DETAILS)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String extChargeId3 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_READY)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String extChargeId4 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_3DS_REQUIRED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String extChargeId5 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_3DS_READY)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String extChargeId6 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_SUCCESS)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(6))
                .body("expiry-failed", is(0));

        asList(extChargeId1, extChargeId6).forEach(chargeId ->
                testBaseExtension.getConnectorRestApiClient()
                        .withAccountId(testBaseExtension.getAccountId())
                        .withChargeId(chargeId)
                        .getCharge()
                        .statusCode(OK.getStatusCode())
                        .contentType(JSON)
                        .body(JSON_CHARGE_KEY, is(chargeId))
                        .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus())));

        String chargeStatus1 = app.getDatabaseTestHelper().getChargeStatusByExternalId(extChargeId1);
        String chargeStatus2 = app.getDatabaseTestHelper().getChargeStatusByExternalId(extChargeId2);
        String chargeStatus3 = app.getDatabaseTestHelper().getChargeStatusByExternalId(extChargeId3);
        String chargeStatus4 = app.getDatabaseTestHelper().getChargeStatusByExternalId(extChargeId4);
        String chargeStatus5 = app.getDatabaseTestHelper().getChargeStatusByExternalId(extChargeId5);
        String chargeStatus6 = app.getDatabaseTestHelper().getChargeStatusByExternalId(extChargeId6);

        assertThat(chargeStatus1, is(EXPIRED.getValue()));
        assertThat(chargeStatus2, is(EXPIRED.getValue()));
        assertThat(chargeStatus3, is(EXPIRED.getValue()));
        assertThat(chargeStatus4, is(EXPIRED.getValue()));
        assertThat(chargeStatus5, is(EXPIRED.getValue()));
        assertThat(chargeStatus6, is(EXPIRED.getValue()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(extChargeId1);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(extChargeId2);
        List<String> events3 = app.getDatabaseTestHelper().getInternalEvents(extChargeId3);
        List<String> events4 = app.getDatabaseTestHelper().getInternalEvents(extChargeId4);
        List<String> events5 = app.getDatabaseTestHelper().getInternalEvents(extChargeId5);
        List<String> events6 = app.getDatabaseTestHelper().getInternalEvents(extChargeId6);

        // pre-authorisation states won't enter the EXPIRE_CANCEL_READY state
        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(ENTERING_CARD_DETAILS.getValue(), EXPIRED.getValue()), is(events2));
        assertThat(asList(AUTHORISATION_READY.getValue(), EXPIRED.getValue()), is(events3));

        assertThat(asList(AUTHORISATION_3DS_REQUIRED.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events4));
        assertThat(asList(AUTHORISATION_3DS_READY.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events5));
        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events6));
    }

    @Test
    void shouldExpireChargesOnlyAfterTheExpiryWindow() {
        String shouldExpireCreatedChargeId = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CREATED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());

        String shouldntExpireCreatedChargeId = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CREATED)
                        .withCreatedDate(Instant.now().minus(89, MINUTES)).build());

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(shouldExpireCreatedChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(shouldExpireCreatedChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(shouldExpireCreatedChargeId);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(shouldntExpireCreatedChargeId);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(Collections.singletonList(CREATED.getValue()), is(events2));
    }

    @Test
    void shouldOnlyExpireChargesUpdatedBeforeThreshold() {
        String chargeExternalIdForChargeThatShouldBeExpired = RandomIdGenerator.newId();
        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalIdForChargeThatShouldBeExpired)
                .withGatewayAccountId(testBaseExtension.getAccountId())
                .withStatus(CREATED)
                .withCreatedDate(Instant.now().minus(90, MINUTES))
                .withUpdatedDate(Instant.now().minus(40, MINUTES))
                .build());
        String chargeExternalIdForChargeThatShouldNotBeExpired = RandomIdGenerator.newId();
        app.getDatabaseTestHelper().addCharge(anAddChargeParams()
                .withExternalChargeId(chargeExternalIdForChargeThatShouldNotBeExpired)
                .withGatewayAccountId(testBaseExtension.getAccountId())
                .withStatus(CREATED)
                .withCreatedDate(Instant.now().minus(90, MINUTES))
                .withUpdatedDate(Instant.now().minus(2, MINUTES))
                .build());

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(chargeExternalIdForChargeThatShouldBeExpired)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeExternalIdForChargeThatShouldBeExpired))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(chargeExternalIdForChargeThatShouldNotBeExpired)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeExternalIdForChargeThatShouldNotBeExpired))
                .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()));
    }

    @Test
    void shouldExpireChargesEvenIfOnGatewayCancellationError() {
        String extChargeId1 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_SUCCESS)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CAPTURE_SUBMITTED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build()); //should not get picked

        app.getWorldpayMockClient().mockCancelError();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(0))
                .body("expiry-failed", is(1));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(extChargeId1)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId1))
                .body(JSON_STATE_KEY, is(EXPIRE_CANCEL_FAILED.toExternal().getStatus()));

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(extChargeId1);

        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRE_CANCEL_FAILED.getValue()), is(events));

    }

    @Test
    void shouldExpireSuccessAndFailForMatchingCharges() {
        final String gatewayTransactionId1 = RandomIdGenerator.newId();
        String extChargeId1 = testBaseExtension.addCharge(anAddChargeParameters().withChargeStatus(AUTHORISATION_SUCCESS)
                .withCreatedDate(Instant.now().minus(90, MINUTES))
                .withTransactionId(gatewayTransactionId1)
                .build());
        
        String extChargeId2 = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_SUCCESS)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());

        testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CAPTURE_SUBMITTED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build()); //should ignore

        app.getWorldpayMockClient().mockCancelError();
        app.getWorldpayMockClient().mockCancelSuccessOnlyFor(gatewayTransactionId1);
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(1));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(extChargeId1)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId1))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(extChargeId2)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId2))
                .body(JSON_STATE_KEY, is(EXPIRE_CANCEL_FAILED.toExternal().getStatus()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(extChargeId1);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(extChargeId2);

        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRE_CANCEL_FAILED.getValue()), is(events2));
    }

    @Test
    void shouldPreserveCardDetailsIfChargeExpires() {
        Long chargeId = nextLong();
        AddChargeParameters addChargeParameters = anAddChargeParameters().withChargeStatus(AUTHORISATION_SUCCESS)
                .withCreatedDate(Instant.now().minus(90, MINUTES))
                .withChargeId(chargeId)
                .build();
        testBaseExtension.addCharge(addChargeParameters);

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails.isEmpty(), is(false));

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode());

        chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
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
    void shouldNotExpireChargesWhenAwaitingCaptureDelayIsLessThan120Hours() {
        String chargeToBeExpiredCreatedStatus = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CREATED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String chargeToBeExpiredAwaitingCaptureRequest = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AWAITING_CAPTURE_REQUEST)
                        .withCreatedDate(Instant.now().minus(120, HOURS).plus(1, MINUTES))
                        .build());

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(chargeToBeExpiredCreatedStatus)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredCreatedStatus))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(chargeToBeExpiredAwaitingCaptureRequest)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredAwaitingCaptureRequest))
                .body(JSON_STATE_KEY, is(AWAITING_CAPTURE_REQUEST.toExternal().getStatus()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(chargeToBeExpiredCreatedStatus);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(chargeToBeExpiredAwaitingCaptureRequest);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(Collections.singletonList(AWAITING_CAPTURE_REQUEST.getValue()), is(events2));
    }

    @Test
    void shouldExpireChargesWhenAwaitingCaptureDelayIsMoreThan120Hours() {
        String chargeToBeExpiredCreatedStatus = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CREATED)
                        .withCreatedDate(Instant.now().minus(90, MINUTES)).build());
        String chargeToBeExpiredAwaitingCaptureRequest = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AWAITING_CAPTURE_REQUEST)
                        .withCreatedDate(Instant.now().minus(120, HOURS).minus(1, MINUTES))
                        .build());

        app.getWorldpayMockClient().mockCancelSuccess();
        app.getWorldpayMockClient().mockAuthorisationQuerySuccess();

        testBaseExtension.getConnectorRestApiClient()
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(2))
                .body("expiry-failed", is(0));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(chargeToBeExpiredCreatedStatus)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredCreatedStatus))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(testBaseExtension.getAccountId())
                .withChargeId(chargeToBeExpiredAwaitingCaptureRequest)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeToBeExpiredAwaitingCaptureRequest))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(chargeToBeExpiredCreatedStatus);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(chargeToBeExpiredAwaitingCaptureRequest);

        assertThat(asList(CREATED.getValue(), EXPIRED.getValue()), is(events1));
        assertThat(asList(AWAITING_CAPTURE_REQUEST.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()), is(events2));
    }
}
