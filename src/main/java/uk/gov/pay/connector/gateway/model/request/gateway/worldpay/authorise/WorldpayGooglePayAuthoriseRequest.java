package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface WorldpayGooglePayAuthoriseRequest extends WorldpayAuthoriseRequest {

    String protocolVersion();
    String signature();
    String signedMessage();

}
