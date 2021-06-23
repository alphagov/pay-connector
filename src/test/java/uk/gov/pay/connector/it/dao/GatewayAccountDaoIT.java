package uk.gov.pay.connector.it.dao;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.common.model.api.CommaDelimitedSetParameter;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyMap;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountDaoIT extends DaoITestBase {

    private GatewayAccountDao gatewayAccountDao;
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private DatabaseFixtures databaseFixtures;
    private long gatewayAccountId;

    @Before
    public void setUp() {
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);
        gatewayAccountCredentialsDao = env.getInstance(GatewayAccountCredentialsDao.class);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        gatewayAccountId = nextLong();
    }

    @After
    public void truncate() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void persist_shouldCreateAnAccount() {
        final CardTypeEntity masterCardCredit = databaseTestHelper.getMastercardCreditCard();
        final CardTypeEntity visaCardDebit = databaseTestHelper.getVisaCreditCard();

        createAccountRecordWithCards();

        String paymentProvider = "test provider";
        GatewayAccountEntity account = new GatewayAccountEntity(paymentProvider, new HashMap<>(), TEST);

        account.setExternalId(randomUuid());
        account.setCardTypes(Arrays.asList(masterCardCredit, visaCardDebit));

        gatewayAccountDao.persist(account);

        assertThat(account.getId(), is(notNullValue()));
        assertThat(account.getEmailNotifications().isEmpty(), is(true));
        assertThat(account.getDescription(), is(nullValue()));
        assertThat(account.getAnalyticsId(), is(nullValue()));
        assertThat(account.getNotificationCredentials(), is(nullValue()));
        assertThat(account.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporatePrepaidCreditCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporatePrepaidDebitCardSurchargeAmount(), is(0L));
        assertThat(account.isSendPayerIpAddressToGateway(), is(false));
        assertThat(account.isSendPayerEmailToGateway(), is(false));
        assertThat(account.isProviderSwitchEnabled(), is(false));

        databaseTestHelper.getAccountCredentials(account.getId());

        List<Map<String, Object>> acceptedCardTypesByAccountId = databaseTestHelper.getAcceptedCardTypesByAccountId(account.getId());

        assertThat(acceptedCardTypesByAccountId, containsInAnyOrder(
                allOf(
                        hasEntry("label", masterCardCredit.getLabel()),
                        hasEntry("type", masterCardCredit.getType().toString()),
                        hasEntry("brand", masterCardCredit.getBrand())
                ), allOf(
                        hasEntry("label", visaCardDebit.getLabel()),
                        hasEntry("type", visaCardDebit.getType().toString()),
                        hasEntry("brand", visaCardDebit.getBrand())
                )));
    }

    @Test
    public void findById_shouldNotFindANonexistentGatewayAccount() {
        assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, 1234L).isPresent(), is(false));
    }

    @Test
    public void findById_shouldFindGatewayAccount() {
        final CardTypeEntity masterCardCredit = databaseTestHelper.getMastercardCreditCard();
        final CardTypeEntity visaCardDebit = databaseTestHelper.getVisaCreditCard();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(masterCardCredit, visaCardDebit);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(accountRecord.getPaymentProvider()));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));
        assertThat(gatewayAccount.getExternalId(), is(accountRecord.getExternalId()));
        assertThat(gatewayAccount.getServiceName(), is(accountRecord.getServiceName()));
        assertThat(gatewayAccount.getDescription(), is(accountRecord.getDescription()));
        assertThat(gatewayAccount.getAnalyticsId(), is(accountRecord.getAnalyticsId()));
        assertThat(gatewayAccount.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(accountRecord.getCorporateCreditCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporateDebitCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporatePrepaidCreditCardSurchargeAmount(), is(accountRecord.getCorporatePrepaidCreditCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporatePrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporatePrepaidDebitCardSurchargeAmount()));
        assertThat(gatewayAccount.getCardTypes(), contains(
                allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(masterCardCredit.getLabel())),
                        hasProperty("type", is(masterCardCredit.getType())),
                        hasProperty("brand", is(masterCardCredit.getBrand()))
                ), allOf(
                        hasProperty("id", is(Matchers.notNullValue())),
                        hasProperty("label", is(visaCardDebit.getLabel())),
                        hasProperty("type", is(visaCardDebit.getType())),
                        hasProperty("brand", is(visaCardDebit.getBrand()))
                )));
        assertThat(gatewayAccount.isAllowTelephonePaymentNotifications(), is(accountRecord.isAllowTelephonePaymentNotifications()));
    }

    @Test
    public void findByCredentialsKeyValue_shouldFindGatewayAccount() {
        var credMap = Map.of("some_payment_provider_account_id", "accountid");
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("test provider")
                .withServiceName("service name")
                .withCredentials(credMap)
                .build());

        Optional<GatewayAccountEntity> maybeGatewayAccount = gatewayAccountDao.findByCredentialsKeyValue("some_payment_provider_account_id", "accountid");
        assertThat(maybeGatewayAccount.isPresent(), is(true));
        Map<String, String> credentialsMap = maybeGatewayAccount.get().getCredentials();
        assertThat(credentialsMap, hasEntry("some_payment_provider_account_id", "accountid"));
    }

    @Test
    public void findById_shouldFindGatewayAccountWithCorporateSurcharges() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCorporateSurcharges();

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(accountRecord.getPaymentProvider()));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
        assertThat(credentialsMap.size(), is(0));
        assertThat(gatewayAccount.getExternalId(), is(accountRecord.getExternalId()));
        assertThat(gatewayAccount.getServiceName(), is(accountRecord.getServiceName()));
        assertThat(gatewayAccount.getDescription(), is(accountRecord.getDescription()));
        assertThat(gatewayAccount.getAnalyticsId(), is(accountRecord.getAnalyticsId()));
        assertThat(gatewayAccount.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(accountRecord.getCorporateCreditCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporateDebitCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporatePrepaidCreditCardSurchargeAmount(), is(accountRecord.getCorporatePrepaidCreditCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporatePrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporatePrepaidDebitCardSurchargeAmount()));

    }

    @Test
    public void findById_shouldUpdateAccountCardTypes() {
        final CardTypeEntity masterCardCredit = databaseTestHelper.getMastercardCreditCard();
        final CardTypeEntity visaCardCredit = databaseTestHelper.getVisaCreditCard();
        final CardTypeEntity visaCardDebit = databaseTestHelper.getVisaDebitCard();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(masterCardCredit, visaCardCredit);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();

        List<CardTypeEntity> cardTypes = gatewayAccount.getCardTypes();

        cardTypes.removeIf(p -> p.getId().equals(visaCardCredit.getId()));
        cardTypes.add(visaCardDebit);

        gatewayAccountDao.merge(gatewayAccount);

        List<Map<String, Object>> acceptedCardTypesByAccountId = databaseTestHelper.getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

        assertThat(acceptedCardTypesByAccountId, contains(
                allOf(
                        hasEntry("label", masterCardCredit.getLabel()),
                        hasEntry("type", masterCardCredit.getType().toString()),
                        hasEntry("brand", masterCardCredit.getBrand())
                ), allOf(
                        hasEntry("label", visaCardDebit.getLabel()),
                        hasEntry("type", visaCardDebit.getType().toString()),
                        hasEntry("brand", visaCardDebit.getBrand())
                )));
    }

    @Test
    public void findById_shouldUpdateEmptyCredentials() {
        String paymentProvider = "test provider";
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway(paymentProvider)
                .withServiceName("a cool service")
                .build());

        final Optional<GatewayAccountEntity> maybeGatewayAccount = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(maybeGatewayAccount.isPresent(), is(true));
        GatewayAccountEntity gatewayAccount = maybeGatewayAccount.get();

        assertThat(gatewayAccount.getCredentials(), is(emptyMap()));

        gatewayAccount.setCredentials(new HashMap<String, String>() {{
            put("username", "Username");
            put("password", "Password");
        }});

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void findById_shouldUpdateAndRetrieveCredentialsWithSpecialCharacters() {
        String paymentProvider = "test provider";
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway(paymentProvider)
                .withServiceName("a cool service")
                .build());

        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        var credMap = Map.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);

        final Optional<GatewayAccountEntity> maybeGatewayAccount = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(maybeGatewayAccount.isPresent(), is(true));
        GatewayAccountEntity gatewayAccount = maybeGatewayAccount.get();
        gatewayAccount.setCredentials(credMap);

        gatewayAccountDao.merge(gatewayAccount);

        Optional<GatewayAccountEntity> serviceAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
    }

    @Test
    public void findById_shouldFindAccountInfoByIdWhenFindingByIdReturningGatewayAccount() {
        String paymentProvider = "test provider";
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway(paymentProvider)
                .withServiceName("a cool service")
                .build());

        Optional<GatewayAccountEntity> gatewayAccountOpt = gatewayAccountDao.findById(gatewayAccountId);

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        assertThat(gatewayAccount.getCredentials().size(), is(0));
    }

    @Test
    public void findById_shouldGetCredentialsWhenFindingGatewayAccountById() {
        String paymentProvider = "test provider";
        HashMap<String, String> credentials = new HashMap<>();
        credentials.put("username", "Username");
        credentials.put("password", "Password");

        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway(paymentProvider)
                .withCredentials(credentials)
                .build());

        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountDao.findById(gatewayAccountId);

        assertThat(gatewayAccount.isPresent(), is(true));
        Map<String, String> accountCredentials = gatewayAccount.get().getCredentials();
        assertThat(accountCredentials, hasEntry("username", "Username"));
        assertThat(accountCredentials, hasEntry("password", "Password"));
    }

    @Test
    public void shouldSaveNotificationCredentials() {
        String paymentProvider = "test provider";
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway(paymentProvider)
                .withServiceName("a cool service")
                .build());

        final Optional<GatewayAccountEntity> maybeGatewayAccount = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(maybeGatewayAccount.isPresent(), is(true));
        GatewayAccountEntity gatewayAccount = maybeGatewayAccount.get();

        NotificationCredentials notificationCredentials = new NotificationCredentials(gatewayAccount);
        notificationCredentials.setPassword("password");
        notificationCredentials.setUserName("username");
        gatewayAccount.setNotificationCredentials(notificationCredentials);

        gatewayAccountDao.merge(gatewayAccount);

        final Optional<GatewayAccountEntity> maybeGatewayAccount_2 = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(maybeGatewayAccount_2.isPresent(), is(true));
        GatewayAccountEntity retrievedGatewayAccount = maybeGatewayAccount.get();

        assertNotNull(retrievedGatewayAccount.getNotificationCredentials());
        assertThat(retrievedGatewayAccount.getNotificationCredentials().getUserName(), is("username"));
        assertThat(retrievedGatewayAccount.getNotificationCredentials().getPassword(), is("password"));
    }

    @Test
    public void shouldReturnAllAccountsWhenNoSearchParamaters() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .build());

        var params = new GatewayAccountSearchParams();

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_1));
        assertThat(gatewayAccounts.get(1).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSearchForAccountsById() {
        long gatewayAccountId_1 = nextLong();
        String externalId_1 = randomUuid();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withExternalId(externalId_1)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        String externalId_2 = randomUuid();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withExternalId(externalId_2)
                .build());
        long gatewayAccountId_3 = gatewayAccountId_2 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_3))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter(gatewayAccountId_1 + "," + gatewayAccountId_2));

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_1));
        assertThat(gatewayAccounts.get(0).getExternalId(), is(externalId_1));
        assertThat(gatewayAccounts.get(1).getId(), is(gatewayAccountId_2));
        assertThat(gatewayAccounts.get(1).getExternalId(), is(externalId_2));
    }

    @Test
    public void shouldSearchForAccountsByMotoEnabled() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withAllowMoto(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withAllowMoto(true)
                .build());

        var params = new GatewayAccountSearchParams();
        params.setMotoEnabled("true");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSearchForAccountsByApplePayEnabled() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withAllowApplePay(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withAllowApplePay(true)
                .build());

        var params = new GatewayAccountSearchParams();
        params.setApplePayEnabled("true");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSearchForAccountsByGooglePayEnabled() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withAllowGooglePay(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withAllowGooglePay(true)
                .build());

        var params = new GatewayAccountSearchParams();
        params.setGooglePayEnabled("true");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSearchForAccountsByRequires3ds() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withRequires3ds(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withRequires3ds(true)
                .build());

        var params = new GatewayAccountSearchParams();
        params.setRequires3ds("true");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSearchForAccountsByType() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withType(TEST)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withType(LIVE)
                .build());

        var params = new GatewayAccountSearchParams();
        params.setType("live");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSearchForAccountsByPaymentProvider() {
        long gatewayAccountId_1 = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withPaymentGateway("sandbox")
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withPaymentGateway("stripe")
                .build());

        var params = new GatewayAccountSearchParams();
        params.setPaymentProvider("stripe");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_2));
    }

    @Test
    public void shouldSaveNotifySettings() {
        String fuser = "fuser";
        String notifyAPIToken = "a_token";
        String notifyTemplateId = "a_template_id";

        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("provider-1")
                .withCredentials(Map.of("user", fuser, "password", "word"))
                .withServiceName("service-name-1")
                .withDescription("description-1")
                .withAnalyticsId("analytics-id-1")
                .build());
        Optional<GatewayAccountEntity> gatewayAccountOptional = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(gatewayAccountOptional.isPresent(), is(true));
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountOptional.get();
        assertThat(gatewayAccountEntity.getNotifySettings(), is(nullValue()));
        var notifySettings = Map.of("notify_api_token", notifyAPIToken, "notify_template_id", notifyTemplateId);
        gatewayAccountEntity.setNotifySettings(notifySettings);
        gatewayAccountDao.merge(gatewayAccountEntity);

        Map<String, String> storedNotifySettings = databaseTestHelper.getNotifySettings(gatewayAccountId);

        assertThat(storedNotifySettings.size(), is(2));
        assertThat(storedNotifySettings.get("notify_api_token"), is(notifyAPIToken));
        assertThat(storedNotifySettings.get("notify_template_id"), is(notifyTemplateId));
    }

    @Test
    public void findByExternalId_shouldFindGatewayAccount() {
        Long id = nextLong();
        String externalId = randomUuid();
        databaseFixtures
                .aTestAccount()
                .withAccountId(id)
                .withExternalId(externalId)
                .insert();
        Optional<GatewayAccountEntity> gatewayAccountOptional = gatewayAccountDao.findByExternalId(externalId);
        assertThat(gatewayAccountOptional.isPresent(), is(true));
        assertThat(gatewayAccountOptional.get().getId(), is(id));
        assertThat(gatewayAccountOptional.get().getExternalId(), is(externalId));
    }

    @Test
    public void isATelephonePaymentNotificationAccount_shouldReturnTrueIfTelephonePaymentNotificationsAreEnabled() {
        long id = nextLong();
        String externalId = randomUuid();
        Map<String, String> credentials = Map.of(CREDENTIALS_MERCHANT_ID, "merchant-id");

        databaseFixtures
                .aTestAccount()
                .withAccountId(id)
                .withExternalId(externalId)
                .withAllowTelephonePaymentNotifications(true)
                .insert();

        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(id).get();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider("test provider")
                .withCredentials(credentials)
                .build();
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);

        boolean result = gatewayAccountDao.isATelephonePaymentNotificationAccount("merchant-id");
        assertThat(result, is(true));
    }

    @Test
    public void isATelephonePaymentNotificationAccount_shouldReturnFalseIfTelephonePaymentNotificationsAreNotEnabled() {
        long id = nextLong();
        String externalId = randomUuid();
        Map<String, String> credentials = Map.of(CREDENTIALS_MERCHANT_ID, "merchant-id");

        databaseFixtures.aTestAccount()
                .withAccountId(id)
                .withExternalId(externalId)
                .withAllowTelephonePaymentNotifications(false)
                .insert();  
        databaseFixtures.aTestAccount()
                .withAccountId(nextLong())
                .withExternalId(randomUuid())
                .withPaymentProvider("random payment provider")
                .withAllowTelephonePaymentNotifications(true)
                .insert();

        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(id).get();

        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider("test provider")
                .withCredentials(credentials)
                .build();
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);

        boolean result = gatewayAccountDao.isATelephonePaymentNotificationAccount("merchant-id");
        assertThat(result, is(false));
    }

    private DatabaseFixtures.TestAccount createAccountRecordWithCards(CardTypeEntity... cardTypes) {
        return databaseFixtures
                .aTestAccount()
                .withCardTypeEntities(Arrays.asList(cardTypes))
                .insert();
    }

    private DatabaseFixtures.TestAccount createAccountRecordWithCorporateSurcharges() {
        return databaseFixtures
                .aTestAccount()
                .withCorporateCreditCardSurchargeAmount(250L)
                .withCorporateDebitCardSurchargeAmount(50L)
                .withCorporatePrepaidCreditCardSurchargeAmount(250L)
                .withCorporatePrepaidDebitCardSurchargeAmount(50L)
                .insert();
    }
}
