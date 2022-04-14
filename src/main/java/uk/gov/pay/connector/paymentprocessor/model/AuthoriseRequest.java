package uk.gov.pay.connector.paymentprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.dropwizard.validation.ValidationMethod;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;

import java.util.Objects;

import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isBetween3To4Digits;
import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isValidCardNumberLength;
import static uk.gov.service.payments.commons.model.CardExpiryDate.CARD_EXPIRY_DATE_PATTERN;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthoriseRequest {

    @NotNull(message = "Missing mandatory attribute: one_time_token")
    @Length(min = 1, message = "Invalid attribute value: one_time_token. Must be a valid one time token")
    private String oneTimeToken;

    @NotNull(message = "Missing mandatory attribute: card_number")
    @Length(min = 12, max = 19, message = "Invalid attribute value: card_number. Must be between {min} and {max} characters long")
    private String cardNumber;

    @NotNull(message = "Missing mandatory attribute: cvc")
    @Length(min = 3, max = 4, message = "Invalid attribute value: card_number. Must be between {min} and {max} characters long")
    private String cvc;

    @NotNull(message = "Missing mandatory attribute: expiry_date")
    @Length(min = 1, message = "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY")
    private String expiryDate;

    @NotNull(message = "Missing mandatory attribute: cardholder_name")
    @Length(min = 1, max = 255, message = "Invalid attribute value: cardholder_name. Must be less than or equal to {max} characters length")
    private String cardholderName;

    public AuthoriseRequest() {
        // for Jackson
    }

    public AuthoriseRequest(String oneTimeToken, String cardNumber, String cvc, String expiryDate, String cardholderName) {
        this.oneTimeToken = oneTimeToken;
        this.cardNumber = cardNumber;
        this.cvc = cvc;
        this.expiryDate = expiryDate;
        this.cardholderName = cardholderName;
    }

    @ValidationMethod(message = "Invalid attribute value: cvc. Must contain numbers only")
    @JsonIgnore
    public boolean isValidCVC() {
        return isBetween3To4Digits(cvc);
    }

    @ValidationMethod(message = "Invalid attribute value: card_number. Must contain numbers only")
    @JsonIgnore
    public boolean isValidCardNumber() {
        return isValidCardNumberLength(cardNumber);
    }

    @ValidationMethod(message = "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY")
    @JsonIgnore
    public boolean isValidExpiryDate() {
        return expiryDate != null && CARD_EXPIRY_DATE_PATTERN.matcher(expiryDate).matches();
    }

    public String getOneTimeToken() {
        return oneTimeToken;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getCvc() {
        return cvc;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthoriseRequest request = (AuthoriseRequest) o;
        return Objects.equals(oneTimeToken, request.oneTimeToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oneTimeToken);
    }
}
