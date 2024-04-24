package uk.gov.pay.connector.events;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;

import javax.inject.Inject;

public class StateTransitionQueueMetricEmitter {
    private final MetricRegistry metricRegistry;
    private StateTransitionQueue stateTransitionQueue;

    @Inject
    public StateTransitionQueueMetricEmitter(
            Environment environment,
            StateTransitionQueue stateTransitionQueue) {
        
        this.stateTransitionQueue = stateTransitionQueue;
        this.metricRegistry = environment.metrics();
    }
    
    public void register() {
        final Gauge<Integer> gauge = () -> stateTransitionQueue.size();
        
        metricRegistry.register("state-transition.in-memory-queue.size", gauge);
    }
}
