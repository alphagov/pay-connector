package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForNewOrder implements PayloadDefinition<EpdqTemplateData> {

    final static String AMOUNT_KEY = "AMOUNT";
    final static String CARD_NO_KEY = "CARDNO";
    final static String CARDHOLDER_NAME_KEY = "CN";
    final static String CURRENCY_KEY = "CURRENCY";
    final static String CVC_KEY = "CVC";
    final static String EXPIRY_DATE_KEY = "ED";
    final static String OPERATION_KEY = "OPERATION";
    final static String ORDER_ID_KEY = "ORDERID";
    final static String OWNER_ADDRESS_KEY = "OWNERADDRESS";
    final static String OWNER_COUNTRY_CODE_KEY = "OWNERCTY";
    final static String OWNER_TOWN_KEY = "OWNERTOWN";
    final static String OWNER_ZIP_KEY = "OWNERZIP";
    final static String PSPID_KEY = "PSPID";
    final static String PSWD_KEY = "PSWD";
    final static String USERID_KEY = "USERID";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqTemplateData templateData) {

        // Keep this list in alphabetical order
        return newParameterBuilder()
                .add(AMOUNT_KEY, templateData.getAmount())
                .add(CARD_NO_KEY, templateData.getAuthCardDetails().getCardNo())
                .add(CARDHOLDER_NAME_KEY, templateData.getAuthCardDetails().getCardHolder())
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, templateData.getAuthCardDetails().getCvc())
                .add(EXPIRY_DATE_KEY, templateData.getAuthCardDetails().getEndDate())
                .add(OPERATION_KEY, templateData.getOperationType())
                .add(ORDER_ID_KEY, templateData.getOrderId())
                .add(OWNER_ADDRESS_KEY, concatAddressLines(templateData.getAuthCardDetails().getAddress().getLine1(),
                                templateData.getAuthCardDetails().getAddress().getLine2()))
                .add(OWNER_COUNTRY_CODE_KEY, templateData.getAuthCardDetails().getAddress().getCountry())
                .add(OWNER_TOWN_KEY, templateData.getAuthCardDetails().getAddress().getCity())
                .add(OWNER_ZIP_KEY, templateData.getAuthCardDetails().getAddress().getPostcode())
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .build();
    }

    private static String concatAddressLines(String addressLine1, String addressLine2) {
        return StringUtils.isBlank(addressLine2) ? addressLine1 : addressLine1 + ", " + addressLine2;
    }

}
