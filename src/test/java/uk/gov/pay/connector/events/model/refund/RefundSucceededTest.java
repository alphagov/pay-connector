package uk.gov.pay.connector.events.model.refund;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class RefundSucceededTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
            .withGatewayAccountEntity(ChargeEntityFixture.defaultGatewayAccountEntity()).build();
    private ZonedDateTime createdDate = ZonedDateTime.parse("2018-03-12T16:25:01.123456Z");
    private UTCDateTimeConverter timeConverter = new UTCDateTimeConverter();

    private RefundHistory refundHistory = new RefundHistory(1L, "external_id", 50L,
            RefundStatus.REFUNDED.getValue(), timeConverter.convertToDatabaseColumn(createdDate), 1L,
            "reference", timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(1L)),
            timeConverter.convertToDatabaseColumn(createdDate.plusSeconds(2L)),
            "user-external-id", "gateway_transaction_id", charge.getExternalId(),
            "test@example.com");

    @Test
    public void serializesEventDetailsForAGivenRefundEvent() throws JsonProcessingException {
        String actual = RefundSucceeded.from(refundHistory).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("REFUND_SUCCEEDED")));
        assertThat(actual, hasJsonPath("$.timestamp", equalTo("2018-03-12T16:25:02.123456Z")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("refund")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(refundHistory.getExternalId())));
        assertThat(actual, hasJsonPath("$.parent_resource_external_id", equalTo(charge.getExternalId())));

        assertThat(actual, hasJsonPath("$.event_details.gateway_transaction_id", equalTo("gateway_transaction_id")));
    }

}
