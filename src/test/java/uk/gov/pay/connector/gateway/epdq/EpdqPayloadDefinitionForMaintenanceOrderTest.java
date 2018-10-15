package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder.AMOUNT_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder.OPERATION_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder.PAYID_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder.PSPID_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder.PSWD_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder.USERID_KEY;

public class EpdqPayloadDefinitionForMaintenanceOrderTest {

    @Test
    public void testBaseValuePairs() {
        EpdqOrderRequestBuilder.EpdqTemplateData templateData = new EpdqOrderRequestBuilder.EpdqTemplateData();
        templateData.setOperationType("Operation-value");
        templateData.setTransactionId("Transaction-id");
        templateData.setMerchantCode("Merchant-code");
        templateData.setPassword("Password");
        templateData.setUserId("User-id");

        EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();
        ImmutableList<NameValuePair> extractPairs = epdqPayloadDefinitionForMaintenanceOrder.extract(templateData);
        BasicNameValuePair operationValue = new BasicNameValuePair(OPERATION_KEY, "Operation-value");
        BasicNameValuePair transactionId = new BasicNameValuePair(PAYID_KEY, "Transaction-id");
        BasicNameValuePair merchantCode = new BasicNameValuePair(PSPID_KEY, "Merchant-code");
        BasicNameValuePair password = new BasicNameValuePair(PSWD_KEY, "Password");
        BasicNameValuePair userId = new BasicNameValuePair(USERID_KEY, "User-id");


        assertThat(extractPairs, contains(operationValue, transactionId, merchantCode, password, userId));
    }

    @Test
    public void testBaseValuePairsWithAmount() {
        EpdqOrderRequestBuilder.EpdqTemplateData templateData = new EpdqOrderRequestBuilder.EpdqTemplateData();
        templateData.setOperationType("Operation-value");
        templateData.setTransactionId("Transaction-id");
        templateData.setMerchantCode("Merchant-code");
        templateData.setPassword("Password");
        templateData.setUserId("User-id");
        templateData.setAmount("400");

        EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();
        ImmutableList<NameValuePair> extractPairs = epdqPayloadDefinitionForMaintenanceOrder.extract(templateData);
        BasicNameValuePair operationValue = new BasicNameValuePair(OPERATION_KEY, "Operation-value");
        BasicNameValuePair transactionId = new BasicNameValuePair(PAYID_KEY, "Transaction-id");
        BasicNameValuePair merchantCode = new BasicNameValuePair(PSPID_KEY, "Merchant-code");
        BasicNameValuePair password = new BasicNameValuePair(PSWD_KEY, "Password");
        BasicNameValuePair userId = new BasicNameValuePair(USERID_KEY, "User-id");
        BasicNameValuePair amount = new BasicNameValuePair(AMOUNT_KEY, "400");


        assertThat(extractPairs, contains(amount, operationValue, transactionId, merchantCode, password, userId));
    }

}
