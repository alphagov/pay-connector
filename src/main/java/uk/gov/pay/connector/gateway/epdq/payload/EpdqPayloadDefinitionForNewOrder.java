package uk.gov.pay.connector.gateway.epdq.payload;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder;
import uk.gov.pay.connector.gateway.templates.PayloadDefinition;

import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinition.newParameterBuilder;

public class EpdqPayloadDefinitionForNewOrder implements PayloadDefinition<EpdqTemplateData> {

    public final static String AMOUNT_KEY = "AMOUNT";
    public final static String CARD_NO_KEY = "CARDNO";
    public final static String CARDHOLDER_NAME_KEY = "CN";
    public final static String CURRENCY_KEY = "CURRENCY";
    public final static String CVC_KEY = "CVC";
    public final static String EXPIRY_DATE_KEY = "ED";
    public final static String OPERATION_KEY = "OPERATION";
    public final static String ORDER_ID_KEY = "ORDERID";
    public final static String OWNER_ADDRESS_KEY = "OWNERADDRESS";
    public final static String OWNER_COUNTRY_CODE_KEY = "OWNERCTY";
    public final static String OWNER_TOWN_KEY = "OWNERTOWN";
    public final static String OWNER_ZIP_KEY = "OWNERZIP";
    public final static String PSPID_KEY = "PSPID";
    public final static String PSWD_KEY = "PSWD";
    public final static String USERID_KEY = "USERID";

    @Override
    public ImmutableList<NameValuePair> extract(EpdqOrderRequestBuilder.EpdqTemplateData templateData) {

        // Keep this list in alphabetical order
        EpdqPayloadDefinition.ParameterBuilder parameterBuilder = newParameterBuilder()
                .add(AMOUNT_KEY, templateData.getAmount())
                .add(CARD_NO_KEY, templateData.getAuthCardDetails().getCardNo())
                .add(CARDHOLDER_NAME_KEY, templateData.getAuthCardDetails().getCardHolder())
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, templateData.getAuthCardDetails().getCvc())
                .add(EXPIRY_DATE_KEY, templateData.getAuthCardDetails().getEndDate())
                .add(OPERATION_KEY, templateData.getOperationType())
                .add(ORDER_ID_KEY, templateData.getOrderId());

        if (templateData.getAuthCardDetails().getAddress().isPresent()) {
            Address address = templateData.getAuthCardDetails().getAddress().get();
            String addressLines = concatAddressLines(address.getLine1(), address.getLine2());

            parameterBuilder.add(OWNER_ADDRESS_KEY, addressLines)
                    .add(OWNER_COUNTRY_CODE_KEY, address.getCountry())
                    .add(OWNER_TOWN_KEY, address.getCity())
                    .add(OWNER_ZIP_KEY, address.getPostcode());
        }

        parameterBuilder.add(PSPID_KEY, templateData.getMerchantCode())
                .add(PSWD_KEY, templateData.getPassword())
                .add(USERID_KEY, templateData.getUserId());

        return parameterBuilder.build();
    }

    private static String concatAddressLines(String addressLine1, String addressLine2) {
        return StringUtils.isBlank(addressLine2) ? addressLine1 : addressLine1 + ", " + addressLine2;
    }

}
