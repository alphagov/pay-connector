package uk.gov.pay.connector.events.model.refund;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RefundCreatedByUserTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
            .withGatewayAccountEntity(ChargeEntityFixture.defaultGatewayAccountEntity()).build();
    private ZonedDateTime createdDate = ZonedDateTime.now().minusSeconds(5L);
    private UTCDateTimeConverter timeConverter = new UTCDateTimeConverter();

    private RefundHistory refundHistory = new RefundHistory(1L, "external_id", 50L, RefundStatus.CREATED.getValue(),
            charge.getId(), timeConverter.convertToDatabaseColumn(createdDate), 1L,
            "reference", timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(1L)),
            timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(2L)),
            "user-external-id", "gateway_transaction_id", charge.getExternalId(), charge.getGatewayAccount().getId());

    @Test
    public void serializesEventDetailsForAGivenRefundEvent() {
        RefundCreatedByUser refundCreatedByUser = RefundCreatedByUser.from(refundHistory);

        assertThat(refundCreatedByUser.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundCreatedByUser.getResourceExternalId(), is("external_id"));

        RefundCreatedByUserEventDetails details = (RefundCreatedByUserEventDetails) refundCreatedByUser.getEventDetails();

        assertThat(details.getAmount(), is(50L));
        assertThat(details.getGatewayAccountId(), is(charge.getGatewayAccount().getId().toString()));
        assertThat(details.getRefundedBy(), is("user-external-id"));
    }

}
