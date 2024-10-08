package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;

import javax.ws.rs.WebApplicationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;

@ExtendWith(MockitoExtension.class)
class QueryAndUpdatePaymentInSubmittedStateTaskHandlerTest {

    QueryAndUpdatePaymentInSubmittedStateTaskHandler taskHandler;
    @Mock
    ChargeService chargeService;
    @Mock
    QueryService queryService;

    @Mock
    private BaseInquiryResponse mockGatewayResponse;

    @BeforeEach
    void setUp() {
        taskHandler = new QueryAndUpdatePaymentInSubmittedStateTaskHandler(chargeService, queryService);
    }

    @Test
    void shouldUpdateChargeStatusToCaptured_whenPaymentProviderReturnsCapturedStatus() throws GatewayException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED)
                .withExternalId("payment-ext-id")
                .build();
        PaymentTaskData taskData = new PaymentTaskData("payment-ext-id");

        when(mockGatewayResponse.getTransactionId()).thenReturn("some-transaction-id");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(CAPTURED, mockGatewayResponse);

        when(queryService.getChargeGatewayStatus(chargeEntity)).thenReturn(chargeQueryResponse);
        when(chargeService.findChargeByExternalId(chargeEntity.getExternalId())).thenReturn(chargeEntity);

        taskHandler.process(taskData);

        verify(chargeService).transitionChargeState(chargeEntity, CAPTURED);
    }

    @Test
    void shouldNotUpdateChargeStatus_ForGatewayError() throws GatewayException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED)
                .withExternalId("payment-ext-id")
                .build();
        PaymentTaskData taskData = new PaymentTaskData("payment-ext-id");

        when(queryService.getChargeGatewayStatus(chargeEntity)).thenThrow(new GatewayException.GatewayErrorException("some error"));
        when(chargeService.findChargeByExternalId(chargeEntity.getExternalId())).thenReturn(chargeEntity);

        taskHandler.process(taskData);

        verify(chargeService, times(0)).transitionChargeState(any(ChargeEntity.class), any());
    }

    @Test
    void shouldNotUpdateChargeStatus_ForWebApplicationException() throws GatewayException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED)
                .withExternalId("payment-ext-id")
                .build();
        PaymentTaskData taskData = new PaymentTaskData("payment-ext-id");

        when(queryService.getChargeGatewayStatus(chargeEntity)).thenThrow(new WebApplicationException("some error"));
        when(chargeService.findChargeByExternalId(chargeEntity.getExternalId())).thenReturn(chargeEntity);

        taskHandler.process(taskData);

        verify(chargeService, times(0)).transitionChargeState(any(ChargeEntity.class), any());
    }

    @Test
    void shouldNotUpdateChargeStatus_whenPaymentProviderStatusIsNotCaptured() throws GatewayException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED)
                .withExternalId("payment-ext-id")
                .build();
        PaymentTaskData taskData = new PaymentTaskData("payment-ext-id");

        when(mockGatewayResponse.getTransactionId()).thenReturn("some-transaction-id");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_ERROR, mockGatewayResponse);

        when(queryService.getChargeGatewayStatus(chargeEntity)).thenReturn(chargeQueryResponse);
        when(chargeService.findChargeByExternalId(chargeEntity.getExternalId())).thenReturn(chargeEntity);

        taskHandler.process(taskData);

        verify(chargeService, times(0)).transitionChargeState(any(ChargeEntity.class), any());
    }

    @Test
    void shouldThrowExceptionIfChargeIsNotFound() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED)
                .withExternalId("payment-ext-id")
                .build();
        PaymentTaskData taskData = new PaymentTaskData("payment-ext-id");

        when(chargeService.findChargeByExternalId(chargeEntity.getExternalId())).thenThrow(new ChargeNotFoundRuntimeException("payment-ext-id"));

        assertThrows(ChargeNotFoundRuntimeException.class, () -> taskHandler.process(taskData));

        verify(chargeService, times(0)).transitionChargeState(any(ChargeEntity.class), any());
    }

}
