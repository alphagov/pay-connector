package uk.gov.pay.connector.payout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.payout.PayoutCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.payout.PayoutEventWithGatewayStatusDetails;
import uk.gov.pay.connector.events.eventdetails.payout.PayoutFailedEventDetails;
import uk.gov.pay.connector.events.eventdetails.payout.PayoutPaidEventDetails;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.payout.PayoutUpdated;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.queue.QueueException;

import javax.ws.rs.WebApplicationException;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.ZonedDateTime.parse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.events.model.ResourceType.PAYOUT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeCredentials.STRIPE_ACCOUNT_ID_KEY;

@RunWith(MockitoJUnitRunner.class)
public class PayoutEmitterServiceTest {

    @Mock
    EventService mockEventService;
    @Mock
    ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    GatewayAccountDao mockGatewayAccountDao;

    private PayoutEmitterService payoutEmitterService;
    private StripePayout payout;
    private ZonedDateTime eventDate = parse("2019-09-04T18:43:23Z");
    private GatewayAccountEntity gatewayAccountEntity;
    @Captor
    private ArgumentCaptor<PayoutEvent> payoutArgumentCaptor;

    @Before
    public void setUp() {
        gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity()
                .withId(1234L)
                .build();
        when(mockConnectorConfiguration.getEmitPayoutEvents()).thenReturn(true);
        when(mockGatewayAccountDao.findByCredentialsKeyValue(STRIPE_ACCOUNT_ID_KEY, "connect-account"))
                .thenReturn(Optional.of(gatewayAccountEntity));

        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountDao);
        payout = new StripePayout("po_123", 1213L, 1589846400L,
                null, "pending", "card", null);
    }

    @Test
    public void emitPayoutEventForPayoutCreatedShouldEmitCorrectEvent() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, "connect-account", payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());

        PayoutCreated payoutEvent = (PayoutCreated) payoutArgumentCaptor.getValue();
        PayoutCreatedEventDetails details = (PayoutCreatedEventDetails) payoutEvent.getEventDetails();

        assertThat(payoutEvent.getEventType(), is("PAYOUT_CREATED"));
        assertThat(payoutEvent.getResourceExternalId(), is("po_123"));
        assertThat(payoutEvent.getResourceType(), is(PAYOUT));
        assertThat(details.getAmount(), is(1213L));
        assertThat(details.getGatewayStatus(), is("pending"));
    }

    @Test
    public void shouldEmitPayoutUpdatedEventCorrectly() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutUpdated.class, eventDate, "connect-account", payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());
        PayoutUpdated payoutEvent = (PayoutUpdated) payoutArgumentCaptor.getValue();
        PayoutEventWithGatewayStatusDetails details = (PayoutEventWithGatewayStatusDetails) payoutEvent.getEventDetails();

        assertCommonPayoutFields(payoutEvent, details.getGatewayStatus());
        assertThat(payoutEvent.getEventType(), is("PAYOUT_UPDATED"));
    }

    @Test
    public void shouldEmitPayoutFailedEventCorrectly() throws QueueException {
        payout = new StripePayout("po_123", "pending", "account_closed",
                "The bank account has been closed", "ba_1GkZtqDv3CZEaFO2CQhLrluk");
        payoutEmitterService.emitPayoutEvent(PayoutFailed.class, eventDate, "connect-account", payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());
        PayoutFailed payoutEvent = (PayoutFailed) payoutArgumentCaptor.getValue();
        PayoutFailedEventDetails details = (PayoutFailedEventDetails) payoutEvent.getEventDetails();

        assertCommonPayoutFields(payoutEvent, details.getGatewayStatus());
        assertThat(payoutEvent.getEventType(), is("PAYOUT_FAILED"));
        assertThat(details.getFailureCode(), is("account_closed"));
        assertThat(details.getFailureMessage(), is("The bank account has been closed"));
        assertThat(details.getFailureBalanceTransaction(), is("ba_1GkZtqDv3CZEaFO2CQhLrluk"));
    }

    @Test
    public void shouldEmitPayoutPaidEventCorrectly() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutPaid.class, eventDate, "connect-account", payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());
        PayoutPaid payoutEvent = (PayoutPaid) payoutArgumentCaptor.getValue();
        PayoutPaidEventDetails details = (PayoutPaidEventDetails) payoutEvent.getEventDetails();

        assertCommonPayoutFields(payoutEvent, details.getGatewayStatus());
        assertThat(payoutEvent.getEventType(), is("PAYOUT_PAID"));
        assertThat(details.getPaidOutDate().toString(), is("2020-05-19T00:00Z"));// arrival date of payout
    }

    private void assertCommonPayoutFields(PayoutEvent event, String gatewayStatus) {
        assertThat(event.getResourceExternalId(), is("po_123"));
        assertThat(event.getResourceType(), is(PAYOUT));
        assertThat(event.getTimestamp(), is(eventDate));
        assertThat(gatewayStatus, is("pending"));
    }

    @Test
    public void emitPayoutEventShouldEmitEventIfFeatureFlagToEmitEventsIsEnabled() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, "connect-account", payout);
        verify(mockEventService).emitEvent(any(), anyBoolean());
    }

    @Test
    public void emitPayoutEventShouldNotEmitEventIfFeatureFlagToEmitEventsIsDisabled() throws QueueException {
        when(mockConnectorConfiguration.getEmitPayoutEvents()).thenReturn(false);
        when(mockGatewayAccountDao.findByCredentialsKeyValue(STRIPE_ACCOUNT_ID_KEY, "connect-account"))
                .thenReturn(Optional.of(gatewayAccountEntity));
        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountDao);

        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, "connect-account", payout);
        verify(mockEventService, never()).emitEvent(any(), anyBoolean());
    }

    @Test
    public void emitPayoutEventShouldNotEmitEventForAnUnknownPayoutEvent() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutEvent.class, eventDate, null, payout);
        verify(mockEventService, never()).emitEvent(any(), anyBoolean());
    }

    @Test(expected = WebApplicationException.class)
    public void emitPayoutEventShouldNotEmitPayoutCreatedEventIfGatewayAccountNotFound() {
        when(mockGatewayAccountDao.findByCredentialsKeyValue(STRIPE_ACCOUNT_ID_KEY, "connect-account"))
                .thenReturn(Optional.empty());
        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountDao);

        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, "connect-account", payout);
    }
}
