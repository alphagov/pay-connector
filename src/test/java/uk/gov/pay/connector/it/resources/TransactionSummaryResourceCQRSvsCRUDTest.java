package uk.gov.pay.connector.it.resources;

import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class TransactionSummaryResourceCQRSvsCRUDTest {

    private static final long GATEWAY_ACCOUNT_ID = 4815162343L;
    
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private RestAssuredClient connectorApi = new RestAssuredClient(app, Long.toString(GATEWAY_ACCOUNT_ID));
    private DatabaseFixtures.TestAccount testAccount;
    private String fromDate;
    private String toDate;
    private Duration crudDuration;
    private Duration cqrsDuration;

    @Test
    public void cqrsVsCrudTest() {
        int numberOfChargeEvents = 500;
        
        Given:
        charge_events_and_refunds_are_in_the_database(numberOfChargeEvents);
        
        When:
        we_get_the_transaction_summary_report_by_crud(numberOfChargeEvents).and()
                .we_get_the_transaction_summary_report_by_cqrs(numberOfChargeEvents);
        
        Then:
        print_the_timings();
    }

    private void print_the_timings() {
        System.out.println("\nTime to run report by CRUD: " + crudDuration.toString());
        System.out.println("\nTime to run report by CQRS: " + cqrsDuration.toString());
    }

    private void we_get_the_transaction_summary_report_by_cqrs(int expectedNumberOfSuccessfulPayments) {
        long startTime = System.currentTimeMillis();
        connectorApi
                .withQueryParam("from_date", fromDate)
                .withQueryParam("to_date", toDate)
                .withQueryParam("accountId", String.valueOf(testAccount.getAccountId()))
                .getTransactionsSummaryByCqrs()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("successful_payments.count", is(expectedNumberOfSuccessfulPayments));

        long endTime = System.currentTimeMillis();
        cqrsDuration = Duration.ofMillis(endTime - startTime);
    }

    private TransactionSummaryResourceCQRSvsCRUDTest and() {
        return this;
    }

    private TransactionSummaryResourceCQRSvsCRUDTest we_get_the_transaction_summary_report_by_crud(int expectedNumberOfSuccessfulPayments) {
        fromDate = ZonedDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_INSTANT);
        toDate = ZonedDateTime.now().plusHours(2).format(DateTimeFormatter.ISO_INSTANT);
        
        long startTime = System.currentTimeMillis();
        connectorApi
                .withQueryParam("from_date", fromDate)
                .withQueryParam("to_date", toDate)
                .withQueryParam("accountId", String.valueOf(testAccount.getAccountId()))
                .getTransactionsSummary()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("successful_payments.count", is(expectedNumberOfSuccessfulPayments));

        long endTime = System.currentTimeMillis();
        crudDuration = Duration.ofMillis(endTime - startTime);
        return this;
    }

    private void charge_events_and_refunds_are_in_the_database(int numberOfChargeEvents) {
        System.out.println(format("Adding %s charge events and %s refunds...\n", numberOfChargeEvents, numberOfChargeEvents/2));

        testAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(GATEWAY_ACCOUNT_ID)
                .insert();
        
//        String url = app.getConf().getDataSourceFactory().getUrl();
        
        for (int i = 0; i < numberOfChargeEvents; i++) {
            ZonedDateTime dateTime = ZonedDateTime.now();

            DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestCharge()
                    .withChargeStatus(CAPTURED)
                    .withCreatedDate(dateTime)
                    .withAmount(200_00L)
                    .withTestAccount(testAccount)
                    .insert();

            DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aSuccessfulChargeEvent()
                    .withAccountGatewayId(testAccount.getAccountId())
                    .withCreatedDate(dateTime)
                    .withExternalChargeId(testCharge.getExternalChargeId())
                    .withAmount(200_00L)
                    .insert();

            if (i%2 == 0) {
                DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestRefund()
                        .withTestCharge(testCharge)
                        .withRefundStatus(REFUNDED)
                        .withCreatedDate(dateTime.plusHours(1))
                        .withAmount(10_00L)
                        .insert();

                DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aRefundedEvent()
                        .withAccountGatewayId(testAccount.getAccountId())
                        .withCreatedDate(dateTime)
                        .withExternalChargeId(testCharge.getExternalChargeId())
                        .withAmount(200_00L)
                        .insert();
            }
        }

        System.out.println("Added\n");
    }

}
