package uk.gov.pay.connector.gateway.adyen.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import jakarta.ws.rs.core.MediaType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.records.AdyenApplePayAuthorisePayload;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AdyenAuthoriseRequestToGatewayOrderConverterTest {

    private static final String MERCHANT_ACCOUNT = "merchant_account";
    private static final String STORE = "store";
    private static final String REFERENCE = "reference";
    private static final String AMOUNT_CURRENCY = "GBP";
    private static final long AMOUNT_VALUE = 232_87L;
    private static final String APPLE_PAY_TOKEN = "apple_pay_token";
    private static final String RETURN_URL = "https://return_url.test/";

    private final AdyenAuthoriseRequestToGatewayOrderConverter adyenAuthoriseRequestToGatewayOrderConverter = 
            new AdyenAuthoriseRequestToGatewayOrderConverter(new JsonObjectMapper(new ObjectMapper()));

    @Test
    void convertsAdyenApplePayAuthoriseRequest() {
        var request = new AdyenApplePayAuthorisePayload(
                MERCHANT_ACCOUNT,
                STORE,
                REFERENCE,
                new Amount(AMOUNT_CURRENCY, AMOUNT_VALUE),
                new AdyenApplePayPaymentMethod(APPLE_PAY_TOKEN),
                RETURN_URL);

        GatewayOrder gatewayOrder = adyenAuthoriseRequestToGatewayOrderConverter.convert(request);

        assertThat(gatewayOrder.orderRequestType(), is(OrderRequestType.AUTHORISE_APPLE_PAY));
        assertThat(gatewayOrder.mediaType(), is(MediaType.APPLICATION_JSON_TYPE));
        JsonAssert.with(gatewayOrder.payload())
                .assertThat("$.merchantAccount", Matchers.is(MERCHANT_ACCOUNT))
                .assertThat("$.store", Matchers.is(STORE))
                .assertThat("$.reference", Matchers.is(REFERENCE))
                .assertThat("$.amount.currency", Matchers.is(AMOUNT_CURRENCY))
                .assertThat("$.amount.value", Matchers.is((int) AMOUNT_VALUE))
                .assertThat("$.paymentMethod.applePayToken", Matchers.is(APPLE_PAY_TOKEN))
                .assertThat("$.paymentMethod.type", Matchers.is("applepay"))
                .assertThat("$.returnUrl", Matchers.is(RETURN_URL));
    }

}
