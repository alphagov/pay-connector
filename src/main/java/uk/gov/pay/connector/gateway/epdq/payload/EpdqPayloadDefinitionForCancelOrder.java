package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public class EpdqPayloadDefinitionForCancelOrder extends EpdqPayloadDefinition {

    private String pspId;
    private String payId;
    private String orderId;
    private String userId;
    private String password;

    public void setPspId(String pspId) {
        this.pspId = pspId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setPayId(String payId) {
        this.payId = payId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public List<NameValuePair> extract() {
        EpdqParameterBuilder parameterBuilder = newParameterBuilder()
                .add("OPERATION", "DES")
                .add("PSPID", pspId)
                .add("PSWD", password)
                .add("USERID", userId);
        Optional.ofNullable(payId).ifPresent(payId -> parameterBuilder.add("PAYID", payId));
        Optional.ofNullable(orderId).ifPresent(orderId -> parameterBuilder.add("ORDERID", orderId));
        return parameterBuilder.build();
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.CANCEL;
    }

}
