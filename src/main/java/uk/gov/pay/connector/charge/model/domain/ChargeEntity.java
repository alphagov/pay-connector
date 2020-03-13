package uk.gov.pay.connector.charge.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.Source;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.SupportedLanguageJpaConverter;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.util.ExternalMetadataConverter;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.UnspecifiedEvent;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
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
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.commons.model.Source.CARD_EXTERNAL_TELEPHONE;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.UNDEFINED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions.isValidTransition;
import static uk.gov.pay.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.pay.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.pay.logging.LoggingKeys.PROVIDER;

@Entity
@Table(name = "charges")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "charges_charge_id_seq",
        sequenceName = "charges_charge_id_seq", allocationSize = 1)
public class ChargeEntity extends AbstractVersionedEntity implements Nettable {
    private static final Logger logger = LoggerFactory.getLogger(ChargeEntity.class);
    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = factory.getValidator();

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
    @Convert(converter = ExternalMetadataConverter.class)
    private ExternalMetadata externalMetadata;

    @Column(name = "parity_check_status")
    @Enumerated(EnumType.STRING)
    private ParityCheckStatus parityCheckStatus;

    @Column(name = "parity_check_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime parityCheckDate;

    @Column(name = "source")
    @Enumerated(EnumType.STRING)
    private Source source;

    @Column(name = "moto")
    private boolean moto;

    public ChargeEntity() {
        //for jpa
    }

    // Only the ChargeEntityFixture should directly call this constructor
    ChargeEntity(
            Long amount,
            ChargeStatus status,
            String returnUrl,
            String description,
            ServicePaymentReference reference,
            GatewayAccountEntity gatewayAccount,
            String email,
            ZonedDateTime createdDate,
            SupportedLanguage language,
            boolean delayedCapture,
            ExternalMetadata externalMetadata,
            Source source,
            String gatewayTransactionId,
            CardDetailsEntity cardDetails,
            boolean moto
    ) {
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
        this.source = source;
        this.gatewayTransactionId = gatewayTransactionId;
        this.cardDetails = cardDetails;
        this.moto = moto;
        setExternalMetadata(externalMetadata);
    }

    private void setExternalMetadata(ExternalMetadata externalMetadata) {
        validateExternalMetadata(externalMetadata);
        this.externalMetadata = externalMetadata;
    }

    private void validateExternalMetadata(ExternalMetadata externalMetadata) {
        if (externalMetadata == null) {
            return;
        }

        String violationMessages = validator.validate(externalMetadata).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        if (violationMessages.isEmpty()) {
            return;
        }

        throw new ValidationException("Cannot set invalid ExternalMetadata when creating a new ChargeEntity: " + violationMessages);
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

    public Optional<ExternalMetadata> getExternalMetadata() {
        return Optional.ofNullable(externalMetadata);
    }

    public boolean isMoto() {
        return moto;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setStatus(ChargeStatus targetStatus) {
        setStatus(targetStatus, new UnspecifiedEvent());
    }

    public void setStatus(ChargeStatus targetStatus, Event event) {
        if (isValidTransition(fromString(this.status), targetStatus, event)) {
            var logMessage = format("Changing charge status for externalId [%s] [%s]->[%s] [event=%s]",
                    this.externalId, this.status, targetStatus.getValue(), event);
            logger.info(logMessage, getStructuredLoggingArgs());
            this.status = targetStatus.getValue();
        } else {
            var logMessage = format("Charge with state %s cannot proceed to %s [charge_external_id=%s, charge_status=%s, event=%s]",
                    this.status, targetStatus, this.externalId, targetStatus, event);
            logger.warn(logMessage, getStructuredLoggingArgs());
            throw new InvalidStateTransitionException(this.status, targetStatus.getValue(), event);
        }
    }

    /**
     * This bypasses checks that we can move from the current charge status to the target charge status as enforced by
     * the standard payment flow.
     * Should be used to set the status only when correcting the charge status when the status we have conflicts with
     * the status with the payment gateway.
     */
    public void setStatusIgnoringValidTransitions(ChargeStatus targetStatus) {
        var logMessage = format("Forcibly changing charge status for externalId [%s] [%s]->[%s]",
                this.externalId, this.status, targetStatus.getValue());
        logger.info(logMessage, getStructuredLoggingArgs());
        this.status = targetStatus.getValue();
    }

    public Object[] getStructuredLoggingArgs() {
        return new StructuredArgument[]{
                kv(PAYMENT_EXTERNAL_ID, externalId),
                kv(GATEWAY_ACCOUNT_ID, getGatewayAccount().getId()),
                kv(PROVIDER, getGatewayAccount().getGatewayName()),
                kv(GATEWAY_ACCOUNT_TYPE, getGatewayAccount().getType())
        };
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

    public void setFee(FeeEntity fee) {
        this.fee = fee;
    }

    public Optional<Long> getFeeAmount() {
        return Optional.ofNullable(fee).map(FeeEntity::getAmountCollected);
    }

    public ParityCheckStatus getParityCheckStatus() {
        return parityCheckStatus;
    }

    public ZonedDateTime getParityCheckDate() {
        return parityCheckDate;
    }

    public void updateParityCheck(ParityCheckStatus parityCheckStatus) {
        this.parityCheckStatus = parityCheckStatus;
        this.parityCheckDate = ZonedDateTime.now(ZoneId.of("UTC"));
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public static final class WebChargeEntityBuilder {
        private Long amount;
        private String returnUrl;
        private String email;
        private GatewayAccountEntity gatewayAccount;
        private String description;
        private ServicePaymentReference reference;
        private SupportedLanguage language;
        private boolean delayedCapture;
        private ExternalMetadata externalMetadata;
        private Source source;
        private boolean moto;

        private WebChargeEntityBuilder() {
        }

        public static WebChargeEntityBuilder aWebChargeEntity() {
            return new WebChargeEntityBuilder();
        }

        public WebChargeEntityBuilder withAmount(Long amount) {
            this.amount = amount;
            return this;
        }

        public WebChargeEntityBuilder withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public WebChargeEntityBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public WebChargeEntityBuilder withGatewayAccount(GatewayAccountEntity gatewayAccount) {
            this.gatewayAccount = gatewayAccount;
            return this;
        }

        public WebChargeEntityBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public WebChargeEntityBuilder withReference(ServicePaymentReference reference) {
            this.reference = reference;
            return this;
        }

        public WebChargeEntityBuilder withLanguage(SupportedLanguage language) {
            this.language = language;
            return this;
        }

        public WebChargeEntityBuilder withDelayedCapture(boolean delayedCapture) {
            this.delayedCapture = delayedCapture;
            return this;
        }

        public WebChargeEntityBuilder withExternalMetadata(ExternalMetadata externalMetadata) {
            this.externalMetadata = externalMetadata;
            return this;
        }

        public WebChargeEntityBuilder withSource(Source source) {
            this.source = source;
            return this;
        }

        public WebChargeEntityBuilder withMoto(boolean moto) {
            this.moto = moto;
            return this;
        }

        public ChargeEntity build() {
            return new ChargeEntity(
                    amount,
                    UNDEFINED,
                    returnUrl,
                    description,
                    reference,
                    gatewayAccount,
                    email,
                    ZonedDateTime.now(ZoneId.of("UTC")),
                    language,
                    delayedCapture,
                    externalMetadata,
                    source,
                    null,
                    null,
                    moto);
        }
    }

    public static final class TelephoneChargeEntityBuilder {
        private Long amount;
        private String gatewayTransactionId;
        private String email;
        private CardDetailsEntity cardDetails;
        private GatewayAccountEntity gatewayAccount;
        private String description;
        private ServicePaymentReference reference;
        private ExternalMetadata externalMetadata;

        private TelephoneChargeEntityBuilder() {
        }

        public static TelephoneChargeEntityBuilder aTelephoneChargeEntity() {
            return new TelephoneChargeEntityBuilder();
        }

        public TelephoneChargeEntityBuilder withAmount(Long amount) {
            this.amount = amount;
            return this;
        }

        public TelephoneChargeEntityBuilder withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        public TelephoneChargeEntityBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public TelephoneChargeEntityBuilder withCardDetails(CardDetailsEntity cardDetails) {
            this.cardDetails = cardDetails;
            return this;
        }

        public TelephoneChargeEntityBuilder withGatewayAccount(GatewayAccountEntity gatewayAccount) {
            this.gatewayAccount = gatewayAccount;
            return this;
        }

        public TelephoneChargeEntityBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public TelephoneChargeEntityBuilder withReference(ServicePaymentReference reference) {
            this.reference = reference;
            return this;
        }

        public TelephoneChargeEntityBuilder withExternalMetadata(ExternalMetadata externalMetadata) {
            this.externalMetadata = externalMetadata;
            return this;
        }

        public ChargeEntity build() {
            return new ChargeEntity(
                    amount,
                    UNDEFINED,
                    null,
                    description,
                    reference,
                    gatewayAccount,
                    email,
                    ZonedDateTime.now(ZoneId.of("UTC")),
                    SupportedLanguage.ENGLISH,
                    false,
                    externalMetadata,
                    CARD_EXTERNAL_TELEPHONE,
                    gatewayTransactionId,
                    cardDetails,
                    false);
        }
    }
}
