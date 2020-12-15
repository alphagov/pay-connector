package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "${PACT_BROKER_HOST:pact-broker-test.cloudapps.digital}", tags = {"${PACT_CONSUMER_TAG}", "test"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"selfservice"})
public class SelfServiceContractTest extends ContractTest {
    @ClassRule
    public static WireMockClassRule worldpayDeviceDataCollectionRule = new WireMockClassRule(WORLDPAY_DDC_PORT_NUMBER);

    @BeforeClass
    public static void setUpBeforeClass() {
        worldpayDeviceDataCollectionRule.stubFor(post(urlPathEqualTo("/shopper/3ds/ddc.html")).willReturn(aResponse().withStatus(200)));
    }
}
