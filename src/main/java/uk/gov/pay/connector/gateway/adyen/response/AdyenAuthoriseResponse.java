package uk.gov.pay.connector.gateway.adyen.response;

import uk.gov.pay.connector.gateway.adyen.response.json.Action;
import uk.gov.pay.connector.gateway.adyen.response.json.AdditionalData;
import uk.gov.pay.connector.gateway.adyen.response.json.AuthoriseResponseBody;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenAuthorisationRejectedCodeMapper;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Map;
import java.util.Optional;

public class AdyenAuthoriseResponse implements BaseAuthoriseResponse {

    private final String transactionId;
    private final AuthoriseStatus authoriseStatus;
    private final String redirectUrl;
    private final String httpMethod3ds;
    private final String refusalReason;
    private final String refusalReasonCode;

    private final String paReq;
    private final String md;
    
    private final String storedPaymentMethodId;

    public static AdyenAuthoriseResponse of(AuthoriseResponseBody authoriseResponseBody) {
        Action action = authoriseResponseBody.action();
        String url = Optional.ofNullable(action).map(Action::url).orElse(null);
        String method = Optional.ofNullable(action).map(Action::method).orElse(null);
        String paReq = Optional.ofNullable(action)
                .map(Action::data)
                .map(items -> items.get("PaReq"))
                .orElse(null);

        String md = Optional.ofNullable(action)
                .map(Action::data)
                .map(items -> items.get("MD"))
                .orElse(null);
        
        var additionalData = authoriseResponseBody.additionalData();
        String storedPaymentMethodId = Optional.ofNullable(additionalData)
                .map(AdditionalData::storedPaymentMethodId)
                .orElse(null);

        return new AdyenAuthoriseResponse(authoriseResponseBody.pspReference(),
                authoriseResponseBody.resultCode(),
                url,
                method,
                paReq,
                md,
                authoriseResponseBody.refusalReason(), 
                authoriseResponseBody.refusalReasonCode(),
                storedPaymentMethodId);
    }

    private AdyenAuthoriseResponse(String transactionId,
                                   String resultCode,
                                   String redirectUrl,
                                   String httpMethod3ds,
                                   String paReq,
                                   String md,
                                   String refusalReason,
                                   String refusalReasonCode,
                                   String storedPaymentMethodId) {
        this.transactionId = transactionId;
        authoriseStatus = mapAuthorisationStatusFrom(resultCode);
        this.redirectUrl = redirectUrl;
        this.httpMethod3ds = httpMethod3ds;
        this.paReq = paReq;
        this.md = md;
        this.refusalReason = refusalReason;
        this.refusalReasonCode = refusalReasonCode;
        this.storedPaymentMethodId = storedPaymentMethodId;
    }

    private static AuthoriseStatus mapAuthorisationStatusFrom(String resultCode) {
        return switch (resultCode) {
            case "Authorised" -> AuthoriseStatus.AUTHORISED;
            case "Refused" -> AuthoriseStatus.REJECTED;
            case "RedirectShopper" -> AuthoriseStatus.REQUIRES_3DS;
            case "Error" -> AuthoriseStatus.ERROR;
            default -> throw new IllegalStateException("Unexpected value: " + resultCode);
        };
    }
    
    @Override
    public Optional<String> getGatewayRejectionReason() {
        if (refusalReason != null && refusalReasonCode != null ) {
            return Optional.of(refusalReasonCode + " - " +  refusalReason  );
        }
        return Optional.empty();
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        return authoriseStatus;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public String getHttpMethod3ds() {
        return httpMethod3ds;
    }

    public String getPaReq() {
        return paReq;
    }

    public String getMd() {
        return md;
    }

    @Override
    public Optional<? extends Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
        if (AuthoriseStatus.REQUIRES_3DS == authoriseStatus) {
            return Optional.of(new Adyen3dsRequiredParams(redirectUrl, httpMethod3ds, paReq, md));
        }
        return Optional.empty();
    }

    @Override
    public Optional<MappedAuthorisationRejectedReason> getMappedAuthorisationRejectedReason() {
        if (authoriseStatus() != AuthoriseStatus.REJECTED) {
            return Optional.empty();
        }

        var mappedAuthorisationRejectedReason = Optional.ofNullable(refusalReasonCode)
                .filter(code -> !code.isBlank())
                .map(AdyenAuthorisationRejectedCodeMapper::toMappedAuthorisationRejectionReason)
                .orElse(MappedAuthorisationRejectedReason.UNCATEGORISED);

        return Optional.of(mappedAuthorisationRejectedReason);
    }

    @Override
    public String getErrorCode() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public Optional<Map<String, String>> getGatewayRecurringAuthToken() {
        return Optional.ofNullable(storedPaymentMethodId)
                .map(_ -> Map.of("storedPaymentMethodId", storedPaymentMethodId));
    }
}
