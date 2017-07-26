package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForMaintenanceOrder implements PayloadDefinition<EpdqTemplateData> {

    final static String OPERATION_KEY = "OPERATION";
    final static String PAYID_KEY = "PAYID";
    final static String PSPID_KEY = "PSPID";
    final static String PSWD_KEY = "PSWD";
    final static String USERID_KEY = "USERID";
    final static String AMOUNT_KEY = "AMOUNT";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqTemplateData templateData) {

        // Keep this list in alphabetical order
        return newParameterBuilder().add(OPERATION_KEY, templateData.getOperationType())
                .add(AMOUNT_KEY, templateData.getAmount())
                .add(PAYID_KEY, templateData.getTransactionId())
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .build();
    }
}
