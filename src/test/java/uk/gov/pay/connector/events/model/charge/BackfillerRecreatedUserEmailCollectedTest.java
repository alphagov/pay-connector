package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;

class BackfillerRecreatedUserEmailCollectedTest {

    private final String time = "2018-03-12T16:25:01.123456Z";

    private ChargeEntityFixture chargeEntityFixture;

    @BeforeEach
    public void setUp() {
        ZonedDateTime latestDateTime = ZonedDateTime.parse(time);

        List<ChargeEventEntity> list = List.of(
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.CREATED).withUpdated(latestDateTime.minusHours(3)).build(),
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.ENTERING_CARD_DETAILS).withUpdated(latestDateTime).build(),
                aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.USER_CANCELLED).withUpdated(latestDateTime.plusHours(1)).build()
        );

        String paymentId = "jweojfewjoifewj";
        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse(time))
                .withStatus(ChargeStatus.USER_CANCELLED)
                .withExternalId(paymentId)
                .withAmount(100L)
                .withEvents(list);
    }

    @Test
    void serializesEventDetailsGivenChargeEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        String actual = BackfillerRecreatedUserEmailCollected.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(time)));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("BACKFILLER_RECREATED_USER_EMAIL_COLLECTED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo(chargeEntity.getEmail())));
    }
}
