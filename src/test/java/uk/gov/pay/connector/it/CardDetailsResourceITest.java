package uk.gov.pay.connector.it;

import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;

public class CardDetailsResourceITest {

    private String accountId = "666";
    private String validCardDetails = buildJsonCardDetailsFor("4242424242424242");

    private String cardUrlFor(String id) {
        return "/v1/frontend/charges/" + id + "/cards";
    }

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void shouldAuthoriseChargeForValidCardDetails() throws Exception {
        String chargeId = createNewCharge();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertChargeStatusIs(chargeId, "AUTHORIZATION SUCCESS");
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatusIfCardDetailsAreInvalid() throws Exception {
        String chargeId = createNewCharge();
        String detailsWithInvalidExpiryDate = buildJsonCardDetailsFor("4242424242424242", "123", "1299");

        givenSetup()
                .body(detailsWithInvalidExpiryDate)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is("Values do not match expected format/length."));

        assertChargeStatusIs(chargeId, "CREATED");
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatusIfSomeCardDetailsHaveAlreadyBeenSubmitted() throws Exception {
        String chargeId = createNewCharge();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        String originalStatus = "AUTHORIZATION SUCCESS";
        assertChargeStatusIs(chargeId, originalStatus);

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(format("Card already processed for charge with id %s.", chargeId)));

        assertChargeStatusIs(chargeId, originalStatus);
    }

    @Test
    public void shouldReturn404IfChargeDoesNotExist() throws Exception {
        String unknownId = "61234569847520367";

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(unknownId))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(format("Parent charge with id %s not found.", unknownId)));
    }

    private String buildJsonCardDetailsFor(String cardNumber) {
        return buildJsonCardDetailsFor(cardNumber, "123", "11/99");
    }

    private String buildJsonCardDetailsFor(String cardNumber, String cvc, String expiryDate) {
        StringBuilder cardBody = new StringBuilder();
        cardBody.append("{");
        cardBody.append("\"card_number\":\"" + cardNumber + "\",");
        cardBody.append("\"cvc\":\"" + cvc + "\",");
        cardBody.append("\"expiry_date\":\"" + expiryDate + "\"");
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
