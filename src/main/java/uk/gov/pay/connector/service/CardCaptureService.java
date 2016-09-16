package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;

import javax.inject.Inject;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation<BaseCaptureResponse> {

    private static ChargeStatus[] legalStatuses = new ChargeStatus[]{
            AUTHORISATION_SUCCESS
    };

    private final UserNotificationService userNotificationService;


    @Inject
    public CardCaptureService(ChargeDao chargeDao, PaymentProviders providers, ConfirmationDetailsService confirmationDetailsService, UserNotificationService userNotificationService) {
        super(chargeDao, providers, confirmationDetailsService);
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
        logger.info(format("Card capture request sent - charge_external_id=%s, transaction_id=%s, provider=%s, status=%s",
                chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), chargeEntity.getGatewayAccount().getGatewayName(),fromString(chargeEntity.getStatus())));
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
            status = capturePostOperationSuccessOf(chargeEntity.getPaymentGatewayName());
        }

        String transactionId = operationResponse.getBaseResponse()
                .map(BaseCaptureResponse::getTransactionId).orElse("");

        logger.info("Card capture response received - charge_external_id={}, transaction_id={}, status={}",
                chargeEntity.getExternalId(), transactionId, status);

        reloadedCharge.setStatus(status);

        if (StringUtils.isBlank(transactionId)) {
            logger.warn("Card capture response received with no transaction id. - charge_external_id={}", reloadedCharge.getExternalId());
        }

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        if (operationResponse.isSuccessful()) {
            userNotificationService.notifyPaymentSuccessEmail(reloadedCharge);
        }
        return operationResponse;
    }

    private ChargeStatus capturePostOperationSuccessOf(PaymentGatewayName paymentGatewayName) {
        if (paymentGatewayName == PaymentGatewayName.SANDBOX) {
            return CAPTURED;
        }
        return CAPTURE_SUBMITTED;
    }
}
