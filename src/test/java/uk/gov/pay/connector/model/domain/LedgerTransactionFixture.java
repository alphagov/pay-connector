package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.card.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.card.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.card.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.Address;
import uk.gov.pay.connector.client.ledger.model.AuthorisationSummary;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.SettlementSummary;
import uk.gov.pay.connector.client.ledger.model.ThreeDSecure;
import uk.gov.pay.connector.client.ledger.model.TransactionState;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getTotalAmountFor;
import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;

public class LedgerTransactionFixture {
    private String status = "created";
    private String externalId;
    private Long amount;
    private String description;
    private String reference;
    private String email;
    private String gatewayTransactionId;
    private String credentialExternalId;
    private String returnUrl;
    private SupportedLanguage language;
    private CardDetails cardDetails;
    private ZonedDateTime createdDate = ZonedDateTime.now();
    private boolean live;
    private Long gatewayAccountId;
    private String paymentProvider = PaymentGatewayName.SANDBOX.getName();
    private String walletType;
    private Long netAmount;
    private Source source;
    private boolean delayedCapture;
    private Long corporateCardSurcharge;
    private Long fee;
    private boolean moto;
    private Long totalAmount;
    private ZonedDateTime captureSubmittedDate;
    private ZonedDateTime capturedDate;
    private ChargeResponse.RefundSummary refundSummary;
    private String parentTransactionId;
    private String refundedBy;
    private String refundedByUserEmail;
    private AuthorisationSummary authorisationSummary;
    private String serviceId;
    private boolean disputed;
    private AuthorisationMode authorisationMode = AuthorisationMode.WEB;
    private String agreementId;

    public static LedgerTransactionFixture aValidLedgerTransaction() {
        return new LedgerTransactionFixture();
    }

    public static LedgerTransactionFixture from(ChargeEntity chargeEntity, List<RefundEntity> refundsList) {
        LedgerTransactionFixture ledgerTransactionFixture =
                aValidLedgerTransaction()
                        .withCreatedDate(chargeEntity.getCreatedDate().atZone(UTC))
                        .withStatus(ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().getStatusV2())
                        .withExternalId(chargeEntity.getExternalId())
                        .withAmount(chargeEntity.getAmount())
                        .withDescription(chargeEntity.getDescription())
                        .withReference(chargeEntity.getReference().toString())
                        .withLanguage(chargeEntity.getLanguage())
                        .withEmail(chargeEntity.getEmail())
                        .withReturnUrl(chargeEntity.getReturnUrl())
                        .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                        .withCredentialExternalId(chargeEntity.getGatewayAccountCredentialsEntity().getExternalId())
                        .withDelayedCapture(chargeEntity.isDelayedCapture())
                        .withSource(chargeEntity.getSource())
                        .withMoto(chargeEntity.isMoto())
                        .withFee(chargeEntity.getFeeAmount().orElse(null))
                        .withCorporateCardSurcharge(chargeEntity.getCorporateSurcharge().orElse(null))
                        .withWalletType(chargeEntity.getWalletType())
                        .withTotalAmount(getTotalAmountFor(chargeEntity))
                        .withNetAmount(chargeEntity.getNetAmount().orElse(null));

        ledgerTransactionFixture.withCreatedDate(getEventDate(chargeEntity.getEvents(), List.of(CREATED, PAYMENT_NOTIFICATION_CREATED)));
        if (chargeEntity.getGatewayAccount() != null) {
            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
            ledgerTransactionFixture.withPaymentProvider(chargeEntity.getPaymentProvider());
            ledgerTransactionFixture.withGatewayAccountId(gatewayAccount.getId());
            ledgerTransactionFixture.isLive(gatewayAccount.isLive());
        }
        if (nonNull(chargeEntity.getChargeCardDetails())) {
            CardDetailsEntity chargeEntityCardDetails = chargeEntity.getChargeCardDetails().getCardDetails().get();
            Address ledgerAddress = chargeEntityCardDetails.getBillingAddress().map(addressEntity -> {
                return new Address(addressEntity.getLine1(),
                        addressEntity.getLine2(),
                        addressEntity.getPostcode(),
                        addressEntity.getCity(),
                        addressEntity.getCounty(),
                        addressEntity.getCountry());
            }).orElse(null);

            CardDetails cardDetails = new CardDetails(chargeEntityCardDetails.getCardHolderName(),
                    ledgerAddress,
                    chargeEntityCardDetails.getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null),
                    ofNullable(chargeEntityCardDetails.getLastDigitsCardNumber()).map(LastDigitsCardNumber::toString).orElse(null),
                    ofNullable(chargeEntityCardDetails.getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).orElse(null),
                    ofNullable(chargeEntityCardDetails.getExpiryDate()).map(CardExpiryDate::toString).orElse(null),
                    ofNullable(chargeEntityCardDetails.getCardType()).map(cardType -> cardType.toString().toLowerCase()).orElse(null)
            );

            ledgerTransactionFixture.withCardDetails(cardDetails);
        }

        ledgerTransactionFixture.withCapturedDate(getEventDate(chargeEntity.getEvents(), List.of(CAPTURED)));
        ledgerTransactionFixture.withCaptureSubmittedDate(getEventDate(chargeEntity.getEvents(), List.of(CAPTURE_SUBMITTED)));

        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        ExternalChargeRefundAvailability refundAvailability;
        if (refundsList != null) {
            refundAvailability = new DefaultExternalRefundAvailabilityCalculator()
                    .calculate(Charge.from(chargeEntity), refundsList.stream().map(Refund::from).collect(Collectors.toList()));
        } else {
            refundAvailability = new DefaultExternalRefundAvailabilityCalculator()
                    .calculate(Charge.from(chargeEntity), List.of());
        }
        refundSummary.setStatus(refundAvailability.getStatus());
        ledgerTransactionFixture.withRefundSummary(refundSummary);

        if (chargeEntity.getChargeCardDetails().get3dsRequiredDetails() != null) {
            AuthorisationSummary authorisationSummary = new AuthorisationSummary();
            ThreeDSecure threeDSecure = new ThreeDSecure();
            threeDSecure.setRequired(true);
            threeDSecure.setVersion(chargeEntity.getChargeCardDetails().get3dsRequiredDetails().getThreeDsVersion());
            authorisationSummary.setThreeDSecure(threeDSecure);
            ledgerTransactionFixture.withAuthorisationSummary(authorisationSummary);
        }

        ledgerTransactionFixture.withAuthorisationMode(chargeEntity.getAuthorisationMode() == null ?
                AuthorisationMode.WEB : chargeEntity.getAuthorisationMode());

        return ledgerTransactionFixture;
    }

    public static LedgerTransactionFixture from(Long gatewayAccountId, RefundEntity refundEntity) {
        LedgerTransactionFixture ledgerTransactionFixture =
                aValidLedgerTransaction()
                        .withGatewayAccountId(gatewayAccountId)
                        .withAmount(refundEntity.getAmount())
                        .withStatus(refundEntity.getStatus().toExternal().getStatus())
                        .withCreatedDate(refundEntity.getCreatedDate())
                        .withExternalId(refundEntity.getExternalId())
                        .withGatewayTransactionId(refundEntity.getGatewayTransactionId())
                        .withParentTransactionId(refundEntity.getChargeExternalId())
                        .withRefundedByUserEmail(refundEntity.getUserEmail())
                        .withRefundedBy(refundEntity.getUserExternalId());

        return ledgerTransactionFixture;
    }

    private static ZonedDateTime getEventDate(List<ChargeEventEntity> chargeEventEntities, List<ChargeStatus> status) {
        return ofNullable(chargeEventEntities).flatMap(entities -> entities.stream()
                        .filter(chargeEvent -> status.contains(chargeEvent.getStatus()))
                        .findFirst()
                        .map(ChargeEventEntity::getUpdated))
                .orElse(null);
    }

    public LedgerTransaction build() {
        var ledgerTransaction = new LedgerTransaction();
        ledgerTransaction.setState(new TransactionState(status));
        ledgerTransaction.setTransactionId(externalId);
        ledgerTransaction.setServiceId(serviceId);
        ledgerTransaction.setAmount(amount);
        ledgerTransaction.setDescription(description);
        ledgerTransaction.setReference(reference);
        ledgerTransaction.setEmail(email);
        ledgerTransaction.setGatewayTransactionId(gatewayTransactionId);
        ledgerTransaction.setCredentialExternalId(credentialExternalId);
        ledgerTransaction.setReturnUrl(returnUrl);
        ledgerTransaction.setLanguage(language);
        ledgerTransaction.setCardDetails(cardDetails);
        ledgerTransaction.setCreatedDate(ofNullable(createdDate)
                .map(ISO_INSTANT_MILLISECOND_PRECISION::format)
                .orElse(null));

        ledgerTransaction.setLive(live);
        ledgerTransaction.setPaymentProvider(paymentProvider);

        if (gatewayAccountId != null) {
            ledgerTransaction.setGatewayAccountId(gatewayAccountId.toString());
        }

        ledgerTransaction.setSource(source);
        ledgerTransaction.setMoto(moto);
        ledgerTransaction.setDelayedCapture(delayedCapture);
        ledgerTransaction.setFee(fee);
        ledgerTransaction.setCorporateCardSurcharge(corporateCardSurcharge);
        ledgerTransaction.setNetAmount(netAmount);
        ledgerTransaction.setTotalAmount(totalAmount);
        ledgerTransaction.setWalletType(walletType);

        SettlementSummary settlementSummary = new SettlementSummary();
        if (capturedDate != null) {
            settlementSummary.setCapturedDate(DateTimeUtils.toUTCDateString(capturedDate));
        }
        settlementSummary.setCaptureSubmitTime(captureSubmittedDate);
        ledgerTransaction.setSettlementSummary(settlementSummary);
        ledgerTransaction.setRefundSummary(refundSummary);

        ledgerTransaction.setParentTransactionId(parentTransactionId);
        ledgerTransaction.setRefundedBy(refundedBy);
        ledgerTransaction.setRefundedByUserEmail(refundedByUserEmail);

        ledgerTransaction.setAuthorisationSummary(authorisationSummary);
        ledgerTransaction.setDisputed(disputed);
        ledgerTransaction.setAuthorisationMode(authorisationMode);
        ledgerTransaction.setAgreementId(agreementId);

        return ledgerTransaction;
    }

    public LedgerTransactionFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
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

    public LedgerTransactionFixture withCredentialExternalId(String credentialExternalId) {
        this.credentialExternalId = credentialExternalId;
        return this;
    }

    public LedgerTransactionFixture withLanguage(SupportedLanguage language) {
        this.language = language;
        return this;
    }

    public LedgerTransactionFixture withCardDetails(CardDetails cardDetails) {
        this.cardDetails = cardDetails;
        return this;
    }

    public LedgerTransactionFixture isLive(boolean live) {
        this.live = live;
        return this;
    }

    public LedgerTransactionFixture withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public LedgerTransactionFixture withPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        return this;
    }

    public LedgerTransactionFixture withNetAmount(Long netAmount) {
        this.netAmount = netAmount;
        return this;
    }

    public LedgerTransactionFixture withWalletType(WalletType walletType) {
        this.walletType = walletType != null ? walletType.toString() : null;
        return this;
    }

    public LedgerTransactionFixture withCorporateCardSurcharge(Long corporateCardSurcharge) {
        this.corporateCardSurcharge = corporateCardSurcharge;
        return this;
    }

    public LedgerTransactionFixture withFee(Long fee) {
        this.fee = fee;
        return this;
    }

    public LedgerTransactionFixture withMoto(boolean moto) {
        this.moto = moto;
        return this;
    }

    public LedgerTransactionFixture withSource(Source source) {
        this.source = source;
        return this;
    }

    public LedgerTransactionFixture withDelayedCapture(boolean delayedCapture) {
        this.delayedCapture = delayedCapture;
        return this;
    }

    public LedgerTransactionFixture withTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public LedgerTransactionFixture withCaptureSubmittedDate(ZonedDateTime captureSubmittedDate) {
        this.captureSubmittedDate = captureSubmittedDate;
        return this;
    }

    public LedgerTransactionFixture withCapturedDate(ZonedDateTime capturedDate) {
        this.capturedDate = capturedDate;
        return this;
    }

    public LedgerTransactionFixture withRefundSummary(ChargeResponse.RefundSummary refundSummary) {
        this.refundSummary = refundSummary;
        return this;
    }

    public LedgerTransactionFixture withParentTransactionId(String chargeExternalId) {
        this.parentTransactionId = chargeExternalId;
        return this;
    }

    public LedgerTransactionFixture withRefundedBy(String userExternalId) {
        this.refundedBy = userExternalId;
        return this;
    }

    public LedgerTransactionFixture withRefundedByUserEmail(String userEmail) {
        this.refundedByUserEmail = userEmail;
        return this;
    }

    public LedgerTransactionFixture withAuthorisationSummary(AuthorisationSummary authorisationSummary) {
        this.authorisationSummary = authorisationSummary;
        return this;
    }

    public LedgerTransactionFixture withServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }
    
    public LedgerTransactionFixture withDisputed(boolean disputed) {
        this.disputed = disputed;
        return this;
    }

    public LedgerTransactionFixture withAuthorisationMode(AuthorisationMode authorisationMode) {
        this.authorisationMode = authorisationMode;
        return this;
    }
    
    public LedgerTransactionFixture withAgreementId(String agreementId) {
        this.agreementId = agreementId;
        return this;
    }
}
