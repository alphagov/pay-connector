package uk.gov.pay.connector.it.resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.Response;
import java.time.ZonedDateTime;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class TransactionsSummaryResourceITest {

    private static final String START_OF_RANGE = "2017-11-25T10:00:00Z";
    private static final String END_OF_RANGE = "2017-11-27T10:00:00Z";

    private static final ZonedDateTime BEFORE_RANGE_1 = ZonedDateTime.parse("2017-11-20T10:00:00Z");
    private static final ZonedDateTime BEFORE_RANGE_2 = ZonedDateTime.parse("2017-11-24T19:00:00Z");
    private static final ZonedDateTime DURING_RANGE_1 = ZonedDateTime.parse("2017-11-25T17:00:00Z");
    private static final ZonedDateTime DURING_RANGE_2 = ZonedDateTime.parse("2017-11-26T11:00:00Z");
    private static final ZonedDateTime DURING_RANGE_3 = ZonedDateTime.parse("2017-11-26T20:00:00Z");
    private static final ZonedDateTime DURING_RANGE_4 = ZonedDateTime.parse("2017-11-26T23:00:00Z");
    private static final ZonedDateTime AFTER_RANGE_1 = ZonedDateTime.parse("2017-11-27T15:00:00Z");

    private static final long GATEWAY_ACCOUNT_ID = 4815162342L;

    @DropwizardTestContext
    private TestContext testContext;
    
    private DatabaseTestHelper databaseTestHelper;
    private RestAssuredClient connectorApi;
    
    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        connectorApi = new RestAssuredClient(testContext.getPort(), Long.toString(GATEWAY_ACCOUNT_ID));
    }
    

    @Test
    public void shouldGetSummaryContainingPaymentsAndRefundsWithinRange() {
        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(GATEWAY_ACCOUNT_ID)
                .insert();

        DatabaseFixtures.TestAccount wrongTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(999000999L)
                .insert();

        // £50 charge before range so doesn’t count
        DatabaseFixtures.TestCharge beforeRangeCharge1 = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(CAPTURE_APPROVED)
                .withCreatedDate(BEFORE_RANGE_1)
                .withAmount(50_00L)
                .withTestAccount(testAccount)
                .insert();

        // £30 refund (for charge before range) before range so doesn’t count
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(beforeRangeCharge1)
                .withRefundStatus(REFUNDED)
                .withAmount(30_00L)
                .withCreatedDate(BEFORE_RANGE_2)
                .insert();

        // £200 charge during range so it counts
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .withCreatedDate(DURING_RANGE_1)
                .withAmount(200_00L)
                .withTestAccount(testAccount)
                .insert();

        // £70 not-yet-successful charge during range so it doesn’t count
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(AUTHORISATION_SUBMITTED)
                .withCreatedDate(DURING_RANGE_1)
                .withAmount(70_00L)
                .withTestAccount(testAccount)
                .insert();

        // £10 refund (for charge before range) during range so it counts
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(beforeRangeCharge1)
                .withRefundStatus(REFUNDED)
                .withCreatedDate(DURING_RANGE_2)
                .withAmount(10_00L)
                .insert();

        // £40 charge for wrong account during range so it doesn’t count
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(AUTHORISATION_SUBMITTED)
                .withCreatedDate(DURING_RANGE_2)
                .withAmount(40_00L)
                .withTestAccount(wrongTestAccount)
                .insert();

        // £100 charge during range so it counts
        DatabaseFixtures.TestCharge duringRangeCharge2 = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(CAPTURED)
                .withCreatedDate(DURING_RANGE_3)
                .withAmount(100_00L)
                .withTestAccount(testAccount)
                .insert();

        // £5 refund (for charge during range) during range so it counts
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(duringRangeCharge2)
                .withRefundStatus(REFUNDED)
                .withCreatedDate(DURING_RANGE_4)
                .withAmount(5_00L)
                .insert();

        // £20 charge after range so it doesn’t count
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeStatus(CAPTURE_SUBMITTED)
                .withCreatedDate(AFTER_RANGE_1)
                .withAmount(20_00L)
                .withTestAccount(testAccount)
                .insert();

        connectorApi
                .withQueryParam("from_date", START_OF_RANGE)
                .withQueryParam("to_date", END_OF_RANGE)
                .getTransactionsSummary()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("successful_payments.count", is(2))
                .body("successful_payments.total_in_pence", is(30000))
                .body("refunded_payments.count", is(2))
                .body("refunded_payments.total_in_pence", is(1500))
                .body("net_income.total_in_pence", is(28500));
    }

}
