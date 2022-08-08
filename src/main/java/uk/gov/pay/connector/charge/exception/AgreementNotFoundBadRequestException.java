package uk.gov.pay.connector.charge.exception;
import javax.ws.rs.WebApplicationException;

/**
 * A version of AgreementNotFoundException that maps to a 400 (bad request)
 * rather than a 404 (not found).
 * A request to /v1/api/accounts/account-id/agreements/non-existent-agreement-id/cancel
 * would be a 404 but a request to /v1/api/accounts/account-id/charges containing
 * "agreement_id": "non-existent-agreement-id" in the JSON body would be a 400.
 */
public class AgreementNotFoundBadRequestException extends WebApplicationException {

    public AgreementNotFoundBadRequestException(String message) {
        super(message);
    }

}
