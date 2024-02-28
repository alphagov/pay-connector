package uk.gov.pay.connector.card.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.connector.charge.exception.InvalidAttributeValueException;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.HOURS;
import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isBetween3To4Digits;
import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isValidCardNumberLength;
import static uk.gov.service.payments.commons.model.CardExpiryDate.CARD_EXPIRY_DATE_PATTERN;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthoriseRequest {

    @NotBlank(message = "Missing mandatory attribute: one_time_token")
    @Schema(example = "123abc123", required = true,
            description = "the one time token provided in the `auth_url_post` link of the create payment API response")
    private String oneTimeToken;

    @NotBlank(message = "Missing mandatory attribute: card_number")
    @Length(min = 12, max = 19, message = "Invalid attribute value: card_number. Must be between {min} and {max} characters long")
    @Schema(example = "4242424242424242", minLength = 12, maxLength = 19, required = true)
    private String cardNumber;

    @NotBlank(message = "Missing mandatory attribute: cvc")
    @Length(min = 3, max = 4, message = "Invalid attribute value: cvc. Must be between {min} and {max} characters long")
    @Schema(example = "123", minLength = 3, maxLength = 4, required = true)
    private String cvc;

    @NotBlank(message = "Missing mandatory attribute: expiry_date")
    @Schema(example = "01/99", minLength = 5, maxLength = 5, description = "5 character string in MM/YY format", required = true)
    private String expiryDate;

    @NotBlank(message = "Missing mandatory attribute: cardholder_name")
    @Length(min = 1, max = 255, message = "Invalid attribute value: cardholder_name. Must be less than or equal to {max} characters length")
    @Schema(example = "Joe B", maxLength = 255, description = "Cardholder name", required = true)
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

    private boolean isMonthAndYearInTheFuture(String expiryDate) {
        final int MAX_TIMEZONE_OFFSET_BEHIND_UTC = 12;

        // Validation is not bound to UTC and allows cards valid far behind UTC.
        // For example cards with an expiry date of 12/22 are still considered valid if entered on 1-Jan-23 4:00AM UTC.
        // And it will be down to card issuers to decline if the expiry date is invalid.
        LocalDateTime dateTimeWithMaxOffsetBehindUTC = LocalDateTime.now(UTC).minus(MAX_TIMEZONE_OFFSET_BEHIND_UTC, HOURS);
        YearMonth yearMonth = YearMonth.of(dateTimeWithMaxOffsetBehindUTC.getYear(), dateTimeWithMaxOffsetBehindUTC.getMonth());

        return !CardExpiryDate.valueOf(expiryDate).toYearMonth().isBefore(yearMonth);
    }

    public void validate() {
        if (!isValidCardNumberLength(cardNumber)) {
            throw new InvalidAttributeValueException("card_number", "Must be a valid card number");
        }
        if (!isBetween3To4Digits(cvc)) {
            throw new InvalidAttributeValueException("cvc", "Must contain numbers only");
        }

        if (!CARD_EXPIRY_DATE_PATTERN.matcher(expiryDate).matches()) {
            throw new InvalidAttributeValueException("expiry_date", "Must be a valid date with the format MM/YY");
        } else {
            if (!isMonthAndYearInTheFuture(expiryDate)) {
                throw new InvalidAttributeValueException("expiry_date", "Must be a valid date in the future");
            }
        }
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
