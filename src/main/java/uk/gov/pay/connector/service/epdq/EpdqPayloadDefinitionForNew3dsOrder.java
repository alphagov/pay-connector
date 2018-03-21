package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForNew3dsOrder extends EpdqPayloadDefinitionForNewOrder implements PayloadDefinition<EpdqTemplateData> {

    static final String ACCEPTURL_KEY = "ACCEPTURL";
    static final String COMPLUS_KEY = "COMPLUS";
    static final String DECLINEURL_KEY = "DECLINEURL";
    static final String EXCEPTIONURL_KEY = "EXCEPTIONURL";
    static final String FLAG3D_KEY = "FLAG3D";
    static final String HTTPACCEPT_URL = "HTTP_ACCEPT";
    static final String HTTPUSER_AGENT_URL = "HTTP_USER_AGENT";
    static final String LANGUAGE_URL = "LANGUAGE";
    static final String PARAMPLUS_URL = "PARAMPLUS";
    static final String WIN3DS_URL = "WIN3DS";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqTemplateData templateData) {

        String frontend3dsIncomingUrl = String.format("%s/card_details/%s/3ds_required_in/epdq", templateData.getFrontendUrl(), templateData.getOrderId());
        // Keep this list in alphabetical order
        return newParameterBuilder()
                .add(ACCEPTURL_KEY, frontend3dsIncomingUrl)
                .add(AMOUNT_KEY, templateData.getAmount())
                .add(CARD_NO_KEY, templateData.getAuthCardDetails().getCardNo())
                .add(CARDHOLDER_NAME_KEY, templateData.getAuthCardDetails().getCardHolder())
                .add(COMPLUS_KEY, "")
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, templateData.getAuthCardDetails().getCvc())
                .add(DECLINEURL_KEY, frontend3dsIncomingUrl + "?status=declined")
                .add(EXCEPTIONURL_KEY, frontend3dsIncomingUrl + "?status=error")
                .add(EXPIRY_DATE_KEY, templateData.getAuthCardDetails().getEndDate())
                .add(FLAG3D_KEY, "Y")
                .add(HTTPACCEPT_URL, templateData.getAuthCardDetails().getAcceptHeader())
                .add(HTTPUSER_AGENT_URL, templateData.getAuthCardDetails().getUserAgentHeader())
                .add(LANGUAGE_URL, "en_GB")
                .add(OPERATION_KEY, templateData.getOperationType())
                .add(ORDER_ID_KEY, templateData.getOrderId())
                .add(OWNER_ADDRESS_KEY, concatAddressLines(templateData.getAuthCardDetails().getAddress().getLine1(),
                        templateData.getAuthCardDetails().getAddress().getLine2()))
                .add(OWNER_COUNTRY_CODE_KEY, templateData.getAuthCardDetails().getAddress().getCountry())
                .add(OWNER_TOWN_KEY, templateData.getAuthCardDetails().getAddress().getCity())
                .add(OWNER_ZIP_KEY, templateData.getAuthCardDetails().getAddress().getPostcode())
                .add(PARAMPLUS_URL, "")
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .add(WIN3DS_URL, "MAINW")
                .build();
    }

    private static String concatAddressLines(String addressLine1, String addressLine2) {
        return StringUtils.isBlank(addressLine2) ? addressLine1 : addressLine1 + ", " + addressLine2;
    }

}
