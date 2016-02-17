package uk.gov.pay.connector.unit.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;

import java.util.HashMap;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider sandboxClient = new SandboxPaymentProvider(new ObjectMapper());

    private Consumer<StatusUpdates> accountUpdater = mock(Consumer.class);

    @Test
    public void shouldSuccessfullyParseANotification() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.handleNotification(
                mockInboundNotificationWithStatus(AUTHORISATION_REJECTED.getValue()),
                x -> true,
                x -> aServiceAccount(),
                accountUpdater
        );

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), hasItem(Pair.of("transaction", AUTHORISATION_REJECTED)));
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreUnknownStatuses() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.handleNotification(
                mockInboundNotificationWithStatus("UNKNOWN_STATUS"),
                x -> true,
                x -> aServiceAccount(),
                accountUpdater);

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreMalformedJson() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.handleNotification("{",
                x -> true,
                x -> aServiceAccount(),
                accountUpdater);

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreJsonWithMissingFields() throws Exception {
        StatusUpdates statusUpdates = sandboxClient.handleNotification("{}",
                x -> true,
                x -> aServiceAccount(),
                accountUpdater);

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    private GatewayAccount aServiceAccount() {
        return new GatewayAccount(1L, "smartpay", new HashMap<String, String>());
    }

    private String mockInboundNotificationWithStatus(String statusValue) {
        return toJson(ImmutableMap.of(
                "transaction_id", "transaction",
                "status", statusValue));
    }


}
