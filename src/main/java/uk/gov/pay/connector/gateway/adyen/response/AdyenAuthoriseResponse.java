package uk.gov.pay.connector.gateway.adyen.response;

import uk.gov.pay.connector.gateway.adyen.response.json.Action;
import uk.gov.pay.connector.gateway.adyen.response.json.AuthoriseResponseBody;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Optional;

public class AdyenAuthoriseResponse implements BaseAuthoriseResponse {

    private final String transactionId;
    private final AuthoriseStatus authoriseStatus;
    private final String redirectUrl;
    private final String httpMethod3ds;

    public static AdyenAuthoriseResponse of(AuthoriseResponseBody authoriseResponseBody) {
        Action action = authoriseResponseBody.action();
        String url = Optional.ofNullable(action).map(Action::url).orElse(null);
        String method = Optional.ofNullable(action).map(Action::method).orElse(null);
        
        return new AdyenAuthoriseResponse(authoriseResponseBody.pspReference(),
                authoriseResponseBody.resultCode(),
                url,
                method);
    }

    private AdyenAuthoriseResponse(String transactionId,
                                   String resultCode,
                                   String redirectUrl,
                                   String httpMethod3ds) {
        this.transactionId = transactionId;
        authoriseStatus = mapAuthorisationStatusFrom(resultCode);
        this.redirectUrl = redirectUrl;
        this.httpMethod3ds = httpMethod3ds;
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

    @Override
    public Optional<? extends Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
        if (AuthoriseStatus.REQUIRES_3DS == authoriseStatus) {
            return Optional.of(new Adyen3dsRequiredParams(redirectUrl, httpMethod3ds));
        }
        return Optional.empty();
    }

    @Override
    public String getErrorCode() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }
}
