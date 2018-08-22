package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class CardCaptureProcess {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeDao chargeDao;
    private final CardCaptureService captureService;
    private final MetricRegistry metricRegistry;
    private final CaptureProcessConfig captureConfig;
    private volatile int readyCaptureQueueSize;
    private volatile int waitingCaptureQueueSize;

    @Inject
    public CardCaptureProcess(Environment environment, ChargeDao chargeDao, CardCaptureService cardCaptureService, ConnectorConfiguration connectorConfiguration) {
        this.chargeDao = chargeDao;
        this.captureService = cardCaptureService;
        this.captureConfig = connectorConfiguration.getCaptureProcessConfig();
        metricRegistry = environment.metrics();
        metricRegistry.gauge("gateway-operations.capture-process.queue-size.ready_capture_queue_size", () -> () -> readyCaptureQueueSize);
        metricRegistry.gauge("gateway-operations.capture-process.queue-size.waiting_capture_queue_size", () -> () -> waitingCaptureQueueSize);
    }

    public void runCapture() {
        MDC.put(HEADER_REQUEST_ID, format("runCapture-%s", RandomIdGenerator.newId()));

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        int captured = 0, skipped = 0, error = 0, failedCapture = 0, total = 0, chargesToCaptureSize = 0;

        try {
            waitingCaptureQueueSize = chargeDao.countChargesAwaitingCaptureRetry(captureConfig.getRetryFailuresEveryAsJavaDuration());

            List<ChargeEntity> chargesToCapture = chargeDao.findChargesForCapture(captureConfig.getBatchSize(),
                    captureConfig.getRetryFailuresEveryAsJavaDuration());
            chargesToCaptureSize = chargesToCapture.size();
            
            if (chargesToCaptureSize < captureConfig.getBatchSize()) {
                readyCaptureQueueSize = chargesToCaptureSize;
            } else {
                readyCaptureQueueSize = chargeDao.countChargesForImmediateCapture(captureConfig.getRetryFailuresEveryAsJavaDuration());
            }
            
            if (chargesToCaptureSize > 0) {
                logger.info("Capturing : " + chargesToCaptureSize + " of " + waitingCaptureQueueSize + readyCaptureQueueSize + " charges");
            }

            Collections.shuffle(chargesToCapture);
            for (ChargeEntity charge : chargesToCapture) {
                total++;
                if (shouldRetry(charge)) {
                    try {
                        logger.info(format("Capturing [%d of %d] [chargeId=%s]", total, chargesToCaptureSize, charge.getExternalId()));
                        GatewayResponse gatewayResponse = captureService.doCapture(charge.getExternalId());
                        if (gatewayResponse.isSuccessful()) {
                            captured++;
                        } else {
                            logger.info(format("Failed to capture [chargeId=%s] due to: %s", charge.getExternalId(), 
                                    gatewayResponse.getGatewayError().orElse("No error message received.")));
                            failedCapture++;
                        }
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
            logger.error(format("Exception [%s] when running capture at charge [%d of %d]", total, chargesToCaptureSize), e.getMessage(), e);
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram("gateway-operations.capture-process.running_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
            logger.info(format("Capture complete [captured=%d] [skipped=%d] [capture_error=%d] [failed_capture=%d] [total=%d]", captured, skipped, error, failedCapture, readyCaptureQueueSize));
        }
        MDC.remove(HEADER_REQUEST_ID);
    }

    private boolean shouldRetry(ChargeEntity charge) {
        return chargeDao.countCaptureRetriesForCharge(charge.getId()) < captureConfig.getMaximumRetries();
    }


    public int getReadyCaptureQueueSize() {
        return readyCaptureQueueSize;
    }

    public int getWaitingCaptureQueueSize() {
        return waitingCaptureQueueSize;
    }
}
