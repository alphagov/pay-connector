package uk.gov.pay.connector.gateway.epdq.model.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

@XmlRootElement(name = "ncresponse")
public class EpdqCaptureResponse extends EpdqBaseResponse implements BaseCaptureResponse {

    private static final String CAPTURED = "91";

    @XmlAttribute(name = "STATUS")
    private String status;

    @XmlAttribute(name = "PAYID")
    private String transactionId;

    private boolean hasError() {
        return !CAPTURED.equals(status);
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String getErrorCode() {
        if (hasError())
            return super.getErrorCode();
        return null;
    }

    @Override
    public String getErrorMessage() {
        if (hasError())
            return super.getErrorMessage();
        return null;
    }

    @Override
    public String toString() {
        return stringify();
    }

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "ePDQ capture response (", ")");
        if (StringUtils.isNotBlank(getTransactionId())) {
            joiner.add("PAYID: " + getTransactionId());
        }
        if (StringUtils.isNotBlank(status)) {
            joiner.add("STATUS: " + status);
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("NCERROR: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("NCERRORPLUS: " + getErrorMessage());
        }
        return joiner.toString();
    }

}
