package uk.gov.pay.connector.it.dao;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountDaoIT {
    @RegisterExtension
    static ITestBaseExtension app = new ITestBaseExtension("sandbox");
    private GatewayAccountDao gatewayAccountDao;
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private DatabaseFixtures databaseFixtures;
    private long gatewayAccountId;

    @BeforeEach
    void setUp() {
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
        gatewayAccountCredentialsDao = app.getInstanceFromGuiceContainer(GatewayAccountCredentialsDao.class);
        databaseFixtures = app.getDatabaseFixtures();
        gatewayAccountId = nextLong();
    }

    @Test
    void shouldFindGatewayAccountsForServiceId() {
        String serviceId = "a-service-id";
        
        GatewayAccountEntity account1 = new GatewayAccountEntity(TEST);
        account1.setExternalId(randomUuid());
        account1.setServiceId(serviceId);
        gatewayAccountDao.persist(account1);

        GatewayAccountEntity account2 = new GatewayAccountEntity(TEST);
        account2.setExternalId(randomUuid());
        account2.setServiceId(serviceId);
        gatewayAccountDao.persist(account2);

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.findByServiceId(serviceId);
        assertThat(gatewayAccounts.size(), is(2));
        assertThat(gatewayAccounts.stream().map(GatewayAccountEntity::getExternalId).collect(Collectors.toList()), 
                containsInAnyOrder(account1.getExternalId(), account2.getExternalId()));
    }

    @Test
    void shouldFindNoGatewayAccountForServiceId() {
        GatewayAccountEntity account1 = new GatewayAccountEntity(TEST);
        account1.setExternalId(randomUuid());
        account1.setServiceId("a-service-id");
        gatewayAccountDao.persist(account1);
        
        assertTrue(gatewayAccountDao.findByServiceId("non-existent").isEmpty());
    }
    
    @Test
    void shouldUpdateGatewayAccount_ToDisabled_NotificationCredentialsRemoved() {
        GatewayAccountEntity account = new GatewayAccountEntity(TEST);
        account.setExternalId(randomUuid());
        GatewayAccountCredentialsEntity gatewayAccountCredentials = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(account)
                .withPaymentProvider(WORLDPAY.getName())
                .withCredentials(Map.of())
                .withState(ACTIVE)
                .build();
        account.setGatewayAccountCredentials(List.of(gatewayAccountCredentials));

        NotificationCredentials notificationCredentials = new NotificationCredentials(account);
        notificationCredentials.setPassword("password");
        notificationCredentials.setUserName("username");
        account.setNotificationCredentials(notificationCredentials);

        gatewayAccountDao.persist(account);

        GatewayAccountEntity accountToUpdate = gatewayAccountDao.findByExternalId(account.getExternalId()).get();
        
        assertFalse(accountToUpdate.isDisabled());
        var gatewayAccountCredentialsEntity = accountToUpdate.getGatewayAccountCredentialsEntity(WORLDPAY.getName());
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
        assertThat(accountToUpdate.getNotificationCredentials(), not(nullValue()));

        accountToUpdate.setDisabled(true);
        accountToUpdate.setNotificationCredentials(null);
        
        gatewayAccountDao.merge(accountToUpdate);

        GatewayAccountEntity updatedAccount = gatewayAccountDao.findByExternalId(account.getExternalId()).get();
        assertTrue(updatedAccount.isDisabled());
        assertNull(updatedAccount.getNotificationCredentials());
    }
    
    @Test
    void persist_shouldCreateAnAccount() {
        final CardTypeEntity masterCardCredit = app.getDatabaseTestHelper().getMastercardCreditCard();
        final CardTypeEntity visaCardDebit = app.getDatabaseTestHelper().getVisaCreditCard();

        GatewayAccountEntity account = new GatewayAccountEntity(TEST);

        account.setExternalId(randomUuid());
        account.setCardTypes(Arrays.asList(masterCardCredit, visaCardDebit));
        account.setSendReferenceToGateway(true);

        gatewayAccountDao.persist(account);

        assertThat(account.getId(), is(notNullValue()));
        assertThat(account.getEmailNotifications().isEmpty(), is(true));
        assertThat(account.getDescription(), is(nullValue()));
        assertThat(account.getAnalyticsId(), is(nullValue()));
        assertThat(account.getNotificationCredentials(), is(nullValue()));
        assertThat(account.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporatePrepaidDebitCardSurchargeAmount(), is(0L));
        assertThat(account.isSendPayerIpAddressToGateway(), is(false));
        assertThat(account.isSendPayerEmailToGateway(), is(false));
        assertThat(account.isProviderSwitchEnabled(), is(false));
        assertThat(account.isSendReferenceToGateway(), is(true));

        List<Map<String, Object>> acceptedCardTypesByAccountId = app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(account.getId());

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
    void findById_shouldNotFindANonexistentGatewayAccount() {
        assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, 1234L).isPresent(), is(false));
    }

    @Test
    void findById_shouldFindGatewayAccount() {
        final CardTypeEntity masterCardCredit = app.getDatabaseTestHelper().getMastercardCreditCard();
        final CardTypeEntity visaCardDebit = app.getDatabaseTestHelper().getVisaCreditCard();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(masterCardCredit, visaCardDebit);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(accountRecord.getPaymentProvider()));
        assertThat(gatewayAccount.getExternalId(), is(accountRecord.getExternalId()));
        assertThat(gatewayAccount.getServiceName(), is(accountRecord.getServiceName()));
        assertThat(gatewayAccount.getServiceId(), is(accountRecord.getServiceId()));
        assertThat(gatewayAccount.getDescription(), is(accountRecord.getDescription()));
        assertThat(gatewayAccount.getAnalyticsId(), is(accountRecord.getAnalyticsId()));
        assertThat(gatewayAccount.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(accountRecord.getCorporateCreditCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporateDebitCardSurchargeAmount()));
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
    void findById_shouldFindGatewayAccountWithCorporateSurcharges() {
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCorporateSurcharges();

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(accountRecord.getPaymentProvider()));
        assertThat(gatewayAccount.getExternalId(), is(accountRecord.getExternalId()));
        assertThat(gatewayAccount.getServiceName(), is(accountRecord.getServiceName()));
        assertThat(gatewayAccount.getDescription(), is(accountRecord.getDescription()));
        assertThat(gatewayAccount.getAnalyticsId(), is(accountRecord.getAnalyticsId()));
        assertThat(gatewayAccount.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(accountRecord.getCorporateCreditCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporateDebitCardSurchargeAmount()));
        assertThat(gatewayAccount.getCorporatePrepaidDebitCardSurchargeAmount(), is(accountRecord.getCorporatePrepaidDebitCardSurchargeAmount()));

    }

    @Test
    void findById_shouldUpdateAccountCardTypes() {
        final CardTypeEntity masterCardCredit = app.getDatabaseTestHelper().getMastercardCreditCard();
        final CardTypeEntity visaCardCredit = app.getDatabaseTestHelper().getVisaCreditCard();
        final CardTypeEntity visaCardDebit = app.getDatabaseTestHelper().getVisaDebitCard();
        DatabaseFixtures.TestAccount accountRecord = createAccountRecordWithCards(masterCardCredit, visaCardCredit);

        Optional<GatewayAccountEntity> gatewayAccountOpt =
                gatewayAccountDao.findById(GatewayAccountEntity.class, accountRecord.getAccountId());

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();

        List<CardTypeEntity> cardTypes = gatewayAccount.getCardTypes();

        cardTypes.removeIf(p -> p.getId().equals(visaCardCredit.getId()));
        cardTypes.add(visaCardDebit);

        gatewayAccountDao.merge(gatewayAccount);

        List<Map<String, Object>> acceptedCardTypesByAccountId = app.getDatabaseTestHelper().getAcceptedCardTypesByAccountId(accountRecord.getAccountId());

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
    void findById_shouldFindAccountInfoByIdWhenFindingByIdReturningGatewayAccount() {
        String paymentProvider = "test provider";
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway(paymentProvider)
                .withServiceName("a cool service")
                .build());

        Optional<GatewayAccountEntity> gatewayAccountOpt = gatewayAccountDao.findById(gatewayAccountId);

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccountEntity gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
    }

    @Test
    void shouldSaveNotificationCredentials() {
        String paymentProvider = "test provider";
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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
    void shouldReturnAllAccountsWhenNoSearchParameters() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .build());

        var params = new GatewayAccountSearchParams();

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(3));
        assertThat(gatewayAccounts.get(0).getId(), is(Long.valueOf(app.getAccountId())));
        assertThat(gatewayAccounts.get(1).getId(), is(gatewayAccountId_1));
        assertThat(gatewayAccounts.get(2).getId(), is(gatewayAccountId_2));
    }

    @Test
    void shouldSearchForAccountsById() {
        long gatewayAccountId_1 = nextLong();
        String externalId_1 = randomUuid();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withExternalId(externalId_1)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        String externalId_2 = randomUuid();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withExternalId(externalId_2)
                .build());
        long gatewayAccountId_3 = gatewayAccountId_2 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_3))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setAccountIds(gatewayAccountId_1 + "," + gatewayAccountId_2);

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_1));
        assertThat(gatewayAccounts.get(0).getExternalId(), is(externalId_1));
        assertThat(gatewayAccounts.get(1).getId(), is(gatewayAccountId_2));
        assertThat(gatewayAccounts.get(1).getExternalId(), is(externalId_2));
    }

    @Test
    void shouldSearchForAccountsByMotoEnabled() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withAllowMoto(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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
    void shouldSearchForAccountsByApplePayEnabled() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withAllowApplePay(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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
    void shouldSearchForAccountsByGooglePayEnabled() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withAllowGooglePay(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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
    void shouldSearchForAccountsByRequires3ds() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withRequires3ds(false)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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
    void shouldSearchForAccountsByType() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withType(TEST)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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
    void shouldSearchForAccountsByPaymentProvider() {
        long gatewayAccountId1 = nextLong();
        long gatewayAccountId2 = nextLong();
        long gatewayAccountId3 = nextLong();
        AddGatewayAccountCredentialsParams account1_credentials1 = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withState(CREATED)
                .withGatewayAccountId(gatewayAccountId1)
                .build();
        AddGatewayAccountCredentialsParams account1_credentials2 = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .withGatewayAccountId(gatewayAccountId1)
                .build();
        AddGatewayAccountCredentialsParams account2_credentials = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .withGatewayAccountId(gatewayAccountId2)
                .build();
        AddGatewayAccountCredentialsParams account3_credentials = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(WORLDPAY.getName())
                .withState(CREATED)
                .withGatewayAccountId(gatewayAccountId3)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId1))
                .withGatewayAccountCredentials(List.of(account1_credentials1, account1_credentials2))
                .build());
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId2))
                .withGatewayAccountCredentials(List.of(account2_credentials))
                .build());
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId3))
                .withGatewayAccountCredentials(List.of(account3_credentials))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setPaymentProvider("worldpay");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts, containsInAnyOrder(
                hasProperty("id", is(gatewayAccountId2)),
                hasProperty("id", is(gatewayAccountId3))
        ));
    }

    @Test
    void shouldSearchForAccountsByProviderSwitchEnabled() {
        long gatewayAccountId_1 = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_1))
                .withPaymentGateway("sandbox")
                .withProviderSwitchEnabled(true)
                .build());
        long gatewayAccountId_2 = gatewayAccountId_1 + 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId_2))
                .withProviderSwitchEnabled(false)
                .withPaymentGateway("sandbox")
                .build());

        var params = new GatewayAccountSearchParams();
        params.setProviderSwitchEnabled("true");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId_1));
    }    
    
    @Test
    void shouldSearchForAccountsByPaymentProviderAccountId() {
        long gatewayAccountId = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of("stripe_account_id", "acc123"))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setPaymentProviderAccountId("acc123");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId));
    }    
    
    @Test
    void shouldSearchForAccountsByWorldpayMerchantCodeInOneOffPaymentCredentials() {
        long gatewayAccountId = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("worldpay")
                .withCredentials(Map.of("one_off_customer_initiated", Map.of("merchant_code", "acc123")))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setPaymentProviderAccountId("acc123");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId));
    }

    @Test
    void shouldSearchForAccountsByWorldpayMerchantCodeInRecurringCustomerInitiatedCredentials() {
        long gatewayAccountId = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("worldpay")
                .withCredentials(Map.of("recurring_customer_initiated", Map.of("merchant_code", "acc123")))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setPaymentProviderAccountId("acc123");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId));
    }

    @Test
    void shouldSearchForAccountsByWorldpayMerchantCodeInRecurringMerchantInitiatedCredentials() {
        long gatewayAccountId = nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("worldpay")
                .withCredentials(Map.of("recurring_merchant_initiated", Map.of("merchant_code", "acc123")))
                .build());

        var params = new GatewayAccountSearchParams();
        params.setPaymentProviderAccountId("acc123");

        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
        assertThat(gatewayAccounts, hasSize(1));
        assertThat(gatewayAccounts.get(0).getId(), is(gatewayAccountId));
    }

    @Test
    void shouldSaveNotifySettings() {
        String fuser = "fuser";
        String notifyAPIToken = "a_token";
        String notifyTemplateId = "a_template_id";

        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
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

        Map<String, String> storedNotifySettings = app.getDatabaseTestHelper().getNotifySettings(gatewayAccountId);

        assertThat(storedNotifySettings.size(), is(2));
        assertThat(storedNotifySettings.get("notify_api_token"), is(notifyAPIToken));
        assertThat(storedNotifySettings.get("notify_template_id"), is(notifyTemplateId));
    }

    @Test
    void findByExternalId_shouldFindGatewayAccount() {
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
    void isATelephonePaymentNotificationAccount_shouldReturnTrueIfTelephonePaymentNotificationsAreEnabled() {
        long id = nextLong();
        String externalId = randomUuid();
        Map<String, Object> credentials = Map.of(CREDENTIALS_MERCHANT_ID, "merchant-id");

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
    void isATelephonePaymentNotificationAccount_shouldReturnFalseIfTelephonePaymentNotificationsAreNotEnabled() {
        long id = nextLong();
        String externalId = randomUuid();
        Map<String, Object> credentials = Map.of(CREDENTIALS_MERCHANT_ID, "merchant-id");

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
                .withCorporatePrepaidDebitCardSurchargeAmount(50L)
                .insert();
    }
}
