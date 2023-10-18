package uk.gov.pay.connector.queue.tasks.handlers;

import com.google.inject.Inject;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.model.RetryPaymentOrRefundEmailTaskData;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;

public class RetryPaymentOrRefundEmailTaskHandler {

    private final ChargeService chargeService;
    private final RefundService refundService;
    private final GatewayAccountService gatewayAccountService;
    private final UserNotificationService userNotificationService;

    @Inject
    public RetryPaymentOrRefundEmailTaskHandler(ChargeService chargeService,
                                                RefundService refundService,
                                                GatewayAccountService gatewayAccountService,
                                                UserNotificationService userNotificationService) {
        this.chargeService = chargeService;
        this.refundService = refundService;
        this.gatewayAccountService = gatewayAccountService;
        this.userNotificationService = userNotificationService;
    }

    public void process(RetryPaymentOrRefundEmailTaskData retryPaymentOrRefundEmailTaskData) {
        if (retryPaymentOrRefundEmailTaskData.getEmailNotificationType() == PAYMENT_CONFIRMED) {
            Charge charge = getCharge(retryPaymentOrRefundEmailTaskData.getResourceExternalId());
            GatewayAccountEntity gatewayAccountEntity = getGatewayAccountEntity(charge.getGatewayAccountId());

            userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, gatewayAccountEntity, false);
        } else if (retryPaymentOrRefundEmailTaskData.getEmailNotificationType() == REFUND_ISSUED) {
            RefundEntity refundEntity = getRefund(retryPaymentOrRefundEmailTaskData.getResourceExternalId());
            Charge charge = getCharge(refundEntity.getChargeExternalId());
            GatewayAccountEntity gatewayAccountEntity = getGatewayAccountEntity(charge.getGatewayAccountId());

            userNotificationService.sendRefundIssuedEmailSynchronously(charge, gatewayAccountEntity, refundEntity, false);
        }
    }

    private GatewayAccountEntity getGatewayAccountEntity(Long gatewayAccountId) {
        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    private RefundEntity getRefund(String refundExternalId) {
        return refundService.findRefundByExternalId(refundExternalId)
                .orElseThrow(() -> new RefundNotFoundRuntimeException(refundExternalId));
    }

    private Charge getCharge(String externalId) {
        return chargeService.findCharge(externalId)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }
}
