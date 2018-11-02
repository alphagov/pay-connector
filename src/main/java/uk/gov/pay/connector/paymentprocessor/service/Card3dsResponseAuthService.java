package uk.gov.pay.connector.paymentprocessor.service;

import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Optional;

public class Card3dsResponseAuthService extends CardAuthoriseBaseService<Auth3dsDetails> {

    @Inject
    public Card3dsResponseAuthService(ChargeDao chargeDao,
                                      ChargeEventDao chargeEventDao,
                                      PaymentProviders providers,
                                      CardExecutorService cardExecutorService,
                                      ChargeService chargeService,
                                      Environment environment) {
        super(chargeDao, chargeEventDao, providers, cardExecutorService, chargeService, environment);
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, Auth3dsDetails auth3DsDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(chargeEntity, auth3DsDetails));
    }

    @Transactional
    public ChargeEntity preOperation(String chargeId, Auth3dsDetails auth3DsDetails) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> chargeService.lockChargeForProcessing(chargeEntity, OperationType.AUTHORISATION_3DS))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    @Transactional
    public void processGatewayAuthorisationResponse(String chargeId, Auth3dsDetails auth3DsDetails, GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        chargeDao.findByExternalId(chargeId).map(chargeEntity -> {

            Optional<String> transactionId = operationResponse.getBaseResponse().map(BaseAuthoriseResponse::getTransactionId);
            ChargeStatus status = determineChargeStatus(operationResponse.getBaseResponse(), Optional.empty());

            logger.info("3DS response authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    operationResponse, chargeEntity.getStatus(), status);

            chargeService.updateChargePost3dsAuthorisation(status, chargeEntity, transactionId);

            metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise-3ds.result.%s",
                    chargeEntity.getGatewayAccount().getGatewayName(),
                    chargeEntity.getGatewayAccount().getType(),
                    chargeEntity.getGatewayAccount().getId(),
                    status.toString())).inc();

            return chargeEntity;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

}
