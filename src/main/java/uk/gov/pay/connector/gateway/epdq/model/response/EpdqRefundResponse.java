package uk.gov.pay.connector.gateway.epdq.model.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;
import java.util.StringJoiner;

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

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "ePDQ refund response (", ")");
        if (StringUtils.isNotBlank(transactionId)) {
            joiner.add("PAYID: " + transactionId);
        }
        if (StringUtils.isNotBlank(payIdSub)) {
            joiner.add("PAYIDSUB: " + payIdSub);
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

    @Override
    public String toString() {
        return stringify();
    }
}
