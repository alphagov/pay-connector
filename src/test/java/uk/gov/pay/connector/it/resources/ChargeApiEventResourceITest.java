package uk.gov.pay.connector.it.resources;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.fixtures.ChargeApiFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import static javax.ws.rs.core.Response.Status.CREATED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

public class ChargeApiEventResourceITest {

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
        ValidatableResponse response = connectorApi
                .postCreateCharge(createPayload)
                .statusCode(CREATED.getStatusCode());

        Long chargeId = Long.parseLong(response.extract().path(JSON_CHARGE_KEY));
        connectorApi
                .withChargeId(chargeId.toString())
                .postChargeCancellation();

        connectorApi
                .getEvents(chargeId)
                .body("charge_id", is(chargeId.intValue()))
                .body("events.status", hasItems("CREATED", "SYSTEM_CANCELLED"))
                .body("events.updated.size()", equalTo(2));
    }
}
