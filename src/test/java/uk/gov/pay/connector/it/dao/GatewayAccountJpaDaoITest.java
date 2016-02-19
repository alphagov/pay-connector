package uk.gov.pay.connector.it.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class GatewayAccountJpaDaoITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    public GuicedTestEnvironment env;
    private GatewayAccountJpaDao gatewayAccountDao;

    @Before
    public void setUp() throws Exception {
        env = GuicedTestEnvironment.from(app.getPersistModule())
                .start();

        gatewayAccountDao = env.getInstance(GatewayAccountJpaDao.class);
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        String paymentProvider = "test provider";
        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        gatewayAccountDao.persist(entity);
        assertNotNull(entity.getId());
    }

    @Test
    public void idIsMissingForMissingAccount() throws Exception {
        assertThat(!gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(123)).isPresent(), is(true));
    }

    @Test
    public void shouldFindAccountInfoById() throws Exception {
        String paymentProvider = "test provider";
        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        gatewayAccountDao.persist(entity);

        // We dont set any credentials, so the json document in the DB is: {}

        Optional<GatewayAccountEntity> gatewayAccountOpt = gatewayAccountDao.findById(GatewayAccountEntity.class, entity.getId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));
    }

    @Test
    public void findByIdNoFound() throws Exception {
        assertFalse(gatewayAccountDao.findById(GatewayAccountEntity.class, Long.valueOf(123)).isPresent());
    }

    @Test
    public void verifyIfIdExists() throws Exception {
        String paymentProvider = "test provider";
        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        gatewayAccountDao.persist(entity);

        assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, entity.getId()).isPresent(), is(true));
    }

    @Test
    public void shouldUpdateEmptyCredentials() throws IOException {
        String paymentProvider = "test provider";
        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        gatewayAccountDao.persist(entity);

        String expectedJsonString = "{\"username\": \"Username\", \"password\": \"Password\"}";
        entity.setCredentials(new ObjectMapper().readValue(expectedJsonString, Map.class));
        gatewayAccountDao.persist(entity);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(GatewayAccountEntity.class, entity.getId());
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void shouldUpdateAndRetrieveCredentialsWithSpecialCharacters() throws Exception {
        String paymentProvider = "test provider";
        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        gatewayAccountDao.persist(entity);

        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> credMap = ImmutableMap.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);
        entity.setCredentials(credMap);

        gatewayAccountDao.persist(entity);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(GatewayAccountEntity.class, entity.getId());
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
    }

//    @Test
//    public void shouldThrowExceptionWhenUpdatingCredentialsIfNotValidJson() {
//        String paymentProvider = "test provider";
//        GatewayAccountEntity entity = new GatewayAccountEntity(paymentProvider, new HashMap<>());
//        gatewayAccountDao.persist(entity);
//
//        String expectedJsonString = "[blah]";
//
//        try {
//            gatewayAccountDao.persist(expectedJsonString, gatewayAccountId);
//            fail();
//        } catch (RuntimeException e) {
//            Optional<GatewayAccount> gatewayAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
//            assertThat(gatewayAccountMaybe.isPresent(), is(true));
//            Map<String, String> credentialsMap = gatewayAccountMaybe.get().getCredentials();
//            assertThat(credentialsMap.size(), is(0));
//        }
//    }

}
