package uk.gov.pay.connector.gateway.epdq.model.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.epdq.EpdqStatusMapper;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

@XmlRootElement(name = "ncresponse")
public class EpdqQueryResponse extends EpdqBaseResponse implements BaseResponse {

    private String status;

    @XmlAttribute(name = "PAYID")
    private String transactionId;

    public String getTransactionId() {
        return transactionId;
    }

    @XmlAttribute(name = "STATUS")
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStatus() {
        return status;
    }

    private boolean hasError() {
        return !"0".equals(super.getErrorCode());
    }
    
    @Override
    public String getErrorMessage() {
        if (hasError())
            return super.getErrorMessage();
        return null;
    }

    @Override
    public String getErrorCode() {
        if (hasError())
            return super.getErrorMessage();
        return null;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "ePDQ query response (", ")");
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

    public static ChargeQueryResponse toChargeQueryResponse(EpdqQueryResponse epdqQueryResponse) {
        return new ChargeQueryResponse(EpdqStatusMapper.map(epdqQueryResponse.getStatus()), epdqQueryResponse.toString());
    }
}
