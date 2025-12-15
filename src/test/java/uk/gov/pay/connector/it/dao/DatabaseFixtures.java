package uk.gov.pay.connector.it.dao;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.MICROS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;
import static uk.gov.service.payments.commons.model.AuthorisationMode.WEB;

public class DatabaseFixtures {

    private final DatabaseTestHelper databaseTestHelper;

    public DatabaseFixtures(DatabaseTestHelper databaseTestHelper) {
        this.databaseTestHelper = databaseTestHelper;
    }

    public static DatabaseFixtures withDatabaseTestHelper(DatabaseTestHelper databaseTestHelper) {
        return new DatabaseFixtures(databaseTestHelper);
    }

    public TestAccount aTestAccount() {
        return new TestAccount();
    }

    public TestCharge aTestCharge() {
        return new TestCharge();
    }

    public TestAgreement aTestAgreement() {
        return new TestAgreement();
    }

    public TestPaymentInstrument aTestPaymentInstrument() {
        return new TestPaymentInstrument();
    }

    public TestChargeEvent aTestChargeEvent() {
        return new TestChargeEvent();
    }

    public TestRefundHistory aTestRefundHistory(TestRefund refund) {
        return new TestRefundHistory(refund);
    }

    public TestToken aTestToken() {
        return new TestToken();
    }

    public TestRefund aTestRefund() {
        return new TestRefund();
    }

    public TestFee aTestFee() {
        return new TestFee();
    }

    public TestCardDetails aTestCardDetails() {
        return new TestCardDetails();
    }

    public TestCardDetails validTestCardDetails() {
        return new TestCardDetails();
    }

    public TestCardType aCardTypeFrom(CardTypeEntity cardTypeEntity) {
        return new TestCardType()
                .withCardTypeId(cardTypeEntity.getId())
                .withBrand(cardTypeEntity.getBrand())
                .withLabel(cardTypeEntity.getLabel())
                .withType(cardTypeEntity.getType())
                .withRequires3ds(cardTypeEntity.isRequires3ds());
    }

    public class TestRefundHistory {

        private final String externalId;
        private final long id;
        private final long amount;
        private final ZonedDateTime createdDate;
        private final String userExternalId;
        private final String chargeExternalId;

        TestRefundHistory(TestRefund testRefund) {
            this.id = testRefund.getId();
            this.externalId = testRefund.getExternalRefundId();
            this.amount = testRefund.getAmount();
            this.createdDate = testRefund.getCreatedDate();
            this.userExternalId = testRefund.getSubmittedByUserExternalId();
            this.chargeExternalId = testRefund.chargeExternalId;
        }

        public TestRefundHistory insert(RefundStatus status, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate) {
            databaseTestHelper.addRefundHistory(id, externalId, "", amount, status.toString(), createdDate, historyStartDate, historyEndDate, null, null, chargeExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), createdDate, historyStartDate, historyEndDate, null, null, chargeExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), createdDate, historyStartDate, null, chargeExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate, String submittedByExternalId, String userEmail) {
            databaseTestHelper.addRefundHistory(id, externalId, "", amount, status.toString(), createdDate, historyStartDate, historyEndDate, submittedByExternalId, userEmail, chargeExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String gatewayTransactionId, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate, String submittedByExternalId, String userEmail) {
            databaseTestHelper.addRefundHistory(id, externalId, gatewayTransactionId, amount, status.toString(), createdDate, historyStartDate, historyEndDate, submittedByExternalId, userEmail, chargeExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate, String submittedByExternalId, String chargeExternalId) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), createdDate, historyStartDate, submittedByExternalId, chargeExternalId);
            return this;
        }
    }

    public class TestChargeEvent {
        private long chargeId;
        private ChargeStatus chargeStatus;
        private ZonedDateTime updated = now();

        public TestChargeEvent withTestCharge(TestCharge testCharge) {
            this.chargeId = testCharge.getChargeId();
            return this;
        }

        public TestChargeEvent withChargeId(long chargeId) {
            this.chargeId = chargeId;
            return this;
        }

        public TestChargeEvent withChargeStatus(ChargeStatus chargeStatus) {
            this.chargeStatus = chargeStatus;
            return this;
        }

        public TestChargeEvent withDate(ZonedDateTime updated) {
            this.updated = updated;
            return this;
        }

        public TestChargeEvent insert() {
            databaseTestHelper.addEvent(chargeId, chargeStatus.getValue(), updated);
            return this;
        }

        public long getChargeId() {
            return chargeId;
        }

        public ChargeStatus getChargeStatus() {
            return chargeStatus;
        }

        public ZonedDateTime getUpdated() {
            return updated;
        }
    }

    public static class TestAddress {

        public String getLine1() {
            return "line1";
        }

        public String getLine2() {
            return "line2";
        }

        public String getPostcode() {
            return "postcode";
        }

        public String getCity() {
            return "city";
        }

        public String getCounty() {
            return "county";
        }

        public String getCountry() {
            return "country";
        }
    }

    public class TestCardDetails {
        private LastDigitsCardNumber lastDigitsCardNumber = LastDigitsCardNumber.of("1234");
        private FirstDigitsCardNumber firstDigitsCardNumber = FirstDigitsCardNumber.of("123456");
        private String cardHolderName = "Mr. Pay McPayment";
        private CardExpiryDate expiryDate = CardExpiryDate.valueOf("02/17");
        private TestAddress billingAddress = new TestAddress();
        private Long chargeId;
        private String cardBrand = "visa";
        private CardType cardType = null;

        public TestCardDetails withLastDigitsOfCardNumber(LastDigitsCardNumber lastDigitsCardNumber) {
            this.lastDigitsCardNumber = lastDigitsCardNumber;
            return this;
        }

        public TestCardDetails withFirstDigitsOfCardNumber(FirstDigitsCardNumber firstDigitsCardNumber) {
            this.firstDigitsCardNumber = firstDigitsCardNumber;
            return this;
        }

        public TestCardDetails withCardHolderName(String cardHolderName) {
            this.cardHolderName = cardHolderName;
            return this;
        }

        public TestCardDetails withExpiryDate(CardExpiryDate expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public TestCardDetails withBillingAddress(TestAddress billingAddress) {
            this.billingAddress = billingAddress;
            return this;
        }

        public TestCardDetails withCardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
            return this;
        }

        public TestCardDetails withChargeId(Long chargeId) {
            this.chargeId = chargeId;
            return this;
        }

        public TestCardDetails withCardType(CardType cardType) {
            this.cardType = cardType;
            return this;
        }

        public LastDigitsCardNumber getLastDigitsCardNumber() {
            return lastDigitsCardNumber;
        }

        public FirstDigitsCardNumber getFirstDigitsCardNumber() {
            return firstDigitsCardNumber;
        }

        public String getCardHolderName() {
            return cardHolderName;
        }

        public CardExpiryDate getExpiryDate() {
            return expiryDate;
        }

        public TestAddress getBillingAddress() {
            return billingAddress;
        }

        public CardType getCardType() {
            return cardType;
        }

        public TestCardDetails update() {
            databaseTestHelper.updateChargeCardDetails(
                    chargeId,
                    cardBrand,
                    lastDigitsCardNumber == null ? null : lastDigitsCardNumber.toString(),
                    firstDigitsCardNumber == null ? null : firstDigitsCardNumber.toString(),
                    cardHolderName,
                    expiryDate,
                    cardType == null ? null : cardType.toString(),
                    billingAddress.getLine1(),
                    billingAddress.getLine2(),
                    billingAddress.getPostcode(),
                    billingAddress.getCity(),
                    billingAddress.getCounty(),
                    billingAddress.getCountry());
            return this;
        }

        public String getCardBrand() {
            return cardBrand;
        }
    }

    public class TestAccount {
        long accountId = secureRandomLong(1, 99999);
        String externalId = randomUuid();
        private String paymentProvider = "sandbox";
        private Map<String, Object> credentialsMap = Map.of();
        private List<AddGatewayAccountCredentialsParams> gatewayAccountCredentialsParams;
        private String serviceName = "service_name";
        private String serviceId = "valid-external-service-id";
        private String description = "a description";
        private String analyticsId = "an analytics id";
        private final EmailCollectionMode emailCollectionMode = EmailCollectionMode.OPTIONAL;
        private Map<EmailNotificationType, TestEmailNotification> emailNotifications =
                Map.of(
                        EmailNotificationType.PAYMENT_CONFIRMED, new TestEmailNotification(),
                        EmailNotificationType.REFUND_ISSUED, new TestEmailNotification()
                );
        private GatewayAccountType type = TEST;
        private List<TestCardType> cardTypes = new ArrayList<>();
        private long corporateCreditCardSurchargeAmount;
        private long corporateDebitCardSurchargeAmount;
        private long corporatePrepaidDebitCardSurchargeAmount;
        private int integrationVersion3ds = 2;
        private boolean allowMoto;
        private boolean allowAuthApi;
        private boolean motoMaskCardNumberInput;
        private boolean motoMaskCardSecurityCodeInput;
        private boolean allowTelephonePaymentNotifications;
        private boolean recurringEnabled;
        private boolean providerSwitchEnabled;
        private boolean requires3ds;
        private final Map<String, Object> defaultCredentials = Map.of(
                CREDENTIALS_MERCHANT_ID, "merchant-id",
                CREDENTIALS_USERNAME, "username",
                CREDENTIALS_PASSWORD, "password"
        );
        private boolean sendPayerEmailToGateway;
        private boolean sendPayerIpAddressToGateway;

        public long getAccountId() {
            return accountId;
        }

        public String getExternalId() {
            return externalId;
        }

        public String getPaymentProvider() {
            return paymentProvider;
        }

        public List<AddGatewayAccountCredentialsParams> getCredentials() {
            return gatewayAccountCredentialsParams;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getDescription() {
            return description;
        }

        public String getAnalyticsId() {
            return analyticsId;
        }

        public long getCorporateCreditCardSurchargeAmount() {
            return corporateCreditCardSurchargeAmount;
        }

        public long getCorporateDebitCardSurchargeAmount() {
            return corporateDebitCardSurchargeAmount;
        }

        public EmailCollectionMode getEmailCollectionMode() {
            return emailCollectionMode;
        }

        public long getCorporatePrepaidDebitCardSurchargeAmount() {
            return corporatePrepaidDebitCardSurchargeAmount;
        }

        public Map<EmailNotificationType, TestEmailNotification> getEmailNotifications() {
            return emailNotifications;
        }

        public boolean isAllowTelephonePaymentNotifications() {
            return allowTelephonePaymentNotifications;
        }

        public boolean isRequires3ds() {
            return requires3ds;
        }

        public void setRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
        }

        public boolean isRecurringEnabled() {
            return recurringEnabled;
        }

        public void setRecurringEnabled(boolean recurringEnabled) {
            this.recurringEnabled = recurringEnabled;
        }

        public boolean isSendPayerEmailToGateway() {
            return sendPayerEmailToGateway;
        }

        public boolean isSendPayerIpAddressToGateway() {
            return sendPayerIpAddressToGateway;
        }

        public TestAccount withAccountId(long accountId) {
            this.accountId = accountId;
            return this;
        }

        public TestAccount withCardTypes(List<TestCardType> cardTypes) {
            this.cardTypes = cardTypes;
            return this;
        }

        public TestAccount withCardTypeEntities(List<CardTypeEntity> cardTypeEntities) {
            cardTypeEntities.forEach(cardTypeEntity -> cardTypes.add(aCardTypeFrom(cardTypeEntity)));
            return this;
        }

        public TestAccount withPaymentProvider(String provider) {
            this.paymentProvider = provider;
            return this;
        }

        public TestAccount withCredentials(Map<String, Object> credentials) {
            this.credentialsMap = credentials;
            return this;
        }

        public TestAccount withGatewayAccountCredentials(List<AddGatewayAccountCredentialsParams> gatewayAccountCredentialsParams) {
            this.gatewayAccountCredentialsParams = gatewayAccountCredentialsParams;
            return this;
        }

        public TestAccount withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public TestAccount withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TestAccount withDescription(String description) {
            this.description = description;
            return this;
        }

        public TestAccount withAnalyticsId(String analyticsId) {
            this.analyticsId = analyticsId;
            return this;
        }

        public TestAccount withType(GatewayAccountType type) {
            this.type = type;
            return this;
        }

        public TestAccount withCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
            this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
            return this;
        }

        public TestAccount withCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
            this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
            return this;
        }

        public TestAccount withEmailNotifications(Map<EmailNotificationType, TestEmailNotification> notifications) {
            this.emailNotifications = notifications;
            return this;
        }

        public TestAccount withCorporatePrepaidDebitCardSurchargeAmount(long corporatePrepaidDebitCardSurchargeAmount) {
            this.corporatePrepaidDebitCardSurchargeAmount = corporatePrepaidDebitCardSurchargeAmount;
            return this;
        }

        public TestAccount withIntegrationVersion3ds(int integrationVersion3ds) {
            this.integrationVersion3ds = integrationVersion3ds;
            return this;
        }

        public TestAccount withAllowMoto(boolean allowMoto) {
            this.allowMoto = allowMoto;
            return this;
        }

        public TestAccount withAllowAuthApi(boolean allowAuthApi) {
            this.allowAuthApi = allowAuthApi;
            return this;
        }

        public TestAccount withMotoMaskCardNumberInput(boolean motoMaskCardNumberInput) {
            this.motoMaskCardNumberInput = motoMaskCardNumberInput;
            return this;
        }

        public TestAccount withMotoMaskCardSecurityCodeInput(boolean motoMaskCardSecurityCodeInput) {
            this.motoMaskCardSecurityCodeInput = motoMaskCardSecurityCodeInput;
            return this;
        }

        public TestAccount withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public TestAccount withAllowTelephonePaymentNotifications(boolean allowTelephonePaymentNotifications) {
            this.allowTelephonePaymentNotifications = allowTelephonePaymentNotifications;
            return this;
        }

        public TestAccount withRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public TestAccount withRecurringEnabled(boolean recurringEnabled) {
            this.recurringEnabled = recurringEnabled;
            return this;
        }

        public TestAccount withDefaultCredentials() {
            this.credentialsMap = defaultCredentials;
            return this;
        }

        public TestAccount withProviderSwitchEnabled(boolean providerSwitchEnabled) {
            this.providerSwitchEnabled = providerSwitchEnabled;
            return this;
        }

        public TestAccount withSendPayerEmailToGateway(boolean sendPayerEmailToGateway) {
            this.sendPayerEmailToGateway = sendPayerEmailToGateway;
            return this;
        }

        public TestAccount withSendPayerIpAddressToGateway(boolean sendPayerIpAddressToGateway) {
            this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;
            return this;
        }

        public TestAccount insert() {
            if (gatewayAccountCredentialsParams == null) {
                gatewayAccountCredentialsParams = Collections.singletonList(
                        anAddGatewayAccountCredentialsParams()
                                .withGatewayAccountId(accountId)
                                .withPaymentProvider(paymentProvider)
                                .withCredentials(credentialsMap)
                                .build());
            }

            databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(accountId))
                    .withExternalId(externalId)
                    .withPaymentGateway(paymentProvider)
                    .withGatewayAccountCredentials(gatewayAccountCredentialsParams)
                    .withServiceName(serviceName)
                    .withType(type)
                    .withDescription(description)
                    .withAnalyticsId(analyticsId)
                    .withEmailCollectionMode(emailCollectionMode)
                    .withCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount)
                    .withCorporateDebitCardSurchargeAmount(corporateDebitCardSurchargeAmount)
                    .withCorporatePrepaidDebitCardSurchargeAmount(corporatePrepaidDebitCardSurchargeAmount)
                    .withIntegrationVersion3ds(integrationVersion3ds)
                    .withAllowMoto(allowMoto)
                    .withAllowAuthApi(allowAuthApi)
                    .withMotoMaskCardNumberInput(motoMaskCardNumberInput)
                    .withMotoMaskCardSecurityCodeInput(motoMaskCardSecurityCodeInput)
                    .withAllowTelephonePaymentNotifications(allowTelephonePaymentNotifications)
                    .withServiceId(serviceId)
                    .withRequires3ds(requires3ds)
                    .withRecurringEnabled(recurringEnabled)
                    .withProviderSwitchEnabled(providerSwitchEnabled)
                    .withSendPayerEmailToGateway(sendPayerEmailToGateway)
                    .withSendPayerIpAddressToGateway(sendPayerIpAddressToGateway)
                    .build());
            for (TestCardType cardType : cardTypes) {
                databaseTestHelper.addAcceptedCardType(this.getAccountId(), cardType.getId());
            }
            emailNotifications.forEach((type, notification) -> databaseTestHelper.addEmailNotification(this.getAccountId(), notification.getTemplate(), notification.isEnabled(), type));

            return this;
        }

    }

    public class TestAgreement {
        Long agreementId = secureRandomLong();
        Long gatewayAccountId = secureRandomLong();
        String externalId = "externalIDxxxyz";
        String reference = "AgreementReference";
        String description = "A valid description";
        String userIdentifier = "a-valid-user-identifier";
        Instant createdDate = Instant.now();
        boolean live = false;
        String serviceId = "service-id";
        Long paymentInstumentId;

        public Long getAgreementId() {
            return agreementId;
        }

        public String getExternalId() {
            return externalId;
        }

        public String getReference() {
            return reference;
        }

        public Instant getCreatedDate() {
            return createdDate;
        }

        public boolean isLive() {
            return live;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getDescription() {
            return description;
        }

        public String getUserIdentifier() {
            return userIdentifier;
        }

        public DatabaseFixtures.TestAgreement withAgreementId(Long agreementId) {
            this.agreementId = agreementId;
            return this;
        }

        public DatabaseFixtures.TestAgreement withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public DatabaseFixtures.TestAgreement withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public DatabaseFixtures.TestAgreement withDescription(String description) {
            this.description = description;
            return this;
        }

        public DatabaseFixtures.TestAgreement withUserIdentifier(String userIdentifier) {
            this.userIdentifier = userIdentifier;
            return this;
        }

        public DatabaseFixtures.TestAgreement withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public DatabaseFixtures.TestAgreement withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public DatabaseFixtures.TestAgreement withLive(boolean live) {
            this.live = live;
            return this;
        }

        public DatabaseFixtures.TestAgreement withGatewayAccountId(long gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public DatabaseFixtures.TestAgreement withPaymentInstrumentId(long paymentInstrumentId) {
            this.paymentInstumentId = paymentInstrumentId;
            return this;
        }

        public TestAgreement insert() {
            if (gatewayAccountId == null)
                throw new IllegalStateException("Test Account must be provided.");

            databaseTestHelper.addAgreement(anAddAgreementParams()
                    .withAgreementId(agreementId)
                    .withExternalAgreementId(externalId)
                    .withLive(live)
                    .withServiceId(serviceId)
                    .withGatewayAccountId(String.valueOf(gatewayAccountId))
                    .withCreatedDate(createdDate)
                    .withReference(reference)
                    .withDescription(description)
                    .withUserIdentifier(userIdentifier)
                    .withPaymentInstrumentId(paymentInstumentId)
                    .build());

            return this;
        }
    }

    public class TestCharge {
        Long chargeId = secureRandomLong();
        String email = "alice.111@mail.test";
        String externalChargeId = RandomIdGenerator.newId();
        long amount = 101L;
        ChargeStatus chargeStatus = ChargeStatus.CREATED;
        String returnUrl = "http://service.com/success-page";
        String transactionId;
        ServicePaymentReference reference = ServicePaymentReference.of("Test reference");
        SupportedLanguage language = SupportedLanguage.ENGLISH;
        boolean delayedCapture = false;
        Long corporateCardSurcharge = null;
        Instant createdDate = Instant.now().truncatedTo(MICROS);
        TestAccount testAccount;
        String paymentProvider = "sandbox";
        TestCardDetails cardDetails;
        WalletType walletType;
        ParityCheckStatus parityCheckStatus;
        private String description = "Test description";
        private ZonedDateTime parityCheckDate;
        Long gatewayCredentialId;
        private AuthorisationMode authorisationMode = WEB;
        private Instant updatedDate;
        private Boolean canRetry;
        private String serviceId;
        private Boolean requires3ds;

        public TestCardDetails getCardDetails() {
            return cardDetails;
        }

        public TestCharge withTestAccount(TestAccount account) {
            this.testAccount = account;
            return this;
        }

        public TestCharge withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public TestCharge withChargeId(long chargeId) {
            this.chargeId = chargeId;
            return this;
        }

        public TestCharge withExternalChargeId(String externalChargeId) {
            this.externalChargeId = externalChargeId;
            return this;
        }

        public TestCharge withReference(ServicePaymentReference reference) {
            this.reference = reference;
            return this;
        }

        public TestCharge withEmail(String email) {
            this.email = email;
            return this;
        }

        public TestCharge withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public TestCharge withChargeStatus(ChargeStatus chargeStatus) {
            this.chargeStatus = chargeStatus;
            return this;
        }

        public TestCharge withTransactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public TestCharge withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public TestCharge withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public TestCharge withUpdatedDate(Instant updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public TestCharge withCardDetails(TestCardDetails testCardDetails) {
            cardDetails = testCardDetails;
            return this;
        }

        public TestCharge withDescription(String description) {
            this.description = description;
            return this;
        }

        public TestCharge withLanguage(SupportedLanguage language) {
            this.language = language;
            return this;
        }

        public TestCharge withDelayedCapture(boolean delayedCapture) {
            this.delayedCapture = delayedCapture;
            return this;
        }

        public TestCharge withParityCheckStatus(ParityCheckStatus parityCheckStatus) {
            this.parityCheckStatus = parityCheckStatus;
            return this;
        }

        public TestCharge withParityCheckDate(ZonedDateTime parityCheckDate) {
            this.parityCheckDate = parityCheckDate;
            return this;
        }

        public TestCharge withGatewayCredentialId(Long gatewayCredentialId) {
            this.gatewayCredentialId = gatewayCredentialId;
            return this;
        }

        public TestCharge withCanRetry(Boolean canRetry) {
            this.canRetry = canRetry;
            return this;
        }

        public TestCharge withRequires3ds(Boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public TestCharge insert() {
            if (testAccount == null)
                throw new IllegalStateException("Test Account must be provided.");

            databaseTestHelper.addCharge(anAddChargeParams()
                    .withChargeId(chargeId)
                    .withExternalChargeId(externalChargeId)
                    .withGatewayAccountId(String.valueOf(testAccount.getAccountId()))
                    .withPaymentProvider(paymentProvider)
                    .withAmount(amount)
                    .withStatus(chargeStatus)
                    .withReturnUrl(returnUrl)
                    .withTransactionId(transactionId)
                    .withDescription(description)
                    .withReference(reference)
                    .withCreatedDate(createdDate)
                    .withVersion(1)
                    .withLanguage(language)
                    .withDelayedCapture(false)
                    .withEmail(email)
                    .withCorporateSurcharge(corporateCardSurcharge)
                    .withParityCheckStatus(parityCheckStatus)
                    .withParityCheckDate(parityCheckDate)
                    .withGatewayCredentialId(gatewayCredentialId)
                    .withAuthorisationMode(authorisationMode)
                    .withCanRetry(canRetry)
                    .withUpdatedDate(updatedDate)
                    .withServiceId(serviceId)
                    .withRequires3ds(requires3ds)
                    .build());

            if (cardDetails != null) {
                cardDetails.update();
            }
            return this;
        }

        public TestCharge withCorporateCardSurcharge(Long corporateCardSurcharge) {
            this.corporateCardSurcharge = corporateCardSurcharge;
            return this;
        }

        public TestCharge withAuthorisationMode(AuthorisationMode authorisationMode) {
            this.authorisationMode = authorisationMode;
            return this;
        }

        public TestCharge withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public Long getChargeId() {
            return chargeId;
        }

        public String getExternalChargeId() {
            return externalChargeId;
        }

        public long getAmount() {
            return amount;
        }

        public ChargeStatus getChargeStatus() {
            return chargeStatus;
        }

        public String getReturnUrl() {
            return returnUrl;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public ServicePaymentReference getReference() {
            return reference;
        }

        public String getEmail() {
            return email;
        }

        public Instant getCreatedDate() {
            return createdDate;
        }

        public Instant getUpdatedDate() {
            return updatedDate;
        }

        public String getDescription() {
            return description;
        }

        public SupportedLanguage getLanguage() {
            return language;
        }

        public boolean isDelayedCapture() {
            return delayedCapture;
        }

        public TestAccount getTestAccount() {
            return testAccount;
        }

        public String getPaymentProvider() {
            return paymentProvider;
        }

        public Long getCorporateCardSurcharge() {
            return corporateCardSurcharge;
        }

        public AuthorisationMode getAuthorisationMode() {
            return authorisationMode;
        }

        public Boolean getCanRetry() {
            return canRetry;
        }

        public Boolean getRequires3ds() {
            return requires3ds;
        }

        public String getServiceId() {
            return serviceId;
        }
    }

    public class TestToken {
        TestCharge testCharge;
        String secureRedirectToken = "3c9fee80-977a-4da5-a003-4872a8cf95b6";
        boolean used = false;

        public TestToken withCharge(TestCharge testCharge) {
            this.testCharge = testCharge;
            return this;
        }

        public TestToken withUsed(boolean used) {
            this.used = used;
            return this;
        }

        public TestToken insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test Charge must be provided.");
            databaseTestHelper.addToken(testCharge.getChargeId(), secureRedirectToken, used);
            return this;
        }

        public String getSecureRedirectToken() {
            return secureRedirectToken;
        }
    }

    public class TestRefund {
        int id;
        String externalRefundId = RandomIdGenerator.newId();
        long amount = 101L;
        RefundStatus status = CREATED;
        ZonedDateTime createdDate = now(ZoneId.of("UTC")).truncatedTo(MICROS);
        TestCharge testCharge;
        String submittedByUserExternalId;
        String gatewayTransactionId;
        private String userEmail;
        String chargeExternalId;
        private ParityCheckStatus parityCheckStatus;
        private ZonedDateTime parityCheckDate;

        public TestRefund withTestCharge(TestCharge charge) {
            this.testCharge = charge;
            return this;
        }

        public TestRefund withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public TestRefund withType(RefundStatus status) {
            this.status = status;
            return this;
        }

        public TestRefund withSubmittedBy(String submittedBy) {
            this.submittedByUserExternalId = submittedBy;
            return this;
        }

        public TestRefund withCreatedDate(ZonedDateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public TestRefund withRefundStatus(RefundStatus status) {
            this.status = status;
            return this;
        }

        public TestRefund withExternalRefundId(String externalRefundId) {
            this.externalRefundId = externalRefundId;
            return this;
        }

        public TestRefund withUserEmail(String userEmail) {
            this.userEmail = userEmail;
            return this;
        }

        public TestRefund withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        public TestRefund withChargeExternalId(String chargeExternalId) {
            this.chargeExternalId = chargeExternalId;
            return this;
        }

        public TestRefund withParityCheckStatus(ParityCheckStatus parityCheckStatus) {
            this.parityCheckStatus = parityCheckStatus;
            return this;
        }

        public TestRefund withParityCheckDate(ZonedDateTime parityCheckDate) {
            this.parityCheckDate = parityCheckDate;
            return this;
        }

        public TestRefund insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test charge must be provided.");
            id = databaseTestHelper.addRefund(externalRefundId, amount, status, gatewayTransactionId,
                    createdDate, submittedByUserExternalId, userEmail,
                    chargeExternalId == null ? testCharge.getExternalChargeId() : chargeExternalId, parityCheckStatus, parityCheckDate);
            return this;
        }

        public long getId() {
            return id;
        }

        public String getExternalRefundId() {
            return externalRefundId;
        }

        public long getAmount() {
            return amount;
        }

        public RefundStatus getStatus() {
            return status;
        }

        public ZonedDateTime getCreatedDate() {
            return createdDate;
        }

        public TestCharge getTestCharge() {
            return testCharge;
        }

        public String getGatewayTransactionId() {
            return gatewayTransactionId;
        }

        public String getSubmittedByUserExternalId() {
            return submittedByUserExternalId;
        }

        public String getUserEmail() {
            return userEmail;
        }
    }

    public class TestFee {
        String externalId = RandomIdGenerator.newId();
        ZonedDateTime createdDate = now(ZoneId.of("UTC"));
        TestCharge testCharge;
        private long feeDue = 100L;
        private long feeCollected = 100L;
        private String gatewayTransactionId = "transaction_id";
        private FeeType feeType = FeeType.TRANSACTION;

        public TestFee withTestCharge(TestCharge charge) {
            this.testCharge = charge;
            return this;
        }

        public TestFee withFeeDue(long feeDue) {
            this.feeDue = feeDue;
            return this;
        }

        public TestFee withFeeCollected(long feeCollected) {
            this.feeCollected = feeCollected;
            return this;
        }

        public TestFee withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        public TestFee withFeeType(FeeType feeType) {
            this.feeType = feeType;
            return this;
        }

        public TestFee insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test charge must be provided.");
            databaseTestHelper.addFee(externalId, testCharge.getChargeId(), feeDue, feeCollected, createdDate, gatewayTransactionId, feeType);
            return this;
        }
    }

    public class TestEmailNotification {

        TestAccount testAccount;
        String template = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        boolean enabled = true;

        public String getTemplate() {
            return template;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public TestEmailNotification withTestAccount(TestAccount testAccount) {
            this.testAccount = testAccount;
            return this;
        }

        public TestEmailNotification withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public TestEmailNotification insert() {
            if (testAccount == null)
                throw new IllegalStateException("Test Account must be provided.");
            databaseTestHelper.addToken(testAccount.getAccountId(), template);
            return this;
        }
    }

    public class TestCardType {
        UUID id = UUID.randomUUID();
        String label = "Mastercard";
        CardType type = CardType.CREDIT;
        String brand = "mastercard-c";
        boolean requires3DS;

        public TestCardType withCardTypeId(UUID id) {
            this.id = id;
            return this;
        }

        public TestCardType withLabel(String label) {
            this.label = label;
            return this;
        }

        public TestCardType withType(CardType type) {
            this.type = type;
            return this;
        }

        public TestCardType withBrand(String brand) {
            this.brand = brand;
            return this;
        }

        public TestCardType withRequires3ds(boolean requires3DS) {
            this.requires3DS = requires3DS;
            return this;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public CardType getType() {
            return type;
        }

        public void setAcceptedType(CardType cardType) {
            this.type = cardType;
        }

        public String getBrand() {
            return brand;
        }

        public boolean getRequires3DS() {
            return requires3DS;
        }
    }

    public class TestPaymentInstrument {
        Long paymentInstrumentId = secureRandomLong();
        ;
        Map<String, String> recurringAuthToken;
        Instant createdDate = Instant.now();
        Instant startDate = Instant.now();
        String externalId = "externalIDabc";

        String agreementExternalId;

        PaymentInstrumentStatus status = PaymentInstrumentStatus.CREATED;

        public Long getPaymentInstrumentId() {
            return paymentInstrumentId;
        }

        public TestPaymentInstrument withPaymentInstrumentId(Long paymentInstrumentId) {
            this.paymentInstrumentId = paymentInstrumentId;
            return this;
        }

        public TestPaymentInstrument withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public TestPaymentInstrument withAgreementExternalId(String agreementExternalId) {
            this.agreementExternalId = agreementExternalId;
            return this;
        }

        public TestPaymentInstrument withStatus(PaymentInstrumentStatus status) {
            this.status = status;
            return this;
        }

        public TestPaymentInstrument insert() {
            databaseTestHelper.addPaymentInstrument(anAddPaymentInstrumentParams()
                    .withPaymentInstrumentId(paymentInstrumentId)
                    .withExternalPaymentInstrumentId(externalId)
                    .withCreatedDate(createdDate)
                    .withStartDate(startDate)
                    .withPaymentInstrumentStatus(status)
                    .withAgreementExternalId(agreementExternalId)
                    .build());
            return this;
        }
    }
}
