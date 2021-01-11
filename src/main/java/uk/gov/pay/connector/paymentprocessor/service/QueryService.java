package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.pay.logging.LoggingKeys.PROVIDER;

public class QueryService {
    private final PaymentProviders providers;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryService(PaymentProviders providers) {
        this.providers = providers;
    }
    
    public ChargeQueryResponse getChargeGatewayStatus(Charge charge, GatewayAccountEntity gatewayAccountEntity) throws GatewayException {
        return providers.byName(PaymentGatewayName.valueFrom(gatewayAccountEntity.getGatewayName()))
                .queryPaymentStatus(charge, gatewayAccountEntity);
    }   
    public ChargeQueryResponse getChargeGatewayStatus(ChargeEntity charge) throws GatewayException {
        return providers.byName(PaymentGatewayName.valueFrom(charge.getGatewayAccount().getGatewayName()))
                .queryPaymentStatus(Charge.from(charge), charge.getGatewayAccount());
    }

    public boolean canQueryChargeGatewayStatus(PaymentGatewayName paymentGatewayName) {
        return providers.byName(paymentGatewayName).canQueryPaymentStatus();
    }

    public Optional<ChargeStatus> getMappedGatewayStatus(ChargeEntity charge) {
        try {
            return getChargeGatewayStatus(charge).getMappedStatus();
        } catch (WebApplicationException | UnsupportedOperationException | GatewayException | IllegalArgumentException e) {
            logger.info(String.format("Unable to retrieve status for charge %s for gateway %s: %s",
                    charge.getExternalId(),
                    charge.getPaymentGatewayName().toString(),
                    e.getMessage()),
                    kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                    kv(PROVIDER, charge.getPaymentGatewayName().toString()));
            return Optional.empty();
        }
    }
    
    public boolean isTerminableWithGateway(ChargeEntity charge) {
        try {
            return getChargeGatewayStatus(charge)
                    .getMappedStatus()
                    .map(chargeStatus -> !chargeStatus.toExternal().isFinished())
                    .orElse(false);
        } catch (WebApplicationException | UnsupportedOperationException | GatewayException | IllegalArgumentException e) {
            logger.info(String.format("Unable to retrieve status for charge %s for gateway %s: %s",
                    charge.getExternalId(),
                    charge.getPaymentGatewayName().toString(),
                    e.getMessage()),
                    kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                    kv(PROVIDER, charge.getPaymentGatewayName().toString()));
            return false;
        }
    }
}
