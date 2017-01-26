package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BaseInquiryResponse;

import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayOrderStatusResponse implements BaseAuthoriseResponse, BaseInquiryResponse {

    private static final String WORLDPAY_AUTHORISED_EVENT = "AUTHORISED";

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
        this.issuerUrl = issuerUrl;
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
    public boolean isAuthorised() {
        return WORLDPAY_AUTHORISED_EVENT.equals(lastEvent);
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        if (paRequest != null && issuerUrl != null) {
            return AuthoriseStatus.REQUIRES_3D;
        }

        if (lastEvent == null) {
            return AuthoriseStatus.ERROR;
        }

        if (WORLDPAY_AUTHORISED_EVENT.equals(lastEvent)) {
            return AuthoriseStatus.AUTHORISED;
        }

        return AuthoriseStatus.REJECTED;
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

    public String getPaRequest() {
        return paRequest;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }
}
