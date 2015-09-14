package uk.gov.pay.connector.it;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

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
    public void shouldAuthoriseChargeForValidCardDetails1() throws Exception {
        shouldAuthoriseChargeFor(validCardDetails);
    }

    @Test
    public void shouldAuthoriseChargeForValidCardDetails2() throws Exception {
        String validCardDetails = buildJsonCardDetailsFor("5105105105105100");
        shouldAuthoriseChargeFor(validCardDetails);
    }

    private void shouldAuthoriseChargeFor(String cardDetails) throws Exception {
        String chargeId = createNewCharge();

        givenSetup()
                .body(cardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertChargeStatusIs(chargeId, "AUTHORIZATION SUCCESS");
    }

    @Test
    public void shouldRejectRandomCardNumberAndNotUpdateChargeStatus() throws Exception {
        String chargeId = createNewCharge();
        String randomCardNumber = buildJsonCardDetailsFor("1111111111111119");

        givenSetup()
                .body(randomCardNumber)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is("Unsupported card details."));

        assertChargeStatusIs(chargeId, "CREATED");
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
                .body("message", is(format("Charge with id [%s] not found.", unknownId)));
    }

    @Test
    public void shouldReturnNotAuthorisedForTheSpecificCardNumber1() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000002");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = "AUTHORIZATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldReturnNotAuthorisedForTheSpecificCardNumber2() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000069");

        String expectedErrorMessage = "The card is expired.";
        String expectedChargeStatus = "AUTHORIZATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldReturnNotAuthorisedForTheSpecificCardNumber3() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000127");

        String expectedErrorMessage = "The CVC code is incorrect.";
        String expectedChargeStatus = "AUTHORIZATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldReturnNotAuthorisedForTheSpecificCardNumber4() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000119");

        String expectedErrorMessage = "This transaction could be not be processed.";
        String expectedChargeStatus = "SYSTEM ERROR";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    private void shouldReturnErrorForCardDetailsWithMessage(String cardDetails, String errorMessage, String status) throws Exception {
        String chargeId = createNewCharge();

        givenSetup()
                .body(cardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertChargeStatusIs(chargeId, status);
    }

    private String buildJsonCardDetailsFor(String cardNumber) {
        return buildJsonCardDetailsFor(cardNumber, "123", "11/99");
    }

    private String buildJsonCardDetailsFor(String cardNumber, String cvc, String expiryDate) {
        Map<String, Object> cardDetails = ImmutableMap.of(
                "card_number", cardNumber,
                "cvc", cvc,
                "expiry_date", expiryDate);
        return toJson(cardDetails);
    }

    private void assertChargeStatusIs(String uniqueChargeId, String status) {
        given().port(app.getLocalPort())
                .get("/v1/frontend/charges/" + uniqueChargeId)
                .then()
                .body("status", is(status));
    }

    private String createNewCharge() {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, CREATED);
        return chargeId;
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }
}
