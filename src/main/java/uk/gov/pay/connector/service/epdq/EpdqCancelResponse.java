package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.service.BaseCancelResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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

}
