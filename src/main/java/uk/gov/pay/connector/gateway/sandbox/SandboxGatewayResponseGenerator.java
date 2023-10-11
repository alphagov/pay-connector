package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.wallets.SandboxWalletMagicValues;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.sandbox.wallets.SandboxWalletMagicValues.DECLINED;
import static uk.gov.pay.connector.gateway.sandbox.wallets.SandboxWalletMagicValues.ERROR;
import static uk.gov.pay.connector.gateway.sandbox.wallets.SandboxWalletMagicValues.REFUSED;
import static uk.gov.pay.connector.gateway.sandbox.wallets.SandboxWalletMagicValues.magicValueFromString;

public class SandboxGatewayResponseGenerator {

    private final SandboxCardNumbers sandboxCardNumbers;

    public SandboxGatewayResponseGenerator(SandboxCardNumbers sandboxCardNumbers) {
        this.sandboxCardNumbers = sandboxCardNumbers;
    }

    public GatewayResponse getSandboxGatewayWalletResponse(String description) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        Optional<SandboxWalletMagicValues> possibleMagicValue = Optional.ofNullable(magicValueFromString(description));
        
        if (possibleMagicValue.isPresent()) {
            var magicValue = possibleMagicValue.get();
            if (magicValue == ERROR) {
                return gatewayResponseBuilder
                        .withGatewayError(new GatewayError(magicValue.errorMessage, GENERIC_GATEWAY_ERROR))
                        .build();
            }
            if (magicValue == DECLINED || magicValue == REFUSED) {
                return getSandboxGatewayResponse(false, true);
            }
        }
        
        return getSandboxGatewayResponse(true, true);
    }


    public GatewayResponse getSandboxGatewayResponse(String lastDigitsCardNumber) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        
        if (sandboxCardNumbers.isErrorCard(lastDigitsCardNumber)) {
            CardError errorInfo = sandboxCardNumbers.cardErrorFor(lastDigitsCardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (sandboxCardNumbers.isRejectedCard(lastDigitsCardNumber)) {
            return getSandboxGatewayResponse(false);
        } else if (sandboxCardNumbers.isValidCard(lastDigitsCardNumber)) {
            return getSandboxGatewayResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }
    
    public GatewayResponse getSandboxGatewayResponse(boolean isAuthorised) {
        return getSandboxGatewayResponse(isAuthorised, false);
    }

    public GatewayResponse getSandboxGatewayResponse(boolean isAuthorised, boolean mockExpiry) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseAuthoriseResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public AuthoriseStatus authoriseStatus() {
                return isAuthorised ? AuthoriseStatus.AUTHORISED : AuthoriseStatus.REJECTED;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
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
            public Optional<Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
                return Optional.empty();
            }
            
            @Override
            public Optional<CardExpiryDate> getCardExpiryDate() { 
                return Optional.ofNullable(mockExpiry ? CardExpiryDate.valueOf(YearMonth.of(2050, 12)) : null); 
            }

            @Override
            public Optional<Map<String, String>> getGatewayRecurringAuthToken() {
                return Optional.of(Map.of("token", randomUUID().toString()));
            }

            @Override
            public Optional<MappedAuthorisationRejectedReason> getMappedAuthorisationRejectedReason() {
                return Optional.ofNullable(authoriseStatus())
                        .filter(authoriseStatus -> authoriseStatus == AuthoriseStatus.REJECTED)
                        .map(authoriseStatus -> MappedAuthorisationRejectedReason.DO_NOT_RETRY);
            }

            @Override
            public String toString() {
                return "Sandbox authorisation response (transactionId: " + getTransactionId()
                        + ", isAuthorised: " + isAuthorised + ')';
            }
        }).build();
    }

}
