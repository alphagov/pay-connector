package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;

public class Card3dsResponseAuthService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeService chargeService;
    private final CardAuthoriseBaseService cardAuthoriseBaseService;
    private final PaymentProviders providers;
    private final MetricRegistry metricRegistry;

    @Inject
    public Card3dsResponseAuthService(PaymentProviders providers,
                                      ChargeService chargeService,
                                      CardAuthoriseBaseService cardAuthoriseBaseService,
                                      Environment environment
    ) {
        this.providers = providers;
        this.chargeService = chargeService;
        this.cardAuthoriseBaseService = cardAuthoriseBaseService;
        this.metricRegistry = environment.metrics();
    }

    public Gateway3DSAuthorisationResponse process3DSecureAuthorisation(String chargeId, Auth3dsDetails auth3DsDetails) {
        return cardAuthoriseBaseService.executeAuthorise(chargeId, () -> {

            final ChargeEntity charge = chargeService.lockChargeForProcessing(chargeId, AUTHORISATION_3DS);
            Gateway3DSAuthorisationResponse gateway3DSAuthorisationResponse = providers
                    .byName(charge.getPaymentGatewayName())
                    .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(charge, auth3DsDetails));
            processGateway3DSecureResponse(
                    charge.getExternalId(),
                    ChargeStatus.fromString(charge.getStatus()),
                    gateway3DSAuthorisationResponse
            );

            return gateway3DSAuthorisationResponse;
        });
    }

    private void processGateway3DSecureResponse(
            String chargeExternalId,
            ChargeStatus oldChargeStatus,
            Gateway3DSAuthorisationResponse operationResponse
    ) {
        Optional<String> transactionId = operationResponse.getTransactionId();

        ChargeEntity updatedCharge = chargeService.updateChargePost3dsAuthorisation(
                chargeExternalId,
                operationResponse.getMappedChargeStatus(),
                AUTHORISATION_3DS,
                transactionId
        );
        logAuthorisation(updatedCharge, oldChargeStatus);
        emitAuthorisationMetric(updatedCharge);
    }

    private void logAuthorisation(
            ChargeEntity updatedCharge,
            ChargeStatus oldChargeStatus
    ) {
        logger.info("{} for {} ({} {}) for {} ({}) .'. {} -> {}",
                "3DS response authorisation",
                updatedCharge.getExternalId(),
                updatedCharge.getPaymentGatewayName().getName(),
                updatedCharge.getGatewayTransactionId(),
                updatedCharge.getGatewayAccount().getAnalyticsId(),
                updatedCharge.getGatewayAccount().getId(),
                oldChargeStatus,
                updatedCharge.getStatus()
        );
    }

    private void emitAuthorisationMetric(ChargeEntity charge) {
        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.%s.result.%s",
                "authorise-3ds",
                charge.getGatewayAccount().getGatewayName(),
                charge.getGatewayAccount().getType(),
                charge.getGatewayAccount().getId(),
                charge.getStatus())
        ).inc();
    }
}
