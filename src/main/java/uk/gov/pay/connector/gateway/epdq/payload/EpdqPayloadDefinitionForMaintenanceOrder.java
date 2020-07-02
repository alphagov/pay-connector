package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;

import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public abstract class EpdqPayloadDefinitionForMaintenanceOrder extends EpdqPayloadDefinition {
    
    @Override
    public List<NameValuePair> extract() {
        var templateData = getEpdqTemplateData();
        EpdqParameterBuilder epdqParameterBuilder = newParameterBuilder();
        Optional.ofNullable(templateData.getAmount()).ifPresent(amount -> epdqParameterBuilder.add("AMOUNT", amount));
        Optional.ofNullable(templateData.getTransactionId()).ifPresent(
                transactionId -> epdqParameterBuilder.add("PAYID", transactionId)
        );
        Optional.ofNullable(templateData.getOrderId()).ifPresent(
                orderId -> epdqParameterBuilder.add("ORDERID", orderId)
        );
        
        return epdqParameterBuilder
                .add("OPERATION", templateData.getOperationType())
                .add("PSPID", templateData.getMerchantCode())
                .add("PSWD", templateData.getPassword())
                .add("USERID", templateData.getUserId())
                .build();
    }
}
