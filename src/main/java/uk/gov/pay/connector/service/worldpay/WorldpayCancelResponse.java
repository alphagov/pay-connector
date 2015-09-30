package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "paymentService")
public class WorldpayCancelResponse {

    @XmlPath("reply/ok/cancelReceived/@orderCode")
    private String transactionIdForOk;

    @XmlPath("reply/error/text()")
    private String errorMessage;

    public boolean isCancelled() {
        return isNotBlank(transactionIdForOk);
    }

    public String getErrorMessage() {
        return trim(errorMessage);
    }
}
