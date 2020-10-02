package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.util.List;

import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public class EpdqPayloadDefinitionForNewOrder extends EpdqPayloadDefinition {

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

    private String pspId;
    private String orderId;
    private String userId;
    private String password;
    private String amount;
    private AuthCardDetails authCardDetails;

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    protected String getOrderId() {
        return orderId;
    }

    public void setPspId(String pspId) {
        this.pspId = pspId;
    }

    protected String getPspId() {
        return pspId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    protected String getUserId() {
        return userId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    protected String getPassword() {
        return password;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    protected String getAmount() {
        return amount;
    }

    public void setAuthCardDetails(AuthCardDetails authCardDetails) {
        this.authCardDetails = authCardDetails;
    }

    protected AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    @Override
    public List<NameValuePair> extract() {
        EpdqParameterBuilder epdqParameterBuilder = newParameterBuilder()
                .add(AMOUNT_KEY, amount)
                .add(CARD_NO_KEY, authCardDetails.getCardNo())
                .add(CARDHOLDER_NAME_KEY, authCardDetails.getCardHolder())
                .add(CURRENCY_KEY, "GBP")
                .add(CVC_KEY, authCardDetails.getCvc())
                .add(EXPIRY_DATE_KEY, authCardDetails.getEndDate().toString())
                .add(OPERATION_KEY, "RES")
                .add(ORDER_ID_KEY, orderId);

        authCardDetails.getAddress().ifPresent(address -> {
            String addressLines = concatAddressLines(address.getLine1(), address.getLine2());
            epdqParameterBuilder.add(OWNER_ADDRESS_KEY, addressLines)
                    .add(OWNER_COUNTRY_CODE_KEY, address.getCountry())
                    .add(OWNER_TOWN_KEY, address.getCity())
                    .add(OWNER_ZIP_KEY, address.getPostcode());
        });

        epdqParameterBuilder.add(PSPID_KEY, pspId)
                .add(PSWD_KEY, password)
                .add(USERID_KEY, userId);

        return epdqParameterBuilder.build();
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    protected static String concatAddressLines(String addressLine1, String addressLine2) {
        return StringUtils.isBlank(addressLine2) ? addressLine1 : addressLine1 + ", " + addressLine2;
    }

}
