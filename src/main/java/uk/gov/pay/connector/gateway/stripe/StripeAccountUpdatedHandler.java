package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.inject.Inject;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeAccountUpdatedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeAccountUpdatedHandler.class);

    private final ObjectMapper objectMapper;
    private final GatewayAccountCredentialsService credentialsService;

    @Inject
    public StripeAccountUpdatedHandler(GatewayAccountCredentialsService credentialsService, ObjectMapper objectMapper) {
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
    }

    public void process(StripeNotification notification) {
        try {
            var dataObject = objectMapper.readValue(notification.getObject(), DataObject.class);
            if (canBeActivated(dataObject)) {
                credentialsService.activateCredentialIfNotYetActive(dataObject.getId());
            }
            LOGGER.info(String.format("Received an account.updated event for stripe account %s", dataObject.getId()),
                    kv("stripe_account_id", dataObject.getId()),
                    kv("payouts_enabled", dataObject.isPayoutsEnabled()),
                    kv("requirements", dataObject.getRequirements()),
                    kv("livemode", notification.getLivemode()));
        } catch (Exception e) {
            LOGGER.error("{} notification parsing for source object failed: {}", STRIPE.getName(), e);
        }
    }

    private boolean canBeActivated(DataObject dataObject) {
        return (!dataObject.getRequirements().hasCurrentlyDue() &&
                !dataObject.getRequirements().hasPastDue() &&
                dataObject.isChargesEnabled() &&
                dataObject.isPayoutsEnabled());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DataObject {

        @JsonProperty("id")
        private String id;

        @JsonProperty("charges_enabled")
        private boolean chargesEnabled;

        @JsonProperty("payouts_enabled")
        private boolean payoutsEnabled;

        @JsonProperty("requirements")
        private Requirements requirements;

        private String getId() {
            return id;
        }

        private boolean isPayoutsEnabled() {
            return payoutsEnabled;
        }

        private Requirements getRequirements() {
            return requirements;
        }

        public boolean isChargesEnabled() {
            return chargesEnabled;
        }


    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Requirements {
        @JsonProperty("currently_due")
        private JsonNode currentlyDue;

        @JsonProperty("past_due")
        private JsonNode pastDue;

        public boolean hasCurrentlyDue() {
            return !currentlyDue.isEmpty();
        }

        public boolean hasPastDue() {
            return !pastDue.isEmpty();
        }
    }
}
