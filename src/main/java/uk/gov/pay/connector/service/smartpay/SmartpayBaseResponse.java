package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseResponse;

import static org.apache.commons.lang3.StringUtils.trim;

abstract public class SmartpayBaseResponse implements BaseResponse {

    @XmlPath("soap:Body/soap:Fault/faultcode/text()")
    private String errorCode;

    @XmlPath("soap:Body/soap:Fault/faultstring/text()")
    private String errorMessage;

    public String getErrorCode() {
        return trim(errorCode);
    }

    public String getErrorMessage() {
        return trim(errorMessage);
    }

}
