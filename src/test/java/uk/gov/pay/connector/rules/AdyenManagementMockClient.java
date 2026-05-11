package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_PAYMENT_METHOD_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_STORE_RESPONSE;

public class AdyenManagementMockClient extends AdyenMockClient {
    public AdyenManagementMockClient(WireMockServer wireMockServer) {
        super(wireMockServer);
    }

    public void mockCreateStore() {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_STORE_RESPONSE);
        var path = "/stores";
        setupPostResponse(responseBody, path, SC_OK);
    }

    public void mockRequestPaymentMethod(String merchantId) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_PAYMENT_METHOD_RESPONSE);
        var path = format("/merchants/%s/paymentMethodSettings", merchantId);
        setupPostResponse(responseBody, path, SC_OK);
    }
}
