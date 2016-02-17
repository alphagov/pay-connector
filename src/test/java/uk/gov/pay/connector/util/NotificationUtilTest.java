package uk.gov.pay.connector.util;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.pay.connector.model.ChargeStatusRequest;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.ChargeStatusBlacklist;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationUtilTest {

    NotificationUtil notificationUtil;

    ChargeStatusBlacklist chargeStatusBlacklist;

    @Before
    public void setup() {
        chargeStatusBlacklist = mock(ChargeStatusBlacklist.class);
        notificationUtil = new NotificationUtil(chargeStatusBlacklist);
    }

    @Test
    public void payloadChecks_ShouldReturnFalseWhenNoChargeStatus() throws Exception {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.empty());

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenChargeStatusIsBlackListed() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.AUTHORISATION_REJECTED));
        when(chargeStatusBlacklist.has(ChargeStatus.AUTHORISATION_REJECTED)).thenReturn(true);

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenTransactionIdIsEmptyString() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.CAPTURED));
        when(chargeStatusBlacklist.has(ChargeStatus.CAPTURED)).thenReturn(false);
        when(chargeStatusRequest.getTransactionId()).thenReturn("");

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenTransactionIdIsNull() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.CAPTURED));
        when(chargeStatusBlacklist.has(ChargeStatus.CAPTURED)).thenReturn(false);
        when(chargeStatusRequest.getTransactionId()).thenReturn(null);

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

}