package uk.gov.pay.connector.charge;

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.service.ChargeService;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ChargesAwaitingCaptureMetricEmitter {

    private static final Logger logger = LoggerFactory.getLogger(ChargesAwaitingCaptureMetricEmitter.class);
    private final CaptureProcessConfig captureConfig;
    private final MetricRegistry metricRegistry;
    private final int CAPTURE_METRIC_UPDATE_DELAY_MINUTES = 20;
    private ChargeService chargeService;

    @Inject
    public ChargesAwaitingCaptureMetricEmitter(
            Environment environment,
            ConnectorConfiguration connectorConfiguration,
            ChargeService chargeService) {

        this.chargeService = chargeService;
        this.captureConfig = connectorConfiguration.getCaptureProcessConfig();

        metricRegistry = environment.metrics();
    }

    public void register() {
        final CachedGauge<Integer> cachedGauge = new CachedGauge<>(CAPTURE_METRIC_UPDATE_DELAY_MINUTES, TimeUnit.MINUTES) {
            @Override
            protected Integer loadValue() {
                try {
                    Duration notAttemptedWithinDuration = Duration.ofMinutes(captureConfig.getChargesConsideredOverdueForCaptureAfter());
                    return chargeService.getNumberOfChargesAwaitingCapture(notAttemptedWithinDuration);
                } catch (Exception e) {
                    logger.warn(
                            "An exception has been caught while retrieving the number of charges to capture metric [{}]",
                            e.getMessage());
                }
                return null;
            }
        };

        metricRegistry.register("gateway-operations.capture-process.queue-size.ready_capture_queue_size", cachedGauge);
    }
}
