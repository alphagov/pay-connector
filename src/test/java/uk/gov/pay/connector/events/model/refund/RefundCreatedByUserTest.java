package uk.gov.pay.connector.events.model.refund;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RefundCreatedByUserTest {

    private final ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
            .withGatewayAccountEntity(ChargeEntityFixture.defaultGatewayAccountEntity()).build();
    private final Charge charge = Charge.from(chargeEntity);
    private ZonedDateTime createdDate = ZonedDateTime.now().minusSeconds(5L);
    private UTCDateTimeConverter timeConverter = new UTCDateTimeConverter();

    private RefundHistory refundHistory = new RefundHistory(1L, "external_id", 50L, RefundStatus.CREATED.getValue(),
            timeConverter.convertToDatabaseColumn(createdDate), 1L,
            timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(1L)),
            timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(2L)),
            "user-external-id", "gateway_transaction_id", chargeEntity.getExternalId(),
            "test@example.com"
            );

    @Test
    void serializesEventDetailsForAGivenRefundEvent() {
        RefundCreatedByUser refundCreatedByUser = RefundCreatedByUser.from(refundHistory, charge);

        assertThat(refundCreatedByUser.getParentResourceExternalId(), is(chargeEntity.getExternalId()));
        assertThat(refundCreatedByUser.getResourceExternalId(), is("external_id"));

        RefundCreatedByUserEventDetails details = (RefundCreatedByUserEventDetails) refundCreatedByUser.getEventDetails();

        assertThat(details.getAmount(), is(50L));
        assertThat(details.getGatewayAccountId(), is(chargeEntity.getGatewayAccount().getId().toString()));
        assertThat(details.getRefundedBy(), is("user-external-id"));
        assertThat(details.getUserEmail(), is("test@example.com"));
    }

}
