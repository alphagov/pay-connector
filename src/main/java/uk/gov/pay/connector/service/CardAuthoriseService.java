package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.Card3dsDao;
import uk.gov.pay.connector.dao.CardDao;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.exception.ChargeExpiredRuntimeException;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.AddressEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.Card3dsEntity;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.model.domain.NumbersInStringsSanitizer.sanitize;

public class CardAuthoriseService {

    private static final Logger LOG = LoggerFactory.getLogger(CardAuthoriseService.class);
    protected final ChargeDao chargeDao;
    protected final ChargeEventDao  chargeEventDao;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ChargeStatusUpdater chargeStatusUpdater;
    private final CardTypeDao cardTypeDao;
    private final CardDao cardDao;
    private final Auth3dsDetailsFactory auth3dsDetailsFactory;
    private final Card3dsDao card3dsDao;
    private final PaymentRequestDao paymentRequestDao;
    private final CardExecutorService cardExecutorService;
    private final PaymentProviders providers;
    protected MetricRegistry metricRegistry;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao,
                                ChargeEventDao chargeEventDao,
                                CardTypeDao cardTypeDao,
                                CardDao cardDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                Auth3dsDetailsFactory auth3dsDetailsFactory,
                                Environment environment,
                                Card3dsDao card3dsDao, PaymentRequestDao paymentRequestDao, ChargeStatusUpdater chargeStatusUpdater) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.metricRegistry = environment.metrics();
        this.chargeStatusUpdater = chargeStatusUpdater;
        this.cardExecutorService = cardExecutorService;
        this.cardTypeDao = cardTypeDao;
        this.cardDao = cardDao;
        this.auth3dsDetailsFactory = auth3dsDetailsFactory;
        this.card3dsDao = card3dsDao;
        this.paymentRequestDao = paymentRequestDao;
    }

    @Transactional
    public ChargeEntity preOperation(String chargeId, AuthCardDetails authCardDetails) {

        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {

            String cardBrand = authCardDetails.getCardBrand();

            if (!chargeEntity.getGatewayAccount().isRequires3ds() && cardBrandRequires3ds(cardBrand)) {

                chargeEntity.setStatus(AUTHORISATION_ABORTED);

                logger.error("AuthCardDetails authorisation failed pre operation. Card brand requires 3ds but Gateway account has 3ds disabled - charge_external_id={}, operation_type={}, card_brand={}",
                        chargeEntity.getExternalId(), OperationType.AUTHORISATION.getValue(), cardBrand);

                chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
                chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), ChargeStatus.fromString(chargeEntity.getStatus()));
            } else {
                preOperation(chargeEntity, OperationType.AUTHORISATION, getLegalStates(), AUTHORISATION_READY);

                Optional<PaymentRequestEntity> paymentRequestEntity = paymentRequestDao.findByExternalId(chargeEntity.getExternalId());

                getPaymentProviderFor(chargeEntity).generateTransactionId().ifPresent(transactionIdValue -> {
                    setGatewayTransactionId(chargeEntity, transactionIdValue, paymentRequestEntity);
                });
            }

            return chargeEntity;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private boolean cardBrandRequires3ds(String cardBrand) {
        List<CardTypeEntity> cardTypes = cardTypeDao.findByBrand(cardBrand).stream()
                .filter(cardTypeEntity -> cardTypeEntity.getBrand().equals(cardBrand))
                .collect(Collectors.toList());
        return cardTypes.stream().anyMatch(CardTypeEntity::isRequires3ds);
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationGatewayRequest.valueOf(chargeEntity, authCardDetails));
    }

    protected List<ChargeStatus> getLegalStates() {
        return ImmutableList.of(
                ENTERING_CARD_DETAILS
        );
    }

    @Transactional
    public GatewayResponse<BaseAuthoriseResponse> postOperation(String chargeId, AuthCardDetails authCardDetails, GatewayResponse<BaseAuthoriseResponse> operationResponse) {

        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {

            ChargeStatus status = operationResponse.getBaseResponse()
                    .map(BaseAuthoriseResponse::authoriseStatus)
                    .map(BaseAuthoriseResponse.AuthoriseStatus::getMappedChargeStatus)
                    .orElseGet(() -> operationResponse.getGatewayError()
                            .map(this::mapError)
                            .orElse(ChargeStatus.AUTHORISATION_ERROR));

            String transactionId = operationResponse.getBaseResponse()
                    .map(BaseAuthoriseResponse::getTransactionId).orElse("");

            operationResponse.getSessionIdentifier().ifPresent(chargeEntity::setProviderSessionId);

            logger.info("Authorisation for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(),
                    StringUtils.isNotBlank(transactionId) ? transactionId : "missing transaction ID",
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    operationResponse, chargeEntity.getStatus(), status);

            GatewayAccountEntity account = chargeEntity.getGatewayAccount();

            metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise.result.%s", account.getGatewayName(), account.getType(), account.getId(), status.toString())).inc();

            chargeEntity.setStatus(status);
            operationResponse.getBaseResponse().ifPresent(response -> auth3dsDetailsFactory.create(response).ifPresent(chargeEntity::set3dsDetails));

            chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), status);
            Optional<PaymentRequestEntity> paymentRequestEntity = paymentRequestDao.findByExternalId(chargeEntity.getExternalId());
            if (StringUtils.isBlank(transactionId)) {
                logger.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}", chargeEntity.getExternalId());
            } else {
                setGatewayTransactionId(chargeEntity, transactionId, paymentRequestEntity);
            }

            CardDetailsEntity detailsEntity = buildCardDetailsEntity(authCardDetails);
            chargeEntity.setCardDetails(detailsEntity);

            if (paymentRequestEntity.isPresent()) {
                CardEntity cardEntity = CardEntity.from(detailsEntity, paymentRequestEntity.get().getChargeTransaction());
                cardDao.persist(cardEntity);
            } else {
                logger.error("Cannot find payment request with external ID {} — this is a bug: the card details will not be saved in the cards table");
            }

            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
            persistCard3ds(chargeEntity);
            logger.info("Stored confirmation details for charge - charge_external_id={}",
                    chargeEntity.getExternalId());
            return operationResponse;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private ChargeStatus mapError(GatewayError gatewayError) {
        switch (gatewayError.getErrorType()) {
            case GENERIC_GATEWAY_ERROR:
                return AUTHORISATION_ERROR;
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return AUTHORISATION_TIMEOUT;
            default:
                return AUTHORISATION_UNEXPECTED_ERROR;
        }
    }

    private CardDetailsEntity buildCardDetailsEntity(AuthCardDetails authCardDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(sanitize(authCardDetails.getCardBrand()));
        detailsEntity.setBillingAddress(new AddressEntity(authCardDetails.getAddress()));
        detailsEntity.setCardHolderName(sanitize(authCardDetails.getCardHolder()));
        detailsEntity.setExpiryDate(authCardDetails.getEndDate());
        detailsEntity.setLastDigitsCardNumber(StringUtils.right(authCardDetails.getCardNo(), 4));
        return detailsEntity;
    }

    private void persistCard3ds(ChargeEntity chargeEntity){
        if(chargeEntity.get3dsDetails() != null) {
            Card3dsEntity card3dsEntity = Card3dsEntity.from(chargeEntity);
            card3dsDao.persist(card3dsEntity);
        }
    }

    public GatewayResponse doAuthorise(String chargeId, AuthCardDetails gatewayAuthRequest) {

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
                throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
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
