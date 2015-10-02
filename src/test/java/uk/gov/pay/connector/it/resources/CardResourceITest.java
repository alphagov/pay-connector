package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;

public class CardResourceITest extends CardResourceITestBase {

    public CardResourceITest() {
        super("sandbox");
    }

    private static final String[] VALID_SANDBOX_CARD_LIST = new String[]{
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

    private String validCardDetails = buildJsonCardDetailsFor(VALID_SANDBOX_CARD_LIST[0]);

    @Test
    public void shouldAuthoriseCharge_ForValidCards() throws Exception {
        for (String cardNo : VALID_SANDBOX_CARD_LIST) {
            shouldAuthoriseChargeFor(buildJsonCardDetailsFor(cardNo));
        }
    }

    @Test
    public void shouldNotAuthoriseCard_ForSpecificCardNumber1() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000002");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthoriseCard_ForSpecificCardNumber2() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000069");

        String expectedErrorMessage = "The card is expired.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthoriseCard_ForSpecificCardNumber3() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000127");

        String expectedErrorMessage = "The CVC code is incorrect.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthoriseCard_ForSpecificCardNumber4() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("4000000000000119");

        String expectedErrorMessage = "This transaction could be not be processed.";
        String expectedChargeStatus = "SYSTEM ERROR";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldAuthoriseCharge_WithMinimalAddress() throws Exception {
        String cardDetails = cardDetailsWithMinimalAddress(VALID_SANDBOX_CARD_LIST[0]);
        shouldAuthoriseChargeFor(cardDetails);
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardWithFullAddress() throws Exception {
        String validCardDetails = buildJsonCardDetailsWithFullAddress();
        shouldAuthoriseChargeFor(validCardDetails);
    }

    @Test
    public void shouldRejectRandomCardNumberAndNotUpdateChargeStatus() throws Exception {
        String chargeId = createNewCharge();
        String randomCardNumberDetails = buildJsonCardDetailsFor("1111111111111119");

        shouldReturnErrorFor(chargeId, randomCardNumberDetails, "Unsupported card details.");

        assertFrontendChargeStatusIs(chargeId, "CREATED");
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatus_IfCardDetailsAreInvalid() throws Exception {
        String chargeId = createNewCharge();
        String detailsWithInvalidExpiryDate = buildJsonCardDetailsFor("4242424242424242", "123", "1299");

        shouldReturnErrorFor(chargeId, detailsWithInvalidExpiryDate, "Values do not match expected format/length.");

        assertFrontendChargeStatusIs(chargeId, "CREATED");
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatus_IfCardDetailsAreAlreadySubmitted() throws Exception {
        String chargeId = createNewCharge();
        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        String originalStatus = "AUTHORISATION SUCCESS";
        assertFrontendChargeStatusIs(chargeId, originalStatus);

        shouldReturnErrorFor(chargeId, validCardDetails, format("Card already processed for charge with id %s.", chargeId));

        assertFrontendChargeStatusIs(chargeId, originalStatus);
    }

    @Test
    public void shouldReturn404IfChargeDoesNotExist_ForAuthorise() throws Exception {
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
    public void shouldReturn404IfChargeDoesNotExist_ForCapture() {
        String unknownId = "398579438759438";

        givenSetup()
                .post(chargeCaptureUrlFor(unknownId))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(format("Charge with id [%s] not found.", unknownId)));
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(chargeCaptureUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "CAPTURE SUBMITTED");
        assertApiStatusIs(chargeId, "IN PROGRESS");
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotAuthorised() {
        String chargeIdNotAuthorised = createNewChargeWithStatus(AUTHORISATION_SUBMITTED);

        givenSetup()
                .post(chargeCaptureUrlFor(chargeIdNotAuthorised))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is("Cannot capture a charge with status " + AUTHORISATION_SUBMITTED.getValue() + "."));

        assertFrontendChargeStatusIs(chargeIdNotAuthorised, "AUTHORISATION SUBMITTED");
    }

    private void shouldAuthoriseChargeFor(String cardDetails) throws Exception {
        String chargeId = createNewCharge();

        givenSetup()
                .body(cardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "AUTHORISATION SUCCESS");
    }

    private void shouldReturnErrorFor(String chargeId, String randomCardNumber, String expectedMessage) {
        givenSetup()
                .body(randomCardNumber)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }
}
