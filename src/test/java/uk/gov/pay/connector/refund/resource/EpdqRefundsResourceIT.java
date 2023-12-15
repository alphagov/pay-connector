package uk.gov.pay.connector.refund.resource;

import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.JsonEncoder;

import java.util.Map;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class EpdqRefundsResourceIT extends ChargingITestBase {

    private DatabaseFixtures.TestCharge charge;
    
    public EpdqRefundsResourceIT() {
        super("epdq");
    }

    @Before
    public void setUp() {
        super.setUp();
        charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("MyUniqueTransactionId!")
                .withTestAccount(getTestAccount())
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(getPaymentProvider())
                .withGatewayCredentialId(credentialParams.getId())
                .insert();
    }

    @Test
    public void refundAttemptShouldReturn404() {
        String payload = JsonEncoder.toJson(Map.of(
                "amount", 100,
                "refund_amount_available", 100));
        
        givenSetup()
                .body(payload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post(format("/v1/api/accounts/%s/charges/%s/refunds", accountId, charge.getExternalChargeId()))
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }
}
