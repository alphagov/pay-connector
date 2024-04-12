package uk.gov.pay.connector.it.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

class XMLUnmarshallerTest {
    @BeforeEach
    void startWiremock() {
        wireMockServer.start();
    }
    @AfterEach
    void stopWiremock() {
        wireMockServer.stop();
    }
    WireMockServer wireMockServer = new WireMockServer(new WireMockConfiguration().dynamicPort());
    @Test
    void shouldUnmarshallXmlIgnoringDTD() throws XMLUnmarshallerException {

        String successPayload = fixture("templates/it/worldpay-cancel-notfication-example-it-dtd-validation-disabled.xml")
                .replace("{{port}}", String.valueOf(wireMockServer.port()));

        wireMockServer.stubFor(get(urlPathEqualTo("/paymentService_v1.dtd")).willReturn(aResponse().withStatus(200)));
        
        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCancelResponse.class);

        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));

        wireMockServer.verify(0, getRequestedFor(urlEqualTo("/paymentService_v1.dtd")));
    }
}
