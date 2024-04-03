package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildCorporateJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildDetailedJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithFullAddress;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewChargeWithAccountId;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class CardResourceAuthoriseIT {

    @RegisterExtension
    public static ITestBaseExtension app = new ITestBaseExtension("sandbox");


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
    void exemption_3ds_should_be_null_for_a_non_worldpay_authorisation() {
        String externalChargeId = shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor("4444333322221111", "visa"));
        assertNull(app.getDatabaseTestHelper().getExemption3ds(getLongChargeId(externalChargeId)));
    }

    @Test
    void shouldAuthoriseCharge_ForValidCards() {
        for (String cardNo : VALID_SANDBOX_CARD_LIST) {
            shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor(cardNo, "visa"));
        }
    }

    @Test
    void shouldAuthoriseCharge_ForAValidAmericanExpress() {
        shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor("371449635398431", "1234", "11/99", "american-express"));
    }

    @Test
    void shouldStoreCardDetailsForAuthorisedCharge() {
        String cardBrand = "visa";
        String externalChargeId = shouldAuthoriseChargeFor(buildJsonAuthorisationDetailsFor("4444333322221111", cardBrand));
        Long chargeId = getLongChargeId(externalChargeId);
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("last_digits_card_number", "1111"));
        assertThat(chargeCardDetails, hasEntry("first_digits_card_number", "444433"));
        assertThat(app.getDatabaseTestHelper().getChargeCardBrand(chargeId), is(cardBrand));
    }

    @Test
    void shouldStoreAddressStateProvinceForAuthorisedChargeFromUnitedStates() {
        String cardBrand = "visa";
        String validUsCardDetails = buildJsonAuthorisationDetailsFor(
                "Mr. Name",
                "4444333322221111",
                "123",
                "10/99",
                "visa",
                "CREDIT",
                "Line1",
                "Line2",
                "Washington D.C.",
                null,
                "20500",
                "US"
        );
        String externalChargeId = shouldAuthoriseChargeFor(validUsCardDetails);
        Long chargeId = getLongChargeId(externalChargeId);
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("address_state_province", "DC"));
        assertThat(app.getDatabaseTestHelper().getChargeCardBrand(chargeId), is(cardBrand));
    }

    @Test
    void sanitizeCardDetails_shouldStoreSanitizedCardDetailsForAuthorisedCharge_forFieldsWithValuesContainingMoreThan10Numbers() {
        String sanitizedValue = "r-**-**-*  Ju&^****-**";
        String valueWithMoreThan10CharactersAsNumbers = "r-12-34-5  Ju&^6501-76";

        String externalChargeId = shouldAuthoriseChargeFor(buildDetailedJsonAuthorisationDetailsFor(
                "4444333322221111", "123", "11/30",
                valueWithMoreThan10CharactersAsNumbers, "CREDIT", valueWithMoreThan10CharactersAsNumbers,
                valueWithMoreThan10CharactersAsNumbers, valueWithMoreThan10CharactersAsNumbers,
                valueWithMoreThan10CharactersAsNumbers, valueWithMoreThan10CharactersAsNumbers,
                valueWithMoreThan10CharactersAsNumbers, valueWithMoreThan10CharactersAsNumbers));

        Long chargeId = getLongChargeId(externalChargeId);
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("last_digits_card_number", "1111"));
        assertThat(chargeCardDetails, hasEntry("first_digits_card_number", "444433"));
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
    void sanitizeCardDetails_shouldNotStoreSanitizedCardDetailsForAuthorisedCharge_forFieldsWithValuesContainingRight10Numbers() {

        String valueWith10CharactersAsNumbers = "r-12-34-5  Ju&^6501-7m";

        String externalChargeId = shouldAuthoriseChargeFor(buildDetailedJsonAuthorisationDetailsFor(
                "4444333322221111", "123", "11/30",
                valueWith10CharactersAsNumbers, "CREDIT", valueWith10CharactersAsNumbers, valueWith10CharactersAsNumbers,
                valueWith10CharactersAsNumbers, valueWith10CharactersAsNumbers, valueWith10CharactersAsNumbers,
                valueWith10CharactersAsNumbers, valueWith10CharactersAsNumbers));

        Long chargeId = getLongChargeId(externalChargeId);
        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, hasEntry("last_digits_card_number", "1111"));
        assertThat(chargeCardDetails, hasEntry("first_digits_card_number", "444433"));
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
    void shouldNotAuthoriseCard_ForSpecificCardNumber1() {
        String cardDetailsToReject = buildJsonAuthorisationDetailsFor("4000000000000002", "visa");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        app.shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    void shouldNotAuthoriseCard_ForSpecificCardNumber2() {
        String cardDetailsToReject = buildJsonAuthorisationDetailsFor("4000000000000119", "visa");

        String expectedErrorMessage = "This transaction could be not be processed.";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        app.shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    void shouldAuthoriseCharge_WithMinimalAddress() {
        String cardDetails = app.authorisationDetailsWithMinimalAddress(VALID_SANDBOX_CARD_LIST[0], "visa", "CREDIT");
        shouldAuthoriseChargeFor(cardDetails);
    }

    @Test
    void shouldAuthoriseCharge_ForValidCardWithFullAddress() {
        String validCardDetails = buildJsonAuthorisationDetailsWithFullAddress();
        shouldAuthoriseChargeFor(validCardDetails);
    }

    @Test
    void shouldRejectRandomCardNumber() {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("1111111111111119234", "visa");

        shouldReturn_unsupportedCardDetails_errorFor(chargeId, randomCardNumberDetails);
        app.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    void shouldReturnError_WhenCardNumberLongerThanMaximumExpected() {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("11111111111111192345", "visa");

        shouldContain_valuesDoNotMatchExpectedFormat_errorMessageFor(chargeId, randomCardNumberDetails);
        app.assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    void shouldReturnError_WhenCvcIsMoreThan4Digits() {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "12345", "11/99", "visa");

        shouldContain_valuesDoNotMatchExpectedFormat_errorMessageFor(chargeId, randomCardNumberDetails);
        app.assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    void shouldReturnError_WhenCvcIsLessThan3Digits() {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "12", "11/99", "visa");

        shouldContain_valuesDoNotMatchExpectedFormat_errorMessageFor(chargeId, randomCardNumberDetails);
        app.assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    void shouldReturnError_WhenCardNumberShorterThanMinimumExpected() {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("11111111111", "visa");

        shouldContain_valuesDoNotMatchExpectedFormat_errorMessageFor(chargeId, randomCardNumberDetails);
        app.assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    void shouldReturnError_WhenCardExpiryDateIsInvalid() {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String randomCardNumberDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "123", "99/99", "visa");

        shouldReturnGenericError(chargeId, randomCardNumberDetails, "CardExpiryDate");
        app.assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    void shouldReturnErrorAndDoNotUpdateChargeStatus_IfCardDetailsAreInvalid() {
        String chargeId = app.createNewCharge();
        String detailsWithInvalidExpiryDate = buildJsonAuthorisationDetailsFor("4242424242424242", "123", "1299");

        shouldContain_valuesDoNotMatchExpectedFormat_errorMessageFor(chargeId, detailsWithInvalidExpiryDate);

        app.assertFrontendChargeStatusIs(chargeId, CREATED.getValue());
    }

    private String shouldAuthoriseChargeFor(String cardDetails) {
        String chargeId = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        app.givenSetup()
                .body(cardDetails)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        app.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
        return chargeId;
    }

    @Test
    void shouldAuthoriseChargeWithSavePaymentInstrumentToAgreement() {
        ChargeUtils.ExternalChargeId externalChargeId = app.addChargeForSetUpAgreement(ENTERING_CARD_DETAILS);

        app.givenSetup()
                .body(buildCorporateJsonAuthorisationDetailsFor(PayersCardType.CREDIT_OR_DEBIT))
                .post(app.authoriseChargeUrlFor(externalChargeId.toString()))
                .then()
                .statusCode(200);

        app.assertFrontendChargeStatusIs(externalChargeId.toString(), AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    void shouldPersistCorporateSurcharge() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        long corporateCreditCardSurchargeAmount = 2222L;
        var gatewayAccountParams = anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("sandbox")
                .withDescription("description")
                .withAnalyticsId("")
                .withCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount)
                .withServiceName("a cool service")
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String externalChargeId = createNewChargeWithAccountId(ENTERING_CARD_DETAILS, randomId(), accountId, app.getDatabaseTestHelper(), app.getPaymentProvider()).toString();
        String cardDetails = buildCorporateJsonAuthorisationDetailsFor(PayersCardType.CREDIT);

        app.givenSetup()
                .body(cardDetails)
                .post(app.authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(200);

        app.assertFrontendChargeCorporateSurchargeAmount(externalChargeId, AUTHORISATION_SUCCESS.getValue(), corporateCreditCardSurchargeAmount);
    }

    @Test
    void shouldReturnAuthError_IfChargeExpired() {
        String chargeId = app.createNewChargeWithNoTransactionId(EXPIRED);
        app.givenSetup()
                .body(validCardDetails)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains(format("Charge not in correct state to be processed, %s", chargeId)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        app.assertFrontendChargeStatusIs(chargeId, EXPIRED.getValue());
    }

    @Test
    void shouldReturn404IfChargeDoesNotExist_ForAuthorise() {
        String unknownId = "61234569847520367";
        app.givenSetup()
                .body(validCardDetails)
                .post(app.authoriseChargeUrlFor(unknownId))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", contains(format("Charge with id [%s] not found.", unknownId)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotEnteringCardDetails() {
        String chargeId = app.createNewChargeWithNoTransactionId(AUTHORISATION_SUCCESS);

        app.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());

        String msg = format("Charge not in correct state to be processed, %s", chargeId);
        app.givenSetup()
                .body(validCardDetails)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains(msg))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        app.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    void shouldReturnErrorAndDoNotUpdateChargeStatus_IfAuthorisationAlreadyInProgress() {
        String chargeId = app.createNewChargeWithNoTransactionId(AUTHORISATION_READY);
        String message = format("Authorisation for charge already in progress, %s", chargeId);
        app.givenSetup()
                .body(validCardDetails)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(202)
                .contentType(JSON)
                .body("message", contains(message))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        app.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_READY.getValue());
    }

    @Test
    void shouldSaveExpectedCardDetailsFromMultipleRequests() throws InterruptedException {

        String charge1 = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        String charge2 = app.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        RequestSpecification firstChargeAuthorize = app.givenSetup()
                .body(buildJsonAuthorisationDetailsFor(
                        "Charge1 Name",
                        "4242424242424242",
                        "123",
                        "10/99",
                        "visa",
                        "CREDIT",
                        "Charge1 Line1",
                        "Charge1 Line2",
                        "Charge1 City",
                        "Charge1 County",
                        "DO11 4RS",
                        "GB"
                ));

        RequestSpecification secondChargeAuthorize = app.givenSetup()
                .body(buildJsonAuthorisationDetailsFor(
                        "Charge2 Name",
                        "4444333322221111",
                        "456",
                        "11/99",
                        "visa",
                        null,
                        "Charge2 Line1",
                        "Charge2 Line2",
                        "Charge2 City",
                        "Charge2 County",
                        "W2 3AF",
                        "DE"
                ));


        List<Callable<ValidatableResponse>> authoriseTasks = Arrays.asList(
                () -> firstChargeAuthorize.post(app.authoriseChargeUrlFor(charge1)).then(),
                () -> secondChargeAuthorize.post(app.authoriseChargeUrlFor(charge2)).then());

        invokeAll(authoriseTasks);

        app.assertFrontendChargeStatusIs(charge1, AUTHORISATION_SUCCESS.getValue());
        app.assertFrontendChargeStatusIs(charge2, AUTHORISATION_SUCCESS.getValue());

        app.givenSetup()
                .get("/v1/frontend/charges/{chargeId}".replace("{chargeId}", charge1))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("card_details.last_digits_card_number", is("4242"))
                .body("card_details.first_digits_card_number", is("424242"))
                .body("card_details.card_type", is("credit"))
                .body("card_details.cardholder_name", is("Charge1 Name"))
                .body("card_details.expiry_date", is("10/99"))
                .body("card_details.billing_address.line1.", is("Charge1 Line1"))
                .body("card_details.billing_address.line2.", is("Charge1 Line2"))
                .body("card_details.billing_address.postcode.", is("DO11 4RS"))
                .body("card_details.billing_address.city.", is("Charge1 City"))
                .body("card_details.billing_address.county.", is("Charge1 County"))
                .body("card_details.billing_address.country.", is("GB"));

        app.givenSetup()
                .get("/v1/frontend/charges/{chargeId}".replace("{chargeId}", charge2))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("card_details.last_digits_card_number", is("1111"))
                .body("card_details.first_digits_card_number", is("444433"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.cardholder_name", is("Charge2 Name"))
                .body("card_details.expiry_date", is("11/99"))
                .body("card_details.billing_address.line1.", is("Charge2 Line1"))
                .body("card_details.billing_address.line2.", is("Charge2 Line2"))
                .body("card_details.billing_address.postcode.", is("W2 3AF"))
                .body("card_details.billing_address.city.", is("Charge2 City"))
                .body("card_details.billing_address.county.", is("Charge2 County"))
                .body("card_details.billing_address.country.", is("DE"));
    }

    @Test
    void shouldUseActivePaymentProviderWhenMultipleCredentials() {
        long accountId = RandomUtils.nextInt();
        AddGatewayAccountCredentialsParams stripeCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(accountId)
                .withState(RETIRED)
                .build();
        AddGatewayAccountCredentialsParams worldpayCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withGatewayAccountId(accountId)
                .withState(ACTIVE)
                .withCredentials(Map.of(
                        ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "a-merchant-code",
                                CREDENTIALS_USERNAME, "a-username",
                                CREDENTIALS_PASSWORD, "a-password")))
                .build();
        withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(accountId)
                .withGatewayAccountCredentials(List.of(stripeCredentialsParams, worldpayCredentialsParams))
                .insert();

        ChargeUtils.ExternalChargeId chargeId = createNewChargeWithAccountId(ENTERING_CARD_DETAILS, null,
                String.valueOf(accountId), app.getDatabaseTestHelper(), WORLDPAY.getName(), worldpayCredentialsParams.getId());

        app.getWorldpayMockClient().mockAuthorisationSuccess();

        app.givenSetup()
                .body(buildJsonAuthorisationDetailsFor("4444333322221111", "visa"))
                .post(app.authoriseChargeUrlFor(chargeId.toString()))
                .then()
                .statusCode(200)
                .body("status", is(AUTHORISATION_SUCCESS.toString()));

        app.getWorldpayWireMockServer().verify(postRequestedFor(urlPathEqualTo(WORLDPAY_URL)));
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

    private void shouldReturnGenericError(String chargeId, String randomCardNumber, String substringExpectedInMessage) {
        app.givenSetup()
                .body(randomCardNumber)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()))
                .body("message", hasItem(containsString(substringExpectedInMessage)));
    }

    private void shouldReturn_unsupportedCardDetails_errorFor(String chargeId, String randomCardNumber) {
        app.givenSetup()
                .body(randomCardNumber)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains("Unsupported card details."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private void shouldContain_valuesDoNotMatchExpectedFormat_errorMessageFor(String chargeId, String randomCardNumber) {
        app.givenSetup()
                .body(randomCardNumber)
                .post(app.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(422)
                .contentType(JSON)
                .body("message", containsInAnyOrder("Values do not match expected format/length."));
    }

    private Long getLongChargeId(String externalChargeId) {
        return Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));
    }
}
