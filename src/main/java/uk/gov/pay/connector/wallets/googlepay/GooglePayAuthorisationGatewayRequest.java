package uk.gov.pay.connector.wallets.googlepay;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.util.Optional;

public record GooglePayAuthorisationGatewayRequest (
        String gatewayTransactionId,
        String email,
        SupportedLanguage language,
        boolean moto,
        String amount,
        String description,
        ServicePaymentReference reference,
        String govUkPayPaymentId,
        GatewayCredentials gatewayCredentials,
        GatewayAccountEntity gatewayAccount,
        AuthorisationMode authorisationMode,
        boolean savePaymentInstrumentToAgreement,
        Optional<AgreementEntity> agreement,
        GooglePayAuthRequest googlePayAuthRequest
) implements AuthorisationGatewayRequest {
//    private GooglePayAuthRequest googlePayAuthRequest;

    public GooglePayAuthorisationGatewayRequest(ChargeEntity charge, GooglePayAuthRequest googlePayAuthRequest) {
        this(
                charge.getGatewayTransactionId(),
                charge.getEmail(),
                charge.getLanguage(),
                charge.isMoto(),
                String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge)),
                charge.getDescription(),
                charge.getReference(),
                charge.getExternalId(),
                Optional.ofNullable(charge.getGatewayAccountCredentialsEntity())
                        .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                        .orElse(null),
                charge.getGatewayAccount(),
                charge.getAuthorisationMode(),
                charge.isSavePaymentInstrumentToAgreement(),
                charge.getAgreement(),
                googlePayAuthRequest
        );
//        super(charge);
//        this.googlePayAuthRequest = googlePayAuthRequest;
    }

    @Override
    public boolean isMoto() {
        return moto;
    }

    @Override
    public Optional<String> getTransactionId() {
        return Optional.ofNullable(gatewayTransactionId);
    }

    @Override
    public boolean isSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    @Override
    public GatewayOperation requestType() {
        return GatewayOperation.AUTHORISE;
    }

    @Override
    public boolean isForRecurringPayment() {
        return agreement.isPresent();
    }

//    public GooglePayAuthRequest getGooglePayAuthRequest() {
//        return googlePayAuthRequest;
//    }

    public static GooglePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, GooglePayAuthRequest googlePayAuthRequest) {
        return new GooglePayAuthorisationGatewayRequest(charge, googlePayAuthRequest);
    }
}
