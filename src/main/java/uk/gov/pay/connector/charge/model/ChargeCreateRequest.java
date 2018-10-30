package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.SupportedLanguageJsonDeserializer;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargeCreateRequest {

    @NotNull(message = "Field [amount] cannot be null")
    @Min(value = 1, message = "Field [amount] can be between 1 and 10_000_000")
    @Max(value = 10_000_000, message = "Field [amount] can be between 1 and 10_000_000")
    @JsonProperty("amount")
    private long amount;

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

    public ChargeCreateRequest() {
        // for Jackson
    }

    ChargeCreateRequest(long amount,
                        String description,
                        String reference,
                        String returnUrl,
                        String email,
                        boolean delayedCapture,
                        SupportedLanguage language) {
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
        this.email = email;
        this.delayedCapture = delayedCapture;
        this.language = language;
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

    public String toStringWithoutPersonalIdentifiableInformation() {
        return "ChargeCreateRequest{" +
                "amount=" + amount +
                ", reference='" + reference + '\'' +
                ", returnUrl='" + returnUrl + '\'' +
                delayedCapture != null ? ", delayed_capture=" + delayedCapture : "" +
                language != null ? ", language=" + language.toString() : "" +
                email != null ? ", email='" + email + '\'' : "" +
                '}';
    }
}
