package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION;

public class Gateway3DSAuthorisationResponse {

    private final BaseAuthoriseResponse.AuthoriseStatus authorisationStatus;
    private final String transactionId;
    private final String stringified;
    private final Gateway3dsRequiredParams gateway3dsRequiredParams;

    private Gateway3DSAuthorisationResponse(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId, String stringified,
                                            Gateway3dsRequiredParams gateway3dsRequiredParams) {
        this.transactionId = transactionId;
        this.authorisationStatus = authorisationStatus;
        this.stringified = stringified;
        this.gateway3dsRequiredParams = gateway3dsRequiredParams;
    }

    public static Gateway3DSAuthorisationResponse of(String stringified, BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, stringified, null);
    }

    public static Gateway3DSAuthorisationResponse of(String stringified, BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId,
                                                     Gateway3dsRequiredParams gateway3dsRequiredParams) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, stringified, gateway3dsRequiredParams);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, "", null);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId,
                                                     Gateway3dsRequiredParams gateway3dsRequiredParams) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, "", gateway3dsRequiredParams);
    }

    public static Gateway3DSAuthorisationResponse of(String stringified, BaseAuthoriseResponse.AuthoriseStatus authorisationStatus) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, null, stringified, null);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, null, "", null);
    }

    public boolean isSuccessful() {
        return authorisationStatus == BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED
                || authorisationStatus == BaseAuthoriseResponse.AuthoriseStatus.AUTH_3DS_READY;
    }

    public boolean isException() {
        return authorisationStatus == EXCEPTION;
    }

    public ChargeStatus getMappedChargeStatus() {
        return authorisationStatus.getMappedChargeStatus();
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(transactionId);
    }

    public Optional<Gateway3dsRequiredParams> getGateway3dsRequiredParams() {
        return Optional.ofNullable(gateway3dsRequiredParams);
    }

    public String toString() {
        return stringified;
    }

}
