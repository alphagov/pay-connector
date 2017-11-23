package uk.gov.pay.connector.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEventEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionEventEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeTransactionEntityBuilder.aChargeTransactionEntity;

@RunWith(MockitoJUnitRunner.class)
public class StatusUpdaterTest {
    private static final String SOME_EXTERNAL_ID = "someExternalId";
    @Mock
    private PaymentRequestDao mockPaymentRequestDao;

    @Test
    public void updatesChangeTransactionStatus() throws Exception {
        PaymentRequestEntity paymentRequest = PaymentRequestEntityFixture.aValidPaymentRequestEntity()
                .withTransactions(aChargeTransactionEntity().withStatus(ChargeStatus.CREATED).build())
                .build();
        when(mockPaymentRequestDao.findByExternalId(SOME_EXTERNAL_ID)).thenReturn(Optional.of(paymentRequest));

        ChargeStatus newChargeStatus = ENTERING_CARD_DETAILS;
        new StatusUpdater(mockPaymentRequestDao).updateChargeTransactionStatus(SOME_EXTERNAL_ID, newChargeStatus);

        assertThat(paymentRequest.getChargeTransaction().getStatus(), is(newChargeStatus));
    }

    @Test
    public void doesNotUpdatesChangeTransactionStatusIfChargeTransactionDoesNotExist() throws Exception {
        PaymentRequestEntity paymentRequest = mock(PaymentRequestEntity.class);
        when(mockPaymentRequestDao.findByExternalId(SOME_EXTERNAL_ID)).thenReturn(Optional.of(paymentRequest));
        when(paymentRequest.hasChargeTransaction()).thenReturn(false);

        new StatusUpdater(mockPaymentRequestDao).updateChargeTransactionStatus(SOME_EXTERNAL_ID, ENTERING_CARD_DETAILS);

        verify(paymentRequest).hasChargeTransaction();
        verifyNoMoreInteractions(paymentRequest);
    }

    @Test
    public void setsGatewayEventTimeIsOneIfProvided() throws Exception {
        PaymentRequestEntity paymentRequest = mock(PaymentRequestEntity.class);
        when(mockPaymentRequestDao.findByExternalId(SOME_EXTERNAL_ID)).thenReturn(Optional.of(paymentRequest));
        ChargeTransactionEntity chargeTransaction = mock(ChargeTransactionEntity.class);
        when(paymentRequest.hasChargeTransaction()).thenReturn(true);
        when(paymentRequest.getChargeTransaction()).thenReturn(chargeTransaction);
        when(chargeTransaction.getStatus()).thenReturn(CREATED);

        ChargeStatus newChargeStatus = ENTERING_CARD_DETAILS;
        ZonedDateTime gatewayEventTime = ZonedDateTime.now();
        new StatusUpdater(mockPaymentRequestDao).updateChargeTransactionStatus(SOME_EXTERNAL_ID, newChargeStatus, gatewayEventTime);

        verify(chargeTransaction).updateStatus(newChargeStatus, gatewayEventTime);
    }

    @Test
    public void canHandleGatewayEventDateBeingNullAndDoesNotSetAValue() throws Exception {
        PaymentRequestEntity paymentRequest = PaymentRequestEntityFixture.aValidPaymentRequestEntity()
                .withTransactions(aChargeTransactionEntity().withStatus(ChargeStatus.CREATED).build())
                .build();
        when(mockPaymentRequestDao.findByExternalId(SOME_EXTERNAL_ID)).thenReturn(Optional.of(paymentRequest));

        new StatusUpdater(mockPaymentRequestDao).updateChargeTransactionStatus(SOME_EXTERNAL_ID, ENTERING_CARD_DETAILS, null);

        List<TransactionEventEntity> transactionEvents = paymentRequest.getChargeTransaction().getTransactionEvents();

        assertThat(transactionEvents.stream().anyMatch(hasEventOfType(ENTERING_CARD_DETAILS)), is(true));
        ChargeTransactionEventEntity enteringCardDetailsEvent = (ChargeTransactionEventEntity) transactionEvents.stream()
                .filter(hasEventOfType(ENTERING_CARD_DETAILS))
                .findFirst()
                .get();
        assertThat(enteringCardDetailsEvent.getGatewayEventDate(), is(nullValue()));
    }

    private Predicate<TransactionEventEntity> hasEventOfType(ChargeStatus status) {
        return event -> event.getStatus().equals(status);
    }
}
