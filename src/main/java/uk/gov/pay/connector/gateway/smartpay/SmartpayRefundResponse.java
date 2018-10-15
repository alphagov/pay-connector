package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;
import java.util.StringJoiner;

@XmlRootElement(name = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayRefundResponse extends SmartpayBaseResponse implements BaseRefundResponse {

    @XmlPath("soap:Body/ns1:refundResponse/ns1:refundResult/ns1:pspReference/text()")
    private String pspReference;

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(pspReference);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "SmartPay refund response (", ")");
        if (StringUtils.isNotBlank(pspReference)) {
            joiner.add("pspReference: " + pspReference);
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("faultcode: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("faultstring: " + getErrorMessage());
        }
        return joiner.toString();
    }

}
