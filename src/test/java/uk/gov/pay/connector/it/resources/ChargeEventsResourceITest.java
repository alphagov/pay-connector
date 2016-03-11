package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.io.Serializable;

import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
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
    public void shouldGetAllEventsForAGivenCharge() throws Exception {

        String createPayload = ChargeApiFixtures.aValidCharge(accountId);
        //create charge
        ValidatableResponse response = connectorApi
                .postCreateCharge(createPayload)
                .statusCode(CREATED.getStatusCode());
        Long chargeId = Long.parseLong(response.extract().path(JSON_CHARGE_KEY));

        //update charge 1
        connectorApi.withChargeId(chargeId.toString())
                .putChargeStatus(updateStatusTo(ENTERING_CARD_DETAILS))
                .statusCode(NO_CONTENT.getStatusCode());

        //cancel charge
        connectorApi
                .withChargeId(chargeId.toString())
                .postChargeCancellation();

        //Then
        connectorApi
                .getEvents(chargeId)
                .body("charge_id", is(chargeId.intValue()))
                .body("events.status", hasItems(EXT_CREATED.getValue()
                        , EXT_IN_PROGRESS.getValue()
                        , EXT_SYSTEM_CANCELLED.getValue()))
                .body("events.updated.size()", equalTo(3));
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
}
