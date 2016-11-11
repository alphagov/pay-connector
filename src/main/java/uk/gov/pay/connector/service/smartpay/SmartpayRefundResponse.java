package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseRefundResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;

@XmlRootElement(name = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayRefundResponse extends SmartpayBaseResponse implements BaseRefundResponse {

    @XmlPath("soap:Body/ns1:refundResponse/ns1:refundResult/ns1:pspReference/text()")
    private String pspReference;

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(pspReference);
    }
}
