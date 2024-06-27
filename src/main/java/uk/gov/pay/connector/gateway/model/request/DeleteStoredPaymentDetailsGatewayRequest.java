package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.util.Map;

public record DeleteStoredPaymentDetailsGatewayRequest (
    String agreementExternalId,
    Map<String, String> recurringAuthToken,
    String gatewayAccountType,
    boolean live,
    GatewayCredentials gatewayCredentials
){
    public static DeleteStoredPaymentDetailsGatewayRequest from(AgreementEntity agreement, PaymentInstrumentEntity paymentInstrument) {
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken()
                .orElseThrow(() -> new IllegalArgumentException("Expected payment instrument to have recurring auth token set"));
        return new DeleteStoredPaymentDetailsGatewayRequest(
                agreement.getExternalId(),
                recurringAuthToken,
                agreement.getGatewayAccount().getType(),
                agreement.isLive(),
                agreement.getGatewayAccount().getGatewayAccountCredentialsEntity(agreement.getGatewayAccount().getGatewayName()).getCredentialsObject()
        );
    }

    public boolean isLive() {
        return live;
    }
}
