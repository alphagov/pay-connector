package uk.gov.pay.connector.expunge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.service.ParityCheckService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class RefundExpungeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ExpungeConfig expungeConfig;
    private final ParityCheckService parityCheckService;
    private final RefundService refundService;
    private final RefundDao refundDao;

    @Inject
    public RefundExpungeService(ConnectorConfiguration connectorConfiguration,
                                ParityCheckService parityCheckService,
                                RefundService refundService, RefundDao refundDao) {
        expungeConfig = connectorConfiguration.getExpungeConfig();
        this.parityCheckService = parityCheckService;
        this.refundService = refundService;
        this.refundDao = refundDao;
    }

    public void expunge(Integer noOfRefundsToExpungeQueryParam) {
        if (!expungeConfig.isExpungeRefundsEnabled()) {
            logger.info("Refunds expunging feature is disabled. No refunds have been expunged");
        } else {
            int noOfRefundsToExpunge = getNumberOfRefundsToExpunge(noOfRefundsToExpungeQueryParam);
            int minimumAgeOfRefundInDays = expungeConfig.getMinimumAgeOfRefundInDays();
            int excludeRefundsParityCheckedWithInDays = expungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays();

            IntStream.range(0, noOfRefundsToExpunge).forEach(number -> {
                refundDao.findRefundToExpunge(minimumAgeOfRefundInDays, excludeRefundsParityCheckedWithInDays)
                        .ifPresent(refundEntity -> {
                            MDC.put(REFUND_EXTERNAL_ID, refundEntity.getExternalId());
                            logger.info(format("Attempting to expunge refund %s", refundEntity.getExternalId()));
                            try {
                                parityCheckAndExpunge(refundEntity);
                            } catch (OptimisticLockException error) {
                                logger.info("Expunging process conflicted with an already running process, exit");
                                MDC.remove(HEADER_REQUEST_ID);
                                throw error;
                            }
                            MDC.remove(REFUND_EXTERNAL_ID);
                        });
            });
        }
    }

    private int getNumberOfRefundsToExpunge(Integer noOfRefundsToExpungeQueryParam) {
        if (noOfRefundsToExpungeQueryParam != null && noOfRefundsToExpungeQueryParam > 0) {
            return noOfRefundsToExpungeQueryParam;
        }
        return expungeConfig.getNumberOfChargesOrRefundsToExpunge();
    }

    private void parityCheckAndExpunge(RefundEntity refundEntity) {
        boolean hasRefundBeenParityCheckedBefore = refundEntity.getParityCheckDate() != null;

        if (isInExpungeableState(refundEntity)) {
            boolean matchesWithLedger = parityCheckService.parityCheckRefundForExpunger(refundEntity);

            if (matchesWithLedger) {
                expungeRefund(refundEntity);
                logger.info("Refund expunged from connector {}", kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()));
            } else if (hasRefundBeenParityCheckedBefore) {
                logger.error("Refund cannot be expunged because parity check with ledger repeatedly failed",
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
