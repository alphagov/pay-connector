package uk.gov.pay.connector.model.domain;

import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.paritycheck.TransactionState;

public class LedgerTransactionFixture {
    private String status = "created";
    private String externalId;
    private Long amount;
    private String description;
    private String reference;
    private String email;
    private String gatewayTransactionId;
    private String returnUrl;
    private SupportedLanguage language;

    public static LedgerTransactionFixture aValidLedgerTransaction() {
        return new LedgerTransactionFixture();
    }

    public static LedgerTransactionFixture from(ChargeEntity chargeEntity) {
        LedgerTransactionFixture ledgerTransactionFixture = aValidLedgerTransaction()
                .withStatus(ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().getStatusV2())
                .withExternalId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .withDescription(chargeEntity.getDescription())
                .withReference(chargeEntity.getReference().toString())
                .withLanguage(chargeEntity.getLanguage())
                .withEmail(chargeEntity.getEmail())
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId());

        return ledgerTransactionFixture;
    }

    public LedgerTransaction build() {
        var ledgerTransaction = new LedgerTransaction();
        ledgerTransaction.setState(new TransactionState(status));
        ledgerTransaction.setTransactionId(externalId);
        ledgerTransaction.setAmount(amount);
        ledgerTransaction.setDescription(description);
        ledgerTransaction.setReference(reference);
        ledgerTransaction.setEmail(email);
        ledgerTransaction.setGatewayTransactionId(gatewayTransactionId);
        ledgerTransaction.setReturnUrl(returnUrl);
        ledgerTransaction.setLanguage(language);
        return ledgerTransaction;
    }

    public LedgerTransactionFixture withStatus(String status) {
        this.status = status;
        return this;
    }

    public LedgerTransactionFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public LedgerTransactionFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public LedgerTransactionFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public LedgerTransactionFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public LedgerTransactionFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public LedgerTransactionFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public LedgerTransactionFixture withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public LedgerTransactionFixture withLanguage(SupportedLanguage language) {
        this.language = language;
        return this;
    }
}
