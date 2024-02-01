package uk.gov.pay.connector.it.resources.stripe;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;


import java.util.List;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;


@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class RefundReversalResourceIT extends ChargingITestBase {

    private final String accountId = String.valueOf(nextLong());

    private StripeMockClient stripeMockClient;

    private LedgerStub ledgerStub;

    private AddGatewayAccountCredentialsParams accountCredentialsParams;


    public RefundReversalResourceIT() {
        super("stripe");
    }

    @Before
    public void setUp() {
        super.setUp();
        stripeMockClient = new StripeMockClient(wireMockServer);
        ledgerStub = new LedgerStub(wireMockServer);
       
        ///Mock 
    }

    @Test
    public void shouldSuccessfullyRefund_usingChargeId() {
        
        var gatewayAccountParams = anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("stripe")
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .build();
   
        long amount = 10L;
        stripeMockClient.mockTransferSuccess();
        stripeMockClient.mockRefund();

        //mock ledger to get the charge and refund;
        //mock charge too
        // stripe mockout might not work becasue
     

//        ValidatableResponse response = given().port(testContext.getPort())
//                .body(refundPayload)
//                .accept(ContentType.JSON)
//                .contentType(ContentType.JSON)
//                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
//                        .replace("gatewayAccountId", accountId)
//                        .replace("{chargeId}", externalChargeId)
//                        .replace("{refundId}", refundId))
//                .then()
//                .statusCode(ACCEPTED_202);

    }

}
