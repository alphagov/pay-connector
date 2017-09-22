package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.CancelServiceFunctions.*;
import static uk.gov.pay.connector.service.StatusFlow.EXPIRE_FLOW;

public class ChargeExpiryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String EXPIRY_SUCCESS = "expiry-success";
    private static final String EXPIRY_FAILED = "expiry-failed";

    public static final List<ChargeStatus> EXPIRABLE_STATUSES = ImmutableList.of(
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_SUCCESS);

    private final ChargeDao chargeDao;
    private final PaymentProviders providers;
    private Provider<TransactionFlow> transactionFlowProvider;

    @Inject
    public ChargeExpiryService(ChargeDao chargeDao,
                               PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
    }

    public Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<Boolean, List<ChargeEntity>> chargesToProcessExpiry = charges
                .stream()
                .collect(Collectors.partitioningBy(chargeEntity ->
                        ChargeStatus.AUTHORISATION_SUCCESS.getValue().equals(chargeEntity.getStatus())));

        int expiredSuccess = expireChargesWithCancellationNotRequired(chargesToProcessExpiry.get(Boolean.FALSE));
        Pair<Integer, Integer> expireWithCancellationResult = expireChargesWithGatewayCancellation(chargesToProcessExpiry.get(Boolean.TRUE));

        return ImmutableMap.of(EXPIRY_SUCCESS, expiredSuccess + expireWithCancellationResult.getLeft(),
                EXPIRY_FAILED, expireWithCancellationResult.getRight());
    }

    private int expireChargesWithCancellationNotRequired(List<ChargeEntity> nonAuthSuccessCharges) {
        List<ChargeEntity> processedEntities = nonAuthSuccessCharges
                .stream().map(chargeEntity -> transactionFlowProvider.get()
                        .executeNext(changeStatusTo(chargeDao, chargeEntity.getExternalId(), EXPIRED, Optional.empty()))
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
                    .executeNext(prepareForTerminate(chargeDao, chargeEntity.getExternalId(), EXPIRE_FLOW))
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
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            GatewayResponse gatewayResponse = context.get(GatewayResponse.class);
            ChargeStatus status = determineTerminalState(chargeEntity, gatewayResponse, EXPIRE_FLOW);
            logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), status);
            chargeEntity.setStatus(status);
            chargeDao.notifyStatusHasChanged(chargeEntity, Optional.empty());
            return chargeEntity;
        };
    }

    private void logUnsuccessfulResponseReasons(ChargeEntity chargeEntity, GatewayResponse gatewayResponse) {
        gatewayResponse.getGatewayError().ifPresent(error ->
                logger.error("Gateway error while cancelling the Charge - charge_external_id={}, gateway_error={}",
                        chargeEntity.getExternalId(), error));
    }
}
