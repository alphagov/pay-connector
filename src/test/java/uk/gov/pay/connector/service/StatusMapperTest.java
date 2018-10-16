package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;

public class StatusMapperTest {

    private final StatusMapper<String> statusMapper =
            StatusMapper
                    .<String>builder()
                    .ignore("IGNORED_STATUS")
                    .map("HERE_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS", CAPTURED)
                    .map("AGAIN_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS", REFUNDED)
                    .map("HERE_WE_CARE_ABOUT_THE_GATEWAY_STATUS_AND_THE_CURRENT_STATUS", USER_CANCEL_SUBMITTED, USER_CANCELLED)
                    .map("HERE_WE_CARE_ABOUT_THE_GATEWAY_STATUS_AND_THE_CURRENT_STATUS", SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED)
                    .build();

    @Test
    public void shouldIgnore() {
        InterpretedStatus ignoredStatus = statusMapper.from("IGNORED_STATUS", AUTHORISATION_SUCCESS);

        assertThat(ignoredStatus.getType(), is(InterpretedStatus.Type.IGNORED));
    }

    @Test
    public void shouldIgnore_WhenNoCurrentStatusProvided() {
        InterpretedStatus ignoredStatus = statusMapper.from("IGNORED_STATUS");

        assertThat(ignoredStatus.getType(), is(InterpretedStatus.Type.IGNORED));
    }

    @Test
    public void shouldMapGatewayStatusOnlyToChargeStatus() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS", CAPTURE_SUBMITTED);

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(mappedStatus.getChargeStatus(), is(CAPTURED));
    }

    @Test
    public void shouldMapGatewayStatusOnlyToChargeStatus_WhenNoCurrentStatusProvided() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS");

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(mappedStatus.getChargeStatus(), is(CAPTURED));
    }

    @Test
    public void shouldMapGatewayStatusOnlyToRefundStatus() {
        InterpretedStatus mappedStatus = statusMapper.from("AGAIN_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS", CAPTURED);

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(mappedStatus.getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldMapGatewayStatusOnlyToRefundStatus_WhenNoCurrentStatusProvided() {
        InterpretedStatus mappedStatus = statusMapper.from("AGAIN_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS");

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(mappedStatus.getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldMapGatewayStatusWithCurrentStatus() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_CARE_ABOUT_THE_GATEWAY_STATUS_AND_THE_CURRENT_STATUS", SYSTEM_CANCEL_SUBMITTED);

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(mappedStatus.getChargeStatus(), is(SYSTEM_CANCELLED));
    }

    @Test
    public void shouldNotMapGatewayStatusWithCurrentStatusUnlessCurrentStatusMatches() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_CARE_ABOUT_THE_GATEWAY_STATUS_AND_THE_CURRENT_STATUS", AUTHORISATION_SUBMITTED);

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test
    public void shouldNotMapGatewayStatus_WhenNoCurrentStatusProvided() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_CARE_ABOUT_THE_GATEWAY_STATUS_AND_THE_CURRENT_STATUS");

        assertThat(mappedStatus.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test
    public void shouldKnowAboutUnknownUnknowns() {
        StatusMapper<String> statusMapper =
                StatusMapper
                        .<String>builder()
                        .build();

        InterpretedStatus unknownStatus = statusMapper.from("UNKNOWN_STATUS", AUTHORISATION_SUCCESS);

        assertThat(unknownStatus.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }
}
