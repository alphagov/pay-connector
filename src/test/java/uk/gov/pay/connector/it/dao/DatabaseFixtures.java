package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomUtils;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.cardtype.model.domain.SupportedType;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class DatabaseFixtures {

    private DatabaseTestHelper databaseTestHelper;

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

        private String externalId;
        private long id;
        private Long chargeId;
        private long amount;
        private ZonedDateTime createdDate;
        private String userExternalId;

        TestRefundHistory(TestRefund testRefund) {
            this.id = testRefund.getId();
            this.chargeId = testRefund.getTestCharge().getChargeId();
            this.externalId = testRefund.getExternalRefundId();
            this.amount = testRefund.getAmount();
            this.createdDate = testRefund.getCreatedDate();
            this.userExternalId = testRefund.getSubmittedByUserExternalId();
        }

        public TestRefundHistory insert(RefundStatus status, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate) {
            databaseTestHelper.addRefundHistory(id, externalId, "", amount, status.toString(), chargeId, createdDate, historyStartDate, historyEndDate, null);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), chargeId, createdDate, historyStartDate, historyEndDate, null);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), chargeId, createdDate, historyStartDate, null);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate, String submittedByExternalId) {
            databaseTestHelper.addRefundHistory(id, externalId, "", amount, status.toString(), chargeId, createdDate, historyStartDate, historyEndDate, submittedByExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate, String submittedByExternalId) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), chargeId, createdDate, historyStartDate, historyEndDate, submittedByExternalId);
            return this;
        }

        public TestRefundHistory insert(RefundStatus status, String reference, ZonedDateTime historyStartDate, String submittedByExternalId) {
            databaseTestHelper.addRefundHistory(id, externalId, reference, amount, status.toString(), chargeId, createdDate, historyStartDate, submittedByExternalId);
            return this;
        }
    }

    public class TestChargeEvent {
        private long chargeId;
        private ChargeStatus chargeStatus;
        private ZonedDateTime updated = ZonedDateTime.now();

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

    public class TestAddress {
        private String line1 = "line1";
        private String line2 = "line2";
        private String postcode = "postcode";
        private String city = "city";
        private String county = "county";
        private String country = "country";

        public String getLine1() {
            return line1;
        }

        public String getLine2() {
            return line2;
        }

        public String getPostcode() {
            return postcode;
        }

        public String getCity() {
            return city;
        }

        public String getCounty() {
            return county;
        }

        public String getCountry() {
            return country;
        }
    }

    public class TestCardDetails {
        private LastDigitsCardNumber lastDigitsCardNumber = LastDigitsCardNumber.of("1234");
        private FirstDigitsCardNumber firstDigitsCardNumber = FirstDigitsCardNumber.of("123456");
        private String cardHolderName = "Mr. Pay McPayment";
        private String expiryDate = "02/17";
        private TestAddress billingAddress = new TestAddress();
        private Long chargeId;
        private String cardBrand = "visa";

        public TestCardDetails withLastDigitsOfCardNumber(String lastDigitsCardNumber) {
            this.lastDigitsCardNumber = LastDigitsCardNumber.ofNullable(lastDigitsCardNumber);
            return this;
        }

        public TestCardDetails withFirstDigitsOfCardNumber(String firstDigitsCardNumber) {
            this.firstDigitsCardNumber = FirstDigitsCardNumber.ofNullable(firstDigitsCardNumber);
            return this;
        }

        public TestCardDetails withCardHolderName(String cardHolderName) {
            this.cardHolderName = cardHolderName;
            return this;
        }

        public TestCardDetails withExpiryDate(String expiryDate) {
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

        public LastDigitsCardNumber getLastDigitsCardNumber() {
            return lastDigitsCardNumber;
        }

        public FirstDigitsCardNumber getFirstDigitsCardNumber() {
            return firstDigitsCardNumber;
        }

        public String getCardHolderName() {
            return cardHolderName;
        }

        public String getExpiryDate() {
            return expiryDate;
        }

        public TestAddress getBillingAddress() {
            return billingAddress;
        }

        public TestCardDetails update() {
            databaseTestHelper.updateChargeCardDetails(
                    chargeId,
                    cardBrand,
                    lastDigitsCardNumber == null ? null : lastDigitsCardNumber.toString(),
                    firstDigitsCardNumber == null ? null : firstDigitsCardNumber.toString(),
                    cardHolderName,
                    expiryDate,
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
        long accountId = RandomUtils.nextLong(1, 99999);
        private String paymentProvider = "sandbox";
        private Map<String, String> credentials = new HashMap<>();
        private String serviceName = "service_name";
        private String description = "a description";
        private String analyticsId = "an analytics id";
        private EmailCollectionMode emailCollectionMode = EmailCollectionMode.OPTIONAL;
        private Map<EmailNotificationType, TestEmailNotification> emailNotifications =
                ImmutableMap.of(
                        EmailNotificationType.PAYMENT_CONFIRMED, new TestEmailNotification(),
                        EmailNotificationType.REFUND_ISSUED, new TestEmailNotification()
                );
        private GatewayAccountEntity.Type type = TEST;
        private List<TestCardType> cardTypes = new ArrayList<>();
        private long corporateCreditCardSurchargeAmount;
        private long corporateDebitCardSurchargeAmount;
        private long corporatePrepaidCreditCardSurchargeAmount;
        private long corporatePrepaidDebitCardSurchargeAmount;
        private int integrationVersion3ds = 2;

        public long getAccountId() {
            return accountId;
        }

        public String getPaymentProvider() {
            return paymentProvider;
        }

        public Map<String, String> getCredentials() {
            return credentials;
        }

        public String getServiceName() {
            return serviceName;
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

        public long getCorporatePrepaidCreditCardSurchargeAmount() {
            return corporatePrepaidCreditCardSurchargeAmount;
        }

        public long getCorporatePrepaidDebitCardSurchargeAmount() {
            return corporatePrepaidDebitCardSurchargeAmount;
        }

        public Map<EmailNotificationType, TestEmailNotification> getEmailNotifications() {
            return emailNotifications;
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

        public TestAccount withCredentials(Map<String, String> credentials) {
            this.credentials = credentials;
            return this;
        }

        public TestAccount withServiceName(String serviceName) {
            this.serviceName = serviceName;
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

        public TestAccount withType(GatewayAccountEntity.Type type) {
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


        public TestAccount withCorporatePrepaidCreditCardSurchargeAmount(long corporatePrepaidCreditCardSurchargeAmount) {
            this.corporatePrepaidCreditCardSurchargeAmount = corporatePrepaidCreditCardSurchargeAmount;
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

        public TestAccount insert() {
            databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                    .withAccountId(String.valueOf(accountId))
                    .withPaymentGateway(paymentProvider)
                    .withCredentials(credentials)
                    .withServiceName(serviceName)
                    .withProviderUrlType(type)
                    .withDescription(description)
                    .withAnalyticsId(analyticsId)
                    .withEmailCollectionMode(emailCollectionMode)
                    .withCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount)
                    .withCorporateDebitCardSurchargeAmount(corporateDebitCardSurchargeAmount)
                    .withCorporatePrepaidCreditCardSurchargeAmount(corporatePrepaidCreditCardSurchargeAmount)
                    .withCorporatePrepaidDebitCardSurchargeAmount(corporatePrepaidDebitCardSurchargeAmount)
                    .withIntegrationVersion3ds(integrationVersion3ds)
                    .build());
            for (TestCardType cardType : cardTypes) {
                databaseTestHelper.addAcceptedCardType(this.getAccountId(), cardType.getId());
            }
            emailNotifications.forEach((type, notification) -> databaseTestHelper.addEmailNotification(this.getAccountId(), notification.getTemplate(), notification.isEnabled(), type));

            return this;
        }
    }

    public class TestCharge {
        Long chargeId = RandomUtils.nextLong();
        private String description = "Test description";
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

        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

        TestAccount testAccount;
        TestCardDetails cardDetails;
        WalletType walletType;
        ParityCheckStatus parityCheckStatus;

        public TestCardDetails getCardDetails() {
            return cardDetails;
        }

        public TestCharge withTestAccount(TestAccount account) {
            this.testAccount = account;
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

        public TestCharge withCreatedDate(ZonedDateTime createdDate) {
            this.createdDate = createdDate;
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

        public TestCharge insert() {
            if (testAccount == null)
                throw new IllegalStateException("Test Account must be provided.");

            databaseTestHelper.addCharge(anAddChargeParams()
                    .withChargeId(chargeId)
                    .withExternalChargeId(externalChargeId)
                    .withGatewayAccountId(String.valueOf(testAccount.getAccountId()))
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
                    .build());

            if (cardDetails != null) {
                cardDetails.update();
            }
            return this;
        }

        public TestCharge withCorporateCardSurcarge(Long corporateCardSurcharge) {
            this.corporateCardSurcharge = corporateCardSurcharge;
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

        public ZonedDateTime getCreatedDate() {
            return createdDate;
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

        public Long getCorporateCardSurcharge() {
            return corporateCardSurcharge;
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
        String reference;
        long amount = 101L;
        RefundStatus status = CREATED;
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        TestCharge testCharge;
        String submittedByUserExternalId;
        String gatewayTransactionId;

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

        public TestRefund withReference(String reference) {
            this.reference = reference;
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

        public TestRefund withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        public TestRefund insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test charge must be provided.");
            id = databaseTestHelper.addRefund(externalRefundId, reference, amount, status, testCharge.getChargeId(), gatewayTransactionId, createdDate, submittedByUserExternalId);
            return this;
        }


        public long getId() {
            return id;
        }

        public String getExternalRefundId() {
            return externalRefundId;
        }

        public String getReference() {
            return reference;
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
    }

    public class TestFee {
        String externalId = RandomIdGenerator.newId();
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        TestCharge testCharge;
        private long feeDue = 100L;
        private long feeCollected = 100L;
        private String gatewayTransactionId = "transaction_id";

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

        public TestFee insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test charge must be provided.");
            databaseTestHelper.addFee(externalId, testCharge.getChargeId(), feeDue, feeCollected, createdDate, gatewayTransactionId);
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
        SupportedType type = SupportedType.CREDIT;
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

        public TestCardType withType(SupportedType type) {
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

        public SupportedType getType() {
            return type;
        }

        public void setAcceptedType(SupportedType supportedType) {
            this.type = supportedType;
        }

        public String getBrand() {
            return brand;
        }

        public boolean getRequires3DS() {
            return requires3DS;
        }
    }
}
