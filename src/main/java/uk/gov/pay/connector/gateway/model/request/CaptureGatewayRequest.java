package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record CaptureGatewayRequest (
        ChargeEntity charge
) implements GatewayRequest {
    public String getAmountAsString() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }
    
    public Long amount() {
        return charge.getAmount();
    }
    
    public String transactionId() {
        return charge.getGatewayTransactionId();
    }
    
    public String externalId() {
        return charge.getExternalId();
    }

    public Instant createdDate() {
        return charge.getCreatedDate();
    }

    public List<ChargeEventEntity> events() {
        return charge.getEvents();
    }
    
    public boolean isCaptureRetry() {
        return charge.getEvents().stream().anyMatch(event -> event.getStatus() == ChargeStatus.CAPTURE_APPROVED_RETRY);
    }

    @Override
    public GatewayAccountEntity gatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation requestType() {
        return GatewayOperation.CAPTURE;
    }

    @Override
    public GatewayCredentials gatewayCredentials() {
        return charge.getGatewayAccountCredentialsEntity().getCredentialsObject();
    }

    @Override
    public AuthorisationMode authorisationMode() {
        return charge.getAuthorisationMode();
    }

    @Override
    public boolean isForRecurringPayment() {
        return charge.getAgreement().isPresent();
    }

    public static CaptureGatewayRequest valueOf(ChargeEntity charge) {
        return new CaptureGatewayRequest(charge);
    }
}
