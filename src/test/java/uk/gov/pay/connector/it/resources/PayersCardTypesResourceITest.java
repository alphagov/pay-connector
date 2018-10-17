package uk.gov.pay.connector.it.resources;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import static org.hamcrest.core.Is.is;

public class PayersCardTypesResourceITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "66757943593456";
    private RestAssuredClient connectorApi = new RestAssuredClient(app, accountId);

    @Before
    public void setUp() {
        app.getDatabaseTestHelper().deleteAllCardTypes();
    }

    @Test
    public void shouldGetNoCardTypesWhenNoCardTypesExist() {
        connectorApi
                .getCardTypes()
                .body("card_types.size()", is(0));
    }

    @Test
    public void shouldGetAllCardTypesWhenCardTypesExist() {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeTestRecord = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aMastercardCreditCardType()
                .withRequires3ds(true)
                .insert();

        connectorApi
                .getCardTypes()
                .body("card_types.size()", is(1))
                .body("card_types[0].id", is(mastercardCreditCardTypeTestRecord.getId().toString()))
                .body("card_types[0].brand", is(mastercardCreditCardTypeTestRecord.getBrand()))
                .body("card_types[0].label", is(mastercardCreditCardTypeTestRecord.getLabel()))
                .body("card_types[0].requires3ds", is(mastercardCreditCardTypeTestRecord.getRequires3DS()))
                .body("card_types[0].type", is(mastercardCreditCardTypeTestRecord.getType().toString()));
    }
}
