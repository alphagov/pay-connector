package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.Card3dsDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.Card3dsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import java.util.HashMap;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

public class Card3dsDaoITest extends DaoITestBase {private DatabaseFixtures.TestAccount defaultTestAccount;
    private Card3dsDao card3dsDao;
    private ChargeDao chargeDao;
    private PaymentRequestDao paymentRequestDao;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        card3dsDao = env.getInstance(Card3dsDao.class);
        chargeDao = env.getInstance(ChargeDao.class);
        paymentRequestDao = env.getInstance(PaymentRequestDao.class);

        insertTestAccount();
    }

    @Test
    public void shouldCreateACardEntity() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                defaultTestAccount.getPaymentProvider(), new HashMap<>(), TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaRequest("Test pa request")
                .withIssuerUrl("http://example.com")
                .build();

        chargeDao.persist(chargeEntity);
        PaymentRequestEntity paymentRequest = PaymentRequestEntity.from(chargeEntity, ChargeTransactionEntity.from(chargeEntity));
        paymentRequestDao.persist(paymentRequest);


        Card3dsEntity card3dsEntity = Card3dsEntity.from(chargeEntity, paymentRequest);

        card3dsDao.persist(card3dsEntity);

        assertThat(card3dsEntity.getId(), is(notNullValue()));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();
    }
}
