package uk.gov.pay.connector.expunge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.service.ParityCheckService;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class RefundExpungeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ExpungeConfig expungeConfig;
    private final ParityCheckService parityCheckService;
    private final RefundService refundService;
    private final ChargeService chargeService;
    private final RefundDao refundDao;

    @Inject
    public RefundExpungeService(ConnectorConfiguration connectorConfiguration,
                                ParityCheckService parityCheckService,
                                RefundService refundService, ChargeService chargeService, RefundDao refundDao) {
        expungeConfig = connectorConfiguration.getExpungeConfig();
        this.parityCheckService = parityCheckService;
        this.refundService = refundService;
        this.chargeService = chargeService;
        this.refundDao = refundDao;
    }

    public void expunge(Integer noOfRefundsToExpunge) {
        if (!expungeConfig.isExpungeRefundsEnabled()) {
            logger.info("Refunds expunging feature is disabled. No refunds have been expunged");
        } else {
            int minimumAgeOfRefundInDays = expungeConfig.getMinimumAgeOfRefundInDays();
            int excludeRefundsParityCheckedWithInDays = expungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays();

            for (int number = 0; number < noOfRefundsToExpunge; number++) {
                Optional<RefundEntity> mayBeRefundToExpunge = refundDao.findRefundToExpunge(minimumAgeOfRefundInDays,
                        excludeRefundsParityCheckedWithInDays);

                if (mayBeRefundToExpunge.isPresent()) {
                    RefundEntity refundEntity = mayBeRefundToExpunge.get();
                    MDC.put(REFUND_EXTERNAL_ID, refundEntity.getExternalId());
                    logger.info(format("Attempting to expunge refund %s", refundEntity.getExternalId()));
                    try {
                        parityCheckAndExpunge(refundEntity);
                    } catch (OptimisticLockException error) {
                        logger.info("Expunging process conflicted with an already running process, exit");
                        MDC.remove(MDC_REQUEST_ID_KEY);
                        MDC.remove(REFUND_EXTERNAL_ID);
                        throw error;
                    }
                    MDC.remove(REFUND_EXTERNAL_ID);
                } else {
                    break;
                }

            }
        }
    }

    private void parityCheckAndExpunge(RefundEntity refundEntity) {
        boolean hasRefundBeenParityCheckedBefore = refundEntity.getParityCheckDate() != null;

        if (chargeExistsForRefund(refundEntity)) {
            refundService.updateRefundParityStatus(refundEntity.getExternalId(), SKIPPED);
            logger.info("Refund cannot be expunged because charge has not been expunged from in-flight database",
                    kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()));
        } else if (isInExpungeableState(refundEntity)) {
            boolean matchesWithLedger = parityCheckService.parityCheckRefundForExpunger(refundEntity);

            if (matchesWithLedger) {
                expungeRefund(refundEntity);
                logger.info("Refund expunged from connector {}", kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()));
            } else if (hasRefundBeenParityCheckedBefore) {
                logger.warn("Refund cannot be expunged because parity check with ledger repeatedly failed",
                        kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()));
            } else {
                logger.info("Refund cannot be expunged because parity check with ledger failed",
                        kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()));
            }
        } else {
            refundService.updateRefundParityStatus(refundEntity.getExternalId(), SKIPPED);
            logger.info("Refund is not in expungeable state",
                    kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()));
        }
    }

    private boolean chargeExistsForRefund(RefundEntity refundEntity) {
        try {
            chargeService.findChargeByExternalId(refundEntity.getChargeExternalId());
        } catch (ChargeNotFoundRuntimeException e) {
            return false;
        }
        return true;
    }

    private boolean isInExpungeableState(RefundEntity refundEntity) {
        long ageInDays = ChronoUnit.DAYS.between(refundEntity.getCreatedDate(), ZonedDateTime.now(UTC));
        boolean isRefundHistoric = ageInDays > expungeConfig.getMinimumAgeForHistoricRefundExceptions();

        RefundStatus refundStatus = refundEntity.getStatus();
        if (isRefundHistoric && REFUND_SUBMITTED.equals(refundStatus)) {
            return true;
        }

        return REFUNDED.equals(refundStatus) || REFUND_ERROR.equals(refundStatus);
    }

    @Transactional
    public void expungeRefund(RefundEntity refundEntity) {
        refundDao.expungeRefund(refundEntity.getExternalId());
    }
}
