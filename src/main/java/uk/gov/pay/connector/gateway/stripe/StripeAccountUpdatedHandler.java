package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;

import javax.inject.Inject;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeAccountUpdatedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeAccountUpdatedHandler.class);

    private final ObjectMapper objectMapper;

    @Inject
    public StripeAccountUpdatedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void process(StripeNotification notification) {
        try {
            var dataObject = objectMapper.readValue(notification.getObject(), DataObject.class);
            LOGGER.info(String.format("Received an account.updated event for stripe account %s", dataObject.getId()),
                    kv("stripe_account_id", dataObject.getId()),
                    kv("payouts_enabled", dataObject.isPayoutsEnabled()),
                    kv("requirements", dataObject.getRequirements()));
        } catch (Exception e) {
            LOGGER.error("{} notification parsing for source object failed: {}", STRIPE.getName(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DataObject {

        @JsonProperty("id")
        private String id;

        @JsonProperty("payouts_enabled")
        private boolean payoutsEnabled;

        @JsonProperty("requirements")
        private JsonNode requirements;

        private String getId() {
            return id;
        }

        private boolean isPayoutsEnabled() {
            return payoutsEnabled;
        }

        private String getRequirements() {
            return requirements.toString();
        }
    }
}
