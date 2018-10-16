package uk.gov.pay.connector.usernotification.util;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.usernotification.model.ChargeStatusRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
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

        assertThat(notificationUtil.payloadChecks(chargeStatusRequest), is(false));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenTransactionIdIsEmptyString() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.CAPTURED));
        when(chargeStatusRequest.getTransactionId()).thenReturn("");

        assertThat(notificationUtil.payloadChecks(chargeStatusRequest), is(false));
    }

    @Test
    public void payLoadChecks_ShouldReturnFalseWhenTransactionIdIsNull() {
        ChargeStatusRequest chargeStatusRequest = mock(ChargeStatusRequest.class);
        when(chargeStatusRequest.getChargeStatus()).thenReturn(Optional.of(ChargeStatus.CAPTURED));
        when(chargeStatusRequest.getTransactionId()).thenReturn(null);

        assertThat(notificationUtil.payloadChecks(chargeStatusRequest), is(false));
    }

}
