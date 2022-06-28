package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
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
import java.util.function.Predicate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargeCreateRequest {

    @NotNull(message = "Field [amount] cannot be null")
    @Min(value = 0, message = "Field [amount] can be between 0 and 10_000_000")
    @Max(value = 10_000_000, message = "Field [amount] can be between 0 and 10_000_000")
    @JsonProperty("amount")
    @Schema(description = "Amount in pence", example = "100", required = true,
            minimum = "0", maximum = "10000000")
    private Long amount;

    @NotNull(message = "Field [description] cannot be null")
    @Length(max = 255, message = "Field [description] can have a size between 0 and 255")
    @JsonProperty("description")
    @Schema(example = "payment description", description = "The payment description (shown to the user on the payment pages)", required = true,
            maximum = "255")
    private String description;

    @NotNull(message = "Field [reference] cannot be null")
    @Length(max = 255, message = "Field [reference] can have a size between 0 and 255")
    @JsonProperty("reference")
    @Schema(example = "payment reference", description = "The reference issued by the government service for this payment", required = true,
            maximum = "255")
    private String reference;

    @JsonProperty("return_url")
    @Schema(example = "https://service-name.gov.uk/transactions/12345",
            description = "The url to return the user to after the payment process has completed. Required when authorisation_mode is 'web'")
    private String returnUrl;

    @Length(max = 254, message = "Field [email] can have a size between 0 and 254")
    @JsonProperty("email")
    @Schema(example = "joe.blogs@example.org")
    private String email;

    @JsonProperty("delayed_capture")
    private Boolean delayedCapture;

    @JsonDeserialize(using = SupportedLanguageJsonDeserializer.class)
    @JsonProperty("language")
    @Schema(example = "en")
    private SupportedLanguage language;

    @JsonProperty("prefilled_cardholder_details")
    @Valid
    private PrefilledCardHolderDetails prefilledCardHolderDetails;

    @JsonProperty("metadata")
    @JsonDeserialize(using = ExternalMetadataDeserialiser.class)
    @Valid
    private ExternalMetadata externalMetadata;

    @JsonProperty("source")
    @JsonDeserialize(using = SourceDeserialiser.class)
    @Schema(example = "CARD_API", description = "Source of payment (e.g. CARD_PAYMENT_LINK) - defaults to CARD_API (which cannot be specified explicitly).")
    private Source source;

    @JsonProperty("moto")
    @Schema(description = "Mail Order / Telephone Order (MOTO) payment flag", example = "true")
    private Boolean moto;

    @JsonProperty("payment_provider")
    @ValidPaymentProvider
    @Schema(description = "Payment provider for the charge. Internal use only", example = "sandbox")
    private String paymentProvider;

    @JsonProperty("agreement_id")
    @Length(min = 26, max = 26, message = "Field [agreementId] length must be 26")
    @Schema(description = "Agreement ID to associate charge with", example = "md1mjge8gb6p4qndfs8mf8gto5")
    private String agreementId;

    @JsonProperty("save_payment_instrument_to_agreement")
    @Schema(description = "Applicable for recurring card payments. Indicated whether the payment method should be saved to agreement")
    private Boolean savePaymentInstrumentToAgreement;

    @JsonProperty("authorisation_mode")
    @Valid
    @Schema(description = "Mode of authorisation for the payment. Payments created in `web` mode require the paying user to visit the `next_url` to complete the payment.")
    private AuthorisationMode authorisationMode;

    public ChargeCreateRequest() {
        // For Jackson
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
                        boolean savePaymentInstrumentToAgreement,
                        AuthorisationMode authorisationMode) {
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
        this.authorisationMode = authorisationMode;
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

    public Optional<String> getReturnUrl() {
        return Optional.ofNullable(returnUrl).filter(Predicate.not(String::isEmpty));
    }

    public String getEmail() {
        return email;
    }

    public boolean isDelayedCapture() {
        return Optional.ofNullable(delayedCapture).orElse(false);
    }

    public SupportedLanguage getLanguage() {
        return Optional.ofNullable(language).orElse(SupportedLanguage.ENGLISH);
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
        return Optional.ofNullable(moto).orElse(false);
    }

    public String getAgreementId() {
        return agreementId;
    }

    public boolean getSavePaymentInstrumentToAgreement() {
        return Optional.ofNullable(savePaymentInstrumentToAgreement).orElse(false);
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public AuthorisationMode getAuthorisationMode() {
        return Optional.ofNullable(authorisationMode).orElse(AuthorisationMode.WEB);
    }

    public String toStringWithoutPersonalIdentifiableInformation() {
        // Don't include:
        // description - some services include PII
        // reference - can come from user input for payment links, in the past they have mistakenly entered card numbers
        return "ChargeCreateRequest{" +
                "amount=" + amount +
                (returnUrl != null ? ", return_url=" + returnUrl : "") +
                (delayedCapture != null ? ", delayed_capture=" + delayedCapture : "") +
                (source != null ? ", source=" + source : "") +
                (moto != null ? ", moto=" + moto : "") +
                (language != null ? ", language=" + language : "") +
                (paymentProvider != null ? ", payment_provider=" + paymentProvider : "") +
                (agreementId != null ? ", agreement_id=" + agreementId : "") +
                (savePaymentInstrumentToAgreement != null ? ", save_payment_instrument_to_agreement=" + savePaymentInstrumentToAgreement : "") +
                (authorisationMode != null ? ", authorisation_mode=" + authorisationMode : "") +
                '}';
    }
}
