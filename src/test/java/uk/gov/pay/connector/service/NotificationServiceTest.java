package uk.gov.pay.connector.service;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.util.DnsUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.service.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.service.PaymentGatewayName.WORLDPAY;

@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceTest {

    private static final String TRANSACTION_ID = "transaction-id";

    private NotificationService notificationService;

    @Mock
    private DnsUtils mockDnsUtils;

    @Mock
    private ChargeDao mockedChargeDao;

    @Mock
    private RefundDao mockedRefundDao;

    @Mock
    private PaymentProviders mockedPaymentProviders;

    @Mock
    private PaymentProvider mockedPaymentProvider;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChargeEntity mockedChargeEntity;

    @Before
    public void setUp() {
        when(mockedPaymentProvider.getPaymentGatewayName()).thenReturn(SANDBOX.getName());
        when(mockedPaymentProviders.byName(SANDBOX)).thenReturn(mockedPaymentProvider);
        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID)).thenReturn(Optional.of(mockedChargeEntity));
        when(mockedChargeEntity.getGatewayAccount().getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE)).thenReturn("a_passphrase");

        when(mockedPaymentProvider.verifyNotification(any(Notification.class), anyString())).thenReturn(true);

        notificationService = new NotificationService(mockedChargeDao, mockedRefundDao, mockedPaymentProviders, () -> new TransactionFlow(), mockDnsUtils);
    }

    private Notifications<Pair<String, Boolean>> createNotificationFor(String transactionId, String reference, Pair<String, Boolean> status) {
        return Notifications.<Pair<String, Boolean>>builder()
                .addNotificationFor(transactionId, reference, status, ZonedDateTime.now(), null)
                .build();
    }

    private StatusMapper createMockedStatusMapper(boolean isUnknownStatus, boolean isIgnoredStatus, Status status) {
        StatusMapper mockedStatusMapper = mock(StatusMapper.class);
        InterpretedStatus mockedStatus = mock(InterpretedStatus.class);

        when(mockedStatus.isUnknown()).thenReturn(isUnknownStatus);
        when(mockedStatus.isIgnored()).thenReturn(isIgnoredStatus);

        when(mockedStatusMapper.from(any())).thenReturn(mockedStatus);

        if (status != null) {
            when(mockedStatus.get()).thenReturn(Optional.of(status));
        }

        return mockedStatusMapper;
    }

    @Test
    public void shouldIgnoreNotificationWhenPayloadParsingFails() {

        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.left("Error"));

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(mockedChargeDao);
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsMappedAsUnknown() {

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(true, false, null);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsMappedAsIgnored() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("AUTHORISATION", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, true, null);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldIgnoreNotificationWhenNoTransactionId() {

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor("", null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldIgnoreNotificationWhenWrongTransactionId() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID))
                .thenReturn(Optional.empty());

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedChargeDao).findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID);
        verifyNoMoreInteractions(mockedChargeDao);
    }

    @Test
    public void shouldIgnoreNotificationWhenIllegalStateTransition() {
        String transactionId = "unknown-transaction-id";

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(transactionId, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), transactionId))
                .thenReturn(Optional.of(mockedChargeEntity));

        doThrow(new InvalidStateTransitionException("AUTHORISATION SUCCESS", "CAPTURED"))
                .when(mockedChargeEntity).setStatus(CAPTURED);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedChargeEntity).getStatus();
        verify(mockedChargeEntity).setStatus(CAPTURED);
        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldIgnoreNotificationWhenNoReference() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("REFUND", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, REFUNDED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldIgnoreNotificationIfNotVerified() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedPaymentProvider.verifyNotification(any(Notification.class), anyString())).thenReturn(false);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldIgnoreNotificationWhenWrongReference() {
        String reference = "reference";

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, reference, Pair.of("REFUND", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, REFUNDED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedRefundDao.findByReference(reference))
                .thenReturn(Optional.empty());

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedRefundDao).findByReference(reference);
        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldAcceptNotificationForCapture() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedChargeEntity).setStatus(CAPTURED);

        ArgumentCaptor<Optional> generatedTimeCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argThat(obj -> mockedChargeEntity.equals(obj)), generatedTimeCaptor.capture());

        assertTrue(ChronoUnit.SECONDS.between((ZonedDateTime) generatedTimeCaptor.getValue().get(), ZonedDateTime.now()) < 10);

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void shouldAcceptNotificationForRefund() {
        String reference = "reference";

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, reference, Pair.of("REFUND", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, REFUNDED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedPaymentProvider.verifyNotification(any(), any())).thenReturn(true);

        RefundEntity mockedRefundEntity = mock(RefundEntity.class);
        GatewayAccountEntity mockedGatewayAccount = mock(GatewayAccountEntity.class);

        when(mockedRefundDao.findByReference(reference)).thenReturn(Optional.of(mockedRefundEntity));
        when(mockedRefundEntity.getChargeEntity()).thenReturn(mockedChargeEntity);
        when(mockedChargeEntity.getGatewayAccount()).thenReturn(mockedGatewayAccount);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedRefundDao).findByReference(reference);
        verify(mockedRefundEntity).setStatus(REFUNDED);
        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void whenSecureNotificationEndpointIsEnabled_shouldRejectNotificationIfIpIsNotValid() throws Exception {
        when(mockedPaymentProviders.byName(WORLDPAY)).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.isNotificationEndpointSecured()).thenReturn(true);
        when(mockedPaymentProvider.getNotificationDomain()).thenReturn("something.com");
        when(mockDnsUtils.reverseDnsLookup(anyString())).thenReturn(Optional.empty());

        assertThat(notificationService.handleNotificationFor("", WORLDPAY, "payload"), is(false));
        verifyZeroInteractions(mockedChargeDao);
    }

    @Test
    public void whenSecureNotificationEndpointIsEnabled_shouldHandleNotificationIfIpBelongsToDomain() throws Exception {
        String ipAddress = "ip-address";
        String domain = "worldpay.com";
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor("", null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(false, false, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);
        when(mockedPaymentProviders.byName(WORLDPAY)).thenReturn(mockedPaymentProvider);

        when(mockDnsUtils.ipMatchesDomain(ipAddress, domain)).thenReturn(true);
        when(mockedPaymentProvider.isNotificationEndpointSecured()).thenReturn(true);
        when(mockedPaymentProvider.getNotificationDomain()).thenReturn(domain);

        assertThat(notificationService.handleNotificationFor(ipAddress, WORLDPAY, "payload"), is(true));
    }
}
