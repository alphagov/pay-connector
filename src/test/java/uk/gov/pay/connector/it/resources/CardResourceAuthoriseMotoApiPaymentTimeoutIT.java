package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.lang.reflect.Field;

import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonForMotoApiPaymentAuthorisation;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

public class CardResourceAuthoriseMotoApiPaymentTimeoutIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(config("captureProcessConfig.backgroundProcessingEnabled", "false"));
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("stripe", app);

    private static final String AUTHORISE_MOTO_API_URL = "/v1/api/charges/authorise";
    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VISA = "visa";

    private DatabaseFixtures.TestToken token;
    private DatabaseFixtures.TestCharge charge;

    @BeforeEach
    public void setupToken() {
        charge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeStatus(CREATED)
                .withAuthorisationMode(MOTO_API)
                .withTestAccount(testBaseExtension.getTestAccount())
                .withGatewayCredentialId((long) testBaseExtension.getGatewayAccountCredentialsId())
                .withPaymentProvider("stripe")
                .insert();

        token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(charge)
                .withUsed(false)
                .insert();
    }
    
    @Test
    void shouldReturn500_andUpdateCharge_forAuthorisationTimeout() throws Exception {
        AuthorisationConfig conf = app.getAuthorisationConfig();
        Field timeoutInMilliseconds = conf.getClass().getDeclaredField("synchronousAuthTimeoutInMilliseconds");
        timeoutInMilliseconds.setAccessible(true);
        timeoutInMilliseconds.setInt(conf, 50);

        app.getStripeMockClient().mockCreatePaymentMethod();
        app.getStripeMockClient().mockCreatePaymentIntentDelayedResponse();

        String cardHolderName = "Joe Bogs";
        String validPayload = buildJsonForMotoApiPaymentAuthorisation(cardHolderName, VALID_CARD_NUMBER, "11/99", "123",
                token.getSecureRedirectToken());
        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

        app.givenSetup()
                .body(validPayload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(500)
                .body("message", hasItems("Authorising the payment timed out"))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_TIMEOUT.toString()));

        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(charge.getExternalChargeId())
                .getFrontendCharge()
                .body("status", is(AUTHORISATION_TIMEOUT.getValue()))
                .body("card_details.cardholder_name", is(cardHolderName));
    }

}
