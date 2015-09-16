package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayCaptureResponse {


    @XmlPath("reply/ok/captureReceived/@orderCode")
    private String transationIdForOk;

    @XmlPath("reply/error/@code")
    private String errorCode;

    @XmlPath("reply/error/text()")
    private String errorMessage;

    //TODO: what define a capture failure response?
    public boolean isCaptured() {
        return isNotBlank(transationIdForOk);
    }

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
