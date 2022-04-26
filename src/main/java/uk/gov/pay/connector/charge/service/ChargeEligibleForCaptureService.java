package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;

public class ChargeEligibleForCaptureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeEligibleForCaptureService.class);

    private final ChargeService chargeService;
    private final ChargeDao chargeDao;
    private final CaptureQueue captureQueue;
    private final LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;
    private final UserNotificationService userNotificationService;

    @Inject
    public ChargeEligibleForCaptureService(ChargeService chargeService, ChargeDao chargeDao,
                                           LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService,
                                           CaptureQueue captureQueue, UserNotificationService userNotificationService) {
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
        this.captureQueue = captureQueue;
        this.linkPaymentInstrumentToAgreementService = linkPaymentInstrumentToAgreementService;
        this.userNotificationService = userNotificationService;
    }

    public ChargeEntity markChargeAsEligibleForCapture(String externalId) {
        ChargeEntity charge = update(externalId);

        if (!charge.isDelayedCapture()) {
            addChargeToCaptureQueue(charge);
            userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        }

        return charge;
    }

    @Transactional
    public ChargeEntity update(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {
            ChargeStatus targetStatus = charge.isDelayedCapture() ? AWAITING_CAPTURE_REQUEST : CAPTURE_APPROVED;

            try {
                chargeService.transitionChargeState(charge, targetStatus);
            } catch (InvalidStateTransitionException e) {
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            if (charge.isSavePaymentInstrumentToAgreement()) {
                linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreementFromCharge(charge);
            }

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    private void addChargeToCaptureQueue(ChargeEntity charge) {
        try {
            captureQueue.sendForCapture(charge);
        } catch (QueueException e) {
            LOGGER.error("Exception sending charge [{}] to capture queue", charge.getExternalId());
            throw new WebApplicationException(format(
                    "Unable to schedule charge [%s] for capture - %s",
                    charge.getExternalId(), e.getMessage()));
        }
    }

}
