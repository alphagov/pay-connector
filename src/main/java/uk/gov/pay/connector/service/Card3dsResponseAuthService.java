package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.exception.ChargeExpiredRuntimeException;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

public class Card3dsResponseAuthService {
    private static final Logger LOG = LoggerFactory.getLogger(Card3dsResponseAuthService.class);
    protected final ChargeDao chargeDao;
    protected final ChargeEventDao  chargeEventDao;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ChargeStatusUpdater chargeStatusUpdater;
    private final PaymentRequestDao paymentRequestDao;
    private final CardExecutorService cardExecutorService;
    private final PaymentProviders providers;
    protected MetricRegistry metricRegistry;

    @Inject
    public Card3dsResponseAuthService(ChargeDao chargeDao,
                                      ChargeEventDao chargeEventDao,
                                      PaymentProviders providers,
                                      CardExecutorService cardExecutorService,
                                      Environment environment, PaymentRequestDao paymentRequestDao, ChargeStatusUpdater chargeStatusUpdater) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.metricRegistry = environment.metrics();
        this.chargeStatusUpdater = chargeStatusUpdater;
        this.cardExecutorService = cardExecutorService;
        this.paymentRequestDao = paymentRequestDao;
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
            chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), status);
            Optional<PaymentRequestEntity> paymentRequestEntity = paymentRequestDao.findByExternalId(chargeEntity.getExternalId());

            if (StringUtils.isBlank(transactionId)) {
                logger.warn("Auth3DSDetails authorisation response received with no transaction id. -  charge_external_id={}", chargeId);
            } else {
                setGatewayTransactionId(chargeEntity, transactionId, paymentRequestEntity);
            }

            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
            return operationResponse;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    protected List<ChargeStatus> getLegalStates() {
        return ImmutableList.of(
                AUTHORISATION_3DS_REQUIRED
        );
    }

    public GatewayResponse doAuthorise(String chargeId, Auth3dsDetails gatewayAuthRequest) {

        Supplier authorisationSupplier = () -> {
            ChargeEntity charge;
            try {
                charge = preOperation(chargeId, gatewayAuthRequest);
                if (charge.hasStatus(ChargeStatus.AUTHORISATION_ABORTED)) {
                    throw new ConflictRuntimeException(chargeId, "configuration mismatch");
                }
            } catch (OptimisticLockException e) {
                LOG.info("OptimisticLockException in doAuthorise for charge external_id=" + chargeId);
                throw new ConflictRuntimeException(chargeId);
            }
             GatewayResponse<BaseAuthoriseResponse> operationResponse = operation(charge, gatewayAuthRequest);
            return postOperation(chargeId, gatewayAuthRequest, operationResponse);
        };

        Pair<CardExecutorService.ExecutionStatus, GatewayResponse> executeResult = cardExecutorService.execute(authorisationSupplier);

        switch (executeResult.getLeft()) {
            case COMPLETED:
                return executeResult.getRight();
            case IN_PROGRESS:
                throw new OperationAlreadyInProgressRuntimeException(CardCaptureService.OperationType.AUTHORISATION.getValue(), chargeId);
            default:
                throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
        }
    }

    protected void setGatewayTransactionId(ChargeEntity chargeEntity, String transactionId, Optional<PaymentRequestEntity> paymentRequestEntity) {
        chargeEntity.setGatewayTransactionId(transactionId);
        paymentRequestEntity.ifPresent(paymentRequest -> {
            if (paymentRequest.hasChargeTransaction()) {
                paymentRequest.getChargeTransaction().setGatewayTransactionId(transactionId);
            }
        });
    }

    public ChargeEntity preOperation(ChargeEntity chargeEntity, OperationType operationType, List<ChargeStatus> legalStatuses, ChargeStatus lockingStatus) {

        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

        logger.info("Card pre-operation - charge_external_id={}, charge_status={}, account_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                chargeEntity.getExternalId(),
                fromString(chargeEntity.getStatus()),
                gatewayAccount.getId(),
                chargeEntity.getAmount(),
                operationType.getValue(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType(),
                lockingStatus);

        if (chargeEntity.hasStatus(ChargeStatus.EXPIRED)) {
            throw new ChargeExpiredRuntimeException(operationType.getValue(), chargeEntity.getExternalId());
        }

        if (!chargeEntity.hasStatus(legalStatuses)) {
            if (chargeEntity.hasStatus(lockingStatus)) {
                throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), chargeEntity.getExternalId());
            }
            logger.error("Charge is not in a legal status to do the pre-operation - charge_external_id={}, status={}, legal_states={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), getLegalStatusNames(legalStatuses));
            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }

        chargeEntity.setStatus(lockingStatus);
        chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), lockingStatus);
        return chargeEntity;
    }

    private String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    public PaymentProviderOperations getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }

    public enum OperationType {
        CAPTURE("Capture"),
        AUTHORISATION("Authorisation"),
        AUTHORISATION_3DS("3D Secure Response Authorisation"),
        CANCELLATION("Cancellation");

        private String value;

        OperationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
