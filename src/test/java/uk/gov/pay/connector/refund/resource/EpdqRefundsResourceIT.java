package uk.gov.pay.connector.refund.resource;

import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.JsonEncoder;

import java.util.Map;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

public class EpdqRefundsResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("epdq", app);
    private DatabaseFixtures.TestCharge charge;

    @BeforeEach
    void setUp() {
        charge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("MyUniqueTransactionId!")
                .withTestAccount(testBaseExtension.getTestAccount())
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .withGatewayCredentialId(testBaseExtension.getCredentialParams().getId())
                .insert();
    }

    @Test
    void refundAttemptShouldReturn404() {
        String payload = JsonEncoder.toJson(Map.of(
                "amount", 100,
                "refund_amount_available", 100));
        
        app.givenSetup()
                .body(payload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post(format("/v1/api/accounts/%s/charges/%s/refunds", testBaseExtension.getAccountId(), charge.getExternalChargeId()))
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }
}
