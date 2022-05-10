package uk.gov.pay.connector.it.resources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.lang.reflect.Field;
import java.util.List;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
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
public class CardResourceAuthoriseMotoApiPaymentDelayedGatewayResponseIT extends ChargingITestBase {

    private static final String AUTHORISE_MOTO_API_URL = "/v1/api/charges/authorise";
    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VISA = "visa";

    public CardResourceAuthoriseMotoApiPaymentDelayedGatewayResponseIT() {
        super("stripe");
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
    public void shouldReturn500ForAuthorisationTimeout() throws Exception {
        AuthorisationConfig conf = testContext.getAuthorisationConfig();
        Field timeoutInMilliseconds = conf.getClass().getDeclaredField("synchronousAuthTimeoutInMilliseconds");
        timeoutInMilliseconds.setAccessible(true);
        timeoutInMilliseconds.setInt(conf, 0);

        stripeMockClient.mockCreatePaymentIntentDelayedResponse();

        String validPayload = buildJsonForMotoApiPaymentAuthorisation("Joe Bogs ", VALID_CARD_NUMBER, "11/99", "123",
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

        assertFrontendChargeStatusIs(charge.getExternalChargeId(), AUTHORISATION_TIMEOUT.getValue());
    }

}
