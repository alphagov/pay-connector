package uk.gov.pay.connector.events.model.refund;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByServiceEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RefundCreatedByServiceTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
            .withGatewayAccountEntity(ChargeEntityFixture.defaultGatewayAccountEntity()).build();
    private ZonedDateTime createdDate = ZonedDateTime.now().minusSeconds(5L);
    private UTCDateTimeConverter timeConverter = new UTCDateTimeConverter();

    private RefundHistory refundHistory = new RefundHistory(charge.getServiceId(), charge.getGatewayAccount().isLive(), 1L, "external_id", 50L, RefundStatus.CREATED.getValue(),
            timeConverter.convertToDatabaseColumn(createdDate), 1L,
            timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(1L)),
            timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(2L)),
            null, "gateway_transaction_id", charge.getExternalId(),
            null);

    @Test
    public void serializesEventDetailsGivenRefund() {
        RefundCreatedByService refundCreatedByService = RefundCreatedByService
                .from(refundHistory, charge.getGatewayAccount().getId());

        assertThat(refundCreatedByService.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundCreatedByService.getResourceExternalId(), is("external_id"));

        RefundCreatedByServiceEventDetails details = (RefundCreatedByServiceEventDetails) refundCreatedByService.getEventDetails();

        assertThat(details.getAmount(), is(50L));
        assertThat(details.getGatewayAccountId(), is(charge.getGatewayAccount().getId().toString()));
    }
}
