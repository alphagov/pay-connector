package uk.gov.pay.connector.charge.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.SupportedLanguageJpaConverter;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.util.JsonNodeConverter;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.WalletType;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions.isValidTransition;

@Entity
@Table(name = "charges")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "charges_charge_id_seq",
        sequenceName = "charges_charge_id_seq", allocationSize = 1)
public class ChargeEntity extends AbstractVersionedEntity {
    private static final Logger logger = LoggerFactory.getLogger(ChargeEntity.class);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charges_charge_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "status")
    private String status;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "return_url")
    private String returnUrl;

    @Column(name = "email")
    private String email;

    @Column(name = "corporate_surcharge")
    private Long corporateSurcharge;

    @Embedded
    private CardDetailsEntity cardDetails;

    @Embedded
    private Auth3dsDetailsEntity auth3dsDetails;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @OneToOne(mappedBy = "chargeEntity", fetch = FetchType.EAGER)
    private FeeEntity fee;

    @OneToMany(mappedBy = "chargeEntity", fetch = FetchType.EAGER)
    @OrderBy("createdDate")
    private List<RefundEntity> refunds = new ArrayList<>();

    @OneToMany(mappedBy = "chargeEntity")
    @OrderBy("updated DESC")
    private List<ChargeEventEntity> events = new ArrayList<>();

    @Column(name = "description")
    private String description;

    @Column(name = "reference")
    @Convert(converter = ServicePaymentReferenceConverter.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private ServicePaymentReference reference;

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @Column(name = "language", nullable = false)
    @Convert(converter = SupportedLanguageJpaConverter.class)
    private SupportedLanguage language;

    @Column(name = "delayed_capture")
    private boolean delayedCapture;

    @Column(name = "wallet")
    @Enumerated(EnumType.STRING)
    private WalletType walletType;

    @Column(name = "external_metadata", columnDefinition = "jsonb")
    @Convert(converter = JsonNodeConverter.class)
    private JsonNode externalMetadata;

    public ChargeEntity() {
        //for jpa
    }

    public ChargeEntity(Long amount, String returnUrl, String description, ServicePaymentReference reference,
                        GatewayAccountEntity gatewayAccount, String email, SupportedLanguage language,
                        boolean delayedCapture, JsonNode externalMetadata) {
        this(amount, CREATED, returnUrl, description, reference, gatewayAccount, email, ZonedDateTime.now(ZoneId.of("UTC")), language, delayedCapture, externalMetadata);
    }

    // Only the ChargeEntityFixture should directly call this constructor
    public ChargeEntity(Long amount, ChargeStatus status, String returnUrl, String description, ServicePaymentReference reference,
                        GatewayAccountEntity gatewayAccount, String email, ZonedDateTime createdDate, SupportedLanguage language,
                        boolean delayedCapture, JsonNode externalMetadata) {
        this.amount = amount;
        this.status = status.getValue();
        this.returnUrl = returnUrl;
        this.description = description;
        this.reference = reference;
        this.gatewayAccount = gatewayAccount;
        this.createdDate = createdDate;
        this.externalId = RandomIdGenerator.newId();
        this.email = email;
        this.language = language;
        this.delayedCapture = delayedCapture;
        this.externalMetadata = externalMetadata;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public String getDescription() {
        return description;
    }

    public ServicePaymentReference getReference() {
        return reference;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public List<RefundEntity> getRefunds() {
        return refunds;
    }

    public List<ChargeEventEntity> getEvents() {
        return events;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getEmail() {
        return email;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public JsonNode getExternalMetadata() {
        return externalMetadata;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setStatus(ChargeStatus targetStatus) {
        if (isValidTransition(fromString(this.status), targetStatus)) {
            logger.info("Changing charge status for externalId [{}] [{}]->[{}]",
                    externalId, this.status, targetStatus.getValue());

            this.status = targetStatus.getValue();
        } else {
            logger.warn("Charge with state {} cannot proceed to {} [charge_external_id={}, charge_status={}]",
                    this.status, targetStatus, this.externalId, targetStatus);
            throw new InvalidStateTransitionException(this.status, targetStatus.getValue());
        }
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
    }

    public WalletType getWalletType() {
        return walletType;
    }

    public void setWalletType(WalletType walletType) {
        this.walletType = walletType;
    }

    public boolean hasExternalStatus(ExternalChargeState... state) {
        return Arrays.stream(state).anyMatch(s -> fromString(getStatus()).toExternal().equals(s));
    }

    public ZonedDateTime getCaptureSubmitTime() {
        return this.events.stream()
                .filter(e -> e.getStatus().equals(CAPTURE_SUBMITTED))
                .findFirst()
                .map(ChargeEventEntity::getUpdated)
                .orElse(null);
    }

    public ZonedDateTime getCapturedTime() {
        return this.events.stream()
                .filter(e -> e.getStatus().equals(CAPTURED))
                .findFirst()
                // use updated for old CAPTURED events that do not have a generated time recorded
                .map(e -> e.getGatewayEventDate().orElse(e.getUpdated()))
                .orElse(null);
    }

    public CardDetailsEntity getCardDetails() {
        return cardDetails;
    }

    public void setCardDetails(CardDetailsEntity cardDetailsEntity) {
        this.cardDetails = cardDetailsEntity;
    }

    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.valueFrom(gatewayAccount.getGatewayName());
    }

    public Auth3dsDetailsEntity get3dsDetails() {
        return auth3dsDetails;
    }

    public void set3dsDetails(Auth3dsDetailsEntity auth3dsDetails) {
        this.auth3dsDetails = auth3dsDetails;
    }

    public SupportedLanguage getLanguage() {
        return language;
    }

    public void setLanguage(SupportedLanguage language) {
        this.language = language;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public void setDelayedCapture(boolean delayedCapture) {
        this.delayedCapture = delayedCapture;
    }

    public Optional<Long> getCorporateSurcharge() {
        return Optional.ofNullable(corporateSurcharge);
    }

    public void setCorporateSurcharge(Long corporateSurcharge) {
        this.corporateSurcharge = corporateSurcharge;
    }

    public Optional<Long> getFeeAmount() {
        return Optional.ofNullable(fee).map(FeeEntity::getAmountCollected);
    }
}
