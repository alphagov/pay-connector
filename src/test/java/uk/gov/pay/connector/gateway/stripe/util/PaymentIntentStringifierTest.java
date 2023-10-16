package uk.gov.pay.connector.gateway.stripe.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.PaymentIntent;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CHARGE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

class PaymentIntentStringifierTest {
    private ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldBuildStringFromPaymentIntentFailed() throws JsonProcessingException {
        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_PAYMENT_INTENT_PAYMENT_FAILED)
                .replace("{{id}}", "pi_123")
                .replace("{{type}}", PAYMENT_INTENT_PAYMENT_FAILED.getType());
        StripeNotification notification = mapper.readValue(payload, StripeNotification.class);
        PaymentIntent paymentIntent = ApiResource.GSON.fromJson(notification.getObject(), PaymentIntent.class);

        String expectedStringifiedPaymentIntent = "payment intent: pi_123 " +
                "(stripe charge: ch_3K6dQPHj08j2jFuB1f9K4dcde, type: invalid_request_error, decline code: generic_decline, " +
                "Mapped rejection reason: GENERIC_DECLINE, " +
                "code: card_declined, message: Your card was declined, status: failed, " +
                "outcome.network_status: declined_by_network, " +
                "outcome.reason: insufficient_funds, outcome.risk_level: normal, " +
                "outcome.seller_message: The bank returned the decline code `insufficient_funds`., " +
                "outcome.type: issuer_declined)";
        String stringified = PaymentIntentStringifier.stringify(paymentIntent);

        assertThat(stringified, is(expectedStringifiedPaymentIntent));
    }

    @Test
    void shouldBuildStringFromPaymentIntentSuccess() throws JsonProcessingException {
        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_PAYMENT_INTENT)
                .replace("{{id}}", "pi_123")
                .replace("{{type}}", PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED.getType());
        StripeNotification notification = mapper.readValue(payload, StripeNotification.class);
        PaymentIntent paymentIntent = ApiResource.GSON.fromJson(notification.getObject(), PaymentIntent.class);

        String expectedStringifiedPaymentIntent = "payment intent: pi_123 " +
                "(stripe charge: ch_1FF3RuEZsufgnuO0IPT8CY3o)";
        String stringified = PaymentIntentStringifier.stringify(paymentIntent);
        assertThat(stringified, is(expectedStringifiedPaymentIntent));
    }

    @Test
    void shouldBuildStringFromPaymentIntentResponse() throws JsonProcessingException {
        String payload = load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CHARGE);
        PaymentIntent response = ApiResource.GSON.fromJson(payload, PaymentIntent.class);

        String expectedStringified = "payment intent: pi_1FHESeEZsufgnuO08A2FUSPy (stripe charge: ch_3K6dQPHj08j2jFuB1f9K4dcde, " +
                "outcome.network_status: approved_by_network, outcome.risk_level: normal, " +
                "outcome.seller_message: Payment complete., outcome.type: authorized)";
        String stringified = PaymentIntentStringifier.stringify(response);
        assertThat(stringified, is(expectedStringified));
    }
}
