package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation<BaseCaptureResponse> {

    private static List<ChargeStatus> legalStatuses = ImmutableList.of(
            AUTHORISATION_SUCCESS,
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY
    );

    private final UserNotificationService userNotificationService;


    @Inject
    public CardCaptureService(ChargeDao chargeDao, PaymentProviders providers, UserNotificationService userNotificationService, Environment environment) {
        super(chargeDao, providers, environment);
        this.userNotificationService = userNotificationService;
    }

    public GatewayResponse<BaseCaptureResponse> doCapture(String externalId) {
        return chargeDao
                .findByExternalId(externalId)
                .map(TransactionalGatewayOperation.super::executeGatewayOperationFor)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    @Transactional
    @Override
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        //TODO PP-2626 As part of refactoring work. Merging operation is not done inside preOperation anymore. This will be (if possible) removed.
        chargeDao.merge(chargeEntity);
        return preOperation(chargeEntity, CardService.OperationType.CAPTURE, legalStatuses, ChargeStatus.CAPTURE_READY);
    }

    @Transactional
    public ChargeEntity markChargeAsCaptureApproved(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {
            if (!AUTHORISATION_SUCCESS.getValue().equals(charge.getStatus())) {
                logger.error("Charge is not in the expect state of AUTHORISATION_SUCCESS to be marked as CAPTURE_APPROVED [charge_external_id={}, charge_status={}]",
                        charge.getExternalId(), charge.getStatus());
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            logger.info("CAPTURE_APPROVED for charge [charge_external_id={}]", externalId);
            charge.setStatus(CAPTURE_APPROVED);
            return chargeDao.mergeAndNotifyStatusHasChanged(charge, Optional.empty());
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    @Transactional
    public ChargeEntity markChargeAsCaptureError(ChargeEntity chargeEntity) {
        logger.error("CAPTURE_ERROR for charge [charge_external_id={}] - reached maximum number of capture attempts",
                chargeEntity.getExternalId());

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(CAPTURE_ERROR);
        return chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.empty());
    }

    @Override
    public GatewayResponse<BaseCaptureResponse> operation(ChargeEntity chargeEntity) {
        return getPaymentProviderFor(chargeEntity)
                .capture(CaptureGatewayRequest.valueOf(chargeEntity));
    }

    @Transactional
    @Override
    public GatewayResponse<BaseCaptureResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse<BaseCaptureResponse> operationResponse) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        ChargeStatus nextStatus = determineNextStatus(operationResponse);

        String transactionId = operationResponse.getBaseResponse()
                .map(BaseCaptureResponse::getTransactionId).orElse("");

        logger.info("Card capture response received - charge_external_id={}, operation_type={}, transaction_id={}, status={}",
                chargeEntity.getExternalId(), OperationType.CAPTURE.getValue(), transactionId, nextStatus);

        reloadedCharge.setStatus(nextStatus);
        //update the charge with the new transaction id from gateway, if present.
        if (isBlank(transactionId)) {
            logger.warn("Card capture response received with no transaction id. - charge_external_id={}", reloadedCharge.getExternalId());
        }

        GatewayAccountEntity account = chargeEntity.getGatewayAccount();

        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.capture.result.%s", account.getGatewayName(), account.getType(), account.getId(), nextStatus.toString())).inc();

        reloadedCharge = chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.empty());

        if (operationResponse.isSuccessful()) {
            userNotificationService.notifyPaymentSuccessEmail(reloadedCharge);
        }

        //for sandbox, immediately move from CAPTURE_SUBMITTED to CAPTURED, as there will be no external notification
        if (chargeEntity.getPaymentGatewayName() == PaymentGatewayName.SANDBOX) {
            reloadedCharge.setStatus(CAPTURED);
            chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.of(ZonedDateTime.now()));
        }

        return operationResponse;
    }

    private ChargeStatus determineNextStatus(GatewayResponse<BaseCaptureResponse> operationResponse) {
        if (operationResponse.isSuccessful()) {
            return CAPTURE_SUBMITTED;
        } else {
            return operationResponse.getGatewayError()
                    .map(timeoutError -> CAPTURE_APPROVED_RETRY)
                    .orElse(CAPTURE_ERROR);
        }
    }
}
