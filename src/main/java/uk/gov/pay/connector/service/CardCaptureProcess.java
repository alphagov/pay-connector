package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class CardCaptureProcess {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeDao chargeDao;
    private final CardCaptureService captureService;
    private final MetricRegistry metricRegistry;
    private final CaptureProcessConfig captureConfig;
    private volatile long queueSize;
    private final Counter queueSizeMetric;

    @Inject
    public CardCaptureProcess(Environment environment, ChargeDao chargeDao, CardCaptureService cardCaptureService, ConnectorConfiguration connectorConfiguration) {
        this.chargeDao = chargeDao;
        this.captureService = cardCaptureService;
        this.captureConfig = connectorConfiguration.getCaptureProcessConfig();
        metricRegistry = environment.metrics();

        queueSizeMetric = metricRegistry.counter("gateway-operations.capture-process.queue-size");
    }

    public void runCapture() {
        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        long captured = 0, skipped = 0, error = 0, total = 0;

        try {
            queueSize = chargeDao.countChargesForCapture();

            updateQueueSizeMetric(queueSize);

            List<ChargeEntity> chargesToCapture = chargeDao.findChargesForCapture(captureConfig.getBatchSize(), captureConfig.getRetryFailuresEveryAsJavaDuration());

            if (chargesToCapture.size() > 0) {
                logger.info("Capturing : " + chargesToCapture.size() + " of " + queueSize + " charges");
            }

            Collections.shuffle(chargesToCapture);
            for (ChargeEntity charge : chargesToCapture) {
                total++;
                if (shouldRetry(charge)) {
                    try {
                        logger.info(format("Capturing [%d of %d] [chargeId=%s]", total, queueSize, charge.getExternalId()));
                        captureService.doCapture(charge.getExternalId());
                        captured++;
                    } catch (ConflictRuntimeException e) {
                        logger.info("Another process has already attempted to capture [chargeId=" + charge.getExternalId() + "]. Skipping.");
                        skipped++;
                    }
                } else {
                    captureService.markChargeAsCaptureError(charge.getExternalId());
                    error++;
                }
            }
        } catch (Exception e) {
            logger.error(format("Exception [%s] when running capture at charge [%d of %d]", total, queueSize), e.getMessage(), e);
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram("gateway-operations.capture-process.running_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
            logger.info(format("Capture complete [captured=%d] [skipped=%d] [capture_error=%d] [total=%d]", captured, skipped, error, queueSize));
        }
    }

    private boolean shouldRetry(ChargeEntity charge) {
        return chargeDao.countCaptureRetriesForCharge(charge.getId()) < captureConfig.getMaximumRetries();
    }

    private void updateQueueSizeMetric(long newQueueSize) {
        // Counters do not provide a set method to record a spot value, thus we need this workaround.
        long currentQueueSizeCounter = queueSizeMetric.getCount();
        queueSizeMetric.inc(newQueueSize - currentQueueSizeCounter); // if input<0, we get decrease
    }

    public long getQueueSize() {
        return queueSize;
    }
}
