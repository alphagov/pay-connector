package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;

import java.util.List;
import java.util.Optional;

public class EpdqPayloadDefinitionForMaintenanceOrder extends EpdqPayloadDefinition {
    @Override
    public List<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {

        EpdqPayloadDefinition.ParameterBuilder parameterBuilder = newParameterBuilder();
        Optional.ofNullable(templateData.getAmount()).ifPresent(amount -> parameterBuilder.add("AMOUNT", amount));
        Optional.ofNullable(templateData.getTransactionId()).ifPresent(
                transactionId -> parameterBuilder.add("PAYID", transactionId)
        );
        Optional.ofNullable(templateData.getOrderId()).ifPresent(
                orderId -> parameterBuilder.add("ORDERID", orderId)
        );
        
        return parameterBuilder
                .add("OPERATION", templateData.getOperationType())
                .add("PSPID", templateData.getMerchantCode())
                .add("PSWD", templateData.getPassword())
                .add("USERID", templateData.getUserId())
                .build();
    }
}
