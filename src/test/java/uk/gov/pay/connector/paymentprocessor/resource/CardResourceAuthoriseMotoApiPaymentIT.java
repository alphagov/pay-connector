package uk.gov.pay.connector.paymentprocessor.resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.hasItems;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonForMotoApiPaymentAuthorisation;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardResourceAuthoriseMotoApiPaymentIT extends ChargingITestBase {

    public CardResourceAuthoriseMotoApiPaymentIT() {
        super("sandbox");
    }

    private DatabaseFixtures.TestToken token;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();

        DatabaseFixtures.TestAccount gatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        DatabaseFixtures.TestCharge charge = DatabaseFixtures
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
    public void authoriseMotoApiPayment_shouldReturn204ResponseForValidPayload() {
        String validPayload = buildJsonForMotoApiPaymentAuthorisation("Joe Bogs ", "4242424242424242", "11/99", "123",
                token.getSecureRedirectToken());
        shouldAuthoriseChargeFor(validPayload);
    }

    @Test
    public void shouldReturnError_ForInvalidPayload() {
        String invalidPayload = buildJsonForMotoApiPaymentAuthorisation("", "", "", "",
                token.getSecureRedirectToken());

        givenSetup()
                .body(invalidPayload)
                .post(authoriseMotoApiChargeUrlFor())
                .then()
                .statusCode(422)
                .contentType(JSON)
                .body("message", hasItems(
                        "Missing mandatory attribute: cardholder_name",
                        "Missing mandatory attribute: card_number",
                        "Missing mandatory attribute: expiry_date",
                        "Missing mandatory attribute: cvc"
                ));
    }

    private void shouldAuthoriseChargeFor(String payload) {
        givenSetup()
                .body(payload)
                .post(authoriseMotoApiChargeUrlFor())
                .then()
                .statusCode(204);
    }

}
