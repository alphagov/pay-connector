package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.queue.tasks.model.RetryPaymentOrRefundEmailTaskData.of;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;

@ExtendWith(MockitoExtension.class)
class RetryPaymentOrRefundEmailTaskHandlerTest {
    @Mock
    private UserNotificationService mockUserNotificationService;
    @Mock
    private GatewayAccountService mockGatewayAccountService;
    @Mock
    private RefundService mockRefundService;
    @Mock
    private ChargeService mockChargeService;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    private final String paymentExternalId = "payment-external-id";
    private final String refundExternalId = "refund-external-id";

    private RetryPaymentOrRefundEmailTaskHandler retryPaymentOrRefundEmailTaskHandler;

    private Clock clock = Clock.fixed(Instant.parse("2020-01-01T10:10:10.100Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        NotifyConfiguration mockNotifyConfiguration = mock(NotifyConfiguration.class);
        when(mockNotifyConfiguration.getRetryFailedEmailAfterSeconds()).thenReturn(10L);
        when(mockConnectorConfiguration.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);
        
        retryPaymentOrRefundEmailTaskHandler = new RetryPaymentOrRefundEmailTaskHandler(
                mockChargeService,
                mockRefundService,
                mockGatewayAccountService,
                mockUserNotificationService,
                mockTaskQueueService,
                mockConnectorConfiguration,
                clock
        );
    }

    @Nested
    class TestRetryFailedPaymentEmailTask {

        @Test
        void shouldThrowExceptionIfChargeIsNotFound() {
            var data = of(paymentExternalId, PAYMENT_CONFIRMED, Instant.parse("2020-01-01T09:10:10.100Z"));

            when(mockChargeService.findCharge(paymentExternalId))
                    .thenThrow(new ChargeNotFoundRuntimeException(paymentExternalId));

            assertThrows(ChargeNotFoundRuntimeException.class, () -> {
                retryPaymentOrRefundEmailTaskHandler.process(data);
            });
        }

        @Test
        void shouldThrowExceptionIfGatewayAccountIsNotFound() {
            var data = of(paymentExternalId, PAYMENT_CONFIRMED, Instant.parse("2020-01-01T09:10:10.100Z"));

            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(1L).build();
            Charge charge = Charge.from(aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build());
            when(mockChargeService.findCharge(paymentExternalId))
                    .thenReturn(Optional.of(charge));

            assertThrows(GatewayAccountNotFoundException.class, () -> {
                retryPaymentOrRefundEmailTaskHandler.process(data);
            });
        }

        @Test
        void shouldSendEmailForPaymentConfirmedNotificationType() {
            var data = of(paymentExternalId, PAYMENT_CONFIRMED, Instant.parse("2020-01-01T09:10:10.100Z"));

            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(1L).build();
            Charge charge = Charge.from(aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build());
            when(mockChargeService.findCharge(paymentExternalId))
                    .thenReturn(Optional.of(charge));
            when(mockGatewayAccountService.getGatewayAccount(1L)).thenReturn(Optional.of(gatewayAccountEntity));

            retryPaymentOrRefundEmailTaskHandler.process(data);

            verify(mockUserNotificationService).sendPaymentConfirmedEmailSynchronously(charge, gatewayAccountEntity, false);
        }
    }

    @Nested
    class TestRetryFailedRefundEmailTask {

        @BeforeEach
        void setup() {
            RefundEntity refund = aValidRefundEntity()
                    .withExternalId(refundExternalId)
                    .withChargeExternalId(paymentExternalId).build();
            when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.of(refund));
        }

        @Test
        void shouldThrowExceptionIfChargeIsNotFound() {
            var data = of(refundExternalId, REFUND_ISSUED, Instant.parse("2020-01-01T09:10:10.100Z"));


            when(mockChargeService.findCharge(paymentExternalId))
                    .thenThrow(new ChargeNotFoundRuntimeException(paymentExternalId));

            assertThrows(ChargeNotFoundRuntimeException.class, () -> {
                retryPaymentOrRefundEmailTaskHandler.process(data);
            });
        }

        @Test
        void shouldThrowExceptionIfGatewayAccountIsNotFound() {
            var data = of(refundExternalId, REFUND_ISSUED, Instant.parse("2020-01-01T09:10:10.100Z"));

            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(1L).build();
            Charge charge = Charge.from(aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build());
            when(mockChargeService.findCharge(paymentExternalId))
                    .thenReturn(Optional.of(charge));

            assertThrows(GatewayAccountNotFoundException.class, () -> {
                retryPaymentOrRefundEmailTaskHandler.process(data);
            });
        }

        @Test
        void shouldThrowExceptionIfRefundIsNotFound() {
            var data = of(refundExternalId, REFUND_ISSUED, Instant.parse("2020-01-01T09:10:10.100Z"));

            when(mockRefundService.findRefundByExternalId(refundExternalId))
                    .thenThrow(new RefundNotFoundRuntimeException(refundExternalId));

            assertThrows(RefundNotFoundRuntimeException.class, () -> {
                retryPaymentOrRefundEmailTaskHandler.process(data);
            });
        }

        @Test
        void shouldSendEmailForRefundConfirmedNotificationType() {
            var data = of(refundExternalId, REFUND_ISSUED, Instant.parse("2020-01-01T09:10:10.100Z"));

            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(1L).build();
            Charge charge = Charge.from(aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build());
            when(mockChargeService.findCharge(paymentExternalId))
                    .thenReturn(Optional.of(charge));
            when(mockGatewayAccountService.getGatewayAccount(1L)).thenReturn(Optional.of(gatewayAccountEntity));

            RefundEntity refund = aValidRefundEntity()
                    .withExternalId(refundExternalId)
                    .withChargeExternalId(paymentExternalId).build();
            when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.of(refund));

            retryPaymentOrRefundEmailTaskHandler.process(data);

            verify(mockUserNotificationService).sendRefundIssuedEmailSynchronously(charge, gatewayAccountEntity, refund, false);
        }
    }

    @Test
    void shouldAddTaskToTheTaskQueueIfTimeElapsedIsLessThanConfiguration() {
        NotifyConfiguration mockNotifyConfiguration = mock(NotifyConfiguration.class);
        when(mockNotifyConfiguration.getRetryFailedEmailAfterSeconds()).thenReturn(10L);
        when(mockConnectorConfiguration.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);

        retryPaymentOrRefundEmailTaskHandler = new RetryPaymentOrRefundEmailTaskHandler(
                mockChargeService,
                mockRefundService,
                mockGatewayAccountService,
                mockUserNotificationService,
                mockTaskQueueService,
                mockConnectorConfiguration,
                Clock.fixed(Instant.parse("2020-01-01T10:10:30.100Z"), ZoneOffset.UTC)
        );

        var data = of(paymentExternalId, PAYMENT_CONFIRMED, Instant.parse("2020-01-01T10:10:25.100Z"));

        retryPaymentOrRefundEmailTaskHandler.process(data);

        verify(mockTaskQueueService).addRetryFailedPaymentOrRefundEmailTask(data);
        verifyNoMoreInteractions(mockUserNotificationService);
    }
}
