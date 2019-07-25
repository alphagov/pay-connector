package uk.gov.pay.connector.events.model.refund;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithReferenceDetails;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;


public class RefundEventFactoryTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
    private ZonedDateTime createdDate = ZonedDateTime.now().minusSeconds(5L);
    private UTCDateTimeConverter timeConverter = new UTCDateTimeConverter();

    @Test
    public void givenARefundCreatedTypeShouldCreateARefundCreatedByUserEvent() {
        RefundHistory refundHistory = new RefundHistory(1L, "external_id", 50L, RefundStatus.CREATED.getValue(),
                charge.getId(), timeConverter.convertToDatabaseColumn(createdDate), 1L,
                null, timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(1L)),
                timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(2L)),
                "user_external_id", null, charge.getExternalId());
        RefundCreatedByUser refundEvent = (RefundCreatedByUser) RefundEventFactory.create(RefundCreatedByUser.class, refundHistory);

        assertThat(refundEvent.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(((RefundCreatedByUserEventDetails)refundEvent.getEventDetails()).getRefundedBy(), is("user_external_id"));
        assertThat(refundEvent.getResourceType(), is(ResourceType.REFUND));
        assertThat(refundEvent.getEventDetails(), is(instanceOf(RefundCreatedByUserEventDetails.class)));
    }

    @Test
    public void givenARefundSubmittedEventTypeShouldCreateTheCorrectPaymentEventType() {
        RefundHistory refundHistory = new RefundHistory(1L, "external_id", 50L, RefundStatus.REFUND_SUBMITTED.getValue(),
                charge.getId(), new UTCDateTimeConverter().convertToDatabaseColumn(createdDate), 1L,
                "refund_reference", new UTCDateTimeConverter().convertToDatabaseColumn(createdDate.plusSeconds(1L)),
                new UTCDateTimeConverter().convertToDatabaseColumn(createdDate.plusSeconds(2L)),
                "user_external_id", "gateway_transaction_id", charge.getExternalId());

        RefundSubmitted refundEvent = (RefundSubmitted) RefundEventFactory.create(RefundSubmitted.class, refundHistory);

        assertThat(refundEvent.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundEvent.getEventDetails(), is(instanceOf(RefundEventWithReferenceDetails.class)));
        assertThat(((RefundEventWithReferenceDetails)refundEvent.getEventDetails()).getReference(), is("refund_reference"));
    }
}
