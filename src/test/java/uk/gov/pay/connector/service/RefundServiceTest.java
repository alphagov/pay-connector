package uk.gov.pay.connector.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.exception.GatewayAccountDisabledException;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.ChargeRefundResponse;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.model.domain.RefundTransactionsForPaymentFixture.aValidRefundTransactionsForPayment;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;

@ExtendWith(MockitoExtension.class)
public class RefundServiceTest {

    private RefundService refundService;
    private Long refundId;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private PaymentProviders mockProviders;
    @Mock
    private PaymentProvider mockProvider;
    @Mock
    private UserNotificationService mockUserNotificationService;
    @Mock
    private StateTransitionService mockStateTransitionService;
    @Mock
    private LedgerService mockLedgerService;
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    private ChargeEntity chargeEntity;
    private List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntityList = new ArrayList<>();

    private final String externalChargeId = "chargeId";
    private final Long accountId = 2L;
    private final GatewayAccountEntity account = aGatewayAccountEntity()
            .withId(accountId)
            .withType(TEST)
            .withGatewayAccountCredentials(gatewayAccountCredentialsEntityList)
            .build();
    private final GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
            .aGatewayAccountCredentialsEntity()
            .withGatewayAccountEntity(account)
            .withState(GatewayAccountCredentialState.ACTIVE)
            .build();

    @BeforeEach
    void setUp() {
        refundId = ThreadLocalRandom.current().nextLong();
        lenient().when(mockProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockProvider);
        lenient().when(mockProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        refundService = new RefundService(
                mockRefundDao, mockGatewayAccountDao, mockProviders, mockUserNotificationService, mockStateTransitionService, mockLedgerService, mockGatewayAccountCredentialsService
        );
    }

    @Test
    void shouldRefundSuccessfully_forWorldpay() {
        Long refundAmount = 100L;
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withPaymentProvider(WORLDPAY.getName())
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .build();
        Charge charge = Charge.from(chargeEntity);

        RefundEntity refundEntity = aValidRefundEntity()
                .withAmount(refundAmount)
                .withChargeExternalId(chargeEntity.getExternalId())
                .build();
        RefundEntity spiedRefundEntity = spy(refundEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        setupWorldpayMock(spiedRefundEntity.getExternalId(), null);

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));
        when(mockRefundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of());

        ChargeRefundResponse gatewayResponse = refundService.doRefund(accountId, charge, new RefundRequest(refundAmount, chargeEntity.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getGatewayRefundResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getGatewayRefundResponse().getError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockRefundDao).persist(argThat(aRefundEntity(refundAmount, chargeEntity)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(chargeEntity, refundAmount)));
        verify(mockRefundDao, times(1)).findById(refundId);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setGatewayTransactionId(refundEntity.getExternalId());
    }

    @Test
    void shouldRefundSuccessfully_forHistoricPayment() {
        Long refundAmount = 100L;

        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);

        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(externalChargeId);
        transaction.setAmount(500L);
        transaction.setRefundSummary(refundSummary);
        transaction.setReference("reference");
        transaction.setDescription("description");
        transaction.setGatewayAccountId(String.valueOf(accountId));
        transaction.setCreatedDate(Instant.now().toString());
        transaction.setPaymentProvider(WORLDPAY.getName());
        transaction.setLive(true);
        Charge charge = Charge.from(transaction);

        RefundEntity refundEntity = aValidRefundEntity().withChargeExternalId(externalChargeId).withAmount(refundAmount).build();
        RefundEntity spiedRefundEntity = spy(refundEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);

        setupWorldpayMock(spiedRefundEntity.getExternalId(), null);

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        LedgerTransaction ledgerRefund = aValidLedgerTransaction()
                .withExternalId("a-refund-in-ledger")
                .withParentTransactionId(externalChargeId)
                .withAmount(100L)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus())
                .build();
        var refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withParentTransactionId(externalChargeId)
                .withTransactions(List.of(ledgerRefund))
                .build();
        when(mockLedgerService.getRefundsForPayment(accountId, externalChargeId))
                .thenReturn(refundTransactionsForPayment);

        ChargeRefundResponse gatewayResponse = refundService.doRefund(accountId, Charge.from(transaction), new RefundRequest(refundAmount, 400L, userExternalId)).get();

        assertThat(gatewayResponse.getGatewayRefundResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getGatewayRefundResponse().getError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockRefundDao).persist(any(RefundEntity.class));
        verify(mockProvider).refund(any(RefundGatewayRequest.class));
        verify(mockRefundDao, times(1)).findById(refundId);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setGatewayTransactionId(refundEntity.getExternalId());
    }

    @Test
    void shouldCreateARefundEntitySuccessfully() {
        Long refundAmount = 100L;

        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withPaymentProvider(WORLDPAY.getName())
                .build();

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        RefundEntity refundEntity = refundService.createRefundEntity(new RefundRequest(refundAmount, chargeEntity.getAmount(), userExternalId), account, Charge.from(chargeEntity));

        assertThat(refundEntity.getAmount(), is(refundAmount));
        assertThat(refundEntity.getStatus(), is(CREATED));
        assertThat(refundEntity.getChargeExternalId(), is(externalChargeId));
    }

    @Test
    void shouldOverrideGeneratedReferenceIfProviderReturnAReference() {
        Long amount = 100L;

        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        String generatedReference = "generated-reference";

        String providerReference = "worldpay-reference";
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withPaymentProvider(WORLDPAY.getName())
                .build();

        String refundExternalId = "someExternalId";
        Charge charge = Charge.from(chargeEntity);
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withExternalId(refundExternalId).withReference(generatedReference).build());

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(providerReference, null);

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundResponse gatewayResponse = refundService.doRefund(accountId, Charge.from(chargeEntity), new RefundRequest(amount, chargeEntity.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getGatewayRefundResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setGatewayTransactionId(providerReference);
    }

    @Test
    void shouldStoreEmptyGatewayReferenceIfGatewayReturnsAnError() {
        Long amount = 100L;

        gatewayAccountCredentialsEntityList.add(GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(account)
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build());
        String generatedReference = "generated-reference";
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withPaymentProvider(WORLDPAY.getName())
                .build();

        String refundExternalId = "someExternalId";
        Charge charge = Charge.from(chargeEntity);
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withExternalId(refundExternalId).withReference(generatedReference).build());

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(null, "error-code");

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundResponse gatewayResponse = refundService.doRefund(accountId, Charge.from(chargeEntity), new RefundRequest(amount, chargeEntity.getAmount(), userExternalId)).get();
        assertThat(gatewayResponse.getGatewayRefundResponse().isSuccessful(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity, never()).setGatewayTransactionId(anyString());
    }

    @Test
    void shouldRefundAndSendEmail_whenGatewayRefundStateIsComplete_forChargeWithNoCorporateSurcharge() {
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        String reference = "reference";

        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId(reference)
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withPaymentProvider(SANDBOX.getName())
                .build();
        Optional<Charge> optionalCharge = Optional.of(Charge.from(chargeEntity));

        testSuccessfulRefund(chargeEntity, 100L, chargeEntity.getAmount());
    }

    @Test
    void shouldRefundAndSendEmail_whenGatewayRefundStatusIsComplete_forChargeWithCorporateSurcharge() {
        gatewayAccountCredentialsEntityList.add(GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(account)
                .withPaymentProvider(SANDBOX.getName())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build());
        String reference = "reference";

        long corporateSurcharge = 50L;
        chargeEntity = aValidChargeEntity()
                .withCorporateSurcharge(corporateSurcharge)
                .withGatewayAccountEntity(account)
                .withTransactionId(reference)
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withPaymentProvider(SANDBOX.getName())
                .build();

        // when there is a corporate surcharge we expect the amount available for refund to include this
        long amountAvailableForRefund = chargeEntity.getAmount() + corporateSurcharge;

        testSuccessfulRefund(chargeEntity, amountAvailableForRefund, amountAvailableForRefund);
    }

    private void testSuccessfulRefund(ChargeEntity chargeEntity, Long refundAmount, Long amountAvailableForRefund) {
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withAmount(refundAmount).build());

        Long accountId = chargeEntity.getGatewayAccount().getId();

        when(mockProviders.byName(SANDBOX)).thenReturn(mockProvider);

        setupSandboxMock(chargeEntity.getGatewayTransactionId(), null);
        Charge charge = Charge.from(chargeEntity);

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            spiedRefundEntity.setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(chargeEntity.getGatewayAccount()));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        ChargeRefundResponse gatewayResponse = refundService.doRefund(accountId, charge, new RefundRequest(refundAmount, amountAvailableForRefund, userExternalId)).get();

        assertThat(gatewayResponse.getGatewayRefundResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getGatewayRefundResponse().getError().isPresent(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockRefundDao, times(2)).findById(refundId);
        verify(mockUserNotificationService).sendRefundIssuedEmail(spiedRefundEntity, charge, chargeEntity.getGatewayAccount());

        // should set refund status to both REFUND_SUBMITTED and REFUNDED in order - as gateway refund state is COMPLETE
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUNDED);
    }

    @Test
    void shouldFailWhenGatewayAccountDisabled() {
        var disabledAccount = aGatewayAccountEntity()
                .withId(accountId)
                .withType(TEST)
                .withGatewayAccountCredentials(gatewayAccountCredentialsEntityList)
                .withDisabled(true)
                .build();

        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(disabledAccount)
                .withExternalId(externalChargeId)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(disabledAccount));

        var thrown = assertThrows(GatewayAccountDisabledException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(100L, 0, userExternalId)));
        assertThat(thrown.getMessage(), is("Attempt to create a refund for a disabled gateway account"));
        verify(mockRefundDao, never()).persist(new RefundEntity(chargeEntity.getAmount(), userExternalId, null, null));
    }

    @Test
    void shouldFailWhenGatewayAccountCredentialEntityNotFound() {
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withExternalId(externalChargeId)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.empty());

        var thrown = assertThrows(GatewayAccountCredentialsNotFoundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(100L, 0, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 404 Not Found"));
        verify(mockRefundDao, never()).persist(new RefundEntity(chargeEntity.getAmount(), userExternalId, null, null));
    }

    @Test
    void shouldFailWhenChargeExternalRefundIsNotAvailable() {
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withExternalId(externalChargeId)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockRefundDao.findRefundsByChargeExternalId(externalChargeId)).thenReturn(List.of());
        when(mockProvider.getExternalChargeRefundAvailability(eq(charge), eq(List.of()))).thenReturn(EXTERNAL_UNAVAILABLE);

        var thrown = assertThrows(RefundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(100L, 0, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 400 Bad Request"));
        assertThat(((ErrorResponse) thrown.getResponse().getEntity()).identifier(), is(ErrorIdentifier.REFUND_NOT_AVAILABLE));
        verify(mockRefundDao, never()).persist(any());
    }

    @Test
    void shouldFailWhenHistoricChargeAndIsDisputed() {
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        LedgerTransaction ledgerTransaction = aValidLedgerTransaction()
                .withGatewayAccountId(accountId)
                .withExternalId(externalChargeId)
                .withRefundSummary(refundSummary)
                .withDisputed(true)
                .build();

        Charge charge = Charge.from(ledgerTransaction);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockRefundDao.findRefundsByChargeExternalId(externalChargeId)).thenReturn(List.of());
        when(mockProvider.getExternalChargeRefundAvailability(eq(charge), eq(List.of()))).thenReturn(EXTERNAL_UNAVAILABLE);

        var refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withParentTransactionId(externalChargeId)
                .withTransactions(List.of())
                .build();
        when(mockLedgerService.getRefundsForPayment(accountId, externalChargeId))
                .thenReturn(refundTransactionsForPayment);

        var thrown = assertThrows(RefundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(100L, 0, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 400 Bad Request"));
        assertThat(((ErrorResponse) thrown.getResponse().getEntity()).identifier(), is(ErrorIdentifier.REFUND_NOT_AVAILABLE_DUE_TO_DISPUTE));
        verify(mockRefundDao, never()).persist(any());
    }

    @Test
    void shouldFailWhenAmountAvailableForRefundMismatchesWithoutCorporateSurcharge() {
        Long amount = 1000L;
        account.setId(accountId);
        account.setGatewayAccountCredentials(gatewayAccountCredentialsEntityList);
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withAmount(amount)
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();
        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockRefundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of());

        var thrown = assertThrows(RefundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(amount, amount + 1, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 412 Precondition Failed"));
        assertThat(((ErrorResponse) thrown.getResponse().getEntity()).identifier(), is(ErrorIdentifier.REFUND_AMOUNT_AVAILABLE_MISMATCH));

        verifyNoMoreInteractions(mockRefundDao);
    }

    @Test
    void shouldFailWhenAmountAvailableForRefundWithCorporateSurchargeMismatches() {
        Long amount = 1000L;
        Long corporateSurcharge = 250L;
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withAmount(amount)
                .withExternalId(externalChargeId)
                .withCorporateSurcharge(corporateSurcharge)
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .withPaymentProvider(SANDBOX.getName())
                .build();
        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockRefundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of());

        // this should fail because amountAvailableForRefund is not including corporate surcharge
        var thrown = assertThrows(RefundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(amount, amount, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 412 Precondition Failed"));
        assertThat(((ErrorResponse) thrown.getResponse().getEntity()).identifier(), is(ErrorIdentifier.REFUND_AMOUNT_AVAILABLE_MISMATCH));

        verifyNoMoreInteractions(mockRefundDao);
    }

    @Test
    void shouldFailWhenANewRefundHasBeenCreatedSincePreviouslyQueried() {
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withAmount(1000L)
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .withPaymentProvider(SANDBOX.getName())
                .build();

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));

        RefundEntity refundExpungedSinceWeFirstChecked = aValidRefundEntity()
                .withAmount(100L)
                .withExternalId("refund1")
                .withChargeExternalId(externalChargeId)
                .withStatus(REFUNDED)
                .build();

        LedgerTransaction refundObtainedFromLedger = aValidLedgerTransaction()
                .withAmount(100L)
                .withExternalId("refund2")
                .withParentTransactionId(externalChargeId)
                .withStatus(REFUNDED.toExternal().getStatus())
                .build();

        RefundEntity newlyCreatedRefund = aValidRefundEntity()
                .withAmount(100L)
                .withExternalId("refund3")
                .withChargeExternalId(externalChargeId)
                .withStatus(CREATED)
                .build();

        Charge charge = Charge.from(chargeEntity);
        charge.setHistoric(true);

        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        // The second time we query, return a different list of refunds containing a new refund and not containing the
        // first refund, which has since been expunged. Both refunds are for the same amount to ensure that we are
        // still including the newly expunged refund in the calculation.
        when(mockRefundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId()))
                .thenReturn(List.of(refundExpungedSinceWeFirstChecked))
                .thenReturn(List.of(newlyCreatedRefund));

        var refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withTransactions(List.of(refundObtainedFromLedger))
                .withParentTransactionId(externalChargeId).build();
        when(mockLedgerService.getRefundsForPayment(accountId, externalChargeId)).thenReturn(refundTransactionsForPayment);

        var thrown = assertThrows(RefundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(100L, 800L, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 412 Precondition Failed"));
        assertThat(((ErrorResponse) thrown.getResponse().getEntity()).identifier(), is(ErrorIdentifier.REFUND_AMOUNT_AVAILABLE_MISMATCH));

        verifyNoMoreInteractions(mockRefundDao);
    }

    @Test
    void shouldFailWhenRefundAmountExceedsRefundAmountAvailable() {
        var amountAvailableForRefund = 1000L;
        var amount = amountAvailableForRefund + 1;
        account.setId(accountId);
        account.setGatewayAccountCredentials(gatewayAccountCredentialsEntityList);
        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        chargeEntity = aValidChargeEntity()
                .withAmount(amountAvailableForRefund)
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockRefundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId())).thenReturn(List.of());

        var thrown = assertThrows(RefundException.class,
                () -> refundService.doRefund(accountId, charge, new RefundRequest(amount, amountAvailableForRefund, userExternalId)));
        assertThat(thrown.getMessage(), is("HTTP 400 Bad Request"));
        assertThat(((ErrorResponse) thrown.getResponse().getEntity()).identifier(), is(ErrorIdentifier.REFUND_NOT_AVAILABLE));

        verifyNoMoreInteractions(mockRefundDao);
    }

    @Test
    void shouldUpdateRefundRecordToFailWhenRefundFails() {
        Long amount = 100L;

        gatewayAccountCredentialsEntityList.add(gatewayAccountCredentialsEntity);
        ChargeEntity capturedCharge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .withPaymentProvider(WORLDPAY.getName())
                .build();

        String refundReference = "someReference";
        String refundExternalId = "refundExternalId";
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity()
                .withReference(refundReference)
                .withExternalId(refundExternalId)
                .withAmount(amount)
                .build()
        );
        Charge charge = Charge.from(capturedCharge);

        when(mockGatewayAccountDao.findById(accountId)).thenReturn(Optional.of(account));
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, account)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);

        setupWorldpayMock(null, "error-code");

        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundResponse gatewayResponse = refundService.doRefund(accountId, Charge.from(capturedCharge), new RefundRequest(amount, capturedCharge.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getGatewayRefundResponse().getError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayRefundResponse().toString(),
                is("Randompay refund response (errorCode: error-code)"));
        assertThat(gatewayResponse.getGatewayRefundResponse().getError().get().getErrorType(), is(ErrorType.GENERIC_GATEWAY_ERROR));

        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, capturedCharge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(capturedCharge, amount)));
        verify(mockRefundDao, times(1)).findById(refundId);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_ERROR);
    }

    @Test
    void shouldOfferRefundStateTransition() {
        ChargeEntity charge = aValidChargeEntity()
                .build();
        RefundEntity refundEntity = aValidRefundEntity().withAmount(100L).build();

        refundService.transitionRefundState(refundEntity, charge.getGatewayAccount(), CREATED, Charge.from(charge));
        verify(mockStateTransitionService).offerRefundStateTransition(refundEntity, CREATED);
    }

    @Test
    void shouldFindRefundsGivenValidNotHistoricCharge() {
        Charge charge = Charge.from(aValidChargeEntity().build());
        charge.setHistoric(false);

        RefundEntity refundOne = aValidRefundEntity()
                .withChargeExternalId(charge.getExternalId())
                .withAmount(100L)
                .withStatus(CREATED)
                .build();
        RefundEntity refundTwo = aValidRefundEntity()
                .withChargeExternalId(charge.getExternalId())
                .withAmount(400L)
                .withStatus(RefundStatus.REFUND_SUBMITTED)
                .build();

        when(mockRefundDao.findRefundsByChargeExternalId(charge.getExternalId()))
                .thenReturn(List.of(refundOne, refundTwo));

        List<Refund> refunds = refundService.findRefunds(charge);

        verify(mockLedgerService, never()).getRefundsForPayment(charge.getGatewayAccountId(), charge.getExternalId());

        assertThat(refunds.size(), is(2));
        assertThat(refunds.get(0).getChargeExternalId(), is(charge.getExternalId()));
        assertThat(refunds.get(0).getAmount(), is(refundOne.getAmount()));
        assertThat(refunds.get(0).getExternalStatus(), is(RefundStatus.CREATED.toExternal()));
        assertThat(refunds.get(1).getExternalStatus(), is(RefundStatus.REFUND_SUBMITTED.toExternal()));
        assertThat(refunds.get(1).getAmount(), is(refundTwo.getAmount()));
    }

    @Test
    void shouldFindRefundsIncludingExpungedRefundsFromLedger() {
        Charge charge = Charge.from(aValidChargeEntity().build());
        charge.setHistoric(true);

        String refundInDatabaseAndLedgerId = "refund-in-database-and-ledger";
        String refundOnlyInDatabaseId = "refund-only-in-database";
        String refundOnlyInLedgerId = "refund-only-in-ledger";

        RefundEntity databaseRefund1 = aValidRefundEntity()
                .withExternalId(refundInDatabaseAndLedgerId)
                .withChargeExternalId(charge.getExternalId())
                .withAmount(100L)
                .withStatus(REFUNDED)
                .build();
        RefundEntity databaseRefund2 = aValidRefundEntity()
                .withExternalId(refundOnlyInDatabaseId)
                .withChargeExternalId(charge.getExternalId())
                .withAmount(200L)
                .withStatus(RefundStatus.REFUND_SUBMITTED)
                .build();

        LedgerTransaction ledgerRefund1 = aValidLedgerTransaction()
                .withExternalId(refundInDatabaseAndLedgerId)
                .withParentTransactionId(charge.getExternalId())
                .withAmount(100L)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus())
                .build();
        LedgerTransaction ledgerRefund2 = aValidLedgerTransaction()
                .withExternalId(refundOnlyInLedgerId)
                .withParentTransactionId(charge.getExternalId())
                .withAmount(300L)
                .withStatus(ExternalRefundStatus.EXTERNAL_ERROR.getStatus())
                .build();

        when(mockRefundDao.findRefundsByChargeExternalId(charge.getExternalId()))
                .thenReturn(List.of(databaseRefund1, databaseRefund2));

        var refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withParentTransactionId(charge.getExternalId())
                .withTransactions(List.of(ledgerRefund1, ledgerRefund2))
                .build();
        when(mockLedgerService.getRefundsForPayment(charge.getGatewayAccountId(), charge.getExternalId()))
                .thenReturn(refundTransactionsForPayment);

        List<Refund> refunds = refundService.findRefunds(charge);
        assertThat(refunds, hasSize(3));
        assertThat(refunds.get(0).getAmount(), is(100L));
        assertThat(refunds.get(0).getExternalStatus(), is(ExternalRefundStatus.EXTERNAL_SUCCESS));
        assertThat(refunds.get(1).getAmount(), is(200L));
        assertThat(refunds.get(1).getExternalStatus(), is(ExternalRefundStatus.EXTERNAL_SUBMITTED));
        assertThat(refunds.get(2).getAmount(), is(300L));
        assertThat(refunds.get(2).getExternalStatus(), is(ExternalRefundStatus.EXTERNAL_ERROR));
    }

    @Test
    void shouldFindHistoricRefundsForChargeExternalIdAndRefundGatewayTransactionId() {
        Charge charge = Charge.from(aValidChargeEntity().build());

        String refundFromLedger1 = "refund-from-ledger1";
        String refundFromLedger2 = "refund-from-ledger2";

        LedgerTransaction ledgerRefund1 = aValidLedgerTransaction()
                .withExternalId(refundFromLedger1)
                .withParentTransactionId(charge.getExternalId())
                .withGatewayTransactionId("refund-gateway-tx-1")
                .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                .build();
        LedgerTransaction ledgerRefund2 = aValidLedgerTransaction()
                .withExternalId(refundFromLedger2)
                .withParentTransactionId(charge.getExternalId())
                .withGatewayTransactionId("refund-gateway-tx-2")
                .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                .build();

        var refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withParentTransactionId(charge.getExternalId())
                .withTransactions(List.of(ledgerRefund1, ledgerRefund2))
                .build();
        when(mockLedgerService.getRefundsForPayment(charge.getGatewayAccountId(), charge.getExternalId()))
                .thenReturn(refundTransactionsForPayment);

        Optional<Refund> mayBeRefund = refundService.findHistoricRefundByChargeExternalIdAndGatewayTransactionId(charge, "refund-gateway-tx-1");
        assertThat(mayBeRefund.isPresent(), is(true));
        assertThat(mayBeRefund.get().getExternalId(), is(ledgerRefund1.getTransactionId()));
        assertThat(mayBeRefund.get().getGatewayTransactionId(), is("refund-gateway-tx-1"));
    }

    @Test
    void shouldReturnOptionalEmptyIfNoHistoricRefundsExistsForChargeExternalIdAndRefundGatewayTransactionId() {
        Charge charge = Charge.from(aValidChargeEntity().build());

        var refundTransactionsForPayment = aValidRefundTransactionsForPayment()
                .withParentTransactionId(charge.getExternalId())
                .withTransactions(List.of())
                .build();
        when(mockLedgerService.getRefundsForPayment(charge.getGatewayAccountId(), charge.getExternalId()))
                .thenReturn(refundTransactionsForPayment);

        Optional<Refund> mayBeRefund = refundService.findHistoricRefundByChargeExternalIdAndGatewayTransactionId(charge, "some-gateway-tx-id");
        assertThat(mayBeRefund.isPresent(), is(false));
    }

    @Test
    void findByExternalIdShouldReturnRefundEntityIfFound() {
        RefundEntity refund = aValidRefundEntity().build();
        when(mockRefundDao.findByExternalId("refund-external-id")).thenReturn(Optional.of(refund));
        Optional<RefundEntity> mayBeRefund = refundService.findRefundByExternalId("refund-external-id");
        assertThat(mayBeRefund.isPresent(), is(true));
        assertThat(mayBeRefund.get().getExternalId(), is(refund.getExternalId()));
    }

    @Test
    void findByExternalIdShouldReturnEmptyOptionalIfRefundEntityIsNotFound() {
        when(mockRefundDao.findByExternalId("refund-external-id")).thenReturn(Optional.empty());
        Optional<RefundEntity> refund = refundService.findRefundByExternalId("refund-external-id");
        assertThat(refund.isPresent(), is(false));
    }

    private ArgumentMatcher<RefundEntity> aRefundEntity(long amount, ChargeEntity chargeEntity) {
        return object -> {
            RefundEntity refundEntity = object;
            return refundEntity.getAmount() == amount &&
                    refundEntity.getChargeExternalId().equals(chargeEntity.getExternalId());
        };
    }

    private ArgumentMatcher<RefundGatewayRequest> aRefundRequestWith(ChargeEntity capturedCharge, long amountInPence) {
        return object -> {
            RefundGatewayRequest refundGatewayRequest = object;
            return refundGatewayRequest.getGatewayAccount().equals(capturedCharge.getGatewayAccount()) &&
                    refundGatewayRequest.getTransactionId().equals(capturedCharge.getGatewayTransactionId()) &&
                    refundGatewayRequest.getAmount().equals(String.valueOf(amountInPence));
        };
    }

    private void setupWorldpayMock(String reference, String errorCode) {
        WorldpayRefundResponse worldpayResponse = mock(WorldpayRefundResponse.class);
        when(worldpayResponse.getReference()).thenReturn(Optional.ofNullable(reference));
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        when(worldpayResponse.stringify()).thenReturn("Randompay refund response (errorCode: " + errorCode + ")");

        GatewayRefundResponse.RefundState refundState;
        if (isNotBlank(errorCode)) {
            refundState = GatewayRefundResponse.RefundState.ERROR;
        } else {
            refundState = GatewayRefundResponse.RefundState.PENDING;
        }

        GatewayRefundResponse gatewayRefundResponse =
                GatewayRefundResponse.fromBaseRefundResponse(worldpayResponse, refundState);

        when(mockProvider.refund(any())).thenReturn(gatewayRefundResponse);
    }

    private void setupSandboxMock(String reference, String errorCode) {
        BaseRefundResponse baseRefundResponse = BaseRefundResponse.fromReference(reference, SANDBOX);

        GatewayRefundResponse gatewayRefundResponse = GatewayRefundResponse.fromBaseRefundResponse(baseRefundResponse,
                GatewayRefundResponse.RefundState.COMPLETE);

        when(mockProvider.refund(any())).thenReturn(gatewayRefundResponse);
    }
}
