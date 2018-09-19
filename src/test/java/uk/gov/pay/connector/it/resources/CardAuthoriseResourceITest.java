package uk.gov.pay.connector.it.resources;

import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRED;

public class CardAuthoriseResourceITest extends ChargingITestBase {

    public CardAuthoriseResourceITest() {
        super("sandbox");
    }

    private static final String[] VALID_SANDBOX_CARD_LIST = new String[]{
            "4444333322221111",
            "4917610000000000003",
            "4242424242424242",
            "4000056655665556",
            "5105105105105100",
            "5200828282828210",
            "371449635398431",
            "3566002020360505",
            "6011000990139424",
            "36148900647913"};

    private String validCardDetails = buildJsonAuthorisationDetailsFor(VALID_SANDBOX_CARD_LIST[0], "visa");

    @Test
    public void shouldAuthoriseCharge_ForValidCards() {
        for (String cardNo : VALID_SANDBOX_CARD_LIST) {
            shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor(cardNo, "visa"));
        }
    }

    @Test
    public void shouldAuthoriseCharge_ForAValidAmericanExpress() {
        shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor("371449635398431", "1234", "11/99", "american-express"));
    }

    @Test
    public void shouldStoreCardDetailsForAuthorisedCharge() {
        String cardBrand = "visa";
        String externalChargeId = shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor("4444333322221111", cardBrand));
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("last_digits_card_number", "1111"));
        assertThat(app.getDatabaseTestHelper().getChargeCardBrand(chargeId), is(cardBrand));
    }

    @Test
    public void sanitizeCardDetails_shouldStoreSanitizedCardDetailsForAuthorisedCharge_forFieldsWithValuesContainingMoreThan10Numbers() { 
        String sanitizedValue = "r-**-**-*  Ju&^****-**";
        String valueWithMoreThan10CharactersAsNumbers = "r-12-34-5  Ju&^6501-76";
        String cardHolderName = valueWithMoreThan10CharactersAsNumbers;
        String addressLine1 = valueWithMoreThan10CharactersAsNumbers;
        String addressLine2 = valueWithMoreThan10CharactersAsNumbers;
        String city = valueWithMoreThan10CharactersAsNumbers;
        String county = valueWithMoreThan10CharactersAsNumbers;
        String postcode = valueWithMoreThan10CharactersAsNumbers;
        String country = valueWithMoreThan10CharactersAsNumbers;
        String cardBrand = valueWithMoreThan10CharactersAsNumbers;

        String externalChargeId = shouldAuthoriseChargeFor(buildDetailedJsonAuthorisationDetailsFor("4444333322221111", "123", "11/30", cardBrand, cardHolderName, addressLine1, addressLine2, city, county, postcode, country));

        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("last_digits_card_number", "1111"));
        assertThat(chargeCardDetails, hasEntry("address_county", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("address_line1", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("address_line2", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("address_country", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("address_city", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("card_brand", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("address_postcode", sanitizedValue));
        assertThat(chargeCardDetails, hasEntry("cardholder_name", sanitizedValue));
    }

    @Test
    public void sanitizeCardDetails_shouldNotStoreSanitizedCardDetailsForAuthorisedCharge_forFieldsWithValuesContainingRight10Numbers() throws Exception {

        String valueWith10CharactersAsNumbers = "r-12-34-5  Ju&^6501-7m";
        String cardHolderName = valueWith10CharactersAsNumbers;
        String addressLine1 = valueWith10CharactersAsNumbers;
        String addressLine2 = valueWith10CharactersAsNumbers;
        String city = valueWith10CharactersAsNumbers;
        String county = valueWith10CharactersAsNumbers;
        String postcode = valueWith10CharactersAsNumbers;
        String country = valueWith10CharactersAsNumbers;
        String cardBrand = valueWith10CharactersAsNumbers;

        String externalChargeId = shouldAuthoriseChargeFor(buildDetailedJsonAuthorisationDetailsFor("4444333322221111", "123", "11/30", cardBrand, cardHolderName, addressLine1, addressLine2, city, county, postcode, country));

        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("last_digits_card_number", "1111"));
        assertThat(chargeCardDetails, hasEntry("address_county", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("address_line1", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("address_line2", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("address_country", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("address_city", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("card_brand", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("address_postcode", valueWith10CharactersAsNumbers));
        assertThat(chargeCardDetails, hasEntry("cardholder_name", valueWith10CharactersAsNumbers));
    }

    @Test
    public void shouldNotAuthoriseCard_ForSpecificCardNumber1() throws Exception {
        String cardDetailsToReject = buildJsonAuthorisationDetailsFor("4000000000000002", "visa");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthoriseCard_ForSpecificCardNumber2() throws Exception {
        String cardDetailsToReject = buildJsonAuthorisationDetailsFor("4000000000000119", "visa");

        String expectedErrorMessage = "This transaction could be not be processed.";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldAuthoriseCharge_WithMinimalAddress() throws Exception {
        String cardDetails = authorisationDetailsWithMinimalAddress(VALID_SANDBOX_CARD_LIST[0], "visa");
        shouldAuthoriseChargeFor(cardDetails);
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardWithFullAddress() throws Exception {
        String validCardDetails = buildJsonAuthorisationDetailsWithFullAddress();
        shouldAuthoriseChargeFor(validCardDetails);
    }

    @Test
    public void shouldRejectRandomCardNumber() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("1111111111111119234", "visa");

        shouldReturnErrorFor(chargeId, randomCardNumberDetails, "Unsupported card details.");
        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    public void shouldReturnError_WhenCardNumberLongerThanMaximumExpected() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("11111111111111192345", "visa");

        shouldReturnErrorFor(chargeId, randomCardNumberDetails, "Values do not match expected format/length.");
        assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    public void shouldReturnError_WhenCvcIsMoreThan4Digits() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "12345", "11/99", "visa");

        shouldReturnErrorFor(chargeId, randomCardNumberDetails, "Values do not match expected format/length.");
        assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    public void shouldReturnError_WhenCvcIsLessThan3Digits() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "12", "11/99", "visa");

        shouldReturnErrorFor(chargeId, randomCardNumberDetails, "Values do not match expected format/length.");
        assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    public void shouldReturnError_WhenCardNumberShorterThanMinimumExpected() throws Exception {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("11111111111", "visa");

        shouldReturnErrorFor(chargeId, randomCardNumberDetails, "Values do not match expected format/length.");
        assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatus_IfCardDetailsAreInvalid() throws Exception {
        String chargeId = createNewCharge();
        String detailsWithInvalidExpiryDate = buildJsonAuthorisationDetailsFor("4242424242424242", "123", "1299");

        shouldReturnErrorFor(chargeId, detailsWithInvalidExpiryDate, "Values do not match expected format/length.");

        assertFrontendChargeStatusIs(chargeId, CREATED.getValue());
    }

    private String shouldAuthoriseChargeFor(String cardDetails) {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        givenSetup()
                .body(cardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        return chargeId;
    }

    @Test
    public void shouldReturnAuthError_IfChargeExpired() {
        String chargeId = createNewChargeWithNoTransactionId(EXPIRED);
        authoriseAndVerifyFor(chargeId, validCardDetails, format("Authorisation for charge failed as already expired, %s", chargeId), 400);
        assertFrontendChargeStatusIs(chargeId, EXPIRED.getValue());
    }

    @Test
    public void shouldReturn404IfChargeDoesNotExist_ForAuthorise() {
        String unknownId = "61234569847520367";
        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(unknownId))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(format("Charge with id [%s] not found.", unknownId)));
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotEnteringCardDetails() {
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_SUCCESS);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());

        String msg = format("Charge not in correct state to be processed, %s", chargeId);
        authoriseAndVerifyFor(chargeId, validCardDetails, msg, 400);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldReturn202_WhenGatewayAuthorisationResponseIsDelayed() throws NoSuchFieldException, IllegalAccessException {
        ExecutorServiceConfig conf = app.getConf().getExecutorServiceConfig();
        Field timeoutInSeconds = conf.getClass().getDeclaredField("timeoutInSeconds");
        timeoutInSeconds.setAccessible(true);
        timeoutInSeconds.setInt(conf, 0);

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String message = format("Authorisation for charge already in progress, %s", chargeId);
        authoriseAndVerifyFor(chargeId, validCardDetails, message, 202);
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatus_IfAuthorisationAlreadyInProgress() {
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_READY);
        String message = format("Authorisation for charge already in progress, %s", chargeId);
        authoriseAndVerifyFor(chargeId, validCardDetails, message, 202);
        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_READY.getValue());
    }

    @Test
    public void shouldSaveExpectedCardDetailsFromMultipleRequests() throws InterruptedException {

        String charge1 = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String charge2 = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        RequestSpecification firstChargeAuthorize = givenSetup()
                .body(buildJsonAuthorisationDetailsFor(
                        "Charge1 Name",
                        "4242424242424242",
                        "123",
                        "10/99",
                        "visa",
                        "Charge1 Line1",
                        "Charge1 Line2",
                        "Charge1 City",
                        "Charge1 County",
                        "DO11 4RS",
                        "GB"
                ));

        RequestSpecification secondChargeAuthorize = givenSetup()
                .body(buildJsonAuthorisationDetailsFor(
                        "Charge2 Name",
                        "4444333322221111",
                        "456",
                        "11/99",
                        "visa",
                        "Charge2 Line1",
                        "Charge2 Line2",
                        "Charge2 City",
                        "Charge2 County",
                        "W2 3AF",
                        "DE"
                ));


        List<Callable<ValidatableResponse>> authoriseTasks = Arrays.asList(
                () -> firstChargeAuthorize.post(authoriseChargeUrlFor(charge1)).then(),
                () -> secondChargeAuthorize.post(authoriseChargeUrlFor(charge2)).then());

        invokeAll(authoriseTasks);

        assertFrontendChargeStatusIs(charge1, AUTHORISATION_SUCCESS.getValue());
        assertFrontendChargeStatusIs(charge2, AUTHORISATION_SUCCESS.getValue());

        givenSetup()
                .get("/v1/frontend/charges/{chargeId}".replace("{chargeId}", charge1))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("card_details.last_digits_card_number", is("4242"))
                .body("card_details.cardholder_name", is("Charge1 Name"))
                .body("card_details.expiry_date", is("10/99"))
                .body("card_details.billing_address.line1.", is("Charge1 Line1"))
                .body("card_details.billing_address.line2.", is("Charge1 Line2"))
                .body("card_details.billing_address.postcode.", is("DO11 4RS"))
                .body("card_details.billing_address.city.", is("Charge1 City"))
                .body("card_details.billing_address.county.", is("Charge1 County"))
                .body("card_details.billing_address.country.", is("GB"));

        givenSetup()
                .get("/v1/frontend/charges/{chargeId}".replace("{chargeId}", charge2))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("card_details.last_digits_card_number", is("1111"))
                .body("card_details.cardholder_name", is("Charge2 Name"))
                .body("card_details.expiry_date", is("11/99"))
                .body("card_details.billing_address.line1.", is("Charge2 Line1"))
                .body("card_details.billing_address.line2.", is("Charge2 Line2"))
                .body("card_details.billing_address.postcode.", is("W2 3AF"))
                .body("card_details.billing_address.city.", is("Charge2 City"))
                .body("card_details.billing_address.county.", is("Charge2 County"))
                .body("card_details.billing_address.country.", is("DE"));
    }

    private List<ValidatableResponse> invokeAll(List<Callable<ValidatableResponse>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        return executor.invokeAll(tasks)
                .stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        fail("Test fail with exception calling resource");
                        return null;
                    }
                })
                .collect(Collectors.toList());
    }

    private void shouldReturnErrorFor(String chargeId, String randomCardNumber, String expectedMessage) {
        authoriseAndVerifyFor(chargeId, randomCardNumber, expectedMessage, 400);
    }

    private void authoriseAndVerifyFor(String chargeId, String randomCardNumber, String expectedMessage, int statusCode) {
        givenSetup()
                .body(randomCardNumber)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(statusCode)
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }
}
