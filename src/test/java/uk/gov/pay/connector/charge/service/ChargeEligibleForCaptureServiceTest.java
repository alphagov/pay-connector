package uk.gov.pay.connector.charge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
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
class ChargeEligibleForCaptureServiceTest {

    @Mock
    private ChargeService mockChargeService;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private CaptureQueue mockCaptureQueue;

    @Mock
    private UserNotificationService mockUserNotificationService;

    private ChargeEligibleForCaptureService chargeEligibleForCaptureService;

    @BeforeEach
    void setUp() {
        chargeEligibleForCaptureService = new ChargeEligibleForCaptureService(mockChargeService, mockChargeDao,
                mockCaptureQueue, mockUserNotificationService);
    }

    @Test
    void shouldChangeStateToCaptureApprovedAddToCaptureQueueAndSendPaymentConfirmedEmail() throws QueueException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());

        assertThat(result, sameInstance(chargeEntity));

        var inOrder = inOrder(mockChargeService, mockCaptureQueue, mockUserNotificationService);
        inOrder.verify(mockChargeService).transitionChargeState(chargeEntity, CAPTURE_APPROVED);
        inOrder.verify(mockCaptureQueue).sendForCapture(result);
        inOrder.verify(mockUserNotificationService).sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount());
    }

    @Test
    void shouldChangeStateToAwaitingCaptureRequestButNotAddToCaptureQueueOrSendPaymentConfirmedEmailIfDelayedCapture() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).withDelayedCapture(true).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());

        assertThat(result, sameInstance(chargeEntity));

        verify(mockChargeService).transitionChargeState(chargeEntity, AWAITING_CAPTURE_REQUEST);
        verifyNoInteractions(mockCaptureQueue);
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void shouldThrowExceptionIfChargeCannotBeTransitioned() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doThrow(InvalidStateTransitionException.class).when(mockChargeService).transitionChargeState(chargeEntity, CAPTURE_APPROVED);

        assertThrows(IllegalStateRuntimeException.class,
                () -> chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId()));

        verifyNoInteractions(mockCaptureQueue);
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void shouldThrowExceptionIfChargeCannotBeAddedToCaptureQueue() throws QueueException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();
        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doThrow(QueueException.class).when(mockCaptureQueue).sendForCapture(chargeEntity);

        assertThrows(WebApplicationException.class,
                () -> chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId()));

        verify(mockChargeService).transitionChargeState(chargeEntity, CAPTURE_APPROVED);
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void shouldThrowExceptionIfChargeNotFound() throws QueueException {
        var externalId = "external-id";
        when(mockChargeDao.findByExternalId(externalId)).thenReturn(Optional.empty());

        assertThrows(ChargeNotFoundRuntimeException.class,
                () -> chargeEligibleForCaptureService.markChargeAsEligibleForCapture(externalId));

        verifyNoInteractions(mockChargeService);
        verifyNoInteractions(mockCaptureQueue);
        verifyNoInteractions(mockUserNotificationService);
    }

}