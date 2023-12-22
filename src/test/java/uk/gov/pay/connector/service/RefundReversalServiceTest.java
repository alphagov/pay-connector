package uk.gov.pay.connector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundReversalService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@ExtendWith(MockitoExtension.class)
public class RefundReversalServiceTest {
    private RefundReversalService refundReversalService;

    private final String refundExternalId = "refund123";

    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private LedgerService mockLedgerService;

    @BeforeEach
    void setUp() {
        refundReversalService = new RefundReversalService(mockLedgerService, mockRefundDao);
    }

    @Test
    void shouldFindRefundInLedger() {

        LedgerTransaction ledgerTransaction = aValidLedgerTransaction()
                .withExternalId(refundExternalId)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus())
                .build();
        when(mockLedgerService.getTransaction(refundExternalId)).thenReturn(Optional.of(ledgerTransaction));

        Optional<Refund> result = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getExternalId(), is(refundExternalId));
    }

    @Test
    void shouldFindRefundInConnector() {
        RefundEntity refundEntityInConnector = aValidRefundEntity().withExternalId(refundExternalId).build();

        when(mockRefundDao.findByExternalId(refundExternalId)).thenReturn(Optional.of(refundEntityInConnector));

        Optional<Refund> result = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getExternalId(), is(refundExternalId));

        verifyNoInteractions(mockLedgerService);
    }
    @Test
    void shouldNotFindRefund() {
        when(mockRefundDao.findByExternalId(refundExternalId)).thenReturn(Optional.empty());
        when(mockLedgerService.getTransaction(refundExternalId)).thenReturn(Optional.empty());

        Optional<Refund> result = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId);

        assertThat(result.isPresent(), is(false));
    }
}