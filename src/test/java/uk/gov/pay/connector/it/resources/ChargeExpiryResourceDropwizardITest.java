package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.collections4.CollectionUtils.isEqualCollection;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeExpiryResourceDropwizardITest extends ChargingITestBase {

    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String PROVIDER_NAME = "worldpay";


    private RestAssuredClient getChargeApi = new RestAssuredClient(app, accountId);

    public ChargeExpiryResourceDropwizardITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldExpireChargesBeforeAndAfterAuthorisationAndShouldHaveTheRightEvents() {
        String extChargeId1 = addCharge(CREATED, "ref", ZonedDateTime.now().minusHours(1), "gatewayTransactionId1");
        String extChargeId2 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "transaction-id");

        worldpay.mockCancelSuccess();

        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(2))
                .body("expiry-failed", is(0));

        asList(extChargeId1, extChargeId2).forEach(chargeId ->
                getChargeApi
                        .withAccountId(accountId)
                        .withChargeId(chargeId)
                        .getCharge()
                        .statusCode(OK.getStatusCode())
                        .contentType(JSON)
                        .body(JSON_CHARGE_KEY, is(chargeId))
                        .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus())));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(extChargeId1);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(extChargeId2);

        assertThat(events1, containsInAnyOrder(CREATED.getValue(), EXPIRED.getValue()));
        assertThat(events2, containsInAnyOrder(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue()));

        assertTrue(isEqualCollection(events1,
                asList(CREATED.getValue(), EXPIRED.getValue())));
        assertTrue(isEqualCollection(events2,
                asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue())));
    }

    @Test
    public void shouldExpireChargesEvenIfOnGatewayCancellationError() {
        String extChargeId1 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "gatewayTransactionId1");
        String extChargeId2 = addCharge(CAPTURE_SUBMITTED, "ref", ZonedDateTime.now().minusHours(1), "gatewayTransactionId2"); //should not get picked

        worldpay.mockCancelError();

        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(0))
                .body("expiry-failed", is(1));

            getChargeApi
                    .withAccountId(accountId)
                    .withChargeId(extChargeId1)
                    .getCharge()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(extChargeId1))
                    .body(JSON_STATE_KEY, is(EXPIRE_CANCEL_FAILED.toExternal().getStatus()));

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(extChargeId1);

        assertTrue(isEqualCollection(events,
                asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRE_CANCEL_FAILED.getValue())));

    }


    @Test
    public void shouldExpireSuccessAndFailForMatchingCharges() throws Exception {
        String extChargeId1 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "gatewayTransactionId1");
        String extChargeId2 = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "gatewayTransactionId2");
        String extChargeId3 = addCharge(CAPTURE_SUBMITTED, "ref", ZonedDateTime.now().minusHours(1), "gatewayTransactionId3"); //should ignore

        worldpay.mockCancelSuccessOnlyFor("gatewayTransactionId1");

        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(1));

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId1)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId1))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId2)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId2))
                .body(JSON_STATE_KEY, is(EXPIRE_CANCEL_FAILED.toExternal().getStatus()));

        List<String> events1 = app.getDatabaseTestHelper().getInternalEvents(extChargeId1);
        List<String> events2 = app.getDatabaseTestHelper().getInternalEvents(extChargeId2);

        assertTrue(isEqualCollection(events1,
                asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRED.getValue())));
        assertTrue(isEqualCollection(events2,
                asList(AUTHORISATION_SUCCESS.getValue(), EXPIRE_CANCEL_READY.getValue(), EXPIRE_CANCEL_FAILED.getValue())));

    }

    @Test
    public void shouldPreserveCardDetailsIfChargeExpires() throws Exception {
        String externalChargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "transaction-id");
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));

        worldpay.mockCancelSuccess();

        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails.isEmpty(), is(false));

        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode());

        chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, is(notNullValue()));
        assertThat(chargeCardDetails.get("card_brand"), is(notNullValue()));
        assertThat(chargeCardDetails.get("last_digits_card_number"), is(notNullValue()));
        assertThat(chargeCardDetails.get("expiry_date"), is(notNullValue()));
        assertThat(chargeCardDetails.get("cardholder_name"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_line1"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_line2"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_postcode"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_country"), is(notNullValue()));
    }

}
