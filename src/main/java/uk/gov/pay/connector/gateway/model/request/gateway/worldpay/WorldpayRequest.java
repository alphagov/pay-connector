package uk.gov.pay.connector.gateway.model.request.gateway.worldpay;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface WorldpayRequest {

    String username();
    String password();
    String merchantCode();
    String orderCode();

}
