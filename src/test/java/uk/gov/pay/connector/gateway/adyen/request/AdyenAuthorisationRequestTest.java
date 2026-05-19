package uk.gov.pay.connector.gateway.adyen.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.request.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.request.json.PaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.PaymentRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;

class AdyenAuthorisationRequestTest {
    public static final String TEST_URL = "/adyen_authorisation";
    private JsonObjectMapper jsonObjectMapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        jsonObjectMapper = new JsonObjectMapper(objectMapper);
    }

    @Test
    void shouldCreateGatewayOrder_withSerialisedPayload() {
        var request = buildValidRequestWithBillingAddress();

        assertThat(request.getGatewayOrder().getOrderRequestType(), is(AUTHORISE));
        assertThat(request.getGatewayOrder().getMediaType(), is(APPLICATION_JSON_TYPE));
        JsonAssert.with(request.getGatewayOrder().getPayload())
                .assertThat("$.amount.value", is(1000))
                .assertThat("$.amount.currency", is("GBP"))
                .assertThat("$.billingAddress.houseNumberOrName", is("houseNumberOrName"))
                .assertThat("$.billingAddress.street", is("street"))
                .assertThat("$.billingAddress.city", is("city"))
                .assertThat("$.billingAddress.country", is("country"))
                .assertThat("$.billingAddress.postalCode", is("postalCode"))
                .assertThat("$.merchantAccount", is("adyen-test-merchant-account-id"))
                .assertThat("$.paymentMethod.cvc", is("737"))
                .assertThat("$.paymentMethod.expiryMonth", is("03"))
                .assertThat("$.paymentMethod.expiryYear", is("2030"))
                .assertThat("$.paymentMethod.holderName", is("John Doe"))
                .assertThat("$.paymentMethod.number", is("4444333322221111"))
                .assertThat("$.paymentMethod.type", is("scheme"))
                .assertThat("$.reference", is("externalid123"))
                .assertThat("$.returnUrl", is("frontend-3ds-url"))
                .assertThat("$.shopperInteraction", is("Ecommerce"))
                .assertThat("$.store", is("store-id"))
                .assertThat("$.channel", is("Web"));
    }

    private AdyenAuthorisationRequest buildValidRequestWithBillingAddress() {
        var paymentRequest = makePaymentRequestWithFullBillingAddress();
        return new AdyenAuthorisationRequest(
                URI.create(TEST_URL),
                Map.of("X-API-Key", "test-api-key"),
                "test", 
                paymentRequest,
                jsonObjectMapper);
    }

    private PaymentRequest makePaymentRequestWithFullBillingAddress() {
        var billingAddress = new BillingAddress(
                "houseNumberOrName",
                "street",
                "city",
                "country",
                "postalCode",
                null);

        var paymentMethod = new PaymentMethod("737",
                "03",
                "2030",
                "John Doe",
                "4444333322221111",
                "scheme");

        return new PaymentRequest(
                new Amount("GBP", 1000L),
                billingAddress,
                "adyen-test-merchant-account-id",
                paymentMethod,
                "externalid123",
                "frontend-3ds-url",
                "Ecommerce",
                "store-id",
                "Web",
                new HashMap<>(Map.of("manualCapture", "true"))
        );
    }
}
