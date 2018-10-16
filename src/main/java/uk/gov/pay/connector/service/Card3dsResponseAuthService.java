package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevents.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;

public class Card3dsResponseAuthService extends CardAuthoriseBaseService<Auth3dsDetails> {

    @Inject
    public Card3dsResponseAuthService(ChargeDao chargeDao,
                                      ChargeEventDao chargeEventDao,
                                      PaymentProviders providers,
                                      CardExecutorService cardExecutorService,
                                      Environment environment) {
        super(chargeDao, chargeEventDao, providers, cardExecutorService, environment);
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, Auth3dsDetails auth3DsDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise3dsResponse(Auth3dsResponseGatewayRequest.valueOf(chargeEntity, auth3DsDetails));
    }

    @Transactional
    public ChargeEntity preOperation(String chargeId, Auth3dsDetails auth3DsDetails) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> preOperation(chargeEntity, OperationType.AUTHORISATION_3DS, getLegalStates(), AUTHORISATION_3DS_READY))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    @Transactional
    public GatewayResponse<BaseAuthoriseResponse> postOperation(String chargeId,
                                                                Auth3dsDetails auth3DsDetails,
                                                                GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {

            ChargeStatus status = operationResponse.getBaseResponse()
                    .map(BaseAuthoriseResponse::authoriseStatus)
                    .map(BaseAuthoriseResponse.AuthoriseStatus::getMappedChargeStatus)
                    .orElse(ChargeStatus.AUTHORISATION_ERROR);

            String transactionId = operationResponse.getBaseResponse()
                    .map(BaseAuthoriseResponse::getTransactionId).orElse("");

            logger.info("3DS response authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    operationResponse, chargeEntity.getStatus(), status);

            GatewayAccountEntity account = chargeEntity.getGatewayAccount();

            metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise-3ds.result.%s", account.getGatewayName(), account.getType(), account.getId(), status.toString())).inc();

            chargeEntity.setStatus(status);

            if (!isBlank(transactionId)) {
                setGatewayTransactionId(chargeEntity, transactionId);
            }
            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
            return operationResponse;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    @Override
    protected List<ChargeStatus> getLegalStates() {
        return ImmutableList.of(
                AUTHORISATION_3DS_REQUIRED
        );
    }
}
