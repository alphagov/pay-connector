package uk.gov.pay.connector.refund.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class RefundNotFoundForChargeException extends WebApplicationException {

    public RefundNotFoundForChargeException(String refundExternalId, String chargeExternalId, Long gatewayAccountId) {
        super(notFoundResponse(format("Refund with id [%s] not found for Charge wih id [%s] and gateway account with id [%s].", refundExternalId, chargeExternalId, gatewayAccountId)));
    }
}
