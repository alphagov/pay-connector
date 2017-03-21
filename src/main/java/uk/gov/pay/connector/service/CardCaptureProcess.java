package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;

public class CardCaptureProcess {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    static final long BATCH_SIZE = 10;
    private final ChargeDao chargeDao;
    private final CardCaptureService captureService;
    private final MetricRegistry metricRegistry;

    @Inject
    public CardCaptureProcess(Environment environment, ChargeDao chargeDao, CardCaptureService cardCaptureService) {
        this.chargeDao = chargeDao;
        this.captureService = cardCaptureService;
        metricRegistry = environment.metrics();
    }

    public void runCapture() {
        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            List<ChargeEntity> chargesToCapture = chargeDao
                    .findAllBy(chargeSearchCriteriaForCapture());
            logger.info("Capturing : "+ chargesToCapture.size() + " charges");

            chargesToCapture
                .forEach((charge) ->  captureService.doCapture(charge.getExternalId()));
        } catch (Exception e) {
            logger.error("Exception when running capture", e);
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram("capture-process.response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private ChargeSearchParams chargeSearchCriteriaForCapture() {
        ChargeSearchParams chargeSearchParams = new ChargeSearchParams();
        chargeSearchParams.withInternalChargeStatuses(Collections.singletonList(CAPTURE_APPROVED));
        chargeSearchParams.withDisplaySize(CardCaptureProcess.BATCH_SIZE);
        chargeSearchParams.withPage(1L);
        return chargeSearchParams;
    }
}
