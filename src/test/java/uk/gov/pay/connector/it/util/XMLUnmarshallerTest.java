package uk.gov.pay.connector.it.util;

import com.google.common.io.Resources;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;

import java.nio.charset.Charset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.PortFactory.findFreePort;
import static org.mockserver.verify.VerificationTimes.exactly;

public class XMLUnmarshallerTest {

    private static final int MOCKSERVER_PORT = findFreePort();

    private ClientAndServer mockServer;

    @Before
    public void setup() {
        mockServer = startClientAndServer(MOCKSERVER_PORT);
    }

    @Test
    public void shouldUnmarshallXmlIgnoringDTD() throws Exception {

        String successPayload = Resources.toString(Resources.getResource("templates/it/worldpay-cancel-notfication-example-it-dtd-validation-disabled.xml"), Charset.defaultCharset())
                .replace("{{port}}", String.valueOf(MOCKSERVER_PORT));

        mockServer
                .when(request().withMethod("GET").withPath("/paymentService_v1.dtd"))
                .respond(response().withStatusCode(200));

        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCancelResponse.class);

        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));

        mockServer.verify(request().withPath("/paymentService_v1.dtd"), exactly(0));
    }

    @After
    public void tearDown() {
        mockServer.stop();
    }
}
