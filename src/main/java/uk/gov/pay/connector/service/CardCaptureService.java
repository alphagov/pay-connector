package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation<BaseCaptureResponse> {

    private static ChargeStatus[] legalStatuses = new ChargeStatus[]{
            AUTHORISATION_SUCCESS
    };

    private final UserNotificationService userNotificationService;


    @Inject
    public CardCaptureService(ChargeDao chargeDao, PaymentProviders providers, UserNotificationService userNotificationService, Environment environment) {
        super(chargeDao, providers, environment);
        this.userNotificationService = userNotificationService;
    }

    public GatewayResponse<BaseCaptureResponse> doCapture(String chargeId) {
        return chargeDao
                .findByExternalId(chargeId)
                .map(TransactionalGatewayOperation.super::executeGatewayOperationFor)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    @Transactional
    @Override
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        return preOperation(chargeEntity, CardService.OperationType.CAPTURE, legalStatuses, ChargeStatus.CAPTURE_READY);
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
        ChargeStatus status = CAPTURE_ERROR;

        if (operationResponse.isSuccessful()) {
            status = CAPTURE_SUBMITTED;
        }

        String transactionId = operationResponse.getBaseResponse()
                .map(BaseCaptureResponse::getTransactionId).orElse("");

        logger.info("Card capture response received - charge_external_id={}, operation_type={}, transaction_id={}, status={}",
                chargeEntity.getExternalId(), OperationType.CAPTURE.getValue(), transactionId, status);

        reloadedCharge.setStatus(status);
        //update the charge with the new transaction id from gateway, if present.
        if (isBlank(transactionId)) {
            logger.warn("Card capture response received with no transaction id. - charge_external_id={}", reloadedCharge.getExternalId());
        }

        GatewayAccountEntity account = chargeEntity.getGatewayAccount();

        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.capture.result.%s", account.getGatewayName(), account.getType(), account.getId(), status.toString())).inc();

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
}
