package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.After;
import org.junit.Before;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.gatewayaccount.GatewayAccountAssertions;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

public class GatewayAccountResourceTestBase {

    static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";

    @DropwizardTestContext
    protected TestContext testContext;
    protected DatabaseTestHelper databaseTestHelper;
    DatabaseFixtures databaseFixtures;

    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }


    void assertCorrectCreateResponse(ValidatableResponse response) {
        assertCorrectCreateResponse(response, GatewayAccountEntity.Type.TEST);
    }

    void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountEntity.Type type) {
        GatewayAccountAssertions.assertGatewayAccountCreation.f(response, type, null, null, null);
    }
}
