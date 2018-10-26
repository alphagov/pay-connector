package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.transaction.TransactionFlow;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.smartpay.SmartpayRefundResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.ChargeRefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;


@RunWith(MockitoJUnitRunner.class)
public class ChargeRefundServiceTest {

    private ChargeRefundService chargeRefundService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private PaymentProviders mockProviders;
    @Mock
    private PaymentProvider mockProvider;
    @Mock
    private UserNotificationService mockUserNotificationService;

    @Before
    public void setUp() {
        when(mockProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockProvider);
        when(mockProvider.getExternalChargeRefundAvailability(any(ChargeEntity.class))).thenReturn(EXTERNAL_AVAILABLE);
        chargeRefundService = new ChargeRefundService(
                mockChargeDao, mockRefundDao, mockProviders, TransactionFlow::new, mockUserNotificationService
        );
    }

    public void setupWorldpayMock(String reference, String errorCode) {
        WorldpayRefundResponse worldpayResponse = mock(WorldpayRefundResponse.class);
        when(worldpayResponse.getReference()).thenReturn(Optional.ofNullable(reference));
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        when(worldpayResponse.toString()).thenReturn("Randompay refund response (errorCode: " + errorCode + ")");
        GatewayResponseBuilder<WorldpayRefundResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse refundResponse = gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .build();
        when(mockProvider.refund(any())).thenReturn(refundResponse);
    }

    public void setupSmartpayMock(String reference, String errorCode) {
        SmartpayRefundResponse smartpayRefundResponse = mock(SmartpayRefundResponse.class);
        when(smartpayRefundResponse.getReference()).thenReturn(Optional.ofNullable(reference));
        when(smartpayRefundResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<SmartpayRefundResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse refundResponse = gatewayResponseBuilder
                .withResponse(smartpayRefundResponse)
                .build();
        when(mockProvider.refund(any())).thenReturn(refundResponse);
    }

    @Test
    public void shouldRefundSuccessfully_forWorldpay() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        RefundEntity refundEntity = aValidRefundEntity().withCharge(charge).build();
        RefundEntity spiedRefundEntity = spy(refundEntity);

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(spiedRefundEntity.getExternalId(), null);

        Long refundId = 12345L;
        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockRefundDao).findById(refundId);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setReference(refundEntity.getExternalId());

        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldRefundSuccessfully_forSmartpay() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "smartpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        String refundExternalId = "refundExternalId";
        RefundEntity refundEntity = aValidRefundEntity().withCharge(charge).withExternalId(refundExternalId).build();
        RefundEntity spiedRefundEntity = spy(refundEntity);

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockProviders.byName(SMARTPAY)).thenReturn(mockProvider);
        String reference = "refund-pspReference";
        setupSmartpayMock(reference, null);

        Long refundId = 12345L;
        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockRefundDao).findById(refundId);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verify(spiedRefundEntity).setReference(reference);

        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldOverrideGeneratedReferenceIfProviderReturnAReference() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String generatedReference = "generated-reference";

        String providerReference = "worldpay-reference";
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        String refundExternalId = "someExternalId";
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withExternalId(refundExternalId).withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(providerReference, null);

        Long refundId = 12345L;
        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference(providerReference);

    }

    @Test
    public void shouldStoreEmptyGatewayReferenceIfGatewayReturnsAnError() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String generatedReference = "generated-reference";
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        String refundExternalId = "someExternalId";
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withExternalId(refundExternalId).withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        setupWorldpayMock(null, "error-code");

        Long refundId = 12345L;
        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount(), userExternalId)).get();
        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference("");

    }

    @Test
    public void shouldRefundSuccessfullyForSandboxAndSendEmail_forChargeWithNoCorporateSurcharge() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;
        String providerName = "sandbox";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String reference = "reference";

        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId(reference)
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        testSandboxRefund(charge, 100L, charge.getAmount());
    }

    @Test
    public void shouldRefundSuccessfullyForSandboxAndSendEmail_forChargeWithCorporateSurcharge() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;
        String providerName = "sandbox";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String reference = "reference";

        long corporateSurcharge = 50L;
        ChargeEntity charge = aValidChargeEntity()
                .withCorporateSurcharge(corporateSurcharge)
                .withGatewayAccountEntity(account)
                .withTransactionId(reference)
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        // when there is a corporate surcharge we expect the amount available for refund to include this
        long amountAvailableForRefund = charge.getAmount() + corporateSurcharge;

        testSandboxRefund(charge, amountAvailableForRefund, amountAvailableForRefund);
    }

    private void testSandboxRefund(ChargeEntity charge, Long refundAmount, Long amountAvailableForRefund) {
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withCharge(charge).build());

        Long accountId = charge.getGatewayAccount().getId();
        when(mockChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));

        when(mockProviders.byName(SANDBOX)).thenReturn(mockProvider);

        setupWorldpayMock(charge.getGatewayTransactionId(), null);

        Long refundId = 12345L;
        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            spiedRefundEntity.setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, charge.getExternalId(), new RefundRequest(refundAmount, amountAvailableForRefund, userExternalId)).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId);
        verify(mockRefundDao).persist(argThat(aRefundEntity(refundAmount, charge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, refundAmount)));
        verify(mockRefundDao, times(2)).findById(refundId);
        verify(mockUserNotificationService).sendRefundIssuedEmail(spiedRefundEntity);

        verify(spiedRefundEntity).setStatus(RefundStatus.REFUNDED);

        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldFailWhenChargeNotFound() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.empty());
        expectedException.expect(ChargeNotFoundRuntimeException.class);
        expectedException.expectMessage("HTTP 404 Not Found");

        chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(100L, 0, userExternalId));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    @Test
    public void shouldFailWhenChargeRefundIsNotAvailable() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;
        GatewayAccountEntity account = new GatewayAccountEntity("sandbox", newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        expectedException.expect(RefundException.class);
        expectedException.expectMessage("HTTP 412 Precondition Failed");

        chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(100L, 0, userExternalId));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldFailWhenAmountAvailableForRefundMismatchesWithoutCorporateSurcharge() {
        Long amount = 1000L;
        String externalChargeId = "chargeId";
        Long accountId = 2L;
        GatewayAccountEntity account = new GatewayAccountEntity("sandbox", newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withAmount(amount)
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        expectedException.expect(RefundException.class);
        expectedException.expectMessage("HTTP 412 Precondition Failed");

        chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, amount + 1, userExternalId));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldFailWhenAmountAvailableForRefundWithCorporateSurchargeMismatches() {
        Long amount = 1000L;
        Long corporateSurcharge = 250L;
        String externalChargeId = "chargeId";
        Long accountId = 2L;
        GatewayAccountEntity account = new GatewayAccountEntity("sandbox", newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withAmount(amount)
                .withCorporateSurcharge(corporateSurcharge)
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        expectedException.expect(RefundException.class);
        expectedException.expectMessage("HTTP 412 Precondition Failed");

        // this should fail because amountAvailableForRefund is not including corporate surcharge
        chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, amount, userExternalId));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    @Test
    public void shouldUpdateRefundRecordToFailWhenRefundFails() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity capturedCharge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId(externalChargeId)
                .withStatus(CAPTURED)
                .build();

        String refundReference = "someReference";
        String refundExternalId = "refundExternalId";
        RefundEntity spiedRefundEntity = spy(aValidRefundEntity()
                .withReference(refundReference)
                .withCharge(capturedCharge)
                .withExternalId(refundExternalId)
                .build()
        );

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(capturedCharge));
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);

        setupWorldpayMock(null, "error-code");

        Long refundId = 12345L;
        doAnswer(invocation -> {
            ((RefundEntity) invocation.getArgument(0)).setId(refundId);
            return null;
        }).when(mockRefundDao).persist(any(RefundEntity.class));

        when(mockRefundDao.findById(refundId)).thenReturn(Optional.of(spiedRefundEntity));

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, capturedCharge.getAmount(), userExternalId)).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isFailed(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().get().getMessage(),
                is("Randompay refund response (errorCode: error-code)"));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().get().getErrorType(), is(ErrorType.GENERIC_GATEWAY_ERROR));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, capturedCharge)));
        verify(mockProvider).refund(argThat(aRefundRequestWith(capturedCharge, amount)));
        verify(mockRefundDao).findById(refundId);
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_ERROR);

        verifyNoMoreInteractions(mockChargeDao, mockRefundDao);
    }

    private ArgumentMatcher<RefundEntity> aRefundEntity(long amount, ChargeEntity chargeEntity) {
        return object -> {
            RefundEntity refundEntity = ((RefundEntity) object);
            return refundEntity.getAmount() == amount &&
                    refundEntity.getChargeEntity().equals(chargeEntity);
        };
    }

    private ArgumentMatcher<RefundGatewayRequest> aRefundRequestWith(ChargeEntity capturedCharge, long amountInPence) {
        return object -> {
            RefundGatewayRequest refundGatewayRequest = ((RefundGatewayRequest) object);
            return refundGatewayRequest.getGatewayAccount().equals(capturedCharge.getGatewayAccount()) &&
                    refundGatewayRequest.getTransactionId().equals(capturedCharge.getGatewayTransactionId()) &&
                    refundGatewayRequest.getAmount().equals(String.valueOf(amountInPence));
        };
    }
}
