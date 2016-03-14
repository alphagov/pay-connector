package uk.gov.pay.connector.it.task;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.task.ChargeExpiryTask.EXPIRED_CHARGES_SWEEP;

public class ChargeExpiryTaskITest {

    private static final String PROVIDER_NAME = "test_gateway";
    private static final long AMOUNT = 6234L;
    public static final String ACCOUNT_ID = "10000";


    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private RestAssuredClient chargeExpiryClient = new RestAssuredClient(app, ACCOUNT_ID);

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(ACCOUNT_ID, PROVIDER_NAME);
    }

    @Test
    public void shouldGetSuccessWhenPostChargeExpiryTask() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, ACCOUNT_ID, AMOUNT, AUTHORISATION_SUCCESS, "/something", null);

        chargeExpiryClient.postAdminTask("", EXPIRED_CHARGES_SWEEP)
                .statusCode(OK.getStatusCode());
    }
}
