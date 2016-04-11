package uk.gov.pay.connector.it.dao;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;

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

    public class TestAccount {
        long accountId = 564532435L;
        String paymentProvider = "test_provider";

        public long getAccountId() {
            return accountId;
        }

        public String getPaymentProvider() {
            return paymentProvider;
        }

        public TestAccount insert() {
            databaseTestHelper.addGatewayAccount(String.valueOf(accountId), paymentProvider);
            return this;
        }
    }

    public class TestCharge {
        Long chargeId = 977L;
        String externalChargeId = "charge977";
        long amount = 101L;
        ChargeStatus chargeStatus = ChargeStatus.CREATED;
        String returnUrl = "http://service.com/success-page";
        String transactionId;
        String reference = "Test reference";
        ZonedDateTime createdDate = ZonedDateTime.now();

        TestAccount testAccount;

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
            databaseTestHelper.addCharge(chargeId, externalChargeId, String.valueOf(testAccount.getAccountId()), amount, chargeStatus, returnUrl, transactionId, reference, createdDate);
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

        public ZonedDateTime getCreatedDate() {
            return createdDate;
        }

        public TestAccount getAccount() {
            return testAccount;
        }
    }
}
