package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;

import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;

public class PaymentRequestDaoITest extends DaoITestBase {
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private PaymentRequestDao paymentRequestDao;
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        insertTestAccount();
    }

    @Test
    public void shouldCreateAPaymentRequest() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        paymentRequestDao.persist(paymentRequestEntity);

        assertThat(paymentRequestEntity.getId(), is(notNullValue()));
        // Ensure always max precision is being millis
        assertThat(paymentRequestEntity.getCreatedDate().getNano() % 1000000, is(0));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
