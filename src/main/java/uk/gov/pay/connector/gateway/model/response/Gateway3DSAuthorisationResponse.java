package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION;

public class Gateway3DSAuthorisationResponse {
    private final BaseAuthoriseResponse.AuthoriseStatus authorisationStatus;
    private final String transactionId;

    private Gateway3DSAuthorisationResponse(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId) {
        this.transactionId = transactionId;
        this.authorisationStatus = authorisationStatus;
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, null);
    }

    public static Gateway3DSAuthorisationResponse ofException() {
        return new Gateway3DSAuthorisationResponse(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED, null);
    }

    public boolean isDeclined() {
        return authorisationStatus == BaseAuthoriseResponse.AuthoriseStatus.REJECTED ||
                authorisationStatus == BaseAuthoriseResponse.AuthoriseStatus.ERROR;
    }

    public boolean isSuccessful() {
        return authorisationStatus == BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;
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
}
