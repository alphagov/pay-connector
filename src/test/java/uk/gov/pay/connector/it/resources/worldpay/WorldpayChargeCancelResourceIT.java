package uk.gov.pay.connector.it.resources.worldpay;

import io.restassured.http.ContentType;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToXml;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST;

public class WorldpayChargeCancelResourceIT extends NewChargingITestBase {

    public WorldpayChargeCancelResourceIT() {
        super("worldpay");
    }

    @Test
    public void cancelCharge_inWorldpaySystem() {
        String chargeId = createNewChargeWith(ChargeStatus.AUTHORISATION_SUCCESS, "MyUniqueTransactionId!");

        worldpayMockClient.mockCancelSuccess();
        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId, chargeId))
                .then()
                .statusCode(204);

        String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST)
                .replace("{{merchantCode}}", "merchant-id")
                .replace("{{transactionId}}", "MyUniqueTransactionId!");

        verifyRequestBodyToWorldpay(WORLDPAY_URL, expectedRequestBody);
    }

    private void verifyRequestBodyToWorldpay(String path, String body) {
        wireMockServer.verify(
                postRequestedFor(urlPathEqualTo(path))
                        .withHeader("Content-Type", equalTo("application/xml"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withRequestBody(equalToXml(body)));
    }

}
