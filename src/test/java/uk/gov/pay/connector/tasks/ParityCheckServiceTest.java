package uk.gov.pay.connector.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.paritycheck.CardDetails;
import uk.gov.pay.connector.paritycheck.LedgerService;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.refund.dao.RefundDao;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.commons.model.Source.CARD_API;
import static uk.gov.pay.commons.model.Source.CARD_PAYMENT_LINK;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.wallets.WalletType.APPLE_PAY;
import static uk.gov.pay.connector.wallets.WalletType.GOOGLE_PAY;

@RunWith(MockitoJUnitRunner.class)
public class ParityCheckServiceTest {

    private ParityCheckService parityCheckService;
    @Mock
    private LedgerService mockLedgerService;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private HistoricalEventEmitter mockHistoricalEventEmitter;
    private ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        parityCheckService = new ParityCheckService(mockLedgerService, mockChargeService, mockRefundDao,
                mockHistoricalEventEmitter);

        chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURED)
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withMoto(true)
                .withSource(CARD_PAYMENT_LINK)
                .withFee(10L)
                .withCorporateSurcharge(25L)
                .withWalletType(APPLE_PAY)
                .withDelayedCapture(true)
                .build();
    }

    @Test
    public void parityCheckChargeForExpunger_shouldReturnTrueIfChargeMatchesFieldsWithLedger() {
        LedgerTransaction transaction = from(chargeEntity).build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(true));
    }

    @Test
    public void parityCheckChargeForExpunger_shouldReturnFalseIfCardDetailsDoesNotMatchWithLedger() {
        chargeEntity.getCardDetails().setBillingAddress(null);
        LedgerTransaction transaction = from(chargeEntity)
                .withCardDetails(new CardDetails("test-name", null, "test-brand",
                        "6666", "123656", "11/88", null))
                .build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(false));
    }

    @Test
    public void parityCheckChargeForExpunger_shouldReturnFalseIfGatewayAccountDetailsDoesNotMatch() {
        LedgerTransaction transaction = from(chargeEntity)
                .withGatewayAccountId(345345L)
                .isLive(true)
                .withPaymentProvider("test-paymemt-provider")
                .build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(false));
        verify(mockHistoricalEventEmitter).processPaymentEvents(chargeEntity, true);
        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
    }

    @Test
    public void parityCheckChargeForExpunger_shouldReturnFalseIfFeatureSpecificFieldsDoesNotMatch() {
        LedgerTransaction transaction = from(chargeEntity)
                .withSource(CARD_API)
                .withMoto(false)
                .withDelayedCapture(false)
                .withFee(10000L)
                .withCorporateCardSurcharge(10000L)
                .withNetAmount(10000L)
                .withWalletType(GOOGLE_PAY)
                .build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(false));
        verify(mockHistoricalEventEmitter).processPaymentEvents(chargeEntity, true);
        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
    }

    @Test
    public void parityCheckChargeForExpunger_shouldReturnFalseIfChargeDoesNotMatchWithLedger() {
        LedgerTransaction transaction = aValidLedgerTransaction().withStatus("pending").build();
        when(mockLedgerService.getTransaction(chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));
        boolean matchesWithLedger = parityCheckService.parityCheckChargeForExpunger(chargeEntity);

        assertThat(matchesWithLedger, is(false));
        verify(mockHistoricalEventEmitter).processPaymentEvents(chargeEntity, true);
        verify(mockChargeService).updateChargeParityStatus(chargeEntity.getExternalId(), DATA_MISMATCH);
    }
}
