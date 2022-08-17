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
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.charge.AgreementSetup;
import uk.gov.pay.connector.events.model.charge.PaymentInstrumentConfirmed;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

@ExtendWith(MockitoExtension.class)
class LinkPaymentInstrumentToAgreementServiceTest {

    private static final String AGREEMENT_ID = "I am very agreeable";
    private static final long GATEWAY_ACCOUNT_ID = 1;

    @Mock
    private AgreementDao mockAgreementDao;

    @Mock
    private LedgerService ledgerService;

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
        linkPaymentInstrumentToAgreementService = new LinkPaymentInstrumentToAgreementService(mockAgreementDao, ledgerService, clock);
    }

    @Test
    void linksPaymentInstrumentFromChargeToAgreementFromChargeAndSetsPaymentInstrumentToActive() {
        when(mockAgreementDao.findByExternalId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockAgreementEntity));
        when(mockAgreementEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockAgreementEntity.getPaymentInstrument()).thenReturn(Optional.of(mockPaymentInstrumentEntity));
        when(mockPaymentInstrumentEntity.getExternalId()).thenReturn("payment instrument external ID");
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(mockPaymentInstrumentEntity).withAgreementId(AGREEMENT_ID).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreementFromCharge(chargeEntity);

        verify(mockAgreementEntity).setPaymentInstrument(mockPaymentInstrumentEntity);
        verify(mockPaymentInstrumentEntity).setPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE);
        verify(ledgerService).postEvent(List.of(
                AgreementSetup.from(mockAgreementEntity, ZonedDateTime.now(clock)),
                PaymentInstrumentConfirmed.from(mockAgreementEntity, ZonedDateTime.now(clock))
        ));
    }

    @Test
    void logsErrorIfChargeDoesNotHavePaymentInstrument() {
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(null).withAgreementId(AGREEMENT_ID).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreementFromCharge(chargeEntity);

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
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(mockPaymentInstrumentEntity).withAgreementId(null).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreementFromCharge(chargeEntity);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        var loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getFormattedMessage(),
                is("Expected charge " + chargeEntity.getExternalId() + " to have an agreement but it does not have one"));

        verifyNoInteractions(mockAgreementEntity);
        verifyNoInteractions(mockPaymentInstrumentEntity);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void logsErrorIfChargeHasPaymentInstrumentAndAgreementIdButAgreementNotFound() {
        given(mockAgreementDao.findByExternalId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).willReturn(Optional.empty());
        var chargeEntity = aValidChargeEntity().withPaymentInstrument(mockPaymentInstrumentEntity).withAgreementId(AGREEMENT_ID).build();

        linkPaymentInstrumentToAgreementService.linkPaymentInstrumentFromChargeToAgreementFromCharge(chargeEntity);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        var loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getFormattedMessage(),
                is("Charge " + chargeEntity.getExternalId() + " references agreement " + AGREEMENT_ID + " but that agreement does not exist"));

        verifyNoInteractions(mockAgreementEntity);
        verifyNoInteractions(mockPaymentInstrumentEntity);
        verifyNoInteractions(ledgerService);
    }

}
