package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Provider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.transaction.TransactionContext;
import uk.gov.pay.connector.charge.service.transaction.TransactionFlow;
import uk.gov.pay.connector.charge.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.service.CancelServiceFunctions.changeStatusTo;
import static uk.gov.pay.connector.charge.service.CancelServiceFunctions.doGatewayCancel;
import static uk.gov.pay.connector.charge.service.CancelServiceFunctions.prepareForTerminate;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;

public class ChargeExpiryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String EXPIRY_SUCCESS = "expiry-success";
    private static final String EXPIRY_FAILED = "expiry-failed";
    private static final int ONE_HOUR_AND_A_HALF = 5400;
    private static final int FORTY_EIGHT_HOURS = 172800;
    private static final String CHARGE_EXPIRY_WINDOW = "CHARGE_EXPIRY_WINDOW_SECONDS";
    private static final String AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW = "AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW";

    public static final List<ChargeStatus> EXPIRABLE_REGULAR_STATUSES = ImmutableList.of(
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_SUCCESS);

    public static final List<ChargeStatus> EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS = ImmutableList.of(AWAITING_CAPTURE_REQUEST);

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final PaymentProviders providers;
    private final Provider<TransactionFlow> transactionFlowProvider;
    static final List<ChargeStatus> GATEWAY_CANCELLABLE_STATUSES = Arrays.asList(
            AUTHORISATION_SUCCESS,
            AWAITING_CAPTURE_REQUEST
    );

    @Inject
    public ChargeExpiryService(ChargeDao chargeDao,
                               ChargeEventDao chargeEventDao,
                               PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
    }

    public Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<Boolean, List<ChargeEntity>> chargesToProcessExpiry = charges
                .stream()
                .collect(Collectors.partitioningBy(chargeEntity ->
                        GATEWAY_CANCELLABLE_STATUSES.contains(ChargeStatus.fromString(chargeEntity.getStatus())))
                );

        int expiredSuccess = expireChargesWithCancellationNotRequired(chargesToProcessExpiry.get(Boolean.FALSE));
        Pair<Integer, Integer> expireWithCancellationResult = expireChargesWithGatewayCancellation(chargesToProcessExpiry.get(Boolean.TRUE));

        return ImmutableMap.of(EXPIRY_SUCCESS, expiredSuccess + expireWithCancellationResult.getLeft(),
                EXPIRY_FAILED, expireWithCancellationResult.getRight());
    }

    public Map<String, Integer> sweepAndExpireCharges() {
        List<ChargeEntity> chargesToExpire = new ArrayList<>();
        chargesToExpire.addAll(chargeDao.findBeforeDateWithStatusIn(getExpiryDateForRegularCharges(), 
                EXPIRABLE_REGULAR_STATUSES));
        chargesToExpire.addAll(chargeDao.findBeforeDateWithStatusIn(getExpiryDateForAwaitingCaptureRequest(), 
                EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS));
        logger.info("Charges found for expiry - number_of_charges={}, since_date={}, awaiting_capture_date{}", 
                chargesToExpire.size(), getExpiryDateForRegularCharges(), getExpiryDateForAwaitingCaptureRequest());
        return expire(chargesToExpire);
    }

    private int expireChargesWithCancellationNotRequired(List<ChargeEntity> nonAuthSuccessCharges) {
        List<ChargeEntity> processedEntities = nonAuthSuccessCharges
                .stream().map(chargeEntity -> transactionFlowProvider.get()
                        .executeNext(changeStatusTo(chargeDao, chargeEventDao, chargeEntity.getExternalId(), EXPIRED, Optional.empty()))
                        .complete()
                        .get(ChargeEntity.class))
                .collect(Collectors.toList());

        return processedEntities.size();
    }

    private Pair<Integer, Integer> expireChargesWithGatewayCancellation(List<ChargeEntity> gatewayAuthorizedCharges) {

        final List<ChargeEntity> expireCancelled = newArrayList();
        final List<ChargeEntity> expireCancelFailed = newArrayList();
        final List<ChargeEntity> unexpectedStatuses = newArrayList();

        gatewayAuthorizedCharges.forEach(chargeEntity -> {
            ChargeEntity processedEntity = transactionFlowProvider.get()
                    .executeNext(prepareForTerminate(chargeDao, chargeEventDao, chargeEntity.getExternalId(), EXPIRE_FLOW))
                    .executeNext(doGatewayCancel(providers))
                    .executeNext(finishExpireCancel())
                    .complete().get(ChargeEntity.class);

            if (processedEntity == null) {
                //this shouldn't happen, but don't break the expiry job
                logger.error("Transaction context did not return a processed ChargeEntity during expiry of charge - charge_external_id={}",
                        chargeEntity.getExternalId());
            } else {
                if (EXPIRED.getValue().equals(processedEntity.getStatus())) {
                    expireCancelled.add(processedEntity);
                } else if (EXPIRE_CANCEL_FAILED.getValue().equals(processedEntity.getStatus())) {
                    expireCancelFailed.add(processedEntity);
                } else {
                    unexpectedStatuses.add(processedEntity); //this shouldn't happen, but still don't break the expiry job
                }
            }
        });

        unexpectedStatuses.forEach(chargeEntity ->
                logger.error("ChargeEntity returned with unexpected status during expiry - charge_external_id={}, status={}",
                        chargeEntity.getExternalId(), chargeEntity.getStatus())
        );

        return Pair.of(
                expireCancelled.size(),
                expireCancelFailed.size()
        );
    }

    private ChargeStatus determineTerminalState(ChargeEntity chargeEntity, GatewayResponse<BaseCancelResponse> cancelResponse, StatusFlow statusFlow) {
        if (!cancelResponse.isSuccessful()) {
            logUnsuccessfulResponseReasons(chargeEntity, cancelResponse);
        }

        return cancelResponse.getBaseResponse().map(response -> {
            switch (response.cancelStatus()) {
                case CANCELLED:
                    return statusFlow.getSuccessTerminalState();
                case SUBMITTED:
                    return statusFlow.getSubmittedState();
                default:
                    return statusFlow.getFailureTerminalState();
            }
        }).orElse(statusFlow.getFailureTerminalState());
    }

    private TransactionalOperation<TransactionContext, ChargeEntity> finishExpireCancel() {
        return context -> {
            String externalId = context.get(ChargeEntity.class).getExternalId();
            return chargeDao.findByExternalId(externalId).map(chargeEntity -> {
                GatewayResponse gatewayResponse = context.get(GatewayResponse.class);
                ChargeStatus status = determineTerminalState(chargeEntity, gatewayResponse, EXPIRE_FLOW);
                logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                        chargeEntity.getExternalId(), chargeEntity.getStatus(), status);
                chargeEntity.setStatus(status);
                chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
                return chargeEntity;
            }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
        };
    }

    private void logUnsuccessfulResponseReasons(ChargeEntity chargeEntity, GatewayResponse gatewayResponse) {
        gatewayResponse.getGatewayError().ifPresent(error ->
                logger.error("Gateway error while cancelling the Charge - charge_external_id={}, gateway_error={}",
                        chargeEntity.getExternalId(), error));
    }

    private ZonedDateTime getExpiryDateForRegularCharges() {
        int chargeExpiryWindowSeconds = ONE_HOUR_AND_A_HALF;
        if (StringUtils.isNotBlank(System.getenv(CHARGE_EXPIRY_WINDOW))) {
            chargeExpiryWindowSeconds = Integer.parseInt(System.getenv(CHARGE_EXPIRY_WINDOW));
        }
        logger.debug("Charge expiry window size in seconds: " + chargeExpiryWindowSeconds);
        return ZonedDateTime.now().minusSeconds(chargeExpiryWindowSeconds);
    }

    private ZonedDateTime getExpiryDateForAwaitingCaptureRequest() {
        int chargeExpiryWindowSeconds = FORTY_EIGHT_HOURS;
        if (StringUtils.isNotBlank(System.getenv(AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW))) {
            chargeExpiryWindowSeconds = Integer.parseInt(System.getenv(AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW));
        }
        logger.debug("Charge expiry window size for awaiting_delay_capture in seconds: " + chargeExpiryWindowSeconds);
        return ZonedDateTime.now().minusSeconds(chargeExpiryWindowSeconds);
    }
}
