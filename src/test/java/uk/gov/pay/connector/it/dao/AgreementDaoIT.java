package uk.gov.pay.connector.it.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomLong;

public class AgreementDaoIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private static final String AGREEMENT_EXTERNAL_ID_ONE = "12345678901234567890123456";
    private static final String AGREEMENT_EXTERNAL_ID_TWO = "65432109876543210987654321";
    private static final String SERVICE_ID = "a-valid-service-id";
    private AgreementDao agreementDao;
    private GatewayAccountEntity gatewayAccount1, gatewayAccount2;

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

    @Nested
    class FindAgreementByExternalId {

        @Test
        void shouldFindAnAgreementEntity() {
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_ONE);
            assertThat(agreement.isPresent(), is(true));
            assertThat(agreement.get().getExternalId(), is(AGREEMENT_EXTERNAL_ID_ONE));
        }

        @Test
        void shouldNotFindAnAgreementEntity_ifAgreementDoesNotExist() {
            Optional<AgreementEntity> agreement = agreementDao.findByExternalId(AGREEMENT_EXTERNAL_ID_TWO);
            assertThat(agreement.isPresent(), is(false));
        }
    }

    @Nested
    class FindAgreementByExternalIdAndGatewayAccountId {

        @Test
        void shouldFindAnAgreementEntity() {
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_TWO, gatewayAccount1.getId());
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndGatewayAccountId(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            assertThat(agreement.isPresent(), is(true));
            assertThat(agreement.get().getExternalId(), is(AGREEMENT_EXTERNAL_ID_ONE));
        }

        @Test
        void shouldNotFindAgreementEntity_forDifferentGatewayAccount() {
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndGatewayAccountId(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount2.getId());
            assertThat(agreement.isPresent(), is(false));
        }

        @Test
        void shouldNotFindAnAgreementEntity_ifAgreementDoesNotExist() {
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndGatewayAccountId(AGREEMENT_EXTERNAL_ID_TWO, gatewayAccount1.getId());
            assertThat(agreement.isPresent(), is(false));
        }
    }

    @Nested
    class FindAgreementByExternalIdAndServiceIdAndAccountType {

        @Test
        void shouldFindAnAgreementEntity() {
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_TWO, gatewayAccount1.getId());
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndServiceIdAndAccountType(AGREEMENT_EXTERNAL_ID_ONE, SERVICE_ID, GatewayAccountType.TEST);
            assertThat(agreement.isPresent(), is(true));
            assertThat(agreement.get().getExternalId(), is(AGREEMENT_EXTERNAL_ID_ONE));
        }

        @Test
        void shouldNotFindAnAgreementEntity_ifAgreementDoesNotExist() {
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndServiceIdAndAccountType(AGREEMENT_EXTERNAL_ID_ONE, SERVICE_ID, GatewayAccountType.TEST);
            assertThat(agreement.isPresent(), is(false));
        }

        @Test
        void shouldNotFindAnAgreementEntity_forADifferentService() {
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndServiceIdAndAccountType(AGREEMENT_EXTERNAL_ID_ONE, "another-service-id", GatewayAccountType.TEST);
            assertThat(agreement.isPresent(), is(false));
        }

        @Test
        void shouldNotFindAnAgreementEntity_forADifferentAccountType() {
            insertTestAgreement(AGREEMENT_EXTERNAL_ID_ONE, gatewayAccount1.getId());
            Optional<AgreementEntity> agreement = agreementDao.findByExternalIdAndServiceIdAndAccountType(AGREEMENT_EXTERNAL_ID_ONE, SERVICE_ID, GatewayAccountType.LIVE);
            assertThat(agreement.isPresent(), is(false));
        }
    }

    private void insertTestAgreement(String agreementExternalId, long gatewayAccountId) {
        app.getDatabaseFixtures()
                .aTestAgreement()
                .withAgreementId(randomLong())
                .withExternalId(agreementExternalId)
                .withReference("ref9876")
                .withGatewayAccountId(gatewayAccountId)
                .withServiceId(SERVICE_ID)
                .withLive(false)
                .insert();
    }

    private DatabaseFixtures.TestAccount insertTestAccount() {
        return app.getDatabaseFixtures()
                .aTestAccount()
                .withAccountId(randomLong())
                .withServiceId(SERVICE_ID)
                .insert();
    }

}
