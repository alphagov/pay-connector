package uk.gov.pay.connector.it;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;

public class CardDetailsResourceITest {

    private String accountId = "666";
    private String validCardDetails = validCardDetails();

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void shouldAuthoriseChargeForValidCardDetails() throws Exception {
        String uniqueChargeId = "983794837598475";
        setupCharge(uniqueChargeId);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post("/v1/frontend/charges/" + uniqueChargeId + "/card")
                .then()
                .statusCode(200);

        assertChargeStatusIs(uniqueChargeId, "AUTHORIZATION SUCCESS");
    }

    @Test
    public void returnErrorAndDoNotUpdateChargeStatusIfSomeCardDetailsHaveAlreadyBeenSubmitted() throws Exception {

        String chargeId = "12345669385794877";
        setupCharge(chargeId);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post("/v1/frontend/charges/" + chargeId + "/card")
                .then()
                .statusCode(200);

        String originalStatus = "AUTHORIZATION SUCCESS";
        assertChargeStatusIs(chargeId, originalStatus);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post("/v1/frontend/charges/" + chargeId + "/card")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(String.format("Card already processed for charge with id %s.", chargeId)));

        assertChargeStatusIs(chargeId, originalStatus);
    }

    @Test
    public void returnErrorAndDoNotUpdateChargeStatusIfCardDetailsAreInvalid() throws Exception {

        String chargeId = "8172643872964549";
        setupCharge(chargeId);

        StringBuilder cardWithInvalidExpiryDateFormat = new StringBuilder();
        cardWithInvalidExpiryDateFormat.append("{");
        cardWithInvalidExpiryDateFormat.append("\"card_number\":\"4242424242424242\",");
        cardWithInvalidExpiryDateFormat.append("\"cvc\":\"123\",");
        cardWithInvalidExpiryDateFormat.append("\"expiry_date\":\"1299\"");
        cardWithInvalidExpiryDateFormat.append("}");

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(cardWithInvalidExpiryDateFormat.toString())
                .post("/v1/frontend/charges/" + chargeId + "/card")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(String.format("Values do not match expected format/length.")));

        assertChargeStatusIs(chargeId, "CREATED");
    }

    @Test
    public void return404IfChargeDoesNotExist() throws Exception {

        String unknownId = "61234569847520367";

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post("/v1/frontend/charges/" + unknownId + "/card")
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(String.format("Parent charge with id %s not found.", unknownId)));
    }

    private String validCardDetails() {
        StringBuilder cardBody = new StringBuilder();
        cardBody.append("{");
        cardBody.append("\"card_number\":\"4242424242424242\",");
        cardBody.append("\"cvc\":\"123\",");
        cardBody.append("\"expiry_date\":\"12/99\"");
        cardBody.append("}");
        return cardBody.toString();
    }

    private void assertChargeStatusIs(String uniqueChargeId, String status) {
        given().port(app.getLocalPort())
                .get("/v1/frontend/charges/" + uniqueChargeId)
                .then()
                .body("status", is(status));
    }

    private void setupCharge(String chargeId) {
        app.getDatabaseTestHelper().addCharge(accountId, chargeId, 500, CREATED);
    }
}
