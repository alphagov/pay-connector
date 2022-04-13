package uk.gov.pay.connector.paymentinstrument.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class PaymentInstrumentServiceTest {
    @Mock
    private PaymentInstrumentDao mockPaymentInstrumentDao;        
    
    @Mock
    private CardDetailsEntity mockCardDetailsEntity;    
    
    private static final Instant NOW = Instant.parse("2022-03-31T15:15:00Z");  
    
    @Mock
    private ChargeService mockChargeService; 
    private PaymentInstrumentService paymentInstrumentService;
    
    @BeforeEach
    void setUp() {
        paymentInstrumentService = new PaymentInstrumentService(mockPaymentInstrumentDao, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPaymentInstrument() {
        ArgumentCaptor<PaymentInstrumentEntity> paymentInstrumentEntityCaptor = ArgumentCaptor.forClass(PaymentInstrumentEntity.class);
        var token = Map.of("token", "myToken");
        var paymentInstrument = paymentInstrumentService.createPaymentInstrument(mockCardDetailsEntity, token);
        verify(mockPaymentInstrumentDao).persist(paymentInstrumentEntityCaptor.capture());
        var actualPaymentInstrumentEntity = paymentInstrumentEntityCaptor.getValue();
        assertThat(actualPaymentInstrumentEntity.getCreatedDate(), is(NOW));
        assertThat(actualPaymentInstrumentEntity.getRecurringAuthToken(), is(token));
        assertThat(actualPaymentInstrumentEntity.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(actualPaymentInstrumentEntity.getStartDate(), is(NOW));
        assertThat(actualPaymentInstrumentEntity.getPaymentInstrumentStatus(), is(PaymentInstrumentStatus.CREATED));
        assertThat(actualPaymentInstrumentEntity, is(paymentInstrument));
    }
}
