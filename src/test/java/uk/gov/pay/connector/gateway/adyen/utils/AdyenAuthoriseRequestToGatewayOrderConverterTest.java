package uk.gov.pay.connector.gateway.adyen.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenGooglePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.records.AdyenApplePayAuthorisePayload;
import uk.gov.pay.connector.gateway.model.request.records.AdyenGooglePayAuthorisePayload;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.model.domain.googlepay.GooglePayPaymentInfoFixture.aGooglePayPaymentInfo;

class AdyenAuthoriseRequestToGatewayOrderConverterTest {

    private static final String MERCHANT_ACCOUNT = "merchant_account";
    private static final String STORE = "store";
    private static final String REFERENCE = "reference";
    private static final String AMOUNT_CURRENCY = "GBP";
    private static final long AMOUNT_VALUE = 232_87L;
    private static final String APPLE_PAY_TOKEN = "apple_pay_token";    
    private static final String GOOGLE_PAY_TOKEN = "google_pay_token";
    private static final String RETURN_URL = "https://return_url.test/";
    private static final String TEXT_HTML = "text/html";

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
                .assertThat("$.merchantAccount", is(MERCHANT_ACCOUNT))
                .assertThat("$.store", is(STORE))
                .assertThat("$.reference", is(REFERENCE))
                .assertThat("$.amount.currency", is(AMOUNT_CURRENCY))
                .assertThat("$.amount.value", is((int) AMOUNT_VALUE))
                .assertThat("$.paymentMethod.applePayToken", is(APPLE_PAY_TOKEN))
                .assertThat("$.paymentMethod.type", is("applepay"))
                .assertThat("$.returnUrl", is(RETURN_URL));
    }

    @Test
    void convertsAdyenGooglePayAuthoriseRequest() {
        var request = new AdyenGooglePayAuthorisePayload(
                MERCHANT_ACCOUNT,
                STORE,
                REFERENCE,
                new Amount(AMOUNT_CURRENCY, AMOUNT_VALUE),
                new AdyenGooglePayPaymentMethod(GOOGLE_PAY_TOKEN),
                new AdyenBrowserInfoFactory().create(aGooglePayPaymentInfo().withAcceptHeader(TEXT_HTML).build()),
                RETURN_URL);

        GatewayOrder gatewayOrder = adyenAuthoriseRequestToGatewayOrderConverter.convert(request);

        assertThat(gatewayOrder.orderRequestType(), is(OrderRequestType.AUTHORISE_GOOGLE_PAY));
        assertThat(gatewayOrder.mediaType(), is(MediaType.APPLICATION_JSON_TYPE));
        JsonAssert.with(gatewayOrder.payload())
                .assertThat("$.merchantAccount", is(MERCHANT_ACCOUNT))
                .assertThat("$.store", is(STORE))
                .assertThat("$.reference", is(REFERENCE))
                .assertThat("$.amount.currency", is(AMOUNT_CURRENCY))
                .assertThat("$.amount.value", is((int) AMOUNT_VALUE))
                .assertThat("$.paymentMethod.googlePayToken", is(GOOGLE_PAY_TOKEN))
                .assertThat("$.paymentMethod.type", is("googlepay"))
                .assertThat("$.returnUrl", is(RETURN_URL))
                .assertThat("$.browserInfo.acceptHeader", is(TEXT_HTML));
    }
}
