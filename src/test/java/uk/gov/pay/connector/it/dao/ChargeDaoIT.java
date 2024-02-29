package uk.gov.pay.connector.it.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestCharge;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import javax.validation.ConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.Duration.ofMinutes;
import static java.time.ZonedDateTime.now;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.Auth3dsRequiredEntityFixture.anAuth3dsRequiredEntity;
import static uk.gov.service.payments.commons.model.AuthorisationMode.EXTERNAL;
import static uk.gov.service.payments.commons.model.AuthorisationMode.WEB;

public class ChargeDaoIT extends DaoITestBase {

    private static final String DESCRIPTION = "Test description";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ChargeDao chargeDao;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseFixtures.TestCardDetails defaultTestCardDetails;

    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @Before
    public void setUp() {
        chargeDao = env.getInstance(ChargeDao.class);

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
    public void chargeEvents_shouldRecordTransactionIdWithEachStatusChange() {
        Long chargeId = 56735L;
        String externalChargeId = "charge456";

        String transactionId = "345654";

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();

        Optional<ChargeEntity> charge = chargeDao.findById(chargeId);
        ChargeEntity entity = charge.get();
        entity.setStatus(ENTERING_CARD_DETAILS);
    }

    @Test
    public void invalidSizeOfReference() {
        expectedEx.expect(RuntimeException.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
        chargeDao.persist(aValidChargeEntity().withReference(ServicePaymentReference.of(RandomStringUtils.randomAlphanumeric(255))).build());
    }

    @Test
    public void shouldCreateANewCharge() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withAuthorisationMode(AuthorisationMode.MOTO_API)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        assertThat(chargeEntity.getId(), is(notNullValue()));

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());
        assertThat(charge.get().getAuthorisationMode(), is(AuthorisationMode.MOTO_API));
    }

    @Test
    public void shouldCreateANewChargeWith3dsDetails() {
        String paRequest = "3dsPaRequest";
        String issuerUrl = "https://issuer.example.com/3ds";
        String htmlOut = "<body><div>Some HTML</div></body>";
        String md = "some-md";
        String worldpayChallengeAcsUrl = "http://example.com/some-challenge";
        String worldpayChallengeTransactionId = "a-transaction-id";
        String worldpayChallengePayload = "{\"payload\": {\"key1\":\"value\"}}";
        String threeDsVersion = "2.0";

        var auth3dsDetailsEntity = anAuth3dsRequiredEntity()
                .withPaRequest(paRequest)
                .withIssuerUrl(issuerUrl)
                .withHtmlOut(htmlOut)
                .withMd(md)
                .withWorldpayChallengeAcsUrl(worldpayChallengeAcsUrl)
                .withWorldpayChallengeTransactionId(worldpayChallengeTransactionId)
                .withWorldpayChallengePayload(worldpayChallengePayload)
                .withThreeDsVersion(threeDsVersion)
                .build();
        var chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withAuth3dsDetailsEntity(auth3dsDetailsEntity)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getPaRequest(), is(paRequest));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getIssuerUrl(), is(issuerUrl));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getHtmlOut(), is(htmlOut));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getMd(), is(md));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengeAcsUrl(), is(worldpayChallengeAcsUrl));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengeTransactionId(), is(worldpayChallengeTransactionId));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getWorldpayChallengePayload(), is(worldpayChallengePayload));
        assertThat(charge.get().getChargeCardDetails().get3dsRequiredDetails().getThreeDsVersion(), is(threeDsVersion));
    }

    @Test
    public void shouldCreateANewChargeWithProviderSessionId() {
        String providerSessionId = "provider-session-id-value";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withProviderSessionId(providerSessionId)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getChargeCardDetails().getProviderSessionId(), is(providerSessionId));
    }

    @Test
    public void shouldCreateANewChargeWithServiceId() {
        var serviceId = "a-valid-external-service-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withServiceId(serviceId)
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getServiceId(), is(serviceId));
    }

    @Test
    public void shouldSetUpdatedDateOnChargeCorrectly() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withId(null)
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withUpdatedDate(Instant.parse("2022-12-03T10:15:30Z"))
                .build();

        assertThat(chargeEntity.getId(), is(nullValue()));

        chargeDao.persist(chargeEntity);

        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getUpdatedDate().toString(), is("2022-12-03T10:15:30Z"));
    }

    @Test
    public void shouldCreateANewChargeWithExternalMetadata() {
        ExternalMetadata expectedExternalMetadata = new ExternalMetadata(
                Map.of("key1", "String1",
                        "key2", 123,
                        "key3", true));

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withExternalMetadata(expectedExternalMetadata)
                .build();

        chargeDao.persist(chargeEntity);
        chargeDao.forceRefresh(chargeEntity);
        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getExternalMetadata().get().getMetadata(), equalTo(expectedExternalMetadata.getMetadata()));
    }

    @Test
    public void shouldCreateANewChargeWithSource() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withSource(Source.CARD_API)
                .build();

        chargeDao.persist(chargeEntity);
        chargeDao.forceRefresh(chargeEntity);
        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getSource(), equalTo(Source.CARD_API));
    }

    @Test
    public void shouldNotSaveChargeWithInvalidExternalMetadata() {
        var metaDataWithAnObject = new ExternalMetadata(
                Map.of("key_1", Map.of("key_1_a", "some value")));

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalMetadata(metaDataWithAnObject)
                .build();
        try {
            chargeDao.persist(chargeEntity);
            fail("Persist should throw a ConstraintViolationException");
        } catch (ConstraintViolationException ex) {
            assertThat(ex.getConstraintViolations().size(), is(1));
            assertThat(ex.getConstraintViolations().iterator().next().getMessage(), is("Field [metadata] values must be of type String, Boolean or Number"));
        }
    }

    @Test
    public void should_update_charge_with_exemption_3ds() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .build();
        chargeDao.persist(chargeEntity);

        assertNull(chargeDao.findByExternalId(chargeEntity.getExternalId()).get().getChargeCardDetails().getExemption3ds());

        chargeEntity.getChargeCardDetails().setExemption3ds(Exemption3ds.EXEMPTION_NOT_REQUESTED);
        chargeDao.merge(chargeEntity);

        assertEquals(chargeDao.findByExternalId(chargeEntity.getExternalId()).get().getChargeCardDetails().getExemption3ds(),
                Exemption3ds.EXEMPTION_NOT_REQUESTED);
    }

    @Test
    public void shouldCreateNewChargeWithParityCheckStatus() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withParityStatus(ParityCheckStatus.EXISTS_IN_LEDGER)
                .build();

        chargeDao.persist(chargeEntity);
        chargeDao.forceRefresh(chargeEntity);
        Optional<ChargeEntity> charge = chargeDao.findById(chargeEntity.getId());

        assertThat(charge.get().getParityCheckStatus(), equalTo(ParityCheckStatus.EXISTS_IN_LEDGER));
        assertThat(charge.get().getParityCheckDate(), is(notNullValue()));
    }
    
    @Test
    public void shouldReturnNullFindingByIdWhenChargeDoesNotExist() {

        Optional<ChargeEntity> charge = chargeDao.findById(5686541L);

        assertThat(charge.isPresent(), is(false));
    }

    @Test
    public void shouldFindChargeEntityByProviderAndTransactionId() {

        // given
        String transactionId = "7826782163";
        Instant createdDate = Instant.now();
        Long chargeId = 9999L;
        String externalChargeId = "charge9999";

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .withChargeId(chargeId)
                .withCreatedDate(createdDate)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();
        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .validTestCardDetails()
                .withChargeId(chargeId)
                .update();

        // when
        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        // then
        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getPaymentProvider(), is(testCharge.getPaymentProvider()));
        assertThat(charge.getChargeCardDetails().getCardBrand(), is(testCardDetails.getCardBrand()));
        assertThat(charge.getExternalMetadata(), is(Optional.empty()));
    }

    @Test
    public void shouldGetGatewayAccountWhenFindingChargeEntityByProviderAndTransactionId() {

        String transactionId = "7826782163";

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(8888L)
                .withExternalChargeId("charge8888")
                .withTransactionId(transactionId)
                .insert();

        Optional<ChargeEntity> chargeOptional = chargeDao.findByProviderAndTransactionId(defaultTestAccount.getPaymentProvider(), transactionId);

        assertThat(chargeOptional.isPresent(), is(true));

        ChargeEntity charge = chargeOptional.get();
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();
        assertThat(gatewayAccount, is(notNullValue()));
        assertThat(gatewayAccount.getId(), is(defaultTestAccount.getAccountId()));
        assertThat(gatewayAccount.getGatewayName(), is(defaultTestAccount.getPaymentProvider()));
    }

    @Test
    public void shouldGetChargeByChargeIdWithCorrectAssociatedAccountId() {
        String transactionId = "7826782163";
        Instant createdDate = Instant.now();
        Long chargeId = 876786L;
        String externalChargeId = "charge876786";

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .withChargeId(chargeId)
                .withCreatedDate(createdDate)
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId)
                .insert();
        DatabaseFixtures.TestCardDetails testCardDetails = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .validTestCardDetails()
                .withChargeId(chargeId)
                .update();

        ChargeEntity charge = chargeDao.findByExternalIdAndGatewayAccount(externalChargeId, defaultTestAccount.getAccountId()).get();

        assertThat(charge.getId(), is(chargeId));
        assertThat(charge.getGatewayTransactionId(), is(transactionId));
        assertThat(charge.getReturnUrl(), is(testCharge.getReturnUrl()));
        assertThat(charge.getStatus(), is(testCharge.getChargeStatus().toString()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getCreatedDate(), is(createdDate));
        assertThat(charge.getReference(), is(testCharge.getReference()));
        assertThat(charge.getGatewayAccount(), is(notNullValue()));
        assertThat(charge.getPaymentProvider(), is(defaultTestAccount.getPaymentProvider()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getChargeCardDetails().getCardBrand(), is(testCardDetails.getCardBrand()));
    }

    @Test
    public void shouldGetChargeByChargeIdAsNullWhenAccountIdDoesNotMatch() {
        insertTestCharge();
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalIdAndGatewayAccount(defaultTestCharge.getExternalChargeId(), 456781L);
        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void findById_shouldFindChargeEntity() {

        // given
        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .withCanRetry(true)
                .insert();
        defaultTestCardDetails
                .withChargeId(defaultTestCharge.chargeId)
                .update();

        insertTestRefund();

        // when
        ChargeEntity charge = chargeDao.findById(defaultTestCharge.getChargeId()).get();

        // then
        assertThat(charge.getId(), is(defaultTestCharge.getChargeId()));
        assertThat(charge.getAmount(), is(defaultTestCharge.getAmount()));
        assertThat(charge.getReference(), is(defaultTestCharge.getReference()));
        assertThat(charge.getDescription(), is(DESCRIPTION));
        assertThat(charge.getStatus(), is(CREATED.getValue()));
        assertThat(charge.getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
        assertThat(charge.getPaymentProvider(), is(defaultTestAccount.getPaymentProvider()));
        assertThat(charge.getReturnUrl(), is(defaultTestCharge.getReturnUrl()));
        assertThat(charge.getCreatedDate(), is(defaultTestCharge.getCreatedDate()));
        assertThat(charge.getChargeCardDetails().getCardBrand(), is(defaultTestCardDetails.getCardBrand()));
        assertThat(charge.getCanRetry(), is(true));
    }

    @Test
    public void findByExternalId_shouldFindAChargeEntity() {
        insertTestCharge();
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(chargeForAccount.isPresent(), is(true));
    }

    @Test
    public void findByExternalId_shouldNotFindAChargeEntity() {
        Optional<ChargeEntity> chargeForAccount = chargeDao.findByExternalId("abcdefg123");
        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void testFindByDate_status_findsValidChargeForStatus() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(Instant.now().minus(Duration.ofHours(1)), chargeStatuses);

        assertThat(charges.size(), is(1));
        assertEquals(charges.get(0).getId(), charge.getChargeId());
    }

    @Test
    public void testFindByDateStatus_findsNoneForValidStatus() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CAPTURE_READY, SYSTEM_CANCELLED);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(Instant.now().minus(Duration.ofHours(1)), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void testFindByDateStatus_findsNoneForExpiredDate() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(ofMinutes(30)))
                .insert();

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(Instant.now().minus(Duration.ofHours(1)), chargeStatuses);

        assertThat(charges.size(), is(0));
    }

    @Test
    public void findChargesByCreatedUpdatedDatesAndWithStatusIn_shouldReturnChargesCorrectly() {
        TestCharge chargeCreatedBeforeDateAndUpdateDateNullShouldReturn = createCharge(Instant.now().minus(ofMinutes(40)), null, CREATED);
        TestCharge chargeUpdatedBeforeDateShouldReturn = createCharge(Instant.now().minus(ofMinutes(40)), Instant.now().minus(ofMinutes(15)), CREATED);
        TestCharge chargeCreatedWithInLast20MinutesShouldExclude = createCharge(Instant.now().minus(ofMinutes(20)), Instant.now().minus(ofMinutes(15)), ENTERING_CARD_DETAILS);
        TestCharge chargeUpdatedWithInLast3MinutesShouldExclude = createCharge(Instant.now().minus(ofMinutes(40)), Instant.now().minus(ofMinutes(3)), ENTERING_CARD_DETAILS);
        TestCharge chargeWithStatusNotQueriedShouldExclude = createCharge(Instant.now().minus(ofMinutes(40)), Instant.now().minus(ofMinutes(3)), AUTHORISATION_3DS_READY);

        ArrayList<ChargeStatus> chargeStatuses = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findChargesByCreatedUpdatedDatesAndWithStatusIn(
                Instant.now().minus(Duration.ofMinutes(30)),
                Instant.now().minus(ofMinutes(10)),
                chargeStatuses);

        assertThat(charges.size(), is(2));

        assertThat(charges, containsInAnyOrder(
                hasProperty("externalId", is(chargeCreatedBeforeDateAndUpdateDateNullShouldReturn.externalChargeId)),
                hasProperty("externalId", is(chargeUpdatedBeforeDateShouldReturn.externalChargeId))
        ));
    }

    private TestCharge createCharge(Instant createdDate, Instant updatedDate, ChargeStatus status) {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(createdDate)
                .withUpdatedDate(updatedDate)
                .withChargeStatus(status)
                .insert();
    }

    @Test
    public void testFindChargeByUnusedTokenId() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now())
                .withAmount(300L)
                .insert();

        databaseTestHelper.addToken(charge.getChargeId(), "some-token-id");

        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId("some-token-id");
        assertTrue(chargeOpt.isPresent());
        assertEquals(chargeOpt.get().getExternalId(), charge.getExternalChargeId());

        assertThat(chargeOpt.get().getGatewayAccount(), is(notNullValue()));
        assertThat(chargeOpt.get().getGatewayAccount().getId(), is(defaultTestAccount.getAccountId()));
    }

    @Test
    public void testFindChargeByUsedTokenId() {
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();

        databaseTestHelper.addToken(charge.getChargeId(), "used-token-id", true);

        Optional<ChargeEntity> chargeOpt = chargeDao.findByTokenId("used-token-id");
        assertTrue(chargeOpt.isEmpty());
    }

    @Test
    public void countChargesForCapture_shouldReturnNumberOfChargesInCaptureApprovedState() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(charge.getChargeId())
                .withDate(now())
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.countChargesForImmediateCapture(Duration.ofHours(1)), is(2));
    }

    @Test
    public void countCaptureRetriesForChargeExternalId_shouldReturnNumberOfRetries() {
        long chargeId = nextLong();
        String externalChargeId = RandomIdGenerator.newId();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();

        assertThat(chargeDao.countCaptureRetriesForChargeExternalId(externalChargeId), is(0));

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId)
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId)
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.countCaptureRetriesForChargeExternalId(externalChargeId), is(2));
    }

    @Test
    public void count3dsRequiredEventsForChargeExternalId() {
        long chargeId = nextLong();
        String externalChargeId = RandomIdGenerator.newId();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .withChargeStatus(AUTHORISATION_3DS_READY)
                .insert();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId)
                .withChargeStatus(AUTHORISATION_3DS_REQUIRED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestChargeEvent()
                .withChargeId(chargeId)
                .withChargeStatus(AUTHORISATION_3DS_REQUIRED)
                .insert();

        assertThat(chargeDao.count3dsRequiredEventsForChargeExternalId(externalChargeId), is(2));
    }

    @Test
    public void findByIdAndLimit() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .withChargeStatus(CAPTURE_APPROVED)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(nextLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .withCreatedDate(Instant.now().minus(Duration.ofHours(2)))
                .withChargeStatus(CAPTURE_APPROVED_RETRY)
                .insert();

        assertThat(chargeDao.findByIdAndLimit(0L, 2).size(), is(2));
    }

    @Test
    public void findByGatewayTransactionId() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("some-external-id")
                .withTransactionId("gateway-transaction-id")
                .insert();

        ChargeEntity chargeEntity = chargeDao.
                findByGatewayTransactionId("gateway-transaction-id")
                .get();

        assertThat(chargeEntity.getExternalId(), is("some-external-id"));
    }

    @Test
    public void findByGatewayTransactionIdAndAccount() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("some-external-id")
                .withTransactionId("gateway-transaction-id")
                .insert();

        DatabaseFixtures.TestAccount anotherGatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(anotherGatewayAccount)
                .withExternalChargeId("some-external-id2")
                .withTransactionId("gateway-transaction-id")
                .insert();

        ChargeEntity chargeEntity = chargeDao.
                findByGatewayTransactionIdAndAccount(defaultTestAccount.getAccountId(), "gateway-transaction-id")
                .get();

        assertThat(chargeEntity.getExternalId(), is("some-external-id"));
    }

    @Test
    public void getChargeWithAFee_shouldReturnFeeOnCharge() {
        insertTestCharge();

        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestFee()
                .withFeeDue(100L)
                .withFeeCollected(10L)
                .withTestCharge(defaultTestCharge)
                .insert();

        assertThat(chargeDao
                        .findById(defaultTestCharge.getChargeId())
                        .flatMap(ChargeEntity::getFeeAmount)
                        .get(),
                is(10L)
        );
    }

    @Test
    public void findMaxId_returnsTheMaximumId() {
        insertTestCharge();

        assertThat(chargeDao.findMaxId(), is(defaultTestCharge.getChargeId()));
    }

    @Test
    public void findChargesByParityCheckStatus() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER)
                .insert();

        var charges = chargeDao.findByParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER, 1, 0L);

        assertThat(charges.size(), is(1));
        assertThat(charges.get(0).getParityCheckStatus(), is(ParityCheckStatus.MISSING_IN_LEDGER));
    }

    @Test
    public void findChargeToExpunge_shouldReturnChargeReadyForExpunging() {
        TestCharge chargeToExpunge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCreatedDate(Instant.now().minus(Duration.ofDays(90)))
                .withParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER)
                .insert();

        TestCharge chargeEligibleButParityCheckedWithInWeek = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCreatedDate(Instant.now().minus(Duration.ofDays(90)))
                .withParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER)
                .withParityCheckDate(now(ZoneId.of("UTC")).minusDays(6))
                .insert();

        TestCharge chargeToExclude = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCreatedDate(Instant.now().minus(Duration.ofDays(4)))
                .withParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER)
                .withParityCheckDate(now(ZoneId.of("UTC")).minusDays(1))
                .insert();

        Optional<ChargeEntity> maybeChargeToExpunge = chargeDao.findChargeToExpunge(5, 7);

        assertThat(maybeChargeToExpunge.isPresent(), is(true));
        ChargeEntity chargeToExpungeFromDB = maybeChargeToExpunge.get();
        assertThat(chargeToExpungeFromDB.getId(), is(chargeToExpunge.getChargeId()));
        assertThat(chargeToExpungeFromDB.getExternalId(), is(chargeToExpunge.getExternalChargeId()));
        assertThat(chargeToExpungeFromDB.getCreatedDate(), is(chargeToExpunge.getCreatedDate()));
    }

    @Test
    public void findChargeToExpunge_shouldReturnParityCheckedChargeIfFallsWithinExcludeChargesParityCheckedWithinDaysParameter() {
        TestCharge chargeToExpunge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withCreatedDate(Instant.now().minus(Duration.ofDays(90)))
                .withParityCheckDate(now(ZoneId.of("UTC")))
                .withParityCheckStatus(ParityCheckStatus.MISSING_IN_LEDGER)
                .insert();

        Optional<ChargeEntity> maybeChargeToExpunge = chargeDao.findChargeToExpunge(5, 0);

        assertThat(maybeChargeToExpunge.isPresent(), is(true));
        ChargeEntity chargeToExpungeFromDB = maybeChargeToExpunge.get();
        assertThat(chargeToExpungeFromDB.getId(), is(chargeToExpunge.getChargeId()));
        assertThat(chargeToExpungeFromDB.getExternalId(), is(chargeToExpunge.getExternalChargeId()));
        assertThat(chargeToExpungeFromDB.getCreatedDate(), is(chargeToExpunge.getCreatedDate()));
    }

    @Test
    public void shouldFindChargesWithPaymentProviderAndStatuses() {
        DatabaseFixtures.TestAccount epdqAccount = insertTestAccountWithProvider("epdq");
        DatabaseFixtures.TestAccount worldpayAccount = insertTestAccountWithProvider("worldpay");

        TestCharge epdqCreatedCharge = insertTestChargeWithStatus(epdqAccount, CREATED);
        insertTestChargeWithStatus(worldpayAccount, CREATED);
        TestCharge epdqEnteringCardDetailsCharge = insertTestChargeWithStatus(epdqAccount, ENTERING_CARD_DETAILS);
        insertTestChargeWithStatus(epdqAccount, AUTHORISATION_SUCCESS);

        List<ChargeEntity> charges = chargeDao.findWithPaymentProviderAndStatusIn("epdq",
                List.of(CREATED, ENTERING_CARD_DETAILS), 10);

        assertThat(charges, hasSize(2));
        assertThat(charges, containsInAnyOrder(
                hasProperty("externalId", is(epdqCreatedCharge.externalChargeId)),
                hasProperty("externalId", is(epdqEnteringCardDetailsCharge.externalChargeId))
        ));
    }

    @Test
    public void shouldFindChargesWithPaymentProviderAndStatusesWithLimit() {
        DatabaseFixtures.TestAccount testAccount = insertTestAccountWithProvider("epdq");
        insertTestChargeWithStatus(testAccount, CREATED);
        insertTestChargeWithStatus(testAccount, CREATED);
        insertTestChargeWithStatus(testAccount, CREATED);

        List<ChargeEntity> charges = chargeDao.findWithPaymentProviderAndStatusIn("epdq", List.of(CREATED), 2);
        assertThat(charges, hasSize(2));
    }

    @Test
    public void shouldFindChargesWithPaymentProvidersAndStatuses() {
        DatabaseFixtures.TestAccount epdqAccount = insertTestAccountWithProvider("epdq");
        DatabaseFixtures.TestAccount worldpayAccount = insertTestAccountWithProvider("worldpay");
        DatabaseFixtures.TestAccount stripeAccount = insertTestAccountWithProvider("stripe");

        TestCharge epdqCreatedCharge = insertTestChargeWithStatus(epdqAccount, CREATED);
        TestCharge worldpayCreatedCharge = insertTestChargeWithStatus(worldpayAccount, CREATED);
        TestCharge stripeCreatedCharge = insertTestChargeWithStatus(stripeAccount, CREATED);
        TestCharge epdqEnteringCardDetailsCharge = insertTestChargeWithStatus(epdqAccount, ENTERING_CARD_DETAILS);
        TestCharge worldpayAuthorisedCharge = insertTestChargeWithStatus(worldpayAccount, AUTHORISATION_SUCCESS);
        TestCharge stripeEnteringCardDetailsCharge = insertTestChargeWithStatus(stripeAccount, ENTERING_CARD_DETAILS);

        List<ChargeEntity> charges = chargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(List.of("epdq", "worldpay", "stripe"),
                List.of(CREATED, ENTERING_CARD_DETAILS), List.of(WEB), 10);

        assertThat(charges, hasSize(5));
        assertThat(charges, containsInAnyOrder(
                hasProperty("externalId", is(epdqCreatedCharge.externalChargeId)),
                hasProperty("externalId", is(epdqEnteringCardDetailsCharge.externalChargeId)),
                hasProperty("externalId", is(worldpayCreatedCharge.externalChargeId)),
                hasProperty("externalId", is(stripeCreatedCharge.externalChargeId)),
                hasProperty("externalId", is(stripeEnteringCardDetailsCharge.externalChargeId))
        ));
        assertThat(charges, not(containsInAnyOrder(
                hasProperty("externalId", is(worldpayAuthorisedCharge.externalChargeId))
        )));
    }

    @Test
    public void shouldFindChargesWithPaymentProvidersAndStatusesWithLimit() {
        DatabaseFixtures.TestAccount epdqAccount = insertTestAccountWithProvider("epdq");
        DatabaseFixtures.TestAccount stripeAccount = insertTestAccountWithProvider("stripe");
        DatabaseFixtures.TestAccount worldpayAccount = insertTestAccountWithProvider("worldpay");
        insertTestChargeWithStatus(epdqAccount, CREATED);
        TestCharge worldpayCreatedCharge = insertTestChargeWithStatus(worldpayAccount, CREATED);
        insertTestChargeWithStatus(stripeAccount, CREATED);
        insertTestChargeWithStatus(stripeAccount, CREATED);

        List<ChargeEntity> charges = chargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(List.of("epdq", "worldpay", "stripe"),
                List.of(CREATED), List.of(WEB), 2);
        assertThat(charges, hasSize(2));
        assertThat(charges, not(containsInAnyOrder(
                hasProperty("externalId", is(worldpayCreatedCharge.externalChargeId))
        )));
    }

    @Test
    public void findWithPaymentProvidersStatusesAndAuthorisationModesIn_shouldFindChargesWithAuthorisationModeCorrectly() {
        DatabaseFixtures.TestAccount stripeAccount = insertTestAccountWithProvider("stripe");
        TestCharge chargeWithAuthorisationModeWeb = createTestCharge(stripeAccount, AUTHORISATION_TIMEOUT).withAuthorisationMode(WEB).insert();
        createTestCharge(stripeAccount, AUTHORISATION_TIMEOUT).withAuthorisationMode(EXTERNAL).insert();
        createTestCharge(stripeAccount, AUTHORISATION_TIMEOUT).withAuthorisationMode(EXTERNAL).insert();

        List<ChargeEntity> charges = chargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(List.of("stripe"),
                List.of(AUTHORISATION_TIMEOUT), List.of(WEB), 1);
        assertThat(charges, hasSize(1));
        assertThat(charges, containsInAnyOrder(
                hasProperty("externalId", is(chargeWithAuthorisationModeWeb.externalChargeId))
        ));
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
    }

    private DatabaseFixtures.TestAccount insertTestAccountWithProvider(String provider) {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .withPaymentProvider(provider)
                .insert();
    }

    private void insertTestCharge() {
        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withPaymentProvider(defaultTestAccount.getPaymentProvider())
                .insert();
        defaultTestCardDetails
                .withChargeId(defaultTestCharge.chargeId)
                .update();
    }

    private TestCharge insertTestChargeWithStatus(DatabaseFixtures.TestAccount testAccount, ChargeStatus created) {
        return createTestCharge(testAccount, created)
                .insert();
    }

    private TestCharge createTestCharge(DatabaseFixtures.TestAccount testAccount, ChargeStatus created) {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper).aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(created)
                .withPaymentProvider(testAccount.getPaymentProvider());
    }

    private void insertTestRefund() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .insert();
    }

    private void insertTestAgreement(String agreementExternalId, long gatewayAccountId) {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAgreement()
                .withAgreementId(nextLong())
                .withExternalId(agreementExternalId)
                .withReference("ref9876")
                .withGatewayAccountId(gatewayAccountId)
                .insert();
    }
}
