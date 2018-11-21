package uk.gov.pay.connector.webhook.service;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.model.Status;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.status.IgnoredStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;
import uk.gov.pay.connector.gateway.model.status.UnknownStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.pay.connector.util.DnsUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;

@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceTest {

    private static final String TRANSACTION_ID = "transaction-id";

    private NotificationService notificationService;

    @Mock
    private DnsUtils mockDnsUtils;

    @Mock
    private ChargeDao mockedChargeDao;

    @Mock
    private ChargeEventDao mockedChargeEventDao;

    @Mock
    private RefundDao mockedRefundDao;

    @Mock
    private PaymentProviders mockedPaymentProviders;

    @Mock
    private PaymentProvider mockedPaymentProvider;

    @Mock
    private UserNotificationService mockedUserNotificationService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChargeEntity mockedChargeEntity;

    @Before
    public void setUp() {
        when(mockedPaymentProvider.getPaymentGatewayName()).thenReturn(SANDBOX);
        when(mockedPaymentProviders.byName(SANDBOX)).thenReturn(mockedPaymentProvider);
        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID)).thenReturn(Optional.of(mockedChargeEntity));
        when(mockedChargeEntity.getStatus()).thenReturn(ChargeStatus.CAPTURED.toString());
        when(mockedChargeEntity.getGatewayAccount().getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE)).thenReturn("a_passphrase");
        when(mockedChargeEntity.getExternalId()).thenReturn("someExternalId");

        when(mockedPaymentProvider.verifyNotification(any(Notification.class), any(GatewayAccountEntity.class))).thenReturn(true);

        notificationService = new NotificationService(mockedChargeDao, mockedChargeEventDao, mockedRefundDao, mockedPaymentProviders, mockDnsUtils, mockedUserNotificationService);
    }

    private Notifications<Pair<String, Boolean>> createNotificationFor(String transactionId, String reference, Pair<String, Boolean> status) {
        return Notifications.<Pair<String, Boolean>>builder()
                .addNotificationFor(transactionId, reference, status, ZonedDateTime.now(), null)
                .build();
    }

    private StatusMapper createMockedStatusMapper(InterpretedStatus.Type type, Status status) {
        StatusMapper mockedStatusMapper = mock(StatusMapper.class);

        switch (type) {
            case CHARGE_STATUS:
                MappedChargeStatus mockedMappedChargeStatus = mock(MappedChargeStatus.class);
                when(mockedMappedChargeStatus.getType()).thenReturn(type);
                when(mockedMappedChargeStatus.getChargeStatus()).thenReturn((ChargeStatus) status);
                when(mockedStatusMapper.from(any())).thenReturn(mockedMappedChargeStatus);
                when(mockedStatusMapper.from(any(), any(ChargeStatus.class))).thenReturn(mockedMappedChargeStatus);
                return mockedStatusMapper;
            case REFUND_STATUS:
                MappedRefundStatus mockedMappedRefundStatus = mock(MappedRefundStatus.class);
                when(mockedMappedRefundStatus.getType()).thenReturn(type);
                when(mockedMappedRefundStatus.getRefundStatus()).thenReturn((RefundStatus) status);
                when(mockedStatusMapper.from(any())).thenReturn(mockedMappedRefundStatus);
                when(mockedStatusMapper.from(any(), any(ChargeStatus.class))).thenReturn(mockedMappedRefundStatus);
                return mockedStatusMapper;
            case IGNORED:
                IgnoredStatus mockedIgnoredStatus = mock(IgnoredStatus.class);
                when(mockedIgnoredStatus.getType()).thenReturn(type);
                when(mockedStatusMapper.from(any())).thenReturn(mockedIgnoredStatus);
                return mockedStatusMapper;
            case UNKNOWN:
            default:
                UnknownStatus mockedUnknownStatus = mock(UnknownStatus.class);
                when(mockedUnknownStatus.getType()).thenReturn(type);
                when(mockedStatusMapper.from(any())).thenReturn(mockedUnknownStatus);
                when(mockedStatusMapper.from(any(), any(ChargeStatus.class))).thenReturn(mockedUnknownStatus);
                return mockedStatusMapper;
        }
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

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.UNKNOWN, null);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsMappedAsIgnored() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("AUTHORISATION", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.IGNORED, null);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenNoTransactionId() {

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor("", null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenVerifyingFailsBecauseOfWrongTransactionId() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID)).thenReturn(Optional.empty());

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenEvaluatingFailsBecauseOfWrongTransactionId() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID)).thenReturn(Optional.of(mockedChargeEntity))
                .thenReturn(Optional.empty());

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenUpdatingFailsBecauseOfWrongTransactionId() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), TRANSACTION_ID)).thenReturn(Optional.of(mockedChargeEntity))
                .thenReturn(Optional.of(mockedChargeEntity)).thenReturn(Optional.empty());

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenIllegalStateTransition() {
        String transactionId = "unknown-transaction-id";

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(transactionId, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedChargeDao.findByProviderAndTransactionId(SANDBOX.getName(), transactionId))
                .thenReturn(Optional.of(mockedChargeEntity));

        doThrow(new InvalidStateTransitionException("AUTHORISATION SUCCESS", "CAPTURED"))
                .when(mockedChargeEntity).setStatus(CAPTURED);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedChargeEntity, atLeastOnce()).getStatus();
        verify(mockedChargeEntity).setStatus(CAPTURED);
        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreRefundNotificationWhenNoReference() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("REFUND_ISSUED", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.REFUND_STATUS, REFUNDED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationIfNotVerified() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedPaymentProvider.verifyNotification(any(Notification.class), any(GatewayAccountEntity.class))).thenReturn(false);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldIgnoreNotificationWhenWrongReference() {
        String reference = "reference";

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, reference, Pair.of("REFUND_ISSUED", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.REFUND_STATUS, REFUNDED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedRefundDao.findByProviderAndReference(SANDBOX.getName(), reference))
                .thenReturn(Optional.empty());

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedRefundDao).findByProviderAndReference(SANDBOX.getName(), reference);
        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldAcceptNotificationForCapture() {
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedChargeEntity).setStatus(CAPTURED);

        ArgumentCaptor<ZonedDateTime> generatedTimeCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(mockedChargeEventDao).persistChargeEventOf(argThat(obj -> mockedChargeEntity.equals(obj)), generatedTimeCaptor.capture());

        assertTrue(ChronoUnit.SECONDS.between(generatedTimeCaptor.getValue(), ZonedDateTime.now()) < 10);

        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
        verifyZeroInteractions(mockedUserNotificationService);
    }

    @Test
    public void shouldAcceptNotificationForRefundAndSendEmail() {
        String reference = "reference";

        Notifications<Pair<String, Boolean>> notifications = createNotificationFor(TRANSACTION_ID, reference, Pair.of("REFUND_ISSUED", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.REFUND_STATUS, REFUNDED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);

        when(mockedPaymentProvider.verifyNotification(any(), any())).thenReturn(true);
        when(mockedPaymentProvider.getPaymentGatewayName()).thenReturn(SANDBOX);

        RefundEntity mockedRefundEntity = mock(RefundEntity.class);
        GatewayAccountEntity mockedGatewayAccount = mock(GatewayAccountEntity.class);

        when(mockedRefundDao.findByProviderAndReference(SANDBOX.getName(), reference)).thenReturn(Optional.of(mockedRefundEntity));
        when(mockedRefundEntity.getChargeEntity()).thenReturn(mockedChargeEntity);
        when(mockedChargeEntity.getGatewayAccount()).thenReturn(mockedGatewayAccount);

        notificationService.handleNotificationFor("", SANDBOX, "payload");

        verify(mockedRefundDao).findByProviderAndReference(SANDBOX.getName(), reference);
        verify(mockedRefundEntity).setStatus(REFUNDED);
        verify(mockedUserNotificationService).sendRefundIssuedEmail(mockedRefundEntity);
        verifyNoMoreInteractions(ignoreStubs(mockedChargeDao));
    }

    @Test
    public void whenSecureNotificationEndpointIsEnabled_shouldRejectNotificationIfIpIsNotValid() {
        when(mockedPaymentProviders.byName(WORLDPAY)).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.isNotificationEndpointSecured()).thenReturn(true);
        when(mockedPaymentProvider.getNotificationDomain()).thenReturn("something.com");

        assertThat(notificationService.handleNotificationFor("", WORLDPAY, "payload"), is(false));
        verifyZeroInteractions(mockedChargeDao);
    }

    @Test
    public void whenSecureNotificationEndpointIsEnabled_shouldHandleNotificationIfIpBelongsToDomain() {
        String ipAddress = "ip-address";
        String domain = "worldpay.com";
        Notifications<Pair<String, Boolean>> notifications = createNotificationFor("", null, Pair.of("CAPTURE", true));
        when(mockedPaymentProvider.parseNotification(any())).thenReturn(Either.right(notifications));

        StatusMapper mockedStatusMapper = createMockedStatusMapper(InterpretedStatus.Type.CHARGE_STATUS, CAPTURED);
        when(mockedPaymentProvider.getStatusMapper()).thenReturn(mockedStatusMapper);
        when(mockedPaymentProviders.byName(WORLDPAY)).thenReturn(mockedPaymentProvider);

        when(mockDnsUtils.ipMatchesDomain(ipAddress, domain)).thenReturn(true);
        when(mockedPaymentProvider.isNotificationEndpointSecured()).thenReturn(true);
        when(mockedPaymentProvider.getNotificationDomain()).thenReturn(domain);

        assertThat(notificationService.handleNotificationFor(ipAddress, WORLDPAY, "payload"), is(true));
    }
}
