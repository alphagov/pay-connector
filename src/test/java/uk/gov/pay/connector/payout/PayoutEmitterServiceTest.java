package uk.gov.pay.connector.payout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.time.Instant;

import static java.time.Instant.parse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.events.model.ResourceType.PAYOUT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeCredentials.STRIPE_ACCOUNT_ID_KEY;

@ExtendWith(MockitoExtension.class)
class PayoutEmitterServiceTest {

    @Mock
    EventService mockEventService;
    @Mock
    ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    private PayoutEmitterService payoutEmitterService;
    private StripePayout payout;
    private Instant eventDate = parse("2019-09-04T18:43:23Z");
    private Instant arrivalDate = parse("2020-05-19T00:00:00Z");
    private String connectAccount = "connect-account";
    @Captor
    private ArgumentCaptor<PayoutEvent> payoutArgumentCaptor;

    @BeforeEach
    void setUp() {
        when(mockConnectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountCredentialsService);
        payout = new StripePayout("po_123", 1213L, arrivalDate.getEpochSecond(),
                eventDate.getEpochSecond(), "pending", "card", null);
    }

    @Test
    void emitPayoutEventForPayoutCreatedShouldEmitCorrectEvent() throws QueueException {
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withId(1234L)
                .build();
        when(mockGatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue(STRIPE_ACCOUNT_ID_KEY, connectAccount))
                .thenReturn(gatewayAccountEntity);


        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, connectAccount, payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());

        PayoutCreated payoutEvent = (PayoutCreated) payoutArgumentCaptor.getValue();
        PayoutCreatedEventDetails details = (PayoutCreatedEventDetails) payoutEvent.getEventDetails();

        assertThat(payoutEvent.getEventType(), is("PAYOUT_CREATED"));
        assertThat(payoutEvent.getResourceExternalId(), is("po_123"));
        assertThat(payoutEvent.getResourceType(), is(PAYOUT));
        assertThat(payoutEvent.getTimestamp(), is(eventDate));
        assertThat(details.getAmount(), is(1213L));
        assertThat(details.getGatewayStatus(), is("pending"));
    }

    @Test
    void shouldEmitPayoutUpdatedEventCorrectly() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutUpdated.class, eventDate, connectAccount, payout);

        verify(mockEventService).emitEvent(payoutArgumentCaptor.capture(), anyBoolean());
        PayoutUpdated payoutEvent = (PayoutUpdated) payoutArgumentCaptor.getValue();
        PayoutEventWithGatewayStatusDetails details = (PayoutEventWithGatewayStatusDetails) payoutEvent.getEventDetails();

        assertCommonPayoutFields(payoutEvent, details.getGatewayStatus());
        assertThat(payoutEvent.getEventType(), is("PAYOUT_UPDATED"));
    }

    @Test
    void shouldEmitPayoutFailedEventCorrectly() throws QueueException {
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
    void shouldEmitPayoutPaidEventCorrectly() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutPaid.class, eventDate, connectAccount, payout);

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
        assertThat(event.getTimestamp(), is(eventDate));
        assertThat(gatewayStatus, is("pending"));
    }

    @Test
    void emitPayoutEventShouldEmitEventIfFeatureFlagToEmitEventsIsEnabled() throws QueueException {
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withId(1234L)
                .build();
        when(mockGatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue(STRIPE_ACCOUNT_ID_KEY, connectAccount))
                .thenReturn(gatewayAccountEntity);

        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, connectAccount, payout);
        verify(mockEventService).emitEvent(any(), anyBoolean());
    }

    @Test
    void emitPayoutEventShouldNotEmitEventIfFeatureFlagToEmitEventsIsDisabled() throws QueueException {
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withId(1234L)
                .build();
                when(mockGatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue(STRIPE_ACCOUNT_ID_KEY, connectAccount))
                .thenReturn(gatewayAccountEntity);

        when(mockConnectorConfiguration.getEmitPayoutEvents()).thenReturn(false);
        payoutEmitterService = new PayoutEmitterService(mockEventService, mockConnectorConfiguration, mockGatewayAccountCredentialsService);
        payoutEmitterService.emitPayoutEvent(PayoutCreated.class, eventDate, connectAccount, payout);
        verify(mockEventService, never()).emitEvent(any(), anyBoolean());
    }

    @Test
    void emitPayoutEventShouldNotEmitEventForAnUnknownPayoutEvent() throws QueueException {
        payoutEmitterService.emitPayoutEvent(PayoutEvent.class, eventDate, null, payout);
        verify(mockEventService, never()).emitEvent(any(), anyBoolean());
    }
}
