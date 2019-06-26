package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;

import java.util.List;

public class EpdqPayloadDefinitionForQueryOrder extends EpdqPayloadDefinition {
    public final static String ORDER_ID_KEY = "ORDERID";
    public final static String PSPID_KEY = "PSPID";
    public final static String PSWD_KEY = "PSWD";
    public final static String USERID_KEY = "USERID";

    @Override
    public List<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {
        return newParameterBuilder()
                .add(ORDER_ID_KEY, templateData.getOrderId())
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .build();
    }
}
