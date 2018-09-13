package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForMaintenanceOrder implements PayloadDefinition<EpdqTemplateData> {

    static final String AMOUNT_KEY = "AMOUNT";
    static final String OPERATION_KEY = "OPERATION";
    static final String PAYID_KEY = "PAYID";
    static final String PSPID_KEY = "PSPID";
    static final String PSWD_KEY = "PSWD";
    static final String USERID_KEY = "USERID";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqTemplateData templateData) {

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
