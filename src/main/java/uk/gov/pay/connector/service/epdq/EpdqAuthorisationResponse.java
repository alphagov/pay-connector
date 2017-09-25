package uk.gov.pay.connector.service.epdq;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

@XmlRootElement(name = "ncresponse")
public class EpdqAuthorisationResponse extends EpdqBaseResponse implements BaseAuthoriseResponse {

    private static final String AUTHORISED = "5";
    private static final String WAITING_EXTERNAL = "50";
    private static final String WAITING = "51";
    private static final String REJECTED = "2";

    @XmlAttribute(name = "STATUS")
    private String status;

    @XmlAttribute(name = "PAYID")
    private String transactionId;

    @Override
    public AuthoriseStatus authoriseStatus() {
        if (AUTHORISED.equals(status)) {
            return AuthoriseStatus.AUTHORISED;
        }

        if (WAITING_EXTERNAL.equals(status) ||
                WAITING.equals(status)) {
            return AuthoriseStatus.SUBMITTED;
        }

        if (REJECTED.equals(status)) {
            return AuthoriseStatus.REJECTED;
        }

        return AuthoriseStatus.ERROR;
    }

    @Override
    public String get3dsPaRequest() {
        return null;
    }

    @Override
    public String get3dsIssuerUrl() {
        return null;
    }

    private boolean hasError() {
        return authoriseStatus() == AuthoriseStatus.ERROR;
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
        StringJoiner joiner = new StringJoiner(", ", "ePDQ authorisation response (", ")");
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
