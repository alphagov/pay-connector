package uk.gov.pay.connector.it;

import org.junit.Test;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;

public class SandboxCardDetailsResourceITest extends BaseCardDetailsResourceITest {

    private static final String[] VALID_CARD_NO_LIST = new String[]{
            "4242424242424242",
            "5105105105105100",
            "348560871512574",
            "4485197542476643",
            "5582575229987470",
            "4917902691983168",
            "3528373272496082",
            "6011188510795021",
            "6763376639165982",
            "36375928148471"
    };
    private String validCardDetails = buildJsonCardDetailsFor(VALID_CARD_NO_LIST[0]);

    public SandboxCardDetailsResourceITest() {
        super("sandbox");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCards() throws Exception {
        for (String cardNo : VALID_CARD_NO_LIST) {
            shouldAuthoriseChargeFor(buildJsonCardDetailsFor(cardNo));
        }
    }

    @Test
    public void shouldAuthoriseChargeForValidCardWithFullAddress() throws Exception {
        String validCardDetails = buildJsonCardDetailsWithFullAddress();
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
}
