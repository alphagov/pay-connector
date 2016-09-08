package uk.gov.pay.connector.model.api;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type;

public class ExternalChargeRefundAvailabilitySmartpayTest {

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenGatewayProviderIsSmartpay() {

        GatewayAccountEntity smartpayGateway = new GatewayAccountEntity("smartpay", newHashMap(), Type.TEST);

        assertThat(ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withGatewayAccountEntity(smartpayGateway)
                .build()), is(EXTERNAL_UNAVAILABLE));
    }
}
