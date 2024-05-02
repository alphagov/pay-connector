package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonForMotoApiPaymentAuthorisation;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.GENERIC;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.INVALID_ATTRIBUTE_VALUE;

public class CardResourceAuthoriseMotoApiPaymentIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(config("captureProcessConfig.backgroundProcessingEnabled", "false"));
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app);
    
    private static final String AUTHORISE_MOTO_API_URL = "/v1/api/charges/authorise";
    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VISA = "visa";
    
    private DatabaseFixtures.TestToken token;
    private DatabaseFixtures.TestCharge charge;

    @BeforeEach
    void setupToken() {
        charge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeStatus(CREATED)
                .withAuthorisationMode(MOTO_API)
                .withTestAccount(testBaseExtension.getTestAccount())
                .insert();

        token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(charge)
                .withUsed(false)
                .insert();
    }

    @Test
    void authoriseMotoApiPayment_shouldReturn204ResponseForValidPayload() throws Exception {
        String validPayload = buildJsonForMotoApiPaymentAuthorisation("Joe Bogs ", VALID_CARD_NUMBER, "11/99", "123",
                token.getSecureRedirectToken());
        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

        app.givenSetup()
                .body(validPayload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(204);

        assertThat(app.getDatabaseTestHelper().isChargeTokenUsed(token.getSecureRedirectToken()), is(true));
        assertThat(app.getDatabaseTestHelper().getChargeStatus(charge.getChargeId()), is(CAPTURE_QUEUED.getValue()));
    }

    @Test
    void shouldReturnError_ForInvalidPayload() {
        String invalidPayload = buildJsonForMotoApiPaymentAuthorisation("", "", "", "",
                token.getSecureRedirectToken());

        app.givenSetup()
                .body(invalidPayload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(422)
                .contentType(JSON)
                .body("message", hasItems(
                        "Missing mandatory attribute: cardholder_name",
                        "Missing mandatory attribute: card_number",
                        "Missing mandatory attribute: expiry_date",
                        "Missing mandatory attribute: cvc"
                ))
                .body("error_identifier", is(GENERIC.toString()));
        ;
    }

    @Test
    void shouldReturnError_ForInvalidCardNumberFormat() {
        String invalidPayload = buildJsonForMotoApiPaymentAuthorisation("Joe", "invalid-card-no", "11/99", "123",
                token.getSecureRedirectToken());

        app.givenSetup()
                .body(invalidPayload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(422)
                .contentType(JSON)
                .body("message", hasItems(
                        "Invalid attribute value: card_number. Must be a valid card number"))
                .body("error_identifier", is(INVALID_ATTRIBUTE_VALUE.toString()));
    }

    @Test
    void shouldTransitionChargeToAuthorisationRejectedWhenCardNumberRejected() throws Exception {
        String payload = buildJsonForMotoApiPaymentAuthorisation("Joe", VALID_CARD_NUMBER, "11/99", "123",
                token.getSecureRedirectToken());

        app.getCardidStub().returnNotFound(VALID_CARD_NUMBER);

        app.givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(402)
                .contentType(JSON)
                .body("message", hasItems(
                        "The card_number is not a valid card number"))
                .body("error_identifier", is(ErrorIdentifier.CARD_NUMBER_REJECTED.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_REJECTED.getValue());
    }

    @Test
    void shouldReturn402WhenPaymentIsRejected() throws Exception {
        String payload = buildJsonForMotoApiPaymentAuthorisation("Joe", "4000000000000002", "11/99", "123",
                token.getSecureRedirectToken());

        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        app.getCardidStub().returnCardInformation("4000000000000002", cardInformation);

        app.givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(402)
                .contentType(JSON)
                .body("message", hasItems("The payment was rejected"))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_REJECTED.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_REJECTED.getValue());
    }

    @Test
    void shouldReturn500ForAuthorisationError() throws Exception {
        String payload = buildJsonForMotoApiPaymentAuthorisation("Joe", "4000000000000119", "11/99", "123",
                token.getSecureRedirectToken());

        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        app.getCardidStub().returnCardInformation("4000000000000119", cardInformation);

        app.givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", hasItems("There was an error authorising the payment"))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_ERROR.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_ERROR.getValue());
    }
}
