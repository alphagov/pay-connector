package uk.gov.pay.connector.it.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

public class AgreementDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();
//    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();

    private AgreementDao agreementDao;
    private GatewayAccountEntity gatewayAccount1, gatewayAccount2;
    private static final String AGREEMENT_EXTERNAL_ID_ONE = "12345678901234567890123456";
    private static final String AGREEMENT_EXTERNAL_ID_TWO = "65432109876543210987654321";
    
    @BeforeEach
    void setUp() {
        agreementDao = app.getInstanceFromGuiceContainer(AgreementDao.class);

        DatabaseFixtures.TestAccount testAccount1 = insertTestAccount();
        gatewayAccount1 = new GatewayAccountEntity(TEST);
        gatewayAccount1.setId(testAccount1.getAccountId());

        DatabaseFixtures.TestAccount testAccount2 = insertTestAccount();
        gatewayAccount2 = new GatewayAccountEntity(TEST);
        gatewayAccount2.setId(testAccount2.accountId);
    }

    @Test
    void findByExternalId_shouldFindAnAgreementEntityForGatewayAccount() {
        insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
        insertTestAgreement(AGREEMENT_EXTERNAL_ID_TWO, gatewayAccount1.getId());
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
        assertThat(agreement.isPresent(), is(true));
        assertThat(agreement.get().getExternalId(), is(AGREEMENT_EXTERNAL_ID_ONE));
    }

    @Test
    void findByExternalId_shouldNotFindAgreementEntityForDifferentGatewayAccount() {
        insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount2.getId());
        assertThat(agreement.isPresent(), is(false));
    }

    @Test
    void findByExternalId_shouldNotFindAnAgreementEntity() {
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_TWO, gatewayAccount1.getId());
        assertThat(agreement.isPresent(), is(false));
    }

    @Test
    void findByExternalIdOnly_shouldFindAnAgreementEntity() {
        insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_ONE);
        assertThat(agreement.isPresent(), is(true));
        assertThat(agreement.get().getExternalId(), is(AGREEMENT_EXTERNAL_ID_ONE));
    }

    @Test
    void findByExternalIdOnly_shouldNotFindAnAgreementEntity() {
        Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_TWO);
        assertThat(agreement.isPresent(), is(false));
    }

    private void insertTestAgreement(String agreementExternalId, long gatewayAccountId) {
        app.getDatabaseFixtures()
                .aTestAgreement()
                .withAgreementId(nextLong())
                .withExternalId(agreementExternalId)
                .withReference("ref9876")
                .withGatewayAccountId(gatewayAccountId)
                .insert();
    }

    private DatabaseFixtures.TestAccount insertTestAccount() {
        return app.getDatabaseFixtures()
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
    }

}
