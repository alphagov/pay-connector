package uk.gov.pay.connector.service.worldpay;

import uk.gov.pay.connector.service.BaseRefundResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;

@XmlRootElement(name = "paymentService")
public class WorldpayRefundResponse extends WorldpayBaseResponse implements BaseRefundResponse {

    @Override
    public Optional<String> getReference() {
        return Optional.empty();
    }
}
