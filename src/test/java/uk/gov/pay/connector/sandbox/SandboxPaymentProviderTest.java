package uk.gov.pay.connector.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.sandbox.SandboxStatusMapper;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider = new SandboxPaymentProvider(new ObjectMapper());

    private Consumer<StatusUpdates> accountUpdater = mock(Consumer.class);

    @Test
    public void shouldGetPaymentProviderName() {
        Assert.assertThat(provider.getPaymentProviderName(), is("sandbox"));
    }

    @Test
    public void shouldGetStatusMapper() {
        Assert.assertThat(provider.getStatusMapper(), sameInstance(SandboxStatusMapper.get()));
    }

   /* @Test
    public void shouldSuccessfullyParseANotification() throws Exception {
        StatusUpdates statusUpdates = provider.handleNotification(
                mockInboundNotificationWithStatus(AUTHORISATION_REJECTED.getValue()),
                x -> true,
                x -> aChargeEntity(),
                accountUpdater
        );

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), hasItem(Pair.of("transaction", AUTHORISATION_REJECTED)));
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreUnknownStatuses() throws Exception {
        StatusUpdates statusUpdates = provider.handleNotification(
                mockInboundNotificationWithStatus("UNKNOWN_STATUS"),
                x -> true,
                x -> aChargeEntity(),
                accountUpdater);

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreMalformedJson() throws Exception {
        StatusUpdates statusUpdates = provider.handleNotification("{",
                x -> true,
                x -> aChargeEntity(),
                accountUpdater);

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }

    @Test
    public void shouldIgnoreJsonWithMissingFields() throws Exception {
        StatusUpdates statusUpdates = provider.handleNotification("{}",
                x -> true,
                x -> aChargeEntity(),
                accountUpdater);

        assertThat(statusUpdates.successful(), is(true));
        assertThat(statusUpdates.getStatusUpdates(), empty());
        assertThat(statusUpdates.getResponseForProvider(), is("OK"));
    }
*/
    private Optional<ChargeEntity> aChargeEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("smartpay");
        gatewayAccount.setCredentials(new HashMap<>());

        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setGatewayAccount(gatewayAccount);

        return Optional.ofNullable(chargeEntity);
    }

    private String mockInboundNotificationWithStatus(String statusValue) {
        return toJson(ImmutableMap.of(
                "transaction_id", "transaction",
                "status", statusValue));
    }


}
