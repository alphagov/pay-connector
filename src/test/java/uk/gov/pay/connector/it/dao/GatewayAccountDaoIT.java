package uk.gov.pay.connector.it.dao;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private GatewayAccountDao gatewayAccountDao;
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private DatabaseFixtures databaseFixtures;
    private long gatewayAccountId;


    @BeforeEach
    void setUp() {
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
        gatewayAccountCredentialsDao = app.getInstanceFromGuiceContainer(GatewayAccountCredentialsDao.class);
        databaseFixtures = app.getDatabaseFixtures();
        gatewayAccountId = secureRandomLong();
    }

    @Nested
    class FindByServiceId {

        @Test
        void shouldFindGatewayAccounts() {
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
        void shouldFindNoGatewayAccount() {
            GatewayAccountEntity account1 = new GatewayAccountEntity(TEST);
            account1.setExternalId(randomUuid());
            account1.setServiceId("a-service-id");
            gatewayAccountDao.persist(account1);

            assertTrue(gatewayAccountDao.findByServiceId("non-existent").isEmpty());
        }
    }

    @Nested
    class FindByGatewayAccountId {

        @Test
        void shouldNotFindANonexistentGatewayAccount() {
            assertThat(gatewayAccountDao.findById(GatewayAccountEntity.class, 1234L).isPresent(), is(false));
        }

        @Test
        void shouldFindGatewayAccount() {
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
            assertThat(gatewayAccount.getCardTypes(), containsInAnyOrder(
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
        void shouldFindGatewayAccountWithCorporateSurcharges() {
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
        void shouldUpdateAccountCardTypes() {
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
        void shouldFindAccountInfoById_whenFindingById_returningGatewayAccount() {
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
    }

    @Nested
    class Search {
        @Test
        void shouldReturnAllAccountsWhenNoSearchParameters() {
            long gatewayAccountId_1 = secureRandomLong();
            app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(gatewayAccountId_1))
                    .build());
            long gatewayAccountId_2 = gatewayAccountId_1 + 1;
            app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(gatewayAccountId_2))
                    .build());

            var params = new GatewayAccountSearchParams();

            List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
            assertThat(gatewayAccounts, hasSize(2));
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_1));
            assertThat(gatewayAccounts.get(1).getId(), is(gatewayAccountId_2));
        }

        @Test
        void shouldSearchForAccountsById() {
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_1));
            assertThat(gatewayAccounts.getFirst().getExternalId(), is(externalId_1));
            assertThat(gatewayAccounts.get(1).getId(), is(gatewayAccountId_2));
            assertThat(gatewayAccounts.get(1).getExternalId(), is(externalId_2));
        }

        @Test
        void shouldSearchForAccountsByMotoEnabled() {
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_2));
        }

        @Test
        void shouldSearchForAccountsByApplePayEnabled() {
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_2));
        }

        @Test
        void shouldSearchForAccountsByGooglePayEnabled() {
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_2));
        }

        @Test
        void shouldSearchForAccountsByRequires3ds() {
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_2));
        }

        @Test
        void shouldSearchForAccountsByType() {
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_2));
        }

        @Test
        void shouldSearchForAccountsByPaymentProvider() {
            long gatewayAccountId1 = secureRandomLong();
            long gatewayAccountId2 = secureRandomLong();
            long gatewayAccountId3 = secureRandomLong();
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
            long gatewayAccountId_1 = secureRandomLong();
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
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId_1));
        }

        @Test
        void shouldSearchForAccountsByPaymentProviderAccountId() {
            long gatewayAccountId = secureRandomLong();
            app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(gatewayAccountId))
                    .withPaymentGateway("sandbox")
                    .withCredentials(Map.of("stripe_account_id", "acc123"))
                    .build());

            var params = new GatewayAccountSearchParams();
            params.setPaymentProviderAccountId("acc123");

            List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
            assertThat(gatewayAccounts, hasSize(1));
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId));
        }

        @Test
        void shouldSearchForAccountsByWorldpayMerchantCodeInOneOffPaymentCredentials() {
            long gatewayAccountId = secureRandomLong();
            app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(gatewayAccountId))
                    .withPaymentGateway("worldpay")
                    .withCredentials(Map.of("one_off_customer_initiated", Map.of("merchant_code", "acc123")))
                    .build());

            var params = new GatewayAccountSearchParams();
            params.setPaymentProviderAccountId("acc123");

            List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
            assertThat(gatewayAccounts, hasSize(1));
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId));
        }

        @Test
        void shouldSearchForAccountsByWorldpayMerchantCodeInRecurringCustomerInitiatedCredentials() {
            long gatewayAccountId = secureRandomLong();
            app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(gatewayAccountId))
                    .withPaymentGateway("worldpay")
                    .withCredentials(Map.of("recurring_customer_initiated", Map.of("merchant_code", "acc123")))
                    .build());

            var params = new GatewayAccountSearchParams();
            params.setPaymentProviderAccountId("acc123");

            List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
            assertThat(gatewayAccounts, hasSize(1));
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId));
        }

        @Test
        void shouldSearchForAccountsByWorldpayMerchantCodeInRecurringMerchantInitiatedCredentials() {
            long gatewayAccountId = secureRandomLong();
            app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(gatewayAccountId))
                    .withPaymentGateway("worldpay")
                    .withCredentials(Map.of("recurring_merchant_initiated", Map.of("merchant_code", "acc123")))
                    .build());

            var params = new GatewayAccountSearchParams();
            params.setPaymentProviderAccountId("acc123");

            List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.search(params);
            assertThat(gatewayAccounts, hasSize(1));
            assertThat(gatewayAccounts.getFirst().getId(), is(gatewayAccountId));
        }
    }

    @Test
    void persist_shouldCreateAnAccount() {
        final CardTypeEntity masterCardCredit = app.getDatabaseTestHelper().getMastercardCreditCard();
        final CardTypeEntity visaCardDebit = app.getDatabaseTestHelper().getVisaCreditCard();

        GatewayAccountEntity account = new GatewayAccountEntity(TEST);

        account.setExternalId(randomUuid());
        account.setCardTypes(Arrays.asList(masterCardCredit, visaCardDebit));
        account.setSendReferenceToGateway(true);
        account.setSendPayerEmailToGateway(true);
        account.setSendPayerIpAddressToGateway(true);

        gatewayAccountDao.persist(account);

        assertThat(account.getId(), is(notNullValue()));
        assertThat(account.getEmailNotifications().isEmpty(), is(true));
        assertThat(account.getDescription(), is(nullValue()));
        assertThat(account.getAnalyticsId(), is(nullValue()));
        assertThat(account.getCorporateNonPrepaidCreditCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporateNonPrepaidDebitCardSurchargeAmount(), is(0L));
        assertThat(account.getCorporatePrepaidDebitCardSurchargeAmount(), is(0L));
        assertThat(account.isSendPayerIpAddressToGateway(), is(true));
        assertThat(account.isSendPayerEmailToGateway(), is(true));
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
        Long id = secureRandomLong();
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
    void findByServiceIdAndAccountType_shouldReturnAllAccounts() {
        long firstAccountId = secureRandomLong();
        String firstExternalId = randomUuid();
        String serviceId = randomUuid();
        databaseFixtures
                .aTestAccount()
                .withAccountId(firstAccountId)
                .withExternalId(firstExternalId)
                .withServiceId(serviceId)
                .insert();

        long secondAccountId = firstAccountId + 1;
        String secondExternalId = randomUuid();
        databaseFixtures
                .aTestAccount()
                .withAccountId(secondAccountId)
                .withExternalId(secondExternalId)
                .withServiceId(serviceId)
                .insert();

        List<GatewayAccountEntity> gatewayAccount = gatewayAccountDao.findByServiceIdAndAccountType(serviceId, TEST);
        assertThat(gatewayAccount.size(), is(2));
        assertTrue(gatewayAccount.stream().anyMatch(ga -> ga.getExternalId().equals(firstExternalId)));
        assertTrue(gatewayAccount.stream().anyMatch(ga -> ga.getExternalId().equals(secondExternalId)));
    }

    @Test
    void isATelephonePaymentNotificationAccount_shouldReturnTrueIfTelephonePaymentNotificationsAreEnabled() {
        long id = secureRandomLong();
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
        long id = secureRandomLong();
        String externalId = randomUuid();
        Map<String, Object> credentials = Map.of(CREDENTIALS_MERCHANT_ID, "merchant-id");

        databaseFixtures.aTestAccount()
                .withAccountId(id)
                .withExternalId(externalId)
                .withAllowTelephonePaymentNotifications(false)
                .insert();
        databaseFixtures.aTestAccount()
                .withAccountId(secureRandomLong())
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
