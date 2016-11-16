package uk.gov.pay.connector.service;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.RefundException;
import uk.gov.pay.connector.model.ErrorType;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.RefundRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.worldpay.WorldpayRefundResponse;

import javax.ws.rs.core.UriInfo;
import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.resources.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.resources.PaymentGatewayName.WORLDPAY;

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
    private UriInfo mockUriInfo;

    @Before
    public void setUp() {
        chargeRefundService = new ChargeRefundService(mockChargeDao, mockRefundDao, mockProviders, TransactionFlow::new);
    }

    public void setupPaymentProviderMock(String reference, String errorCode) {
        WorldpayRefundResponse worldpayResponse = mock(WorldpayRefundResponse.class);
        when(worldpayResponse.getReference()).thenReturn(Optional.ofNullable(reference));
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponse refundResponse = GatewayResponse.with(worldpayResponse);
        when(mockProvider.refund(any())).thenReturn(refundResponse);
    }

    @Test
    public void shouldRefundSuccessfully() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "worldpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockChargeDao.merge(charge)).thenReturn(charge);

        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        when(mockProvider.generateRefundReference()).thenReturn(Optional.empty());
        setupPaymentProviderMock("reference", null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProviders, times(2)).byName(WORLDPAY);
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockProvider).generateRefundReference();
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    @Test
    public void shouldNotOverrideGeneratedReferenceIfProviderReturnsNullReference() {
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
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(charge)).thenReturn(charge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        when(mockProvider.generateRefundReference()).thenReturn(Optional.of(generatedReference));
        setupPaymentProviderMock(null, null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();
        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference(generatedReference);
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
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(charge)).thenReturn(charge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        when(mockProvider.generateRefundReference()).thenReturn(Optional.of(generatedReference));
        setupPaymentProviderMock(providerReference, null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();
        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference(providerReference);
    }

    @Test
    public void shouldNotOverrideGeneratedReferenceIfProviderReturnsAnError() {
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
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withReference(generatedReference).withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(charge)).thenReturn(charge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);
        when(mockProvider.generateRefundReference()).thenReturn(Optional.of(generatedReference));
        setupPaymentProviderMock(null, "error-code");

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();
        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));
        verify(spiedRefundEntity).setReference(generatedReference);
    }

    @Test
    public void shouldRefundSuccessfullyForSandbox() {

        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "sandbox";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap(), TEST);
        account.setId(accountId);
        String reference = "reference";
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId(reference)
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().withCharge(charge).build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockChargeDao.merge(charge)).thenReturn(charge);

        when(mockProviders.byName(SANDBOX)).thenReturn(mockProvider);

        setupPaymentProviderMock(reference, null);

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);
        when(mockProvider.generateRefundReference()).thenReturn(Optional.empty());

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, charge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(false));

        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProviders, times(2)).byName(SANDBOX);
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockProvider).generateRefundReference();
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUNDED);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    @Test
    public void shouldFailWhenChargeNotFound() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.empty());

        try {
            chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(100L, 0));
            fail("Should throw an exception here");
        } catch (Exception e) {
            assertEquals(e.getClass(), ChargeNotFoundRuntimeException.class);
        }

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
        when(mockChargeDao.merge(charge)).thenReturn(charge);

        try {
            chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(100L, 0));
            fail("Should throw an exception here");
        } catch (Exception e) {
            assertEquals(e.getClass(), RefundException.class);
        }

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
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
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(capturedCharge));
        when(mockChargeDao.merge(capturedCharge)).thenReturn(capturedCharge);
        when(mockProviders.byName(WORLDPAY)).thenReturn(mockProvider);

        setupPaymentProviderMock(null, "error-code");
        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);
        when(mockChargeDao.merge(capturedCharge)).thenReturn(capturedCharge);
        when(mockProvider.generateRefundReference()).thenReturn(Optional.empty());
        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, new RefundRequest(amount, capturedCharge.getAmount())).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isFailed(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().get().getMessage(), is("[error-code]"));
        assertThat(gatewayResponse.getRefundGatewayResponse().getGatewayError().get().getErrorType(), is(ErrorType.GENERIC_GATEWAY_ERROR));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(capturedCharge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, capturedCharge)));
        verify(mockProviders, times(2)).byName(WORLDPAY);
        verify(mockProvider).refund(argThat(aRefundRequestWith(capturedCharge, amount)));
        verify(mockProvider).generateRefundReference();
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_ERROR);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    private Matcher<RefundEntity> aRefundEntity(long amount, ChargeEntity chargeEntity) {
        return new TypeSafeMatcher<RefundEntity>() {
            @Override
            protected boolean matchesSafely(RefundEntity refundEntity) {
                return refundEntity.getAmount() == amount &&
                        refundEntity.getChargeEntity().equals(chargeEntity);

            }

            @Override
            public void describeTo(Description description) {
            }
        };
    }

    private Matcher<RefundGatewayRequest> aRefundRequestWith(ChargeEntity capturedCharge, long amountInPence) {
        return new TypeSafeMatcher<RefundGatewayRequest>() {
            @Override
            protected boolean matchesSafely(RefundGatewayRequest refundGatewayRequest) {
                return refundGatewayRequest.getGatewayAccount().equals(capturedCharge.getGatewayAccount()) &&
                        refundGatewayRequest.getTransactionId().equals(capturedCharge.getGatewayTransactionId()) &&
                        refundGatewayRequest.getAmount().equals(String.valueOf(amountInPence));
            }

            @Override
            public void describeTo(Description description) {
            }
        };
    }
}
