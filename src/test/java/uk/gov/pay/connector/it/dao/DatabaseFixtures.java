package uk.gov.pay.connector.it.dao;

import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.util.test.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;
import java.util.HashMap;

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

    public TestToken aTestToken() {
        return new TestToken();
    }

    public class TestAccount {
        long accountId = 564532435L;
        String paymentProvider = "test_provider";
        String serviceName = "service_name";

        public long getAccountId() {
            return accountId;
        }

        public String getPaymentProvider() {
            return paymentProvider;
        }

        public String getServiceName() {
            return serviceName;
        }

        public TestAccount withAccountId(long accountId){
            this.accountId = accountId;
            return this;
        }

        public TestAccount insert() {
            databaseTestHelper.addGatewayAccount(String.valueOf(accountId), paymentProvider, new HashMap<String, String>(), serviceName);
            return this;
        }
    }

    public class TestCharge {
        Long chargeId = RandomUtils.nextLong(1, 99999);
        String externalChargeId = "charge_" + chargeId;
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

        public TestCharge getTestCharge() {
            return testCharge;
        }

        public String getSecureRedirectToken() {
            return secureRedirectToken;
        }
    }
}
