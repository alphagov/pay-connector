package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.util.Map;

public class DeleteStoredPaymentDetailsGatewayRequest {
    private String agreementExternalId;
    private Map<String, String> recurringAuthToken;
    private String gatewayAccountType;
    private boolean live;
    private Map<String, Object> gatewayCredentials;

    private DeleteStoredPaymentDetailsGatewayRequest(String agreementExternalId,
                                                     Map<String, String> recurringAuthToken,
                                                     String gatewayAccountType,
                                                     boolean live,
                                                     Map<String, Object> gatewayCredentials) {
        this.agreementExternalId = agreementExternalId;
        this.recurringAuthToken = recurringAuthToken;
        this.gatewayAccountType = gatewayAccountType;
        this.live = live;
        this.gatewayCredentials = gatewayCredentials;
    }

    public static DeleteStoredPaymentDetailsGatewayRequest from(AgreementEntity agreement, PaymentInstrumentEntity paymentInstrument) {
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken()
                .orElseThrow(() -> new IllegalArgumentException("Expected payment instrument to have recurring auth token set"));
        return new DeleteStoredPaymentDetailsGatewayRequest(
                agreement.getExternalId(),
                recurringAuthToken,
                agreement.getGatewayAccount().getType(),
                agreement.isLive(),
                agreement.getGatewayAccount().getCredentials(agreement.getGatewayAccount().getGatewayName())
        );
    }

    public String getAgreementExternalId() {
        return agreementExternalId;
    }

    public Map<String, String> getRecurringAuthToken() {
        return recurringAuthToken;
    }

    public String getGatewayAccountType() {
        return gatewayAccountType;
    }

    public boolean isLive() {
        return live;
    }

    public Map<String, Object> getGatewayCredentials() {
        return gatewayCredentials;
    }
}
