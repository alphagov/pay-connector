package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public class RecurringPaymentAuthorisationGatewayRequest implements GatewayRequest{
    private PaymentInstrumentEntity paymentInstrument;
    private String agreementId;
    private String amount;
    private String gatewayTransactionId;
    private String description;
    private GatewayCredentials credentials;
    private GatewayAccountEntity gatewayAccountEntity;
    private String govUkPayPaymentId;
    private final AgreementPaymentType agreementPaymentType;

    private RecurringPaymentAuthorisationGatewayRequest(GatewayAccountEntity gatewayAccountEntity,
                                                        GatewayCredentials credentials,
                                                        String agreementId,
                                                        String amount,
                                                        String gatewayTransactionId,
                                                        String description,
                                                        PaymentInstrumentEntity paymentInstrument, 
                                                        String govUkPayPaymentId, 
                                                        AgreementPaymentType agreementPaymentType) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        this.credentials = credentials;
        this.agreementId = agreementId;
        this.amount = amount;
        this.gatewayTransactionId = gatewayTransactionId;
        this.description = description;
        this.paymentInstrument = paymentInstrument;
        this.govUkPayPaymentId = govUkPayPaymentId;
        this.agreementPaymentType = agreementPaymentType;
    }

    public static RecurringPaymentAuthorisationGatewayRequest valueOf(ChargeEntity charge) {
        return new RecurringPaymentAuthorisationGatewayRequest(charge.getGatewayAccount(),
                Optional.ofNullable(charge.getGatewayAccountCredentialsEntity())
                        .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                        .orElse(null),
                charge.getAgreement().map(AgreementEntity::getExternalId).orElse(null),
                String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge)),
                charge.getGatewayTransactionId(),
                charge.getDescription(),
                charge.getPaymentInstrument().orElse(null),
                charge.getExternalId(),
                charge.getAgreementPaymentType());
    }

    public Optional<PaymentInstrumentEntity> getPaymentInstrument() {
        return Optional.ofNullable(paymentInstrument);
    }

    public String getAgreementId() {
        return agreementId;
    }

    public String getAmount() {
        return amount;
    }

    public Optional<String> getGatewayTransactionId() {
        return Optional.ofNullable(gatewayTransactionId);
    }

    public String getDescription() {
        return description;
    }

    public String getGovUkPayPaymentId() {
        return govUkPayPaymentId;
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccountEntity;
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    @Override
    public GatewayCredentials getGatewayCredentials() {
        return credentials;
    }

    @Override
    public AuthorisationMode getAuthorisationMode() {
        return AuthorisationMode.AGREEMENT;
    }

    @Override
    public boolean isForRecurringPayment() {
        return true;
    }

    
    public AgreementPaymentType getAgreementPaymentType() {
        return agreementPaymentType;
    }

}
