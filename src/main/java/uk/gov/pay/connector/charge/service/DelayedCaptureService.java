package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;

public class DelayedCaptureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelayedCaptureService.class);

    private final ChargeService chargeService;
    private final ChargeDao chargeDao;
    private final CaptureQueue captureQueue;

    @Inject
    public DelayedCaptureService(ChargeService chargeService, ChargeDao chargeDao, CaptureQueue captureQueue) {
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
        this.captureQueue = captureQueue;
    }

    public ChargeEntity markDelayedCaptureChargeAsCaptureApproved(String externalId, Long accountId) {
        ChargeEntity charge = updateStatusToCaptureApprovedIfCurrentStatusAwaitingCaptureRequest(externalId, accountId);
        addChargeToCaptureQueue(charge);
        return charge;
    }

    @Transactional
    public ChargeEntity updateStatusToCaptureApprovedIfCurrentStatusAwaitingCaptureRequest(String externalId, Long accountId) {
        return chargeDao.findByExternalIdAndGatewayAccount(externalId, accountId).map(charge -> {
            switch (fromString(charge.getStatus())) {
                case AWAITING_CAPTURE_REQUEST:
                    try {
                        chargeService.transitionChargeState(charge, CAPTURE_APPROVED);
                    } catch (InvalidStateTransitionException e) {
                        throw new ConflictRuntimeException(charge.getExternalId(),
                                "attempt to perform delayed capture on invalid charge state " + e.getMessage());
                    }

                    return charge;

                case CAPTURE_APPROVED:
                case CAPTURE_APPROVED_RETRY:
                case CAPTURE_READY:
                case CAPTURE_SUBMITTED:
                case CAPTURED:
                    return charge;

                default:
                    throw new ConflictRuntimeException(charge.getExternalId(),
                            format("attempt to perform delayed capture on charge not in %s state.", AWAITING_CAPTURE_REQUEST)
                    );
            }

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
