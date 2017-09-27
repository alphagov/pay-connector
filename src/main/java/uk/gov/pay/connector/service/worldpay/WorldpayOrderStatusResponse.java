package uk.gov.pay.connector.service.worldpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BaseCancelResponse;
import uk.gov.pay.connector.service.BaseInquiryResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayOrderStatusResponse implements BaseAuthoriseResponse, BaseCancelResponse, BaseInquiryResponse {

    private static final String WORLDPAY_AUTHORISED_EVENT = "AUTHORISED";
    private static final String WORLDPAY_REFUSED_EVENT = "REFUSED";
    private static final String WORLDPAY_CANCELLED_EVENT = "CANCELLED";

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
    public AuthoriseStatus authoriseStatus() {
        if (paRequest != null && issuerUrl != null) {
            return AuthoriseStatus.REQUIRES_3DS;
        }

        if (WORLDPAY_AUTHORISED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.AUTHORISED;
        }

        if(WORLDPAY_REFUSED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.REJECTED;
        }

        if(WORLDPAY_CANCELLED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.CANCELLED;
        }

        return AuthoriseStatus.ERROR;
    }

    @Override
    public CancelStatus cancelStatus() {
        return CancelStatus.CANCELLED;
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
    public String get3dsPaRequest() {
        return paRequest;
    }

    @Override
    public String get3dsIssuerUrl() {
        return issuerUrl;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Worldpay authorisation response (", ")");
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
        if (StringUtils.isNotBlank(get3dsIssuerUrl())) {
            joiner.add("issuerURL: " + get3dsIssuerUrl());
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
