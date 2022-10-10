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
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.time.ZonedDateTime;

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
    GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    private PayoutEmitterService payoutEmitterService;
    private StripePayout payout;
    private ZonedDateTime eventDate = parse("2019-09-04T18:43:23Z");
    private ZonedDateTime arrivalDate = parse("2020-05-19T00:00Z");
    private String connectAccount = "connect-account";
    @Captor
    private ArgumentCaptor<PayoutEvent> payoutArgumentCaptor;

    @Before
    public void setUp() {
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withId(1234L)
                .build();

        when(mockConnectorConfiguration.getEmitPayoutEvents()).thenReturn(true);
        when(mockGatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue(STRIPE_ACCOUNT_ID_KEY, connectAccount))
                .thenReturn(gatewayAccountEntity);

        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountCredentialsService);
        payout = new StripePayout("po_123", 1213L, arrivalDate.toEpochSecond(),
                eventDate.toEpochSecond(), "pending", "card", null);
    }

    @Test
    public void emitPayoutEventForPayoutCreatedShouldEmitCorrectEvent() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate.toInstant(), connectAccount, payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());

        PayoutCreated payoutEvent = (PayoutCreated) payoutArgumentCaptor.getValue();
        PayoutCreatedEventDetails details = (PayoutCreatedEventDetails) payoutEvent.getEventDetails();

        assertThat(payoutEvent.getEventType(), is("PAYOUT_CREATED"));
        assertThat(payoutEvent.getResourceExternalId(), is("po_123"));
        assertThat(payoutEvent.getResourceType(), is(PAYOUT));
        assertThat(payoutEvent.getTimestamp(), is(eventDate.toInstant()));
        assertThat(details.getAmount(), is(1213L));
        assertThat(details.getGatewayStatus(), is("pending"));
    }

    @Test
    public void shouldEmitPayoutUpdatedEventCorrectly() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutUpdated.class, eventDate.toInstant(), connectAccount, payout);

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
        payoutEmitterService.emitPayoutEvent(PayoutFailed.class, eventDate.toInstant(), "connect-account", payout);

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
        payoutEmitterService.emitPayoutEvent(PayoutPaid.class, eventDate.toInstant(), connectAccount, payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());
        PayoutPaid payoutEvent = (PayoutPaid) payoutArgumentCaptor.getValue();
        PayoutPaidEventDetails details = (PayoutPaidEventDetails) payoutEvent.getEventDetails();

        assertCommonPayoutFields(payoutEvent, details.getGatewayStatus());
        assertThat(payoutEvent.getEventType(), is("PAYOUT_PAID"));
        assertThat(details.getPaidOutDate(), is(arrivalDate));
    }

    private void assertCommonPayoutFields(PayoutEvent event, String gatewayStatus) {
        assertThat(event.getResourceExternalId(), is("po_123"));
        assertThat(event.getResourceType(), is(PAYOUT));
        assertThat(event.getTimestamp(), is(eventDate.toInstant()));
        assertThat(gatewayStatus, is("pending"));
    }

    @Test
    public void emitPayoutEventShouldEmitEventIfFeatureFlagToEmitEventsIsEnabled() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate.toInstant(), connectAccount, payout);
        verify(mockEventService).emitEvent(any(), anyBoolean());
    }

    @Test
    public void emitPayoutEventShouldNotEmitEventIfFeatureFlagToEmitEventsIsDisabled() throws QueueException {
        when(mockConnectorConfiguration.getEmitPayoutEvents()).thenReturn(false);
        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountCredentialsService);
        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate.toInstant(), connectAccount, payout);
        verify(mockEventService, never()).emitEvent(any(), anyBoolean());
    }

    @Test
    public void emitPayoutEventShouldNotEmitEventForAnUnknownPayoutEvent() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutEvent.class, eventDate.toInstant(), null, payout);
        verify(mockEventService, never()).emitEvent(any(), anyBoolean());
    }
}
