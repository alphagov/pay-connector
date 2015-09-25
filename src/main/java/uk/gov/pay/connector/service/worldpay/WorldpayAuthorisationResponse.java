package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayAuthorisationResponse {

    private static final String AUTHORISED = "AUTHORISED";

    @XmlPath("reply/orderStatus/payment/lastEvent/text()")
    private String lastEvent;

    @XmlPath("reply/orderStatus/payment/ISO8583ReturnCode/@description")
    private String refusedReturnCodeDescription;

    @XmlPath("reply/orderStatus/payment/ISO8583ReturnCode/@code")
    private String refusedReturnCode;

    @XmlPath("reply/error/@code")
    private String errorCode;

    @XmlPath("reply/error/text()")
    private String errorMessage;

    public String getLastEvent() {
        return lastEvent;
    }

    public String getRefusedReturnCodeDescription() {
        return refusedReturnCodeDescription;
    }

    public String getRefusedReturnCode() {
        return refusedReturnCode;
    }

    //TODO: what define a authorised request
    public boolean isAuthorised() {
        return AUTHORISED.equals(lastEvent);
    }

    //TODO: what define an error response
    public boolean isError() {
        return isNotBlank(errorCode) || isNotBlank(errorMessage);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return trim(errorMessage);
    }
}
