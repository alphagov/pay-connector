package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;

import jakarta.inject.Inject;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeRefundUpdatedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeRefundUpdatedHandler.class);

    private final ObjectMapper objectMapper;

    @Inject
    public StripeRefundUpdatedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public void process(StripeNotification notification) {
        try {
            var dataObject = objectMapper.readValue(notification.getObject(), DataObject.class);
            if (dataObject.status.equals("failed")) {
                LOGGER.info("Received a charge.refund.updated event with status failed",
                        kv("stripe_refund_id", dataObject.refundId),
                        kv("stripe_payment_id", dataObject.paymentIntent),
                        kv("failure_reason", dataObject.failureReason));
            }
        } catch (Exception e) {
            LOGGER.error("{} notification parsing for charge.refund.updated source object failed: {}", STRIPE.getName(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DataObject {

        @JsonProperty("id")
        private String refundId;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("failure_reason")
        private String failureReason;
        
        @JsonProperty("payment_intent")
        private String paymentIntent;
    }
}
