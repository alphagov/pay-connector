package uk.gov.pay.connector.it.dao;

import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.RefundStatus.CREATED;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import org.apache.commons.lang3.RandomUtils;
import uk.gov.pay.connector.model.domain.CardTypeEntity.Type;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.spike.TransactionEntity.TransactionOperation;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;

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

    public  TestChargeNew aTestChargeNew() {
        return new TestChargeNew();
    }
    public TestRefundNew aTestRefundNew() {
        return new TestRefundNew();
    }


    public TestPaymentRequest aTestPaymentRequest() {
        return new TestPaymentRequest();
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

    public TestCardDetails aTestCardDetails() {
        return new TestCardDetails();
    }

    public TestCardType aMastercardCreditCardType() {
        return new TestCardType().withLabel("MasterCard").withType(Type.CREDIT).withBrand("mastercard");
    }

    public TestCardType aMastercardDebitCardType() {
        return new TestCardType().withLabel("MasterCard").withType(Type.DEBIT).withBrand("mastercard");
    }

    public TestCardType aVisaCreditCardType() {
        return new TestCardType().withLabel("Visa").withType(Type.CREDIT).withBrand("visa");
    }

    public TestCardType aVisaDebitCardType() {
        return new TestCardType().withLabel("Visa").withType(Type.DEBIT).withBrand("visa");
    }

    public TestEmailNotification anEmailNotification() {
        return new TestEmailNotification();
    }

    public TestCardDetails validTestCardDetails() {
        return new TestCardDetails();
    }

    public TestCardType aMaestroDebitCardType() {
        return new TestCardType().withLabel("Maestro").withType(Type.DEBIT).withBrand("maestro").withRequires3ds(true);
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
        private String lastDigitsCardNumber = "1234";
        private String cardHolderName = "Mr. Pay McPayment";
        private String expiryDate = "02/17";
        private TestAddress billingAddress = new TestAddress();
        private Long chargeId;
        private String cardBrand = "VISA";

        public TestCardDetails withLastDigitsOfCardNumber(String lastDigitsCardNumber) {
            this.lastDigitsCardNumber = lastDigitsCardNumber;
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

        public String getLastDigitsCardNumber() {
            return lastDigitsCardNumber;
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
            databaseTestHelper.updateChargeCardDetailsNew(chargeId, cardBrand, lastDigitsCardNumber, cardHolderName, expiryDate, billingAddress.getLine1(), billingAddress.getLine2(), billingAddress.getPostcode(), billingAddress.getCity(), billingAddress.getCounty(), billingAddress.getCountry());
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
        private GatewayAccountEntity.Type type = TEST;
        private List<TestCardType> cardTypes = new ArrayList<>();


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

        public TestAccount withAccountId(long accountId) {
            this.accountId = accountId;
            return this;
        }

        public TestAccount withCardTypes(List<TestCardType> cardTypes) {
            this.cardTypes = cardTypes;
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

        public TestAccount withType(GatewayAccountEntity.Type type) {
            this.type = type;
            return this;
        }

        public TestAccount insert() {
            databaseTestHelper.addGatewayAccount(
                    String.valueOf(accountId),
                    paymentProvider,
                    credentials,
                    serviceName,
                    type,
                    description,
                    analyticsId);
            for (TestCardType cardType : cardTypes) {
                databaseTestHelper.addAcceptedCardType(this.getAccountId(), cardType.getId());
            }
            return this;
        }
    }

    public class TestPaymentRequest {
        long id = RandomUtils.nextLong(1, 99999);
        long amount = 101L;
        long gatewayAccountId = 102L;
        String reference = "reference";
        String description = "Test description";
        String returnUrl = "return_url";
        String externalId = "external_id";
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

        public long getId() {
            return id;
        }

        public TestPaymentRequest withGatewayAccountId(Long gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public TestPaymentRequest insert() {
            databaseTestHelper.addPaymentRequest(id, reference, description, amount, gatewayAccountId,returnUrl, externalId, createdDate, 0);
            return this;
        }

        public long getAmount() {
            return amount;
        }

        public long getGatewayAccountId() {
            return gatewayAccountId;
        }

        public String getReference() {
            return reference;
        }

        public String getDescription() {
            return description;
        }

        public String getReturnUrl() {
            return returnUrl;
        }

        public String getExternalId() {
            return externalId;
        }

        public TestPaymentRequest withDescription(String description) {
            this.description = description;
            return this;
        }

        public ZonedDateTime getCreatedDate() {
            return createdDate;
        }
        public TestPaymentRequest withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public TestPaymentRequest withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }
    }
    private abstract class TestTransaction {
        Long transactionId = RandomUtils.nextLong(1, 99999);
        TransactionStatus transactionStatus = TransactionStatus.CREATED;
        long amount = 101L;
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        TransactionOperation operation = TransactionOperation.CHARGE;
        TestPaymentRequest testPaymentRequest = new TestPaymentRequest();

        public Long getTransactionId() {
            return transactionId;
        }

        public TransactionStatus getTransactionStatus() {
            return transactionStatus;
        }

        public long getAmount() {
            return amount;
        }

        public ZonedDateTime getCreatedDate() {
            return createdDate;
        }

        public TransactionOperation getOperation() {
            return operation;
        }

        public TestPaymentRequest getPaymentRequest() {
            return testPaymentRequest;
        }
    }

    public class TestChargeNew extends TestTransaction {
        String email = "aaa@bbb.com";
        String gatewayTransactionId = "gateway_transaction_id";
        TestCardDetails cardDetails;

        public TestChargeNew withTestPaymentRequest(TestPaymentRequest testPaymentRequest) {
            this.testPaymentRequest = testPaymentRequest;
            return this;
        }

        public TestChargeNew insert() {
            if (testPaymentRequest == null)
                throw new IllegalStateException("Test PaymentRequest must be provided.");

            databaseTestHelper.addChargeNew(transactionId, testPaymentRequest.id, amount, transactionStatus,
                gatewayTransactionId, null, createdDate, 0, email);

            if (cardDetails != null) {
                cardDetails.update();
            }
            return this;
        }

        public String getReturnUrl() {
            return this.testPaymentRequest.returnUrl;
        }
        public String getDescription() {
            return this.testPaymentRequest.description;
        }

        public String getReference() {
            return this.testPaymentRequest.reference;
        }
        public String getEmail() {
            return email;
        }

        public String getGatewayTransactionId() {
            return gatewayTransactionId;
        }

        public TestChargeNew withGatewayTransactionId(String chargeId) {
            this.gatewayTransactionId = chargeId;
            return this;
        }
        public TestChargeNew withTransactionId(long chargeId) {
            this.transactionId = chargeId;
            return this;
        }

        public TestChargeNew withCreatedDate(ZonedDateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public TestChargeNew withEmail(String email) {
            this.email = email;
            return this;
        }

        public TestChargeNew withTransactionStatus(TransactionStatus transactionStatus) {
            this.transactionStatus = transactionStatus;
            return this;
        }

    }
    public class TestCharge {
        Long chargeId = RandomUtils.nextLong(1, 99999);
        String email = "alice.111@mail.fake";
        String externalChargeId = RandomIdGenerator.newId();
        long amount = 101L;
        ChargeStatus chargeStatus = ChargeStatus.CREATED;
        String returnUrl = "http://service.com/success-page";
        String transactionId;
        String reference = "Test reference";

        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

        TestAccount testAccount;
        TestCardDetails cardDetails;

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

        public TestCharge withReference(String reference) {
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

        public TestCharge insert() {
            if (testAccount == null)
                throw new IllegalStateException("Test Account must be provided.");

            databaseTestHelper.addCharge(chargeId, externalChargeId, String.valueOf(testAccount.getAccountId()), amount, chargeStatus, returnUrl, transactionId, reference, createdDate, email);

            if (cardDetails != null) {
                cardDetails.update();
            }
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

        public String getReference() {
            return reference;
        }

        public String getEmail() {
            return email;
        }

        public ZonedDateTime getCreatedDate() {
            return createdDate;
        }

        public TestCharge withCardDetails(TestCardDetails testCardDetails) {
            cardDetails = testCardDetails;
            return this;
        }
    }

    public class TestToken {
        TestCharge testCharge;
        String secureRedirectToken = "3c9fee80-977a-4da5-a003-4872a8cf95b6";

        public TestToken withTestToken(TestCharge testCharge) {
            this.testCharge = testCharge;
            return this;
        }

        public TestToken insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test Charge must be provided.");
            databaseTestHelper.addToken(testCharge.getChargeId(), secureRedirectToken);
            return this;
        }

        public String getSecureRedirectToken() {
            return secureRedirectToken;
        }
    }

    public class TestRefundNew extends TestTransaction {
        private String smartpayPspReference = "smartpayPspRe1ference";
        private String epdqPayId = "epdq1";
        private String epdqPayIdSub = "epdq2";
        private String refundedBy = "refundedby1";
        private String externalId = "externalId";

        public String getSmartpayPspReference() {
            return smartpayPspReference;
        }

        public TestRefundNew withTestPaymentRequest(TestPaymentRequest testPaymentRequest) {
            this.testPaymentRequest = testPaymentRequest;
            return this;
        }

        public TestRefundNew withSmartpayPspReference(String smartpayPspReference) {
            this.smartpayPspReference = smartpayPspReference;
            return this;
        }

        public String getExternalId() {
            return externalId;
        }

        public TestRefundNew withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public String getEpdqPayId() {
            return epdqPayId;
        }

        public TestRefundNew withEpdqPayId(String epdqPayId) {
            this.epdqPayId = epdqPayId;
            return this;
        }

        public String getEpdqPayIdSub() {
            return epdqPayIdSub;
        }

        public TestRefundNew withEpdqPayIdSub(String epdqPayIdSub) {
            this.epdqPayIdSub = epdqPayIdSub;
            return this;
        }

        public String getRefundedBy() {
            return refundedBy;
        }

        public TestRefundNew withRefundedBy(String refundedBy) {
            this.refundedBy = refundedBy;
            return this;
        }


        public TestRefundNew insert() {
            if (testPaymentRequest == null)
                throw new IllegalStateException("Test payment request be provided.");
            databaseTestHelper.addRefundNew(transactionId, testPaymentRequest.id, amount, transactionStatus, createdDate, externalId, smartpayPspReference, epdqPayId, epdqPayIdSub, refundedBy, 0);
            return this;
        }
    }
    public class TestRefund {
        Long id = RandomUtils.nextLong(1, 99999);
        String externalRefundId = RandomIdGenerator.newId();
        String reference;
        long amount = 101L;
        RefundStatus status = CREATED;
        ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        TestCharge testCharge;
        String submittedByUserExternalId;

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

        public TestRefund insert() {
            if (testCharge == null)
                throw new IllegalStateException("Test charge must be provided.");
            databaseTestHelper.addRefund(id, externalRefundId, reference, amount, status.toString(), testCharge.getChargeId(), createdDate, submittedByUserExternalId);
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

        public String getSubmittedByUserExternalId() {
            return submittedByUserExternalId;
        }
    }

    public class TestEmailNotification {

        TestAccount testAccount;
        String template = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";

        public TestEmailNotification withTestAccount(TestAccount testAccount) {
            this.testAccount = testAccount;
            return this;
        }

        public TestEmailNotification insert() {
            if (testAccount == null)
                throw new IllegalStateException("Test Account must be provided.");
            databaseTestHelper.addToken(testAccount.getAccountId(), template);
            return this;
        }

        public TestAccount getTestAccount() {
            return testAccount;
        }
    }

    public class TestCardType {
        UUID id = UUID.randomUUID();
        String label = "Mastercard";
        Type type = Type.CREDIT;
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

        public TestCardType withType(Type type) {
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

        public TestCardType insert() {
            databaseTestHelper.addCardType(id, label, type.toString(), brand, requires3DS);
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

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public String getBrand() {
            return brand;
        }

        public boolean getRequires3DS() {
            return requires3DS;
        }
    }
}
