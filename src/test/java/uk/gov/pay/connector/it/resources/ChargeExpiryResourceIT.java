package uk.gov.pay.connector.it.resources;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.http.ContentType.JSON;
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

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargeExpiryResourceIT extends ChargingITestBase {

    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String PROVIDER_NAME = "worldpay";

    public ChargeExpiryResourceIT() {
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
    public void shouldExpireCharges() {
        
        ChargeStatus[] expirableChargeStatuses = {CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, AUTHORISATION_3DS_READY, AUTHORISATION_3DS_REQUIRED};
        String[] shouldExpireChargeId = new String[5];
        String[] shouldntExpireChargeId = new String[5];
        Set<List<String>> events = new HashSet<>();
        
        for(int i = 0; i < shouldExpireChargeId.length; i++) {
            shouldExpireChargeId[i] = addCharge(expirableChargeStatuses[i], String.valueOf(i), ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
            shouldntExpireChargeId[i] = addCharge(expirableChargeStatuses[i], String.valueOf(i), ZonedDateTime.now().minusMinutes(89), RandomIdGenerator.newId());
        }
        
        worldpayMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(5))
                .body("expiry-failed", is(0));
        
        for(String chargeId : shouldExpireChargeId) {
            connectorRestApiClient
                    .withAccountId(accountId)
                    .withChargeId(chargeId)
                    .getCharge()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(chargeId))
                    .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));
        }
        
        for(int i = 0; i < shouldExpireChargeId.length; i++) {
            events.add(databaseTestHelper.getInternalEvents(shouldExpireChargeId[i]));
            events.add(databaseTestHelper.getInternalEvents(shouldntExpireChargeId[i]));
        }
        
        assertThat(events.contains(asList(CREATED.getValue(), EXPIRED.getValue())), is(true));
        assertThat(events.contains(Collections.singletonList(CREATED.getValue())), is(true));
        assertThat(events.contains(asList(ENTERING_CARD_DETAILS.getValue(), EXPIRED.getValue())), is(true));
        assertThat(events.contains(Collections.singletonList(ENTERING_CARD_DETAILS.getValue())), is(true));
        assertThat(events.contains(asList(AUTHORISATION_READY.getValue(), EXPIRED.getValue())), is(true));
        assertThat(events.contains(Collections.singletonList(AUTHORISATION_READY.getValue())), is(true));
        assertThat(events.contains(asList(AUTHORISATION_3DS_READY.getValue(), EXPIRED.getValue())), is(true));
        assertThat(events.contains(Collections.singletonList(AUTHORISATION_3DS_READY.getValue())), is(true));
        assertThat(events.contains(asList(AUTHORISATION_3DS_REQUIRED.getValue(), EXPIRED.getValue())), is(true));
        assertThat(events.contains(Collections.singletonList(AUTHORISATION_3DS_REQUIRED.getValue())), is(true));
    }

    @Test
    public void shouldExpireChargesOnlyAfterTheExpiryWindow() {
        String shouldExpireCreatedChargeId = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String shouldntExpireCreatedChargeId = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(89), RandomIdGenerator.newId());
        
        worldpayMockClient.mockCancelSuccess();

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)  §
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
    public void shouldNotExpireChargesWhenAwaitingCaptureDelayIsLessThan120Hours() {
        String chargeToBeExpiredCreatedStatus = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String chargeToBeExpiredAwaitingCaptureRequest = addCharge(AWAITING_CAPTURE_REQUEST, "ref", ZonedDateTime.now().minusHours(120L).plusMinutes(1L), RandomIdGenerator.newId());

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
        assertThat(Collections.singletonList(AWAITING_CAPTURE_REQUEST.getValue()), is(events2));
    }

    @Test
    public void shouldExpireChargesWhenAwaitingCaptureDelayIsMoreThan120Hours() {
        String chargeToBeExpiredCreatedStatus = addCharge(CREATED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());
        String chargeToBeExpiredAwaitingCaptureRequest = addCharge(AWAITING_CAPTURE_REQUEST, "ref", ZonedDateTime.now().minusHours(120L).minusMinutes(1L), RandomIdGenerator.newId());

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

    @Test
    public void shouldExpireWithGatewayIfExistsInCancellableStateWithGatewayEvenIfChargeIsPreAuthorisation() {
        String chargeId = addCharge(AUTHORISATION_3DS_REQUIRED, "ref", ZonedDateTime.now().minusMinutes(90), RandomIdGenerator.newId());

        WireMock.reset();
        worldpayMockClient.mockCancelSuccess();
        worldpayMockClient.mockAuthorisationQuerySuccess(chargeId);

        connectorRestApiClient
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("state.status", is(EXPIRED.toExternal().getStatus()));

        verify(exactly(2), postRequestedFor(UrlPattern.fromOneOf(null, null, "/jsp/merchant/xml/paymentService.jsp", null)));
    }
}
