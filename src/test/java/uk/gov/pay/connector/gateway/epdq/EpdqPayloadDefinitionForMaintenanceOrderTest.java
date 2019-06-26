package uk.gov.pay.connector.gateway.epdq;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertThat;

public class EpdqPayloadDefinitionForMaintenanceOrderTest {
    private EpdqOrderRequestBuilder.EpdqTemplateData templateData;
    @Before
    public void setUp() {
        templateData = new EpdqOrderRequestBuilder.EpdqTemplateData();
        templateData.setOperationType("Operation-value");
        templateData.setTransactionId("Transaction-id");
        templateData.setMerchantCode("Merchant-code");
        templateData.setPassword("Password");
        templateData.setUserId("User-id");
    }

    @Test
    public void testBaseValuePairs() {
        EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();
        List<NameValuePair> extractPairs = epdqPayloadDefinitionForMaintenanceOrder.extract(templateData);
        BasicNameValuePair operationValue = new BasicNameValuePair("OPERATION", "Operation-value");
        BasicNameValuePair transactionId = new BasicNameValuePair("PAYID", "Transaction-id");
        BasicNameValuePair merchantCode = new BasicNameValuePair("PSPID", "Merchant-code");
        BasicNameValuePair password = new BasicNameValuePair("PSWD", "Password");
        BasicNameValuePair userId = new BasicNameValuePair("USERID", "User-id");


        assertThat(extractPairs, contains(operationValue, transactionId, merchantCode, password, userId));
    }

    @Test
    public void testBaseValuePairsWithAmount() {
        templateData.setAmount("400");

        EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();
        List<NameValuePair> extractPairs = epdqPayloadDefinitionForMaintenanceOrder.extract(templateData);
        BasicNameValuePair operationValue = new BasicNameValuePair("OPERATION", "Operation-value");
        BasicNameValuePair transactionId = new BasicNameValuePair("PAYID", "Transaction-id");
        BasicNameValuePair merchantCode = new BasicNameValuePair("PSPID", "Merchant-code");
        BasicNameValuePair password = new BasicNameValuePair("PSWD", "Password");
        BasicNameValuePair userId = new BasicNameValuePair("USERID", "User-id");
        BasicNameValuePair amount = new BasicNameValuePair("AMOUNT", "400");


        assertThat(extractPairs, contains(amount, operationValue, transactionId, merchantCode, password, userId));
    }

    @Test
    public void testOnlyTransactionIdIsSentIfBothTransactionIdAndOrderIdAvailable() {
        templateData.setOrderId("Order-Id");

        EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();
        List<NameValuePair> extractPairs = epdqPayloadDefinitionForMaintenanceOrder.extract(templateData);
        BasicNameValuePair transactionId = new BasicNameValuePair("PAYID", "Transaction-id");

        assertThat(extractPairs, hasItem(transactionId));
    }

    @Test
    public void testThatOrderIdIsSentIfTransactionIsNotAvailable() {
        EpdqOrderRequestBuilder.EpdqTemplateData templateData = new EpdqOrderRequestBuilder.EpdqTemplateData();
        templateData.setOperationType("Operation-value");
        templateData.setOrderId("Order-Id");
        templateData.setMerchantCode("Merchant-code");
        templateData.setPassword("Password");
        templateData.setUserId("User-id");

        EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();
        List<NameValuePair> extractPairs = epdqPayloadDefinitionForMaintenanceOrder.extract(templateData);
        BasicNameValuePair orderId = new BasicNameValuePair("ORDERID", "Order-Id");

        assertThat(extractPairs, hasItem(orderId));
    }

}
