package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;

public class StatusMapperTest {

    private final StatusMapper<String> statusMapper =
            StatusMapper
                    .<String>builder()
                    .ignore("IGNORED_STATUS")
                    .map("HERE_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS", CAPTURED)
                    .map("AGAIN_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS", REFUNDED)
                    .build();

    @Test
    public void shouldIgnore() {
        InterpretedStatus ignoredStatus = statusMapper.from("IGNORED_STATUS");
        assertThat(ignoredStatus.getType()).isEqualTo(InterpretedStatus.Type.IGNORED);
    }

    @Test
    public void shouldIgnore_WhenNoCurrentStatusProvided() {
        InterpretedStatus ignoredStatus = statusMapper.from("IGNORED_STATUS");
        assertThat(ignoredStatus.getType()).isEqualTo(InterpretedStatus.Type.IGNORED);
    }

    @Test
    public void shouldMapGatewayStatusOnlyToChargeStatus() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS");
        assertThat(mappedStatus.getType()).isEqualTo(InterpretedStatus.Type.CHARGE_STATUS);
        assertThat(mappedStatus.getChargeStatus()).isEqualTo(CAPTURED);
    }

    @Test
    public void shouldMapGatewayStatusOnlyToRefundStatus() {
        InterpretedStatus mappedStatus = statusMapper.from("AGAIN_WE_ONLY_CARE_ABOUT_THE_GATEWAY_STATUS");
        assertThat(mappedStatus.getType()).isEqualTo(InterpretedStatus.Type.REFUND_STATUS);
        assertThat(mappedStatus.getRefundStatus()).isEqualTo(REFUNDED);
    }

    @Test
    public void shouldNotMapGatewayStatus_WhenNoCurrentStatusProvided() {
        InterpretedStatus mappedStatus = statusMapper.from("HERE_WE_CARE_ABOUT_THE_GATEWAY_STATUS_AND_THE_CURRENT_STATUS");
        assertThat(mappedStatus.getType()).isEqualTo(InterpretedStatus.Type.UNKNOWN);
    }

    @Test
    public void shouldKnowAboutUnknownUnknowns() {
        StatusMapper<String> statusMapper = StatusMapper.<String>builder().build();
        InterpretedStatus unknownStatus = statusMapper.from("UNKNOWN_STATUS");
        assertThat(unknownStatus.getType()).isEqualTo(InterpretedStatus.Type.UNKNOWN);
    }
}
