package uk.gov.pay.connector.card.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class QueryService {
    private final PaymentProviders providers;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public QueryService(PaymentProviders providers, GatewayAccountCredentialsService gatewayAccountCredentialsService) {
        this.providers = providers;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
    }
    
    public ChargeQueryResponse getChargeGatewayStatus(Charge charge, GatewayAccountEntity gatewayAccountEntity) throws GatewayException {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = findGatewayAccountCredentialsEntityFromCharge(charge, gatewayAccountEntity);

        return providers.byName(PaymentGatewayName.valueFrom(charge.getPaymentGatewayName()))
                .queryPaymentStatus(ChargeQueryGatewayRequest.valueOf(charge, gatewayAccountEntity, gatewayAccountCredentialsEntity));
    }   
    public ChargeQueryResponse getChargeGatewayStatus(ChargeEntity chargeEntity) throws GatewayException {
        Charge charge = Charge.from(chargeEntity);
        GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = findGatewayAccountCredentialsEntityFromCharge(charge, gatewayAccountEntity);

        return providers.byName(chargeEntity.getPaymentGatewayName())
                .queryPaymentStatus(ChargeQueryGatewayRequest.valueOf(charge, gatewayAccountEntity, gatewayAccountCredentialsEntity));
    }

    private GatewayAccountCredentialsEntity findGatewayAccountCredentialsEntityFromCharge(Charge charge, GatewayAccountEntity gatewayAccountEntity) {
        return gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)
                .orElseThrow(() -> new GatewayAccountCredentialsNotFoundException("Unable to find gateway account credentials to use to refund charge."));
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
