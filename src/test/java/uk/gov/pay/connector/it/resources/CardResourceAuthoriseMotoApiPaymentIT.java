package uk.gov.pay.connector.it.resources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonForMotoApiPaymentAuthorisation;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.GENERIC;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.INVALID_ATTRIBUTE_VALUE;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml",
        configOverrides = {@ConfigOverride(key = "captureProcessConfig.backgroundProcessingEnabled", value = "true")},
        withDockerSQS = true
)
public class CardResourceAuthoriseMotoApiPaymentIT extends ChargingITestBase {

    private static final String AUTHORISE_MOTO_API_URL = "/v1/api/charges/authorise";
    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VISA = "visa";

    public CardResourceAuthoriseMotoApiPaymentIT() {
        super("sandbox");
    }

    private DatabaseFixtures.TestToken token;
    private DatabaseFixtures.TestCharge charge;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        
        CardTypeEntity visaCreditCard = databaseTestHelper.getVisaCreditCard();
        DatabaseFixtures.TestAccount gatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withCardTypeEntities(List.of(visaCreditCard))
                .insert();

        charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(CREATED)
                .withAuthorisationMode(MOTO_API)
                .withTestAccount(gatewayAccount)
                .insert();

        token = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestToken()
                .withCharge(charge)
                .withUsed(false)
                .insert();
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void authoriseMotoApiPayment_shouldReturn204ResponseForValidPayload() throws Exception {
        String validPayload = buildJsonForMotoApiPaymentAuthorisation("Joe Bogs ", VALID_CARD_NUMBER, "11/99", "123",
                token.getSecureRedirectToken());
        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        cardidStub.returnCardInformation(VALID_CARD_NUMBER, cardInformation);

        shouldAuthoriseChargeFor(validPayload);

        assertThat(databaseTestHelper.isChargeTokenUsed(token.getSecureRedirectToken()), is(true));

        Thread.sleep(100L); // wait for charge to be captured
        assertFrontendChargeStatusIs(charge.getExternalChargeId(), CAPTURED.getValue());
    }

    @Test
    public void shouldReturnError_ForInvalidPayload() {
        String invalidPayload = buildJsonForMotoApiPaymentAuthorisation("", "", "", "",
                token.getSecureRedirectToken());

        givenSetup()
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
    public void shouldReturnError_ForInvalidCardNumberFormat() {
        String invalidPayload = buildJsonForMotoApiPaymentAuthorisation("Joe", "invalid-card-no", "11/99", "123",
                token.getSecureRedirectToken());

        givenSetup()
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
    public void shouldTransitionChargeToAuthorisationRejectedWhenCardNumberRejected() throws Exception {
        String payload = buildJsonForMotoApiPaymentAuthorisation("Joe", VALID_CARD_NUMBER, "11/99", "123",
                token.getSecureRedirectToken());

        cardidStub.returnNotFound(VALID_CARD_NUMBER);
        
        givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(402)
                .contentType(JSON)
                .body("message", hasItems(
                        "The card_number is not a valid card number"))
                .body("error_identifier", is(ErrorIdentifier.CARD_NUMBER_REJECTED.toString()));

        assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldReturn402WhenPaymentIsRejected() throws Exception {
        String payload = buildJsonForMotoApiPaymentAuthorisation("Joe", "4000000000000002", "11/99", "123",
                token.getSecureRedirectToken());

        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        cardidStub.returnCardInformation("4000000000000002", cardInformation);

        givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(402)
                .contentType(JSON)
                .body("message", hasItems("The payment was rejected"))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_REJECTED.toString()));

        assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldReturn500ForAuhorisationError() throws Exception {
        String payload = buildJsonForMotoApiPaymentAuthorisation("Joe", "4000000000000119", "11/99", "123",
                token.getSecureRedirectToken());

        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        cardidStub.returnCardInformation("4000000000000119", cardInformation);

        givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", hasItems("There was an error authorising the payment"))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_ERROR.toString()));

        assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_ERROR.getValue());
    }

    private void shouldAuthoriseChargeFor(String payload) {
        givenSetup()
                .body(payload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(204);
    }

}
