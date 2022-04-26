package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.connector.charge.validation.ValidPaymentProvider;
import uk.gov.service.payments.commons.api.json.ExternalMetadataDeserialiser;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.SupportedLanguageJsonDeserializer;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargeCreateRequest {

    @NotNull(message = "Field [amount] cannot be null")
    @Min(value = 0, message = "Field [amount] can be between 0 and 10_000_000")
    @Max(value = 10_000_000, message = "Field [amount] can be between 0 and 10_000_000")
    @JsonProperty("amount")
    private Long amount;

    @NotNull(message = "Field [description] cannot be null")
    @Length(max = 255, message = "Field [description] can have a size between 0 and 255")
    @JsonProperty("description")
    private String description;

    @NotNull(message = "Field [reference] cannot be null")
    @Length(max = 255, message = "Field [reference] can have a size between 0 and 255")
    @JsonProperty("reference")
    private String reference;

    @NotNull(message = "Field [return_url] cannot be null")
    @JsonProperty("return_url")
    private String returnUrl;

    @Length(max = 254, message = "Field [email] can have a size between 0 and 254")
    @JsonProperty("email")
    private String email;

    @JsonProperty("delayed_capture")
    private boolean delayedCapture = false;

    @JsonDeserialize(using = SupportedLanguageJsonDeserializer.class)
    @JsonProperty("language")
    private SupportedLanguage language = SupportedLanguage.ENGLISH;
    
    @JsonProperty("prefilled_cardholder_details")
    @Valid
    private PrefilledCardHolderDetails prefilledCardHolderDetails;

    @JsonProperty("metadata")
    @JsonDeserialize(using = ExternalMetadataDeserialiser.class)
    @Valid
    private ExternalMetadata externalMetadata;

    @JsonProperty("source")
    @JsonDeserialize(using = SourceDeserialiser.class)
    private Source source;
    
    @JsonProperty("moto")
    private boolean moto;
    
    @JsonProperty("payment_provider")
    @ValidPaymentProvider
    private String paymentProvider;

    @JsonProperty("agreement_id")
    @Length(min = 26, max = 26, message = "Field [agreementId] length must be 26")
    private String agreementId;

    @JsonProperty("save_payment_instrument_to_agreement")
    private boolean savePaymentInstrumentToAgreement;
    
    @JsonProperty("authorisation_mode")
    @Valid
    private AuthorisationMode authorisationMode; // TODO   = AuthorisationMode.WEB;

    public ChargeCreateRequest() {
    }

    ChargeCreateRequest(long amount,
                        String description,
                        String reference,
                        String returnUrl,
                        String email,
                        boolean delayedCapture,
                        SupportedLanguage language,
                        PrefilledCardHolderDetails prefilledCardHolderDetails,
                        ExternalMetadata externalMetadata,
                        Source source,
                        boolean moto,
                        String paymentProvider,
                        String agreementId,
                        boolean savePaymentInstrumentToAgreement) {
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
        this.email = email;
        this.delayedCapture = delayedCapture;
        this.language = language;
        this.prefilledCardHolderDetails = prefilledCardHolderDetails;
        this.externalMetadata = externalMetadata;
        this.source = source;
        this.moto = moto;
        this.paymentProvider = paymentProvider;
        this.agreementId = agreementId;
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
    }

    public long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getEmail() {
        return email;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public SupportedLanguage getLanguage() {
        return language;
    }
    
    public Optional<PrefilledCardHolderDetails> getPrefilledCardHolderDetails() {
        return Optional.ofNullable(prefilledCardHolderDetails);
    }

    public Optional<ExternalMetadata> getExternalMetadata() {
        return Optional.ofNullable(externalMetadata);
    }

    public Source getSource() {
        return source;
    }

    public boolean isMoto() {
        return moto;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public boolean getSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    public String toStringWithoutPersonalIdentifiableInformation() {
        // Don't include:
        // description - some services include PII
        // reference - can come from user input for payment links, in the past they have mistakenly entered card numbers
        return "ChargeCreateRequest{" +
                "amount=" + amount +
                ", returnUrl='" + returnUrl + '\'' +
                ", delayed_capture=" + delayedCapture +
                ", source=" + source +
                ", moto=" + moto +
                (language != null ? ", language=" + language.toString() : "") +
                (paymentProvider != null ? ", payment_provider=" + paymentProvider : "") +
                (agreementId != null ? ", agreementId=" + agreementId.toString() : "") +
                ", savePaymentInstrumentToAgreement=" + savePaymentInstrumentToAgreement +
                ", authorisationMode=" + authorisationMode + 
                '}';
    }
}
