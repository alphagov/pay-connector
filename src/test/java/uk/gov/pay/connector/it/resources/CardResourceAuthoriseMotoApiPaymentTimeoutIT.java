package uk.gov.pay.connector.it.resources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.lang.reflect.Field;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonForMotoApiPaymentAuthorisation;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml",
        configOverrides = {@ConfigOverride(key = "chargeAsyncOperationsConfig.backgroundProcessingEnabled", value = "true")},
        withDockerSQS = true
)
public class CardResourceAuthoriseMotoApiPaymentTimeoutIT extends ChargingITestBase {

    private static final String AUTHORISE_MOTO_API_URL = "/v1/api/charges/authorise";
    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VISA = "visa";

    public CardResourceAuthoriseMotoApiPaymentTimeoutIT() {
        super("stripe");
    }
    
    private DatabaseFixtures.TestToken token;
    private DatabaseFixtures.TestCharge charge;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();

        charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(CREATED)
                .withAuthorisationMode(MOTO_API)
                .withTestAccount(getTestAccount())
                .withGatewayCredentialId((long) gatewayAccountCredentialsId)
                .withPaymentProvider("stripe")
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
    public void shouldReturn500_andUpdateCharge_forAuthorisationTimeout() throws Exception {
        AuthorisationConfig conf = testContext.getAuthorisationConfig();
        Field timeoutInMilliseconds = conf.getClass().getDeclaredField("synchronousAuthTimeoutInMilliseconds");
        timeoutInMilliseconds.setAccessible(true);
        timeoutInMilliseconds.setInt(conf, 50);

        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntentDelayedResponse();

        String cardHolderName = "Joe Bogs";
        String validPayload = buildJsonForMotoApiPaymentAuthorisation(cardHolderName, VALID_CARD_NUMBER, "11/99", "123",
                token.getSecureRedirectToken());
        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        cardidStub.returnCardInformation(VALID_CARD_NUMBER, cardInformation);

        givenSetup()
                .body(validPayload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(500)
                .body("message", hasItems("Authorising the payment timed out"))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_TIMEOUT.toString()));

        connectorRestApiClient
                .withChargeId(charge.getExternalChargeId())
                .getFrontendCharge()
                .body("status", is(AUTHORISATION_TIMEOUT.getValue()))
                .body("card_details.cardholder_name", is(cardHolderName));
    }

}
