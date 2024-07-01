package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public record Auth3dsResponseGatewayRequest (
    ChargeEntity charge,
    Auth3dsResult auth3dsResult
)  implements GatewayRequest {
    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    public Optional<ProviderSessionIdentifier> getProviderSessionId() {
        return Optional.ofNullable(charge.getProviderSessionId()).map(ProviderSessionIdentifier::of);
    }
    
    public Optional<String> transactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    @Override
    public GatewayAccountEntity gatewayAccount() { return charge.getGatewayAccount(); }

    @Override
    public GatewayOperation requestType() { return GatewayOperation.AUTHORISE; }

    @Override
    public GatewayCredentials gatewayCredentials() { return charge.getGatewayAccountCredentialsEntity().getCredentialsObject(); }

    @Override
    public AuthorisationMode authorisationMode() { return charge.getAuthorisationMode(); }

    @Override
    public boolean isForRecurringPayment() {
        return charge.getAgreement().isPresent();
    }

    public static Auth3dsResponseGatewayRequest valueOf(ChargeEntity charge, Auth3dsResult auth3DsResult) {
        return new Auth3dsResponseGatewayRequest(charge, auth3DsResult);
    }
    
    public String amount() { return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge)); }
    
    public String description() { return charge.getDescription(); }
}
