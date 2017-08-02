package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.service.BaseRefundResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;

@XmlRootElement(name = "ncresponse")
public class EpdqRefundResponse extends EpdqBaseResponse implements BaseRefundResponse {
    private static final String REFUND_PENDING = "81";
    private static final String DELETION_PENDING = "71";

    @XmlAttribute(name = "STATUS")
    private String status;
    @XmlAttribute(name = "PAYID")
    private String transactionId;
    @XmlAttribute(name = "PAYIDSUB")
    private String payIdSub;

    private boolean hasError() {
        return !REFUND_PENDING.equals(status) && !DELETION_PENDING.equals(status);
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
    public Optional<String> getReference() {
        if (isNullOrEmpty(transactionId) || isNullOrEmpty(payIdSub)) {
            return Optional.empty();
        }
        return Optional.of(transactionId + "/" + payIdSub);
    }
}
