package uk.gov.pay.connector.util;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.ChargeStatusRequest;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationUtilTest {

    private NotificationUtil notificationUtil;

    @Before
    public void setup() {
        notificationUtil = new NotificationUtil();
    }

    @Test
    public void payloadChecks_ShouldReturnFalseWhenNoChargeStatus() throws Exception {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.empty());

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenTransactionIdIsEmptyString() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.CAPTURED));
        when(chargeStatusRequest.getTransactionId()).thenReturn("");

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenTransactionIdIsNull() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.CAPTURED));
        when(chargeStatusRequest.getTransactionId()).thenReturn(null);

        assertFalse(notificationUtil.payloadChecks(chargeStatusRequest));
    }

}
