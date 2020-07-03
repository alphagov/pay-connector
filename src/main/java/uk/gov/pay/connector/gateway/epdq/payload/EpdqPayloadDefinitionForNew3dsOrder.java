package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.util.List;
import java.util.Optional;

import static java.util.function.Predicate.not;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public class EpdqPayloadDefinitionForNew3dsOrder extends EpdqPayloadDefinitionForNewOrder {

    public static final String ACCEPTURL_KEY = "ACCEPTURL";
    public static final String COMPLUS_KEY = "COMPLUS";
    public static final String DECLINEURL_KEY = "DECLINEURL";
    public static final String EXCEPTIONURL_KEY = "EXCEPTIONURL";
    public static final String FLAG3D_KEY = "FLAG3D";
    public static final String HTTPACCEPT_KEY = "HTTP_ACCEPT";
    public static final String HTTPUSER_AGENT_KEY = "HTTP_USER_AGENT";
    public static final String LANGUAGE_URL = "LANGUAGE";
    public static final String PARAMPLUS_URL = "PARAMPLUS";
    public static final String WIN3DS_URL = "WIN3DS";
    
    public static final String DEFAULT_BROWSER_ACCEPT_HEADER = "*/*";
    public final static String DEFAULT_BROWSER_USER_AGENT = "Mozilla/5.0";
    
    private final String frontendUrl;

    public EpdqPayloadDefinitionForNew3dsOrder(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public List<NameValuePair> extract() {
        String frontend3dsIncomingUrl = String.format("%s/card_details/%s/3ds_required_in/epdq", frontendUrl, getOrderId());

        EpdqParameterBuilder epdqParameterBuilder = newParameterBuilder()
                .add(ACCEPTURL_KEY, frontend3dsIncomingUrl)
                .add(AMOUNT_KEY, getAmount())
                .add(CARD_NO_KEY, getAuthCardDetails().getCardNo())
                .add(CARDHOLDER_NAME_KEY, getAuthCardDetails().getCardHolder())
                .add(COMPLUS_KEY, "")
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, getAuthCardDetails().getCvc())
                .add(DECLINEURL_KEY, frontend3dsIncomingUrl + "?status=declined")
                .add(EXCEPTIONURL_KEY, frontend3dsIncomingUrl + "?status=error")
                .add(EXPIRY_DATE_KEY, getAuthCardDetails().getEndDate())
                .add(FLAG3D_KEY, "Y")
                .add(HTTPACCEPT_KEY, getBrowserAcceptHeader())
                .add(HTTPUSER_AGENT_KEY, getBrowserUserAgent())
                .add(LANGUAGE_URL, "en_GB")
                .add(OPERATION_KEY, getOperationType())
                .add(ORDER_ID_KEY, getOrderId());

        getAuthCardDetails().getAddress().ifPresent(address -> {
            String addressLines = concatAddressLines(address.getLine1(), address.getLine2());
            epdqParameterBuilder.add(OWNER_ADDRESS_KEY, addressLines)
                    .add(OWNER_COUNTRY_CODE_KEY, address.getCountry())
                    .add(OWNER_TOWN_KEY, address.getCity())
                    .add(OWNER_ZIP_KEY, address.getPostcode());
        });

        epdqParameterBuilder.add(PARAMPLUS_URL, "")
                .add(PSPID_KEY, getPspId())
                .add(PSWD_KEY, getPassword())
                .add(USERID_KEY, getUserId())
                .add(WIN3DS_URL, "MAINW");

        return epdqParameterBuilder.build();
    }

    @Override
    public String getOperationType() {
        return "RES";
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.AUTHORISE_3DS;
    }

    String getBrowserAcceptHeader() {
        return Optional.ofNullable(getAuthCardDetails().getAcceptHeader())
                .filter(not(String::isEmpty))
                .orElse(DEFAULT_BROWSER_ACCEPT_HEADER);
    }

    String getBrowserUserAgent() {
        return Optional.ofNullable(getAuthCardDetails().getUserAgentHeader())
                .filter(not(String::isEmpty))
                .orElse(DEFAULT_BROWSER_USER_AGENT);
    }
}
