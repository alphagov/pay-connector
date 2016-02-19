package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.fixtures.ChargeApiFixtures;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargeApiEventJpaResourceITest {

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

    private String updateStatusTo(ChargeStatus chargeStatus) {
        return toJson(ImmutableMap.of("new_status", chargeStatus.getValue()));
    }
}
