package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsQueue;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;

@ExtendWith(MockitoExtension.class)
class DelayedCaptureServiceTest {

    private static final String HTTP_409_CONFLICT = "HTTP 409 Conflict";
    
    @Mock
    private ChargeService mockChargeService;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeAsyncOperationsQueue mockChargeAsyncOperationsQueue;

    private DelayedCaptureService delayedCaptureService;

    @BeforeEach
    void setUp() {
        delayedCaptureService = new DelayedCaptureService(mockChargeService, mockChargeDao, mockChargeAsyncOperationsQueue);
    }

    @Test
    void shouldChangeStateToCaptureApprovedAndAddToCaptureQueueIfChargeInAwaitingCaptureRequestState() throws QueueException, JsonProcessingException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AWAITING_CAPTURE_REQUEST).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeEntity.getExternalId());

        assertThat(result, sameInstance(chargeEntity));

        var inOrder = inOrder(mockChargeService, mockChargeAsyncOperationsQueue);
        inOrder.verify(mockChargeService).transitionChargeState(chargeEntity, CAPTURE_APPROVED);
        inOrder.verify(mockChargeAsyncOperationsQueue).sendForCapture(chargeEntity);
    }

    @ParameterizedTest
    @EnumSource(value = ChargeStatus.class, names = {"CAPTURE_APPROVED", "CAPTURE_APPROVED_RETRY", "CAPTURE_READY", "CAPTURE_SUBMITTED", "CAPTURED"})
    void shouldChangeStateToCaptureApprovedAndAddToCaptureQueueIfChargeInCaptureState(ChargeStatus initialChargeStatus) throws QueueException, JsonProcessingException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(initialChargeStatus).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeEntity.getExternalId());

        assertThat(result, sameInstance(chargeEntity));

        verifyNoInteractions(mockChargeService);
        verify(mockChargeAsyncOperationsQueue).sendForCapture(chargeEntity);
    }

    @Test
    public void shouldThrowExceptionIfChargeIsNotHasIncorrectStatus() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        var conflictRuntimeException = assertThrows(ConflictRuntimeException.class,
                () -> delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeEntity.getExternalId()));

        assertThat(conflictRuntimeException.getMessage(), containsString(HTTP_409_CONFLICT));

        verifyNoInteractions(mockChargeService);
        verifyNoInteractions(mockChargeAsyncOperationsQueue);
    }

    @Test
    public void shouldThrowExceptionIfChargeCannotBeTransitioned() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AWAITING_CAPTURE_REQUEST).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doThrow(InvalidStateTransitionException.class).when(mockChargeService).transitionChargeState(chargeEntity, CAPTURE_APPROVED);


        var conflictRuntimeException = assertThrows(ConflictRuntimeException.class,
                () -> delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeEntity.getExternalId()));

        assertThat(conflictRuntimeException.getMessage(), containsString(HTTP_409_CONFLICT));

        verifyNoInteractions(mockChargeAsyncOperationsQueue);
    }

    @Test
    void shouldThrowExceptionIfChargeCannotBeAddedToCaptureQueue() throws QueueException, JsonProcessingException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AWAITING_CAPTURE_REQUEST).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doThrow(new QueueException()).when(mockChargeAsyncOperationsQueue).sendForCapture(chargeEntity);

        assertThrows(WebApplicationException.class,
                () -> delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeEntity.getExternalId()));

        var inOrder = inOrder(mockChargeService, mockChargeAsyncOperationsQueue);
        inOrder.verify(mockChargeService).transitionChargeState(chargeEntity, CAPTURE_APPROVED);
        inOrder.verify(mockChargeAsyncOperationsQueue).sendForCapture(chargeEntity);
    }

    @Test
    public void shouldThrowExceptionIfChargeNotFound() {
        var externalId = "external-id";
        when(mockChargeDao.findByExternalId(externalId)).thenReturn(Optional.empty());

        assertThrows(ChargeNotFoundRuntimeException.class,
                () -> delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(externalId));

        verifyNoInteractions(mockChargeService);
        verifyNoInteractions(mockChargeAsyncOperationsQueue);
    }

}
