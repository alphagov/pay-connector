package uk.gov.pay.connector.it;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUBMITTED;
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
    public void authoriseChargeForCardDetails() throws Exception {
        String uniqueChargeId = "98234732938487";

        app.getDatabaseTestHelper().addCharge(accountId, uniqueChargeId, 500, CREATED);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post("/v1/frontend/charges/" + uniqueChargeId + "/card")
                .then()
                .statusCode(201);
        //.contentType(JSON);
    }

    @Test
    public void returnErrorIfCardDetailsHaveAlreadyBeenSubmitted() throws Exception {

        String processedId = "12345669385794877";
        app.getDatabaseTestHelper().addCharge(accountId, processedId, 500, AUTHORIZATION_SUBMITTED);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post("/v1/frontend/charges/" + processedId + "/card")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(String.format("Card already processed for charge with id %s.", processedId)));
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
        cardBody.append("\"card_number\":\"1234567890123456\",");
        cardBody.append("\"cvv\":\"123\",");
        cardBody.append("\"expiry_date\":\"12/99\"");
        cardBody.append("}");
        return cardBody.toString();
    }
}
