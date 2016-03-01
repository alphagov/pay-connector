package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class GatewayAccountJpaDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    public GuicedTestEnvironment env;
    private GatewayAccountJpaDao gatewayAccountDao;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();
        databaseTestHelper = app.getDatabaseTestHelper();
        gatewayAccountDao = env.getInstance(GatewayAccountJpaDao.class);
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        String paymentProvider = "test provider";
        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        gatewayAccountDao.persist(entity);
        assertNotNull(entity.getId());
    }

    @Test
    public void idIsMissingForMissingAccountFindingByIdReturningEntity() throws Exception {
        assertThat(!gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(123)).isPresent(), is(true));
    }

    @Test
    public void shouldFindAccountInfoByIdUsingFindByIdReturningEntity() throws Exception {
        String paymentProvider = "test provider";
        String accountId = "666";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        // We dont set any credentials, so the json document in the DB is: {}

        Optional<GatewayAccountEntity> gatewayAccountOpt = gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(accountId));

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));
    }

    @Test
    public void findByIdNoFoundWhenFindById() throws Exception {
        assertFalse(gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(123)).isPresent());
    }

    @Test
    public void verifyIfIdExistsUsingFindById() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "999";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(accountId)).isPresent(), is(true));
    }

    @Test
    public void shouldUpdateEmptyCredentials() throws IOException {

        String paymentProvider = "test provider";
        String accountId = "888";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        String expectedJsonString = "{\"username\": \"Username\", \"password\": \"Password\"}";
        gatewayAccountDao.saveCredentials(expectedJsonString, accountId);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(accountId));
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void shouldUpdateAndRetrieveCredentialsWithSpecialCharacters() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "333";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> credMap = ImmutableMap.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(paymentProvider, credMap);
        gatewayAccount.setId(Long.valueOf(accountId));

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(accountId));
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
    }

    @Test
    public void insertANewGatewayAccountIdWhenCreateGatewayAccount() throws Exception {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.createGatewayAccount(paymentProvider);
        assertTrue(isNotBlank(gatewayAccountId));
        assertThat(Long.valueOf(gatewayAccountId), is(notNullValue()));
    }

    @Test
    public void idIsMissingForMissingAccount() throws Exception {
        assertThat(gatewayAccountDao.idIsMissing("999"), is(true));
    }

    @Test
    public void shouldFindAccountInfoByIdWhenFindingByIdReturningGatewayAccount() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "12345";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);


        // We dont set any credentials, so the json document in the DB is: {}

        Optional<GatewayAccount> gatewayAccountOpt = gatewayAccountDao.findById(accountId);

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccount gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));
    }

    @Test
    public void findByIdNoFound() throws Exception {
        assertFalse(gatewayAccountDao.findById("123").isPresent());
    }

    @Test
    public void verifyIfIdExists() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "555";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);


        assertThat(gatewayAccountDao.idIsMissing("555"), is(false));
    }

    @Test
    public void shouldUpdateEmptyCredentialsWhenFindingByIdReturningGatewayAccount() {
        String paymentProvider = "test provider";
        String accountId = "786";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);


        String expectedJsonString = "{\"username\": \"Username\", \"password\": \"Password\"}";
        gatewayAccountDao.saveCredentials(expectedJsonString, accountId);

        Optional<GatewayAccount> serviceAccountMaybe = gatewayAccountDao.findById(accountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void shouldGetCredentialsWhenFindingGatewayAccountById() {

        String paymentProvider = "test provider";
        String accountId = "786";
        HashMap<String, String> credentials = new HashMap<>();
        credentials.put("username", "Username");
        credentials.put("password", "Password");

        databaseTestHelper.addGatewayAccount(accountId, paymentProvider, credentials);

        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId));

        assertThat(gatewayAccount.isPresent(), is(true));
        Map<String, String> accountCredentials = gatewayAccount.get().getCredentials();
        assertThat(accountCredentials, hasEntry("username", "Username"));
        assertThat(accountCredentials, hasEntry("password", "Password"));
    }

    @Test
    public void shouldUpdateAndRetrieveCredentialsWithSpecialCharactersWhenFindingByIdReturningGatewayAccount() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "655";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);


        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> credMap = ImmutableMap.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);
        String expectedJsonString = new Gson().toJson(credMap);

        gatewayAccountDao.saveCredentials(expectedJsonString, accountId);

        Optional<GatewayAccount> serviceAccountMaybe = gatewayAccountDao.findById(accountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
    }

    @Test
    public void shouldThrowExceptionWhenUpdatingCredentialsIfNotValidJsonWhenFindingByIdReturningGatewayAccount() {
        String paymentProvider = "test provider";
        String accountId = "76543";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        String expectedJsonString = "[blah]";

        try {
            gatewayAccountDao.saveCredentials(expectedJsonString, accountId);
            fail();
        } catch (RuntimeException e) {
            Optional<GatewayAccount> gatewayAccountMaybe = gatewayAccountDao.findById(accountId);
            assertThat(gatewayAccountMaybe.isPresent(), is(true));
            Map<String, String> credentialsMap = gatewayAccountMaybe.get().getCredentials();
            assertThat(credentialsMap.size(), is(0));
        }
    }
}
