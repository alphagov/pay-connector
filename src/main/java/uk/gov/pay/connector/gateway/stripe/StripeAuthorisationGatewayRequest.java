package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Currency;
import java.util.Optional;

public class StripeAuthorisationGatewayRequest implements AuthorisationGatewayRequest {

    private AuthCardDetails authCardDetails;
    private long amount;
    private Currency currency = Currency.getInstance("GBP");
    private String description;
    private String source = "card";
    private GatewayAccountEntity gatewayAccountEntity;
    private String chargeExternalId;

    StripeAuthorisationGatewayRequest(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        this.authCardDetails = authCardDetails;
        this.amount = chargeEntity.getAmount();
        this.description = chargeEntity.getDescription();
        this.gatewayAccountEntity = chargeEntity.getGatewayAccount();
        this.chargeExternalId = chargeEntity.getExternalId();
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return this.gatewayAccountEntity;
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    @Override
    public Optional<String> getTransactionId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AuthCardDetails getAuthCardDetails() {
        return this.authCardDetails;
    }

    @Override
    public String getAmount() {
        return String.valueOf(this.amount);
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getChargeExternalId() {
        return this.chargeExternalId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getSource() {
        return source;
    }
}
