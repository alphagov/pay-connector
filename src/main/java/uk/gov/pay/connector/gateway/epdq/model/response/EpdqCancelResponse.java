package uk.gov.pay.connector.gateway.epdq.model.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

@XmlRootElement(name = "ncresponse")
public class EpdqCancelResponse extends EpdqBaseResponse implements BaseCancelResponse {

    private static final String CANCELLED = "6";
    private static final String SUBMITTED = "61";

    @XmlAttribute(name = "STATUS")
    private String status;

    @XmlAttribute(name = "PAYID")
    private String transactionId;

    private boolean hasError() {
        return !CANCELLED.equals(status) && !SUBMITTED.equals(status);
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public CancelStatus cancelStatus() {
        switch(status) {
            case CANCELLED:
                return CancelStatus.CANCELLED;
            case SUBMITTED:
                return CancelStatus.SUBMITTED;
            default:
                return CancelStatus.ERROR;
        }
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
        StringJoiner joiner = new StringJoiner(", ", "ePDQ cancel response (", ")");
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
