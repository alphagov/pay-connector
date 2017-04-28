package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForNewOrder implements PayloadDefinition<EpdqTemplateData> {

    final static String AMOUNT_KEY = "AMOUNT";
    final static String CARD_NO_KEY = "CARDNO";
    final static String CURRENCY_KEY = "CURRENCY";
    final static String CVC_KEY = "CVC";
    final static String ED_KEY = "ED";
    final static String OPERATION_KEY = "OPERATION";
    final static String ORDER_ID_KEY = "ORDERID";
    final static String PSPID_KEY = "PSPID";
    final static String PSWD_KEY = "PSWD";
    final static String USERID_KEY = "USERID";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqTemplateData templateData) {

        // Keep this list in alphabetical order
        return newParameterBuilder()
                .add(AMOUNT_KEY, templateData.getAmount())
                .add(CARD_NO_KEY, templateData.getAuthCardDetails().getCardNo())
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, templateData.getAuthCardDetails().getCvc())
                .add(ED_KEY, templateData.getAuthCardDetails().getEndDate())
                .add(OPERATION_KEY, templateData.getOperationType())
                .add(ORDER_ID_KEY, templateData.getOrderId())
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .build();
    }

}
