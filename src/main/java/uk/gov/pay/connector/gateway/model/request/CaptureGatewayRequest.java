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

public record CaptureGatewayRequest (
        ChargeEntity charge
) implements GatewayRequest {
    public String getAmountAsString() {
        return String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
    }
    
    public Long getAmount() {
        return charge.getAmount();
    }
    
    public String getGatewayTransactionId() {
        return charge.getGatewayTransactionId();
    }
    
    public String getExternalId() {
        return charge.getExternalId();
    }

    public Instant getCreatedDate() {
        return charge.getCreatedDate();
    }

    public List<ChargeEventEntity> getEvents() {
        return charge.getEvents();
    }
    
    public boolean isCaptureRetry() {
        return charge.getEvents().stream().anyMatch(event -> event.getStatus() == ChargeStatus.CAPTURE_APPROVED_RETRY);
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.CAPTURE;
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

    public static CaptureGatewayRequest valueOf(ChargeEntity charge) {
        return new CaptureGatewayRequest(charge);
    }
}
