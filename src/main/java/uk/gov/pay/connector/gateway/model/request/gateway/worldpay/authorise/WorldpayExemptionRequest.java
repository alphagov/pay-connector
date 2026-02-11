package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record WorldpayExemptionRequest(Type type, Placement placement) {

    public enum Type {
        CP, OP;
    }

    public enum Placement {
        AUTHORISATION, OPTIMISED;
    }

}
