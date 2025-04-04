package uk.gov.pay.connector.gateway.worldpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayQueryResponse implements BaseInquiryResponse {

    @XmlPath("reply/orderStatus/@orderCode")
    private String transactionId;

    @XmlPath("reply/orderStatus/payment/lastEvent/text()")
    private String lastEvent;

    @XmlPath("reply/orderStatus/payment/ISO8583ReturnCode/@description")
    private String refusedReturnCodeDescription;

    @XmlPath("reply/orderStatus/payment/ISO8583ReturnCode/@code")
    private String refusedReturnCode;

    private String errorCode;

    private String errorMessage;

    private String paRequest;
    private String issuerUrl;

    @XmlPath("reply/error/@code")
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @XmlPath("reply/orderStatus/error/@code")
    public void setOrderStatusErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @XmlPath("reply/error/text()")
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @XmlPath("reply/orderStatus/error/text()")
    public void setOrderStatusErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @XmlPath("/reply/orderStatus/requestInfo/request3DSecure/paRequest/text()")
    public void set3dsPaRequest(String paRequest) {
        this.paRequest = paRequest;
    }

    @XmlPath("/reply/orderStatus/requestInfo/request3DSecure/issuerURL/text()")
    public void set3dsIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl != null ? issuerUrl.trim() : null;
    }

    public String getLastEvent() {
        return lastEvent;
    }

    public String getRefusedReturnCodeDescription() {
        return refusedReturnCodeDescription;
    }

    public String getRefusedReturnCode() {
        return refusedReturnCode;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String getErrorCode() {
        return trim(errorCode);
    }

    @Override
    public String getErrorMessage() {
        return trim(errorMessage);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Worldpay query response (", ")");
        if (StringUtils.isNotBlank(getTransactionId())) {
            joiner.add("orderCode: " + getTransactionId());
        }
        if (StringUtils.isNotBlank(getLastEvent())) {
            joiner.add("lastEvent: "+ getLastEvent());
        }
        if (StringUtils.isNotBlank(getRefusedReturnCode())) {
            joiner.add("ISO8583ReturnCode code: " + getRefusedReturnCode());
        }
        if (StringUtils.isNotBlank(getRefusedReturnCodeDescription())) {
            joiner.add("ISO8583ReturnCode description: " + getRefusedReturnCodeDescription());
        }
        if (StringUtils.isNotBlank(issuerUrl)) {
            joiner.add("issuerURL: " + issuerUrl);
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("error code: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("error: " + getErrorMessage());
        }
        return joiner.toString();
    }
}
