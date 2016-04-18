package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargeEventsResourceITest {

    private static final String JSON_CHARGE_KEY = "charge_id";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private RestAssuredClient connectorApi = new RestAssuredClient(app, accountId);

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "sandbox");
    }

    @Test
    public void shouldIncludeAllAttributesForAGivenCharge() {
        String createPayload = ChargeApiFixtures.aValidCharge(accountId);
        //create charge
        ValidatableResponse response = connectorApi
                .postCreateCharge(createPayload)
                .statusCode(Response.Status.CREATED.getStatusCode());

        String chargeId = response.extract().path(JSON_CHARGE_KEY);

        connectorApi
                .getEvents(chargeId)
                .body("charge_id", is(chargeId))
                .body("events[0].status", is(EXT_CREATED.getValue()))
                .body("events[0].updated", isWithin(1, ChronoUnit.MINUTES));
    }

    @Test
    public void shouldGetAllEventsForAGivenCharge() throws Exception {

        String createPayload = ChargeApiFixtures.aValidCharge(accountId);
        //create charge
        ValidatableResponse response = connectorApi
                .postCreateCharge(createPayload)
                .statusCode(Response.Status.CREATED.getStatusCode());

        String chargeId = response.extract().path(JSON_CHARGE_KEY);

        //update charge 1
        connectorApi.withChargeId(chargeId)
                .putChargeStatus(updateStatusTo(ENTERING_CARD_DETAILS))
                .statusCode(NO_CONTENT.getStatusCode());

        //cancel charge
        connectorApi
                .withChargeId(chargeId)
                .postChargeCancellation();

        //Then
        connectorApi
                .getEvents(chargeId)
                .body("charge_id", is(chargeId))
                .body("events.status", hasItems(EXT_CREATED.getValue()
                        , EXT_IN_PROGRESS.getValue()
                        , EXT_SYSTEM_CANCELLED.getValue()))
                .body("events.updated.size()", equalTo(3));
    }

    @Test
    public void shouldNotGetRepeatedExternalChargeEvents() throws Exception {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge4";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, 100L, chargeStatus, "returnUrl", null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        List<ChargeStatus> statuses = asList(ChargeStatus.CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, SYSTEM_CANCELLED, ENTERING_CARD_DETAILS);
        setupLifeCycleEventsFor(app, chargeId, statuses);

        connectorApi
                .getEvents(externalChargeId)
                .body("charge_id", Matchers.is(externalChargeId))
                .body("events.status", hasSize(4))
                .body("events.status[0]", Matchers.is(EXT_CREATED.getValue()))
                .body("events.status[1]", Matchers.is(EXT_IN_PROGRESS.getValue()))
                .body("events.status[2]", Matchers.is(EXT_SYSTEM_CANCELLED.getValue()))
                .body("events.status[3]", Matchers.is(EXT_IN_PROGRESS.getValue()))
                .body("events.chargeId[0]", isEmptyOrNullString()); // chargeId should not be there in json response for every event
    }

    @Test
    public void shouldReturn404WhenAccountIdIsNonNumeric() {
        connectorApi.withAccountId("invalidAccountId")
                .getEvents("123charge")
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    public static class ChargeApiFixtures {

        private static final String JSON_AMOUNT_KEY = "amount";
        private static final String JSON_REFERENCE_KEY = "reference";
        private static final String JSON_DESCRIPTION_KEY = "description";
        private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
        private static final String JSON_RETURN_URL_KEY = "return_url";

        private static Long defaultAmount = 6234L;
        private static String defaultReference = "a-reference";
        private static String defaultDescription = "a-description";
        private static String defaultReturnUrl = "http://service.url/success-page/";

        public static String aValidCharge(String accountId) {
            return buildCharge(accountId, defaultAmount, defaultReference, defaultDescription, defaultReturnUrl);
        }

        private static String buildCharge(String accountId, Long amount, Serializable reference, String description, String returnUrl) {
            return toJson(ImmutableMap.of(
                    JSON_AMOUNT_KEY, amount,
                    JSON_REFERENCE_KEY, reference,
                    JSON_DESCRIPTION_KEY, description,
                    JSON_GATEWAY_ACC_KEY, accountId,
                    JSON_RETURN_URL_KEY, returnUrl));
        }

    }

    private String updateStatusTo(ChargeStatus chargeStatus) {
        return toJson(ImmutableMap.of("new_status", chargeStatus.getValue()));
    }

    private static void setupLifeCycleEventsFor(DropwizardAppWithPostgresRule app, Long chargeId, List<ChargeStatus> statuses) {
        statuses.stream().forEach(
                st -> app.getDatabaseTestHelper().addEvent(chargeId, st.getValue())
        );
    }
}
