package uk.gov.pay.connector.gateway.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.ChargeRefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RefundNotificationProcessorTest {

    @Mock
    private ChargeRefundService refundService;
    @Mock
    private UserNotificationService userNotificationService;

    RefundNotificationProcessor refundNotificationProcessor;
    RefundEntity refundEntity;

    private static final PaymentGatewayName paymentGatewayName = PaymentGatewayName.WORLDPAY;
    private static final String reference = "reference";
    private static final String transactionId = "transactionId";

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Before
    public void setup() {

        refundEntity = aValidRefundEntity().build();
        Optional<RefundEntity> optionalRefundEntity = Optional.of(refundEntity);

        when(refundService.findByProviderAndReference(paymentGatewayName.getName(), reference)).thenReturn(optionalRefundEntity);

        refundNotificationProcessor = new RefundNotificationProcessor(refundService, userNotificationService);

        Logger root = (Logger) LoggerFactory.getLogger(RefundNotificationProcessor.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldInvokeSendEmailNotificationsForSuccessfulRefunds() {
        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUNDED, reference, transactionId);
        verify(userNotificationService).sendRefundIssuedEmail(refundEntity);
    }

    @Test
    public void shouldNotInvokeSendEmailNotifications_WhenRefundStatusIsNotRefunded() {
        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUND_ERROR, reference, transactionId);
        verify(userNotificationService, never()).sendRefundIssuedEmail(refundEntity);
    }

    @Test
    public void shouldLogError_whenReferenceIsNotAvailable() {
        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUND_ERROR, null, transactionId);
        
        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = String.format("%s refund notification could not be used to update charge (missing reference)", paymentGatewayName);

        assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage));
    }

    @Test
    public void shouldLogError_whenRefundEntityIsNotAvailable() {
        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUNDED, "unknown", transactionId);
        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = String.format("%s notification '%s' could not be used to update refund (associated refund entity not found)",
                paymentGatewayName,
                "unknown");

        assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage));
    }

    public static RefundEntityFixture aValidRefundEntity() {
        return new RefundEntityFixture();
    }
}
