package uk.gov.pay.connector.gateway.epdq.payload;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;
import uk.gov.pay.connector.gateway.templates.PayloadDefinition;

import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForMaintenanceOrder implements PayloadDefinition<EpdqTemplateData> {

    public static final String AMOUNT_KEY = "AMOUNT";
    public static final String OPERATION_KEY = "OPERATION";
    public static final String PAYID_KEY = "PAYID";
    public static final String PSPID_KEY = "PSPID";
    public static final String PSWD_KEY = "PSWD";
    public static final String USERID_KEY = "USERID";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {

        // Keep this list in alphabetical order
        EpdqPayloadDefinition.ParameterBuilder parameterBuilder = newParameterBuilder();
        String amount = templateData.getAmount();
        if (amount != null) {
            parameterBuilder.add(AMOUNT_KEY, amount);
        }
        return parameterBuilder.add(OPERATION_KEY, templateData.getOperationType())
                .add(PAYID_KEY, templateData.getTransactionId())
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .build();
    }
}
