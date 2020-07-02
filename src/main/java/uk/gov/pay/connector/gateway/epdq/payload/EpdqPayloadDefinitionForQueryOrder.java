package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.util.List;

import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public class EpdqPayloadDefinitionForQueryOrder extends EpdqPayloadDefinition {
    
    public final static String ORDER_ID_KEY = "ORDERID";
    public final static String PSPID_KEY = "PSPID";
    public final static String PSWD_KEY = "PSWD";
    public final static String USERID_KEY = "USERID";

    private String orderId;
    private String pspId;
    private String userId;
    private String password;

    @Override
    public List<NameValuePair> extract() {
        return newParameterBuilder()
                .add(ORDER_ID_KEY, orderId)
                .add(PSPID_KEY, pspId)
                .add(PSWD_KEY, password)
                .add(USERID_KEY, userId)
                .build();
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setPspId(String pspId) {
        this.pspId = pspId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getOperationType() {
        return "RES";
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

}
