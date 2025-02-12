package uk.gov.pay.connector.refund.service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class WorldpayRefundEntityFactoryTest {

    private static final long AMOUNT = 100L;
    private static final String USER_EXTERNAL_ID = "luser";
    public static final String EMAIL = "luser@email.test";
    public static final String CHARGE_EXTERNAL_ID = "abc";

    private final WorldpayRefundEntityFactory worldpayRefundEntityFactory = new WorldpayRefundEntityFactory();

    @Test
    void shouldCreateRefundEntity() {
        var refundEntity = worldpayRefundEntityFactory.create(AMOUNT, USER_EXTERNAL_ID, EMAIL, CHARGE_EXTERNAL_ID);

        assertThat(refundEntity.getAmount(), is(AMOUNT));
        assertThat(refundEntity.getUserExternalId(), is(USER_EXTERNAL_ID));
        assertThat(refundEntity.getUserEmail(), is(EMAIL));
        assertThat(refundEntity.getChargeExternalId(), is(CHARGE_EXTERNAL_ID));
        assertThat(refundEntity.getExternalId(), is(notNullValue()));
        assertThat(refundEntity.getGatewayTransactionId(), is(refundEntity.getExternalId()));
    }

}
