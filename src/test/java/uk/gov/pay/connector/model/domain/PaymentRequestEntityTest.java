package uk.gov.pay.connector.model.domain;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class PaymentRequestEntityTest {

    private PaymentRequestEntity paymentRequestEntity;

    @Before
    public void setUp() throws Exception {

        paymentRequestEntity = new PaymentRequestEntity();
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        paymentRequestEntity.setGatewayAccount(gatewayAccount);
    }


}
