package uk.gov.pay.connector.paymentinstrument.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentInstrumentServiceTest {
    @Mock
    private PaymentInstrumentDao mockPaymentInstrumentDao;        

    private ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

    private static final Instant NOW = Instant.parse("2022-03-31T15:15:00Z");  

    @Mock
    private PaymentInstrumentService paymentInstrumentService;

    @Mock
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        paymentInstrumentService = new PaymentInstrumentService(mockPaymentInstrumentDao, ledgerService, InstantSource.fixed(NOW));
    }

    @Test
    void createPaymentInstrument() {
        ArgumentCaptor<PaymentInstrumentEntity> paymentInstrumentEntityCaptor = ArgumentCaptor.forClass(PaymentInstrumentEntity.class);
        var token = Map.of("token", "myToken");
        var paymentInstrument = paymentInstrumentService.createPaymentInstrument(chargeEntity, token);
        verify(mockPaymentInstrumentDao).persist(paymentInstrumentEntityCaptor.capture());
        var actualPaymentInstrumentEntity = paymentInstrumentEntityCaptor.getValue();
        assertThat(actualPaymentInstrumentEntity.getCreatedDate(), is(NOW));
        assertThat(actualPaymentInstrumentEntity.getRecurringAuthToken(), is(Optional.of(token)));
        assertThat(actualPaymentInstrumentEntity.getCardDetails(), is(chargeEntity.getCardDetails()));
        assertThat(actualPaymentInstrumentEntity.getStartDate(), is(NOW));
        assertThat(actualPaymentInstrumentEntity.getStatus(), is(PaymentInstrumentStatus.CREATED));
        assertThat(actualPaymentInstrumentEntity, is(paymentInstrument));
    }
}
