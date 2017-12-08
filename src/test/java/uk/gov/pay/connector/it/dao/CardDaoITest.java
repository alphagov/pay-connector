package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.CardDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import java.util.HashMap;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.CardDetailsEntityFixture.aValidCardDetailsEntity;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class CardDaoITest extends DaoITestBase {

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private CardDao cardDao;
    private ChargeDao chargeDao;
    private PaymentRequestDao paymentRequestDao;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        cardDao = env.getInstance(CardDao.class);
        chargeDao = env.getInstance(ChargeDao.class);
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);
        insertTestAccount();
    }

    @Test
    public void shouldCreateACardEntity() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        chargeDao.persist(chargeEntity);

        ChargeTransactionEntity chargeTransactionEntity = ChargeTransactionEntity.from(chargeEntity);

        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntity.from(chargeEntity, chargeTransactionEntity);
        paymentRequestDao.persist(paymentRequestEntity);

        CardDetailsEntity cardDetailsEntity = aValidCardDetailsEntity().build();
        CardEntity cardEntity = CardEntity.from(cardDetailsEntity, chargeTransactionEntity);
        cardDao.persist(cardEntity);

        assertThat(cardEntity.getId(), is(notNullValue()));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }

}
