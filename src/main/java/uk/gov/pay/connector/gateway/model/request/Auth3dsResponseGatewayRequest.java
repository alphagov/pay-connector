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

public class Auth3dsResponseGatewayRequest implements GatewayRequest {

    private final ChargeEntity charge;
    private final Auth3dsResult auth3dsResult;

    public Auth3dsResponseGatewayRequest(ChargeEntity charge, Auth3dsResult auth3dsResult) {
        this.charge = charge;
        this.auth3dsResult = auth3dsResult;
    }

    public Auth3dsResult getAuth3dsResult() {
        return auth3dsResult;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
    }

    public String getChargeExternalId() {
        return String.valueOf(charge.getExternalId());
    }

    public Optional<ProviderSessionIdentifier> getProviderSessionId() {
        return Optional.ofNullable(charge.getCardDetails().getProviderSessionId()).map(ProviderSessionIdentifier::of);
    }

    public ChargeEntity getCharge() {
        return charge;
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    @Override
    public GatewayCredentials getGatewayCredentials() {
        return charge.getGatewayAccountCredentialsEntity().getCredentialsObject();
    }

    @Override
    public AuthorisationMode getAuthorisationMode() {
        return charge.getAuthorisationMode();
    }

    @Override
    public boolean isForRecurringPayment() {
        return charge.getAgreement().isPresent();
    }

    public static Auth3dsResponseGatewayRequest valueOf(ChargeEntity charge, Auth3dsResult auth3DsResult) {
        return new Auth3dsResponseGatewayRequest(charge, auth3DsResult);
    }

    public String getAmount() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }

    public String getDescription() {
        return charge.getDescription();
    }
}
