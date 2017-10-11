package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.CardholderDataDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.CardholderDataEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;

import java.util.HashMap;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.CardholderDataEntityFixture.aValidCardholderDataEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.PaymentRequestEntityFixture.aValidPaymentRequestEntity;

public class CardholderDataDaoITest extends DaoITestBase {

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private CardholderDataDao cardholderDataDao;
    private PaymentRequestDao paymentRequestDao;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        cardholderDataDao = env.getInstance(CardholderDataDao.class);
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        insertTestAccount();
    }

    @Test
    public void shouldCreateACardholderDataEntity() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
        PaymentRequestEntity paymentRequestEntity = aValidPaymentRequestEntity()
                .withGatewayAccountEntity(gatewayAccount).build();

        paymentRequestDao.persist(paymentRequestEntity);

        CardholderDataEntity cardholderDataEntity = aValidCardholderDataEntity()
                .withPaymentRequestExternalId(paymentRequestEntity.getExternalId())
                .build();

        cardholderDataDao.persist(cardholderDataEntity);

        assertThat(cardholderDataEntity.getId(), is(notNullValue()));

    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
