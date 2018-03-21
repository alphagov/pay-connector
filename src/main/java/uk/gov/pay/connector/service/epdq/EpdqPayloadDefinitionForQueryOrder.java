package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForQueryOrder implements PayloadDefinition<EpdqOrderRequestBuilder.EpdqTemplateData> {
    final static String ORDER_ID_KEY = "ORDERID";
    final static String PSPID_KEY = "PSPID";
    final static String PSWD_KEY = "PSWD";
    final static String USERID_KEY = "USERID";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        // Keep this list in alphabetical order
        return newParameterBuilder()
                .add(ORDER_ID_KEY, templateData.getOrderId())
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .build();
    }
}
