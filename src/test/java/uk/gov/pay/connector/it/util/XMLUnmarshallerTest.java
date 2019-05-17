package uk.gov.pay.connector.it.util;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class XMLUnmarshallerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    @Test
    public void shouldUnmarshallXmlIgnoringDTD() throws XMLUnmarshallerException {

        String successPayload = fixture("templates/it/worldpay-cancel-notfication-example-it-dtd-validation-disabled.xml")
                .replace("{{port}}", String.valueOf(wireMockRule.port()));

        wireMockRule.stubFor(get(urlPathEqualTo("/paymentService_v1.dtd")).willReturn(aResponse().withStatus(200)));
        
        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCancelResponse.class);

        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));

        wireMockRule.verify(0, getRequestedFor(urlEqualTo("/paymentService_v1.dtd")));
    }
}
