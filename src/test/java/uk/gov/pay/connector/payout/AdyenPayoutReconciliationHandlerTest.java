package uk.gov.pay.connector.payout;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.adyen.AdyenPaymentProvider;
import uk.gov.pay.connector.gateway.adyen.report.AdyenPayoutReportParser;
import uk.gov.pay.connector.queue.payout.AdyenPayoutReconciliationPayload;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.report.AdyenReportDateConverter.convertToInstant;
import static uk.gov.pay.connector.queue.payout.AdyenPayoutReconciliationPayloadFixture.anAdyenPayoutReconciliationPayloadFixture;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_BALANCE_PLATFORM_REPORT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class AdyenPayoutReconciliationHandlerTest {

    @Mock
    PaymentProviders mockPaymentProviders;
    @Mock
    AdyenPayoutReportParser mockAdyenPayoutReportParser;
    @Mock
    AdyenPaymentProvider adyenPaymentProvider;
    @Mock
    EventService eventService;
    @Mock
    PayoutEmitterService payoutEmitterService;

    ArgumentCaptor<PaymentIncludedInPayout> paymentCaptor = ArgumentCaptor.forClass(PaymentIncludedInPayout.class);
    ArgumentCaptor<RefundIncludedInPayout> refundCaptor = ArgumentCaptor.forClass(RefundIncludedInPayout.class);
    ArgumentCaptor<PayoutPaid> payoutCaptor = ArgumentCaptor.forClass(PayoutPaid.class);

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenPayoutReconciliationHandler.class);

    AdyenPayoutReconciliationHandler handler;
    PayoutReconcileMessage payoutReconcileMessage;

    @BeforeEach
    void setUp() {
        when(mockPaymentProviders.byName(ADYEN)).thenReturn(adyenPaymentProvider);
        handler = new AdyenPayoutReconciliationHandler(mockPaymentProviders,
                mockAdyenPayoutReportParser, eventService, payoutEmitterService);
        payoutReconcileMessage = mockPayoutReconcileMessage();
    }
    
    @Test void shouldParseAdyenCreationDateWithTimeZoneOffsetWithoutSeconds() {
        AdyenPayoutReconciliationPayload payload = new AdyenPayoutReconciliationPayload(
                "balanceplatform_payout_report",
                "pay_test",
                "2026-09-24T00:04+02:00",
                "https://some-url",
                "balanceplatform_payout_report_2026_09_24",
                "test");
        payoutReconcileMessage = PayoutReconcileMessage.of(payload, mock(QueueMessage.class));
        mockValidParsingOfPayoutReport(load(ADYEN_BALANCE_PLATFORM_REPORT));
        
        boolean result = handler.reconcile(payoutReconcileMessage);
        
        assertTrue(result);
        verify(payoutEmitterService).emitPayoutEvent(payoutCaptor.capture(), eq("BA00000000000000000000001"));
        assertEquals(Instant.parse("2026-09-23T22:04:00Z"), payoutCaptor.getValue().getTimestamp());
    }

    @Test
    void shouldDownloadAndParseCSVDataForReconciliation() {
        when(adyenPaymentProvider.downloadReport(any(AdyenPayoutReconciliationPayload.class))).thenReturn("csv-data");

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertTrue(result);

        verify(adyenPaymentProvider).downloadReport(any(AdyenPayoutReconciliationPayload.class));
        verify(mockAdyenPayoutReportParser).parse("csv-data");

        logs.assertContains("Finished reconciling Adyen balance platform payout report");
    }

    @Test
    void shouldLogErrorAndReturnFalseWhenFailedToDownloadReport() {
        doThrow(new RuntimeException("Some exception")).when(adyenPaymentProvider).downloadReport(any());

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertFalse(result);

        verify(adyenPaymentProvider).downloadReport(any(AdyenPayoutReconciliationPayload.class));
        verifyNoInteractions(mockAdyenPayoutReportParser);

        logs.assertContains("Failed to reconcile Adyen balance platform payout report");
    }

    @Test
    void shouldLogErrorWhenCSVParserThrowsException() {
        when(adyenPaymentProvider.downloadReport(any(AdyenPayoutReconciliationPayload.class))).thenReturn("csv-data");
        doThrow(new RuntimeException("Some exception")).when(mockAdyenPayoutReportParser).parse(any());

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertFalse(result);

        verify(adyenPaymentProvider).downloadReport(any(AdyenPayoutReconciliationPayload.class));
        verify(mockAdyenPayoutReportParser).parse("csv-data");

        logs.assertContains("Failed to reconcile Adyen balance platform payout report");
    }

    @Test
    void shouldEmitPaymentEvents() throws QueueException {
        mockValidParsingOfPayoutReport(load(ADYEN_BALANCE_PLATFORM_REPORT));

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertTrue(result);

        verify(eventService).emitEvent(paymentCaptor.capture(), eq(false));

        var eventEmitted = paymentCaptor.getValue();

        assertEquals("PAYMENT_INCLUDED_IN_PAYOUT", eventEmitted.getEventType());
        assertEquals("charge-external-id-123", eventEmitted.getResourceExternalId());
        assertEquals(eventEmitted.getTimestamp(), convertToInstant("2023-12-15 07:00:12"));
        logs.assertContains("Emitted event for payment charge-external-id-123 included in payout 3JY1Y65VXYY7GD3F");
    }

    @Test
    void shouldEmitRefundEvents() throws QueueException {
        mockValidParsingOfPayoutReport(load(ADYEN_BALANCE_PLATFORM_REPORT));

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertTrue(result);

        verify(eventService, times(1)).emitEvent(refundCaptor.capture(), eq(false));

        var refundEventEmitted = refundCaptor.getValue();

        assertEquals("REFUND_INCLUDED_IN_PAYOUT", refundEventEmitted.getEventType());
        assertEquals("refund-external-id-123", refundEventEmitted.getResourceExternalId());
        assertEquals(refundEventEmitted.getTimestamp(), convertToInstant("2023-12-15 07:00:12"));
        logs.assertContains("Emitted event for refund refund-external-id-123 included in payout 3CY1XOPVXWKYA3O9");
    }

    @Test
    void shouldEmitPayoutEvents() {
        mockValidParsingOfPayoutReport(load(ADYEN_BALANCE_PLATFORM_REPORT));

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertTrue(result);

        verify(payoutEmitterService, times(1)).emitPayoutEvent(payoutCaptor.capture(), eq("BA00000000000000000000001"));

        var payoutEventEmitted = payoutCaptor.getValue();

        assertEquals("PAYOUT_PAID", payoutEventEmitted.getEventType());
        assertEquals("3CY1XOPVXWKYA3O9", payoutEventEmitted.getResourceExternalId());
        assertEquals("2026-09-23T22:04:12Z", payoutEventEmitted.getTimestamp().toString());
        logs.assertContains("Emitted event for payout 3CY1XOPVXWKYA3O9");
    }

    @Test
    void shouldLogErrorWhenEmittingEventFails() throws QueueException {
        mockValidParsingOfPayoutReport(load(ADYEN_BALANCE_PLATFORM_REPORT));
        doThrow(new QueueException("some exception")).when(eventService).emitEvent(any(), eq(false));

        boolean result = handler.reconcile(payoutReconcileMessage);

        assertFalse(result);

        logs.assertContains("Failed to reconcile Adyen balance platform payout report");
    }

    @Test
    void shouldLogErrorWhenTransferRecordTypeIsNull() {
        mockValidParsingOfPayoutReport(load(ADYEN_BALANCE_PLATFORM_REPORT).replace("capture", ""));
        
        handler.reconcile(payoutReconcileMessage);

        logs.assertContains("Payout contains type null, which is unexpected.");
    }
    
    @Test
    void shouldNotEmitEventIfBankTransferRecordIsNotFoundForTheBalanceAccount() throws QueueException {
        var csv = load(ADYEN_BALANCE_PLATFORM_REPORT).replaceFirst("BA00000000000000000000001", "lost_balance_account_id")
                .replace("refund", "capture");
        mockValidParsingOfPayoutReport(csv);
        
        handler.reconcile(payoutReconcileMessage);

        verify(eventService, times(1)).emitEvent(any(), eq(false));
        logs.assertContains("Payout record is not found for a balance account");
    }

    private PayoutReconcileMessage mockPayoutReconcileMessage() {
        AdyenPayoutReconciliationPayload payload = anAdyenPayoutReconciliationPayloadFixture()
                .build();
        QueueMessage queueMessage = mock(QueueMessage.class);
        return PayoutReconcileMessage.of(payload, queueMessage);
    }

    private void mockValidParsingOfPayoutReport(String balancePlatformReport) {
        handler = new AdyenPayoutReconciliationHandler(mockPaymentProviders,
                new AdyenPayoutReportParser(), eventService, payoutEmitterService);
        when(adyenPaymentProvider.downloadReport(any(AdyenPayoutReconciliationPayload.class))).thenReturn(balancePlatformReport);
    }
}
