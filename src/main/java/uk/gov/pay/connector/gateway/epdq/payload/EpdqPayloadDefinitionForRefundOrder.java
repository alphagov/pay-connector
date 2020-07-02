package uk.gov.pay.connector.gateway.epdq.payload;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.util.List;

import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;

public class EpdqPayloadDefinitionForRefundOrder extends EpdqPayloadDefinition {

    private String pspId;
    private String payId;
    private String userId;
    private String password;
    private String amount;

    public void setPspId(String pspId) {
        this.pspId = pspId;
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

    public void setAmount(String amount) {
        this.amount = amount;
    }

    @Override
    public List<NameValuePair> extract() {
        return newParameterBuilder()
                .add("OPERATION", getOperationType())
                .add("PSPID", pspId)
                .add("PSWD", password)
                .add("USERID", userId)
                .add("PAYID", payId)
                .add("AMOUNT", amount)
                .build();
    }
    
    @Override
    public String getOperationType() {
        return "RFD";
    }

    @Override
    protected OrderRequestType getOrderRequestType() {
        return OrderRequestType.REFUND;
    }

}
