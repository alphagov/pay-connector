package uk.gov.pay.connector.gateway.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;

import static org.apache.commons.lang3.StringUtils.trim;

abstract public class WorldpayBaseResponse implements BaseResponse {

    @XmlPath("reply/error/@code")
    private String errorCode;

    @XmlPath("reply/error/text()")
    private String errorMessage;

    public String getErrorCode() {
        return trim(errorCode);
    }

    public String getErrorMessage() {
        return trim(errorMessage);
    }

    @Override
    public abstract String toString();

}
