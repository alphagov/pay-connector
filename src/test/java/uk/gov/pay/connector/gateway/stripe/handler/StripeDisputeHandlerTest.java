package uk.gov.pay.connector.gateway.stripe.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.request.StripeSubmitTestDisputeEvidenceRequest;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_SUBMIT_DISPUTE_EVIDENCE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class StripeDisputeHandlerTest {
    @Mock
    private GatewayClient client;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayClient.Response response;

    private JsonObjectMapper mapper = new JsonObjectMapper(new ObjectMapper());

    private StripeDisputeHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new StripeDisputeHandler(client, stripeGatewayConfig, mapper);
    }

    @Test
    void shouldPostStripeDisputeUpdate() throws GatewayException {
        String disputeId = "dispute-id";
        String evidenceText = "winning_evidence";
        String transactionId = "transaction-id";
        when(response.getEntity()).thenReturn(load(STRIPE_SUBMIT_DISPUTE_EVIDENCE_RESPONSE));
        when(client.postRequestFor(any())).thenReturn(response);
        StripeDisputeData response = handler.submitTestDisputeEvidence(disputeId, evidenceText, transactionId);
        assertThat(response.getEvidence().getUncategorizedText(), is(evidenceText));
    }

    @Test
    void shouldCreateStripeSubmitTestDisputeEvidenceRequest() throws Exception {
        when(response.getEntity()).thenReturn(load(STRIPE_SUBMIT_DISPUTE_EVIDENCE_RESPONSE));
        when(client.postRequestFor(any())).thenReturn(response);
        when(stripeGatewayConfig.getUrl()).thenReturn("https://example.org");

        ArgumentCaptor<StripeSubmitTestDisputeEvidenceRequest> captor = ArgumentCaptor.forClass(StripeSubmitTestDisputeEvidenceRequest.class);
        StripeDisputeHandler handler = new StripeDisputeHandler(client, stripeGatewayConfig, new JsonObjectMapper(new ObjectMapper()));

        handler.submitTestDisputeEvidence("dispute-id", "winning_evidence", "transaction-id");

        verify(client).postRequestFor(captor.capture());

        assertThat(captor.getValue().getUrl().toString(), Matchers.is("https://example.org/v1/disputes/dispute-id"));
        assertThat(captor.getValue().getGatewayOrder().getPayload(), containsString("winning_evidence"));
    }
}
