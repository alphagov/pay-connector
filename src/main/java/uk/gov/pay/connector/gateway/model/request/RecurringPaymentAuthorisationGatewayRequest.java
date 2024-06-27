package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public record RecurringPaymentAuthorisationGatewayRequest (
    GatewayAccountEntity gatewayAccount,
    GatewayCredentials gatewayCredentials,
    String agreementId,
    String amount,
    String gatewayTransactionId,
    String description,
    PaymentInstrumentEntity paymentInstrument,
    String govUkPayPaymentId
) implements GatewayRequest {
    public static RecurringPaymentAuthorisationGatewayRequest valueOf(ChargeEntity charge) {
        return new RecurringPaymentAuthorisationGatewayRequest(
                charge.getGatewayAccount(),
                Optional.ofNullable(charge.getGatewayAccountCredentialsEntity())
                        .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                        .orElse(null),
                charge.getAgreement().map(AgreementEntity::getExternalId).orElse(null),
                String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge)),
                charge.getGatewayTransactionId(),
                charge.getDescription(),
                charge.getPaymentInstrument().orElse(null),
                charge.getExternalId());
    }

    public Optional<PaymentInstrumentEntity> getPaymentInstrument() {
        return Optional.ofNullable(paymentInstrument);
    }
    
    public Optional<String> getGatewayTransactionId() {
        return Optional.ofNullable(gatewayTransactionId);
    }

    @Override
    public GatewayAccountEntity gatewayAccount() {
        return gatewayAccount;
    }
//    public GatewayAccountEntity getGatewayAccount() {
//        return gatewayAccount;
//    }

    @Override
    public GatewayOperation requestType() {
        return GatewayOperation.AUTHORISE;
    }
//    public GatewayOperation getRequestType() {
//        return GatewayOperation.AUTHORISE;
//    }

    @Override
    public GatewayCredentials gatewayCredentials() {
        return gatewayCredentials;
    }
//    public GatewayCredentials getGatewayCredentials() {
//        return gatewayCredentials;
//    }

    @Override
    public AuthorisationMode authorisationMode() {
        return AuthorisationMode.AGREEMENT;
    }
//    public AuthorisationMode getAuthorisationMode() {
//        return AuthorisationMode.AGREEMENT;
//    }

    @Override
    public boolean isForRecurringPayment() {
        return true;
    }
}
