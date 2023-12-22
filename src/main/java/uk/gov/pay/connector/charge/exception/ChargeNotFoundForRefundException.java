package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class ChargeNotFoundForRefundException extends WebApplicationException {
    public ChargeNotFoundForRefundException(String chargeExternalId, String refundExternalId, Long gatewayAccountId) {
        super(notFoundResponse(format("Charge with id [%s] does not exist for Refund with id [%s] and Gateway Account with id [%s].", chargeExternalId, refundExternalId, gatewayAccountId)));
    }
}
