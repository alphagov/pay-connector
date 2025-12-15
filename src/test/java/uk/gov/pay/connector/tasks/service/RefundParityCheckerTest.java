package uk.gov.pay.connector.tasks.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.pact.RefundHistoryEntityFixture;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;

@ExtendWith(MockitoExtension.class)
class RefundParityCheckerTest {

    @Mock
    private RefundDao mockRefundDao;
    @InjectMocks
    RefundParityChecker refundParityChecker;
    private Long gatewayAccountId = secureRandomLong();
    private RefundEntity refundEntity;

    @BeforeEach
    public void setUp() {
        refundEntity = RefundEntityFixture.aValidRefundEntity().build();
    }

    @Test
    void parityCheck_shouldMatchIfRefundMatchesWithLedgerTransaction() {
        RefundHistory refundHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity().
                withHistoryStartDate(refundEntity.getCreatedDate()).build();

        when(mockRefundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(refundEntity.getExternalId(), CREATED))
                .thenReturn(Optional.of(refundHistory));

        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).build();
        assertParityCheckStatus(transaction, EXISTS_IN_LEDGER);
    }

    @Test
    void parityCheck_shouldReturnMissingInLedgerIfTransactionIsNull() {
        assertParityCheckStatus(null, MISSING_IN_LEDGER);
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfRefundDoesNotMatchWithLedger() {
        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).withAmount(null).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfUserEmailDoesNotMatchWithLedger() {
        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).withRefundedByUserEmail(null).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfUserExternalIdDoesNotMatchWithLedger() {
        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).withRefundedBy(null).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfGatewayTransactionIdDoesNotMatchWithLedger() {
        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).withGatewayTransactionId(null).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfStatusDoesNotMatchWithLedger() {
        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).withStatus(REFUNDED.toExternal().getStatus()).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);

        transaction = from(gatewayAccountId, refundEntity).withStatus(null).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfCreatedDateDoesNotMatchWithLedger() {
        RefundHistory refundHistory = RefundHistoryEntityFixture.aValidRefundHistoryEntity().
                withHistoryStartDate(refundEntity.getCreatedDate()).build();

        when(mockRefundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(refundEntity.getExternalId(), CREATED))
                .thenReturn(Optional.of(refundHistory));

        LedgerTransaction transaction = from(gatewayAccountId, refundEntity).withCreatedDate(ZonedDateTime.now(UTC).minusDays(1)).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);

        transaction = from(gatewayAccountId, refundEntity).withCreatedDate(null).build();
        assertParityCheckStatus(transaction, DATA_MISMATCH);
    }

    private void assertParityCheckStatus(LedgerTransaction transaction, ParityCheckStatus parityCheckStatus) {
        ParityCheckStatus status = refundParityChecker.checkParity(refundEntity, transaction);
        assertThat(status, is(parityCheckStatus));
    }
}
