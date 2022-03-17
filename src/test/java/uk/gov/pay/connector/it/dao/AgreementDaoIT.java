package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

public class AgreementDaoIT extends DaoITestBase {

    private AgreementDao agreementDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    private static final String AGREEMENT_EXTERNAL_ID_ONE = "12345678901234567890123456";
    private static final String AGREEMENT_EXTERNAL_ID_TWO = "65432109876543210987654321";
    
    @Before
    public void setUp() {
        agreementDao = env.getInstance(AgreementDao.class);
        defaultTestCardDetails = new DatabaseFixtures(databaseTestHelper).validTestCardDetails();
        insertTestAccount();

        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());

        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .withState(ACTIVE)
                .build();
        gatewayAccountCredentialsEntity.setId(defaultTestAccount.getCredentials().get(0).getId());
    }

    @After
    public void clear() {
        databaseTestHelper.truncateAllData();
    }
    
    @Test
    public void findByExternalId_shouldFindAnAgreementEntity() {
        insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE);
        insertTestAgreement(AGREEMENT_EXTERNAL_ID_TWO);
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_ONE);
        assertThat(agreement.isPresent(), is(true));
        assertThat(agreement.get().getExternalId(), is(AGREEMENT_EXTERNAL_ID_ONE));
    }

    @Test
    public void findByExternalId_shouldNotFindAnAgreementEntity() {
        //insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE);
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_TWO);
        assertThat(agreement.isPresent(), is(false));
    }
    
    private void insertTestAgreement(String agreementExternalId) {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAgreement()
                .withAgreementId(nextLong())
                .withExternalId(agreementExternalId)
                .withReference("ref9876")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .insert();
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
    }
    
}
