package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;

import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION;

public class Gateway3DSAuthorisationResponse {

    private final BaseAuthoriseResponse.AuthoriseStatus authorisationStatus;
    private final String transactionId;
    private final String stringified;
    private final Gateway3dsRequiredParams gateway3dsRequiredParams;
    private final ProviderSessionIdentifier providerSessionIdentifier;
    private final Map<String, String> gatewayRecurringAuthToken;

    private Gateway3DSAuthorisationResponse(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId, String stringified,
                                            Gateway3dsRequiredParams gateway3dsRequiredParams, ProviderSessionIdentifier providerSessionIdentifier, Map<String, String> gatewayRecurringAuthToken) {
        this.transactionId = transactionId;
        this.authorisationStatus = authorisationStatus;
        this.stringified = stringified;
        this.gateway3dsRequiredParams = gateway3dsRequiredParams;
        this.providerSessionIdentifier = providerSessionIdentifier;
        this.gatewayRecurringAuthToken = gatewayRecurringAuthToken;
    }

    public static Gateway3DSAuthorisationResponse of(String stringified, BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, stringified, null, null, null);
    }

    public static Gateway3DSAuthorisationResponse of(String stringified, BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId,
                                                     Gateway3dsRequiredParams gateway3dsRequiredParams, ProviderSessionIdentifier providerSessionIdentifier, Map<String, String> gatewayRecurringAuthToken) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, stringified, gateway3dsRequiredParams, providerSessionIdentifier, gatewayRecurringAuthToken);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, "", null, null, null);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, String transactionId,
                                                     Gateway3dsRequiredParams gateway3dsRequiredParams, ProviderSessionIdentifier providerSessionIdentifier) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, transactionId, "", gateway3dsRequiredParams, providerSessionIdentifier, null);
    }

    public static Gateway3DSAuthorisationResponse of(String stringified, BaseAuthoriseResponse.AuthoriseStatus authorisationStatus) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, null, stringified, null, null, null);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, null, "", null, null, null);
    }

    public static Gateway3DSAuthorisationResponse of(BaseAuthoriseResponse.AuthoriseStatus authorisationStatus, Gateway3dsRequiredParams gateway3dsRequiredParams) {
        return new Gateway3DSAuthorisationResponse(authorisationStatus, null, "", gateway3dsRequiredParams, null, null);
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

    public Optional<ProviderSessionIdentifier> getProviderSessionIdentifier() {
        return Optional.ofNullable(providerSessionIdentifier);
    }

    public Optional<Map<String, String>> getGatewayRecurringAuthToken() {
        return Optional.ofNullable(gatewayRecurringAuthToken);
    }

    public String toString() {
        return stringified;
    }

}
