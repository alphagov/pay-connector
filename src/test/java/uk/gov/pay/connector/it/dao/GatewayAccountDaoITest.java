package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GatewayAccountDaoITest extends DaoITestBase {

    private GatewayAccountDao gatewayAccountDao;
    private DatabaseFixtures databaseFixtures;

    @Before
    public void setUp() throws Exception {
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
    }

    @Test
    public void persist_shouldCreateAnAccount() throws Exception {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        createAccountRecord(mastercardCreditCardTypeRecord, visaDebitCardTypeRecord);

        String paymentProvider = "test provider";
        GatewayAccountEntity account = new GatewayAccountEntity(paymentProvider, new HashMap<>());

        CardTypeEntity masterCardCreditCardType = new CardTypeEntity();
        masterCardCreditCardType.setId(mastercardCreditCardTypeRecord.getId());

        CardTypeEntity visaCardDebitCardType = new CardTypeEntity();
        visaCardDebitCardType.setId(visaDebitCardTypeRecord.getId());

        account.setCardTypes(Arrays.asList(masterCardCreditCardType, visaCardDebitCardType));

        gatewayAccountDao.persist(account);

        assertNotNull(account.getId());

        databaseTestHelper.getAccountCredentials(account.getId());

        List<Map<String, Object>> acceptedCardTypesByAccountId = databaseTestHelper.getAcceptedCardTypesByAccountId(account.getId());

        assertThat(acceptedCardTypesByAccountId, containsInAnyOrder(
                allOf(
                        org.hamcrest.Matchers.hasEntry("label", mastercardCreditCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", mastercardCreditCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", mastercardCreditCardTypeRecord.getBrand())
                ), allOf(
                        org.hamcrest.Matchers.hasEntry("label", visaDebitCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", visaDebitCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", visaDebitCardTypeRecord.getBrand())
                )));
    }

    @Test
    public void findById_shouldNotFindAnUnexistentGatewayAccount() throws Exception {
        assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, 1234L).isPresent(), is(false));
    }

    @Test
    public void findById_shouldFindGatewayAccount() throws Exception {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord(mastercardCreditCardTypeRecord, visaDebitCardTypeRecord);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(accountRecord.getPaymentProvider()));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));

        assertThat(gatewayAccount.getCardTypes(), containsInAnyOrder(
                allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(mastercardCreditCardTypeRecord.getLabel())),
                        hasProperty("type", is(mastercardCreditCardTypeRecord.getType())),
                        hasProperty("brand", is(mastercardCreditCardTypeRecord.getBrand()))
                ), allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(visaDebitCardTypeRecord.getLabel())),
                        hasProperty("type", is(visaDebitCardTypeRecord.getType())),
                        hasProperty("brand", is(visaDebitCardTypeRecord.getBrand()))
                )));
    }

    @Test
    public void findById_shouldUpdateAccountCardTypes() throws Exception {
        DatabaseFixtures.TestCardType mastercardCreditCardTypeRecord = createMastercardCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaCreditCardTypeRecord = createVisaCreditCardTypeRecord();
        DatabaseFixtures.TestCardType visaDebitCardTypeRecord = createVisaDebitCardTypeRecord();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecord(mastercardCreditCardTypeRecord, visaCreditCardTypeRecord);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();

        CardTypeEntity visaDebitCardType = new CardTypeEntity();
        visaDebitCardType.setId(visaDebitCardTypeRecord.getId());

        List<CardTypeEntity> cardTypes = gatewayAccount.getCardTypes();

        cardTypes.removeIf(p -> p.getId().equals(visaCreditCardTypeRecord.getId()));
        cardTypes.add(visaDebitCardType);

        gatewayAccountDao.merge(gatewayAccount);

        List<Map<String, Object>> acceptedCardTypesByAccountId = databaseTestHelper.getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        assertThat(acceptedCardTypesByAccountId, containsInAnyOrder(
                allOf(
                        org.hamcrest.Matchers.hasEntry("label", mastercardCreditCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", mastercardCreditCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", mastercardCreditCardTypeRecord.getBrand())
                ), allOf(
                        org.hamcrest.Matchers.hasEntry("label", visaDebitCardTypeRecord.getLabel()),
                        org.hamcrest.Matchers.hasEntry("type", visaDebitCardTypeRecord.getType().toString()),
                        org.hamcrest.Matchers.hasEntry("brand", visaDebitCardTypeRecord.getBrand())
                )));
    }

    @Test
    public void findById_shouldUpdateEmptyCredentials() throws IOException {

        String paymentProvider = "test provider";
        Long accountId = 888L;
        databaseTestHelper.addGatewayAccount(accountId.toString(), paymentProvider);

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(accountId).get();

        assertThat(gatewayAccount.getCredentials(), is(emptyMap()));

        gatewayAccount.setCredentials(new HashMap<String, String>() {{
            put("username", "Username");
            put("password", "Password");
        }});

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(accountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void findById_shouldUpdateAndRetrieveCredentialsWithSpecialCharacters() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "333";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> credMap = ImmutableMap.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(Long.valueOf(accountId)).get();
        gatewayAccount.setCredentials(credMap);

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(Long.valueOf(accountId));
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
    }

    @Test
    public void findById_shouldFindAccountInfoByIdWhenFindingByIdReturningGatewayAccount() throws Exception {

        String paymentProvider = "test provider";
        String accountId = "12345";
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider);

        Optional<GatewayAccountEntity> gatewayAccountOpt = gatewayAccountDao.findById(Long.valueOf(accountId));

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        assertThat(gatewayAccount.getCredentials().size(), is(0));
    }

    @Test
    public void findById_shouldGetCredentialsWhenFindingGatewayAccountById() {

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

    public DatabaseFixtures.TestCardType createMastercardCreditCardTypeRecord() {
        return databaseFixtures.aMastercardCreditCardType().insert();
    }

    public DatabaseFixtures.TestCardType createVisaDebitCardTypeRecord() {
        return databaseFixtures.aVisaDebitCardType().insert();
    }

    public DatabaseFixtures.TestCardType createVisaCreditCardTypeRecord() {
        return databaseFixtures.aVisaCreditCardType().insert();
    }

    public DatabaseFixtures.TestAccount createAccountRecord(DatabaseFixtures.TestCardType... cardTypes) {
        return databaseFixtures
                .aTestAccount()
                .withCardTypes(Arrays.asList(cardTypes))
                .insert();
    }
}
