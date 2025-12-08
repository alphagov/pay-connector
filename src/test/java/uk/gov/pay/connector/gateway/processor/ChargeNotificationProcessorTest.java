package uk.gov.pay.connector.gateway.processor;

import org.exparity.hamcrest.date.InstantMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

@ExtendWith(MockitoExtension.class)
class ChargeNotificationProcessorTest {

    protected static final long GATEWAY_ACCOUNT_ID = 10L;

    protected ChargeNotificationProcessor chargeNotificationProcessor;
    protected GatewayAccountEntity gatewayAccount;

    @Mock
    protected ChargeService chargeService;

    @Mock
    protected EventService eventService;

    @BeforeEach
    void setUp() {
        gatewayAccount = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .build();
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);
        chargeNotificationProcessor = new ChargeNotificationProcessor(chargeService, eventService);
    }

    @Test
    void receivedCaptureNotification_shouldEmitEvent() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_ERROR).build();
        Charge charge = Charge.from(chargeEntity);

        chargeNotificationProcessor.processCaptureNotificationForExpungedCharge(gatewayAccount, charge.getGatewayTransactionId(), charge, CAPTURED);

        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);

        verify(eventService).emitEvent(eventArgumentCaptor.capture());
        Event event = eventArgumentCaptor.getValue();

        assertThat(event.getEventType(), is("CAPTURE_CONFIRMED_BY_GATEWAY_NOTIFICATION"));
        assertThat(event.getTimestamp(), InstantMatchers.within(5, SECONDS, Instant.now()));
        assertThat(event.getResourceType(), is(ResourceType.PAYMENT));
    }
}
