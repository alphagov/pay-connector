package uk.gov.pay.connector.gateway.worldpay;

import jakarta.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;

import java.util.StringJoiner;

@XmlRootElement(name = "paymentService")
public class WorldpayDeleteTokenResponse extends WorldpayBaseResponse {
    
    @XmlPath("reply/ok/deleteTokenReceived/@paymentTokenID")
    private String paymentTokenID;
    
    public String getPaymentTokenID() {
        return paymentTokenID;
    }
    
    public String stringify() {
        if (!StringUtils.isNotBlank(getErrorCode()) && !StringUtils.isNotBlank(getErrorMessage())) {
            return "Worldpay delete token response";
        }
        StringJoiner joiner = new StringJoiner(", ", "Worldpay delete token response (", ")");
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("error code: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("error: " + getErrorMessage());
        }
        return joiner.toString();
    }

    @Override
    public String toString() {
        return stringify();
    }
}
