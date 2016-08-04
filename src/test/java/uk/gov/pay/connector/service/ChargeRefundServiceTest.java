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
import uk.gov.pay.connector.exception.RefundNotAvailableRuntimeException;
import uk.gov.pay.connector.model.ErrorType;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.RefundGatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.transaction.TransactionFlow;

import javax.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.RefundGatewayResponse.failureResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

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
    public void setUp() throws Exception {
        chargeRefundService = new ChargeRefundService(mockChargeDao, mockRefundDao, mockProviders, () -> new TransactionFlow());
    }

    @Test
    public void shouldRefundSucessfully() {
        String externalChargeId = "chargeId";
        Long amount = 100L;
        Long accountId = 2L;
        String providerName = "testpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap());
        account.setId(accountId);
        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(charge));

        when(mockChargeDao.merge(charge)).thenReturn(charge);

        when(mockProviders.resolve(providerName)).thenReturn(mockProvider);

        when(mockProvider.refund(argThat(aRefundRequestWith(charge, amount))))
                .thenReturn(new RefundGatewayResponse(GatewayResponse.ResponseStatus.SUCCEDED, null, RefundStatus.REFUND_SUBMITTED));

        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, amount).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().isFailed(), is(false));
        assertThat(gatewayResponse.getRefundGatewayResponse().getError(), is(nullValue()));
        assertThat(gatewayResponse.getRefundGatewayResponse().isInProgress(), is(false));
        assertThat(gatewayResponse.getRefundEntity(), is(spiedRefundEntity));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(charge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, charge)));
        verify(mockProviders).resolve(providerName);
        verify(mockProvider).refund(argThat(aRefundRequestWith(charge, amount)));
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_SUBMITTED);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    @Test
    public void shouldFailWhenChargeNotFound() {
        String externalChargeId = "chargeId";
        Long accountId = 2L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.empty());

        try {
            chargeRefundService.doRefund(accountId, externalChargeId, 100L);
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
        GatewayAccountEntity account = new GatewayAccountEntity("testpay", newHashMap());
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
            chargeRefundService.doRefund(accountId, externalChargeId, 100L);
            fail("Should throw an exception here");
        } catch (Exception e) {
            assertEquals(e.getClass(), RefundNotAvailableRuntimeException.class);
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
        String providerName = "testpay";

        GatewayAccountEntity account = new GatewayAccountEntity(providerName, newHashMap());
        account.setId(accountId);
        ChargeEntity capturedCharge = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transactionId")
                .withStatus(CAPTURED)
                .build();

        RefundEntity spiedRefundEntity = spy(aValidRefundEntity().build());

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId))
                .thenReturn(Optional.of(capturedCharge));
        when(mockChargeDao.merge(capturedCharge)).thenReturn(capturedCharge);
        when(mockProviders.resolve(providerName)).thenReturn(mockProvider);
        when(mockProvider.refund(argThat(aRefundRequestWith(capturedCharge, amount))))
                .thenReturn(failureResponse("Error"));
        when(mockRefundDao.merge(any(RefundEntity.class))).thenReturn(spiedRefundEntity);
        when(mockChargeDao.merge(capturedCharge)).thenReturn(capturedCharge);

        ChargeRefundService.Response gatewayResponse = chargeRefundService.doRefund(accountId, externalChargeId, amount).get();

        assertThat(gatewayResponse.getRefundGatewayResponse().isSuccessful(), is(false));
        assertThat(gatewayResponse.getRefundGatewayResponse().isFailed(), is(true));
        assertThat(gatewayResponse.getRefundGatewayResponse().getError().getMessage(), is("Error"));
        assertThat(gatewayResponse.getRefundGatewayResponse().getError().getErrorType(), is(ErrorType.GENERIC_GATEWAY_ERROR));
        assertThat(gatewayResponse.getRefundGatewayResponse().isInProgress(), is(false));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, accountId);
        verify(mockChargeDao).merge(capturedCharge);
        verify(mockRefundDao).persist(argThat(aRefundEntity(amount, capturedCharge)));
        verify(mockProviders).resolve(providerName);
        verify(mockProvider).refund(argThat(aRefundRequestWith(capturedCharge, amount)));
        verify(mockRefundDao).merge(any(RefundEntity.class));
        verify(spiedRefundEntity).setStatus(RefundStatus.REFUND_ERROR);
        verifyNoMoreInteractions(mockChargeDao, mockRefundDao, mockProviders, mockProvider);
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertEquals(EXTERNAL_PENDING, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(CREATED).build()));
        assertEquals(EXTERNAL_PENDING, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(ENTERING_CARD_DETAILS).build()));
        assertEquals(EXTERNAL_PENDING, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(AUTHORISATION_READY).build()));
        assertEquals(EXTERNAL_PENDING, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS).build()));
        assertEquals(EXTERNAL_PENDING, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(CAPTURE_READY).build()));
        assertEquals(EXTERNAL_PENDING, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED).build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(AUTHORISATION_REJECTED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(AUTHORISATION_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(EXPIRED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(CAPTURE_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(EXPIRE_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(EXPIRE_CANCEL_FAILED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(SYSTEM_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(SYSTEM_CANCEL_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(SYSTEM_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(USER_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(USER_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(USER_CANCEL_ERROR).build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsAvailable() {
        RefundEntity[] refunds = new RefundEntity[]{
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        };

        assertEquals(EXTERNAL_AVAILABLE, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFull() {
        RefundEntity[] refunds = new RefundEntity[]{
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(201L).build()
        };

        assertEquals(EXTERNAL_FULL, chargeRefundService.estabishChargeRefundAvailability(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
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
