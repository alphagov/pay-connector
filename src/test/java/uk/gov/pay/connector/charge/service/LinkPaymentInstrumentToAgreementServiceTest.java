package uk.gov.pay.connector.charge.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.agreement.AgreementSetUp;
import uk.gov.pay.connector.events.model.charge.PaymentInstrumentConfirmed;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

@ExtendWith(MockitoExtension.class)
class LinkPaymentInstrumentToAgreementServiceTest {

    private static final String AGREEMENT_ID = "I am very agreeable";
    private static final long GATEWAY_ACCOUNT_ID = 1;

    @Mock
    private PaymentInstrumentDao paymentInstrumentDao;

    @Mock
    private LedgerService ledgerService;
    
    @Mock
    private TaskQueueService taskQueueService;

    @Mock
    private AgreementEntity mockAgreementEntity;

    @Mock
    private PaymentInstrumentEntity mockPaymentInstrumentEntity;
    
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    private Clock clock;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService;

    @BeforeEach
    public void setUp() {
        var logger = (Logger) LoggerFactory.getLogger(LinkPaymentInstrumentToAgreementService.class);
        logger.setLevel(Level.ERROR);
        logger.addAppender(mockAppender);

        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        linkPaymentInstrumentToAgreementService = new LinkPaymentInstrumentToAgreementService(paymentInstrumentDao, ledgerService, taskQueueService, clock);
    }

    @Test
    void linksPaymentInstrumentFromChargeToAgreementFromChargeAndSetsPaymentInstrumentToActive() {
        String agreementExternalId = "an-agreement-external-id";
        when(mockAgreementEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockAgreementEntity.getPaymentInstrument()).thenReturn(Optional.of(mockPaymentInstrumentEntity));
        when(mockAgreementEntity.getExternalId()).thenReturn(agreementExternalId);
        when(mockPaymentInstrumentEntity.getExternalId()).thenReturn("payment instrument external ID");
        
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(mockPaymentInstrumentEntity)
                .withAgreementEntity(mockAgreementEntity).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreement(chargeEntity);

        verify(mockAgreementEntity).setPaymentInstrument(mockPaymentInstrumentEntity);
        verify(mockPaymentInstrumentEntity).setAgreementExternalId(agreementExternalId);
        verify(mockPaymentInstrumentEntity).setStatus(PaymentInstrumentStatus.ACTIVE);
        verify(ledgerService).postEvent(List.of(
                AgreementSetUp.from(mockAgreementEntity, clock.instant()),
                PaymentInstrumentConfirmed.from(mockAgreementEntity, clock.instant())
        ));
    }

    @Test
    void shouldUpdatePreviousActivePaymentInstrumentsToCancelledAndAddToTaskQueue() {
        var oldPaymentInstrument1 = mock(PaymentInstrumentEntity.class);
        var oldPaymentInstrument2 = mock(PaymentInstrumentEntity.class);
        
        String agreementExternalId = "an-agreement-external-id";
        when(mockAgreementEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockAgreementEntity.getPaymentInstrument()).thenReturn(Optional.of(mockPaymentInstrumentEntity));
        when(mockAgreementEntity.getExternalId()).thenReturn(agreementExternalId);
        when(mockPaymentInstrumentEntity.getExternalId()).thenReturn("payment instrument external ID");
        when(paymentInstrumentDao.findPaymentInstrumentsByAgreementAndStatus(agreementExternalId, PaymentInstrumentStatus.ACTIVE))
                .thenReturn(List.of(oldPaymentInstrument1, oldPaymentInstrument2));

        var chargeEntity = aValidChargeEntity().withPaymentInstrument(mockPaymentInstrumentEntity)
                .withAgreementEntity(mockAgreementEntity).build();
        
        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreement(chargeEntity);
        
        verify(oldPaymentInstrument1).setStatus(PaymentInstrumentStatus.CANCELLED);
        verify(oldPaymentInstrument2).setStatus(PaymentInstrumentStatus.CANCELLED);
        verify(taskQueueService).addDeleteStoredPaymentDetailsTask(mockAgreementEntity, oldPaymentInstrument1);
        verify(taskQueueService).addDeleteStoredPaymentDetailsTask(mockAgreementEntity, oldPaymentInstrument2);
    }

    @Test
    void logsErrorIfChargeDoesNotHavePaymentInstrument() {
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(null).withAgreementEntity(mockAgreementEntity).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreement(chargeEntity);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        var loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getFormattedMessage(),
                is("Expected charge " + chargeEntity.getExternalId() + " to have a payment instrument but it does not have one"));

        verifyNoInteractions(mockAgreementEntity);
        verifyNoInteractions(mockPaymentInstrumentEntity);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void logsErrorIfChargeHasPaymentInstrumentButDoesNotHaveAgreementId() {
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(mockPaymentInstrumentEntity).withAgreementEntity(null).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreement(chargeEntity);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        var loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getFormattedMessage(),
                is("Expected charge " + chargeEntity.getExternalId() + " to have an agreement but it does not have one"));

        verifyNoInteractions(mockAgreementEntity);
        verifyNoInteractions(mockPaymentInstrumentEntity);
        verifyNoInteractions(ledgerService);
    }

}
