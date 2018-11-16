package uk.gov.pay.connector.gateway.epdq.payload;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.templates.PayloadDefinition;

import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForNew3dsOrder extends EpdqPayloadDefinitionForNewOrder implements PayloadDefinition<EpdqTemplateData> {

    public static final String ACCEPTURL_KEY = "ACCEPTURL";
    public static final String COMPLUS_KEY = "COMPLUS";
    public static final String DECLINEURL_KEY = "DECLINEURL";
    public static final String EXCEPTIONURL_KEY = "EXCEPTIONURL";
    public static final String FLAG3D_KEY = "FLAG3D";
    public static final String HTTPACCEPT_URL = "HTTP_ACCEPT";
    public static final String HTTPUSER_AGENT_URL = "HTTP_USER_AGENT";
    public static final String LANGUAGE_URL = "LANGUAGE";
    public static final String PARAMPLUS_URL = "PARAMPLUS";
    public static final String WIN3DS_URL = "WIN3DS";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqTemplateData templateData) {
        AuthCardDetails authCardDetails = templateData.getAuthCardDetails(); 
        String frontend3dsIncomingUrl = String.format("%s/card_details/%s/3ds_required_in/epdq", templateData.getFrontendUrl(), templateData.getOrderId());

        // Keep this list in alphabetical order
        EpdqPayloadDefinition.ParameterBuilder parameterBuilder = newParameterBuilder()
                .add(ACCEPTURL_KEY, frontend3dsIncomingUrl)
                .add(AMOUNT_KEY, templateData.getAmount())
                .add(CARD_NO_KEY, authCardDetails.getCardNo())
                .add(CARDHOLDER_NAME_KEY, authCardDetails.getCardHolder())
                .add(COMPLUS_KEY, "")
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, authCardDetails.getCvc())
                .add(DECLINEURL_KEY, frontend3dsIncomingUrl + "?status=declined")
                .add(EXCEPTIONURL_KEY, frontend3dsIncomingUrl + "?status=error")
                .add(EXPIRY_DATE_KEY, authCardDetails.getEndDate())
                .add(FLAG3D_KEY, "Y")
                .add(HTTPACCEPT_URL, authCardDetails.getAcceptHeader())
                .add(HTTPUSER_AGENT_URL, authCardDetails.getUserAgentHeader())
                .add(LANGUAGE_URL, "en_GB")
                .add(OPERATION_KEY, templateData.getOperationType())
                .add(ORDER_ID_KEY, templateData.getOrderId());

        if (authCardDetails.getAddress().isPresent()) {
            Address address = authCardDetails.getAddress().get();
            String addressLines = concatAddressLines(address.getLine1(), address.getLine2());

            parameterBuilder.add(OWNER_ADDRESS_KEY, addressLines)
                    .add(OWNER_COUNTRY_CODE_KEY, address.getCountry())
                    .add(OWNER_TOWN_KEY, address.getCity())
                    .add(OWNER_ZIP_KEY, address.getPostcode());
        }

        parameterBuilder.add(PARAMPLUS_URL, "")
                .add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId())
                .add(WIN3DS_URL, "MAINW");

        return parameterBuilder.build();
    }

    private static String concatAddressLines(String addressLine1, String addressLine2) {
        return StringUtils.isBlank(addressLine2) ? addressLine1 : addressLine1 + ", " + addressLine2;
    }

}
