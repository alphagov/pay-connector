package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.service.BaseCaptureResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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

}
