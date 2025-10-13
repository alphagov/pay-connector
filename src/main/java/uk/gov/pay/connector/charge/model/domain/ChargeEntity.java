package uk.gov.pay.connector.charge.model.domain;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
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
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.SupportedLanguageJpaConverter;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.UNDEFINED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions.isValidTransition;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.AUTHORISATION_MODE;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

@Entity
@Table(name = "charges")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "charges_charge_id_seq",
        sequenceName = "charges_charge_id_seq", allocationSize = 1)
public class ChargeEntity extends AbstractVersionedEntity {
    private static final Logger logger = LoggerFactory.getLogger(ChargeEntity.class);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charges_charge_id_seq")
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
    private Auth3dsRequiredEntity auth3dsRequiredDetails;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @ManyToOne
    @JoinColumn(name = "gateway_account_credential_id", updatable = false)
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @OneToMany(mappedBy = "chargeEntity", cascade = CascadeType.PERSIST)
    private List<FeeEntity> fees = new ArrayList<>();

    @OneToMany(mappedBy = "chargeEntity")
    @OrderBy("updated DESC")
    private List<ChargeEventEntity> events = new ArrayList<>();

    @Column(name = "description")
    private String description;

    @Column(name = "reference")
    @Convert(converter = ServicePaymentReferenceConverter.class)
    private ServicePaymentReference reference;

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Column(name = "created_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant createdDate;

    @Column(name = "language", nullable = false)
    @Convert(converter = SupportedLanguageJpaConverter.class)
    private SupportedLanguage language;

    @Column(name = "delayed_capture")
    private boolean delayedCapture;

    @Column(name = "wallet")
    @Enumerated(EnumType.STRING)
    private WalletType walletType;

    @Valid
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

    @Column(name = "exemption_3ds")
    @Enumerated(EnumType.STRING)
    private Exemption3ds exemption3ds;

    @Column(name = "exemption_3ds_requested")
    @Enumerated(EnumType.STRING)
    private Exemption3dsType exemption3dsRequested;

    @Column(name = "payment_provider")
    private String paymentProvider;

    @Column(name = "service_id")
    private String serviceId;
    
    @ManyToOne
    @JoinColumn(name = "agreement_external_id", referencedColumnName="external_id", updatable = false, nullable = true)
    private AgreementEntity agreementEntity;

    @Column(name = "agreement_payment_type")
    @Enumerated(EnumType.STRING)
    private AgreementPaymentType agreementPaymentType;

    @Column(name = "save_payment_instrument_to_agreement")
    private boolean savePaymentInstrumentToAgreement;

    @OneToOne
    @JoinColumn(name = "payment_instrument_id", nullable = true)
    private PaymentInstrumentEntity paymentInstrument;
    
    @Column(name = "authorisation_mode")
    @Enumerated(EnumType.STRING)
    private AuthorisationMode authorisationMode;

    @Column(name = "can_retry")
    private Boolean canRetry;

    @Column(name = "updated_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant updatedDate;

    @Column(name = "requires_3ds")
    private Boolean requires3ds;

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
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity,
            String paymentProvider,
            String email,
            Instant createdDate,
            SupportedLanguage language,
            boolean delayedCapture,
            ExternalMetadata externalMetadata,
            Source source,
            String gatewayTransactionId,
            CardDetailsEntity cardDetails,
            boolean moto,
            String serviceId,
            AgreementEntity agreementEntity,
            AgreementPaymentType agreementPaymentType,
            boolean savePaymentInstrumentToAgreement,
            AuthorisationMode authorisationMode,
            Boolean canRetry,
            Boolean requires3ds
   
    ) {
        this.amount = amount;
        this.status = status.getValue();
        this.returnUrl = returnUrl;
        this.description = description;
        this.reference = reference;
        this.gatewayAccount = gatewayAccount;
        this.gatewayAccountCredentialsEntity = gatewayAccountCredentialsEntity;
        this.paymentProvider = paymentProvider;
        this.createdDate = createdDate;
        this.externalId = RandomIdGenerator.newId();
        this.email = email;
        this.language = language;
        this.delayedCapture = delayedCapture;
        this.externalMetadata = externalMetadata;
        this.source = source;
        this.gatewayTransactionId = gatewayTransactionId;
        this.cardDetails = cardDetails;
        this.moto = moto;
        this.serviceId = serviceId;
        this.agreementEntity = agreementEntity;
        this.agreementPaymentType = agreementPaymentType;
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
        this.authorisationMode = authorisationMode;
        this.canRetry = canRetry;
        this.requires3ds = requires3ds;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Optional<PaymentInstrumentEntity> getPaymentInstrument() {
        return Optional.ofNullable(paymentInstrument);
    }

    public void setPaymentInstrument(PaymentInstrumentEntity paymentInstrument) {
        this.paymentInstrument = paymentInstrument;
    }
    
    public Long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }
    
    public ChargeStatus getChargeStatus() {
        return ChargeStatus.fromString(status);
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

    public GatewayAccountCredentialsEntity getGatewayAccountCredentialsEntity() {
        return gatewayAccountCredentialsEntity;
    }

    public String getDescription() {
        return description;
    }

    public ServicePaymentReference getReference() {
        return reference;
    }

    public Instant getCreatedDate() {
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

    public String getServiceId() {
        return serviceId;
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

    public void setExemption3ds(Exemption3ds exemption3ds) {
        this.exemption3ds = exemption3ds;
    }

    public Exemption3ds getExemption3ds() {
        return exemption3ds;
    }

    public Exemption3dsType getExemption3dsRequested() {
        return exemption3dsRequested;
    }

    public void setExemption3dsRequested(Exemption3dsType exemption3dsType) {
        this.exemption3dsRequested = exemption3dsType;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setStatus(ChargeStatus targetStatus) {
        Event event = new UnspecifiedEvent();
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
        ArrayList<StructuredArgument> structuredArguments = new ArrayList<>(List.of(
                kv(PAYMENT_EXTERNAL_ID, externalId),
                kv(GATEWAY_ACCOUNT_ID, getGatewayAccount().getId()),
                kv(PROVIDER, paymentProvider),
                kv(GATEWAY_ACCOUNT_TYPE, getGatewayAccount().getType()),
                kv(AUTHORISATION_MODE, authorisationMode)
        ));
        getAgreement().ifPresent(agreementEntity -> structuredArguments.add(kv(AGREEMENT_EXTERNAL_ID, agreementEntity.getExternalId())));
        return structuredArguments.toArray();
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
    
    public void setAgreementEntity(AgreementEntity agreementEntity) {
        this.agreementEntity = agreementEntity;
    }

    public void setSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
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
        return PaymentGatewayName.valueFrom(paymentProvider);
    }

    public Auth3dsRequiredEntity get3dsRequiredDetails() {
        return auth3dsRequiredDetails;
    }

    public void set3dsRequiredDetails(Auth3dsRequiredEntity auth3dsRequiredDetails) {
        this.auth3dsRequiredDetails = auth3dsRequiredDetails;
        this.requires3ds = true;
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

    public void addFee(FeeEntity fee) {
        this.fees.add(fee);
    }

    public Optional<Long> getFeeAmount() {
        return fees.isEmpty() ? Optional.empty() :
            Optional.of(fees.stream()
                .map(FeeEntity::getAmountCollected)
                .reduce(0L, Long::sum));
    }

    public Optional<Long> getNetAmount() {
        return getFeeAmount().map(fee -> {
            if (getChargeStatus().toExternal() == EXTERNAL_SUCCESS) {
                return getAmount() + getCorporateSurcharge().orElse(0L) - fee;
            }
            return -fee;
        });
    }

    public List<FeeEntity> getFees() {
        return fees;
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

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    
    public Optional<AgreementEntity> getAgreement() {
        return Optional.ofNullable(agreementEntity);
    }

    public AgreementPaymentType getAgreementPaymentType() {
        return agreementPaymentType;
    }

    public boolean isSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    public void setAuthorisationMode(AuthorisationMode authorisationMode) {
        this.authorisationMode = authorisationMode;
    }

    public Boolean getCanRetry() {
        return canRetry;
    }

    public void setCanRetry(Boolean canRetry) {
        this.canRetry = canRetry;
    }

    public Boolean getRequires3ds() {
        return requires3ds;
    }

    public void setRequires3ds(Boolean requires3ds) {
        this.requires3ds = requires3ds;
    }

    public void setUpdatedDate(Instant updatedDate) {
        this.updatedDate = updatedDate;
    }

    public Instant getUpdatedDate() {
        return updatedDate;
    }

    public static final class WebChargeEntityBuilder {
        private Long amount;
        private String returnUrl;
        private String email;
        private GatewayAccountEntity gatewayAccount;
        private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
        private String paymentProvider;
        private String description;
        private ServicePaymentReference reference;
        private SupportedLanguage language;
        private boolean delayedCapture;
        private ExternalMetadata externalMetadata;
        private Source source;
        private boolean moto;
        private String serviceId;
        private AgreementEntity agreementEntity;
        private boolean savePaymentInstrumentToAgreement;
        private AuthorisationMode authorisationMode;
        private AgreementPaymentType agreementPaymentType;

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

        public WebChargeEntityBuilder withGatewayAccountCredentialsEntity(
                GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
            this.gatewayAccountCredentialsEntity = gatewayAccountCredentialsEntity;
            return this;
        }

        public WebChargeEntityBuilder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
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

        public WebChargeEntityBuilder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public WebChargeEntityBuilder withAgreementEntity(AgreementEntity agreementEntity) {
            this.agreementEntity = agreementEntity;
            return this;
        }

        public WebChargeEntityBuilder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
            this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
            return this;
        }
        
        public WebChargeEntityBuilder withAuthorisationMode(AuthorisationMode authorisationMode) {
            this.authorisationMode = authorisationMode;
            return this;
        }
        
        public WebChargeEntityBuilder withAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
            this.agreementPaymentType = agreementPaymentType;
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
                    gatewayAccountCredentialsEntity,
                    paymentProvider,
                    email,
                    Instant.now(),
                    language,
                    delayedCapture,
                    externalMetadata,
                    source,
                    null,
                    null,
                    moto,
                    serviceId,
                    agreementEntity,
                    agreementPaymentType,
                    savePaymentInstrumentToAgreement,
                    authorisationMode,
                    null,
                    null);
        }
    }

    public static final class TelephoneChargeEntityBuilder {
        private Long amount;
        private String gatewayTransactionId;
        private String email;
        private CardDetailsEntity cardDetails;
        private GatewayAccountEntity gatewayAccount;
        private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
        private String paymentProvider;
        private String description;
        private ServicePaymentReference reference;
        private ExternalMetadata externalMetadata;
        private String serviceId;
        private AgreementEntity agreementEntity;
        private boolean savePaymentInstrumentToAgreement;

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

        public TelephoneChargeEntityBuilder withGatewayAccountCredentialsEntity(
                GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
            this.gatewayAccountCredentialsEntity = gatewayAccountCredentialsEntity;
            return this;
        }

        public TelephoneChargeEntityBuilder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
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

        public TelephoneChargeEntityBuilder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public TelephoneChargeEntityBuilder withAgreementEntity(AgreementEntity agreementEntity) {
            this.agreementEntity = agreementEntity;
            return this;
        }
        
        public TelephoneChargeEntityBuilder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
            this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
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
                    gatewayAccountCredentialsEntity,
                    paymentProvider,
                    email,
                    Instant.now(),
                    SupportedLanguage.ENGLISH,
                    false,
                    externalMetadata,
                    CARD_EXTERNAL_TELEPHONE,
                    gatewayTransactionId,
                    cardDetails,
                    false,
                    serviceId,
                    agreementEntity,
                    null,
                    savePaymentInstrumentToAgreement,
                    AuthorisationMode.EXTERNAL,
                    null,
                    null);
        }
    }
}
