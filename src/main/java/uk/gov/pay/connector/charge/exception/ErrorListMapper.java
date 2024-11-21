package uk.gov.pay.connector.charge.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import java.util.ArrayList;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ACCOUNT_DISABLED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_API_NOT_ALLOWED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.MOTO_NOT_ALLOWED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED;

public class ErrorListMapper implements ExceptionMapper<ErrorList> {
    @Override
    public Response toResponse(ErrorList errorList) {
        List<ErrorResponse> errors = new ArrayList<>(errorList.getExceptions().size());
        
        for (Error exception : errorList.getExceptions()) {
            switch (exception) {
                case AuthorisationApiNotAllowedForGatewayAccountException e ->
                        errors.add(new ErrorResponse(AUTHORISATION_API_NOT_ALLOWED, e.getMessage()));
                case CardNumberInPaymentLinkReferenceException e ->
                        errors.add(new ErrorResponse(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED, e.getMessage()));
                case GatewayAccountDisabledException e ->
                        errors.add(new ErrorResponse(ACCOUNT_DISABLED, e.getMessage()));
                case HttpReturnUrlNotAllowedForLiveGatewayAccountException e ->
                        errors.add(new ErrorResponse(NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT, e.getMessage()));
                case MotoPaymentNotAllowedForGatewayAccountException e ->
                        errors.add(new ErrorResponse(MOTO_NOT_ALLOWED, e.getMessage()));
                case ZeroAmountNotAllowedForGatewayAccountException e ->
                        errors.add(new ErrorResponse(ZERO_AMOUNT_NOT_ALLOWED, e.getMessage()));
            }
        }

        return Response.status(errorList.getHttpStatus())
                .entity(errors)
                .type(APPLICATION_JSON)
                .build();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        @JsonProperty("error_identifier")
        @Schema(example = "GENERIC")
        private String errorIdentifier;

        @JsonProperty("message")
        @ArraySchema(schema = @Schema(example = "error message"))
        private String message;

        public ErrorResponse(ErrorIdentifier errorIdentifier, String message) {
            this.errorIdentifier = errorIdentifier.name();
            this.message = message;
        }
    }

    public sealed interface Error permits AuthorisationApiNotAllowedForGatewayAccountException, 
            CardNumberInPaymentLinkReferenceException, GatewayAccountDisabledException, 
            HttpReturnUrlNotAllowedForLiveGatewayAccountException, MotoPaymentNotAllowedForGatewayAccountException, 
            ZeroAmountNotAllowedForGatewayAccountException {
        
    }
}
