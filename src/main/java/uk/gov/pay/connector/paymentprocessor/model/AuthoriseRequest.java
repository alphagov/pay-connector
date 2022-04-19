package uk.gov.pay.connector.paymentprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.dropwizard.validation.ValidationMethod;
import org.hibernate.validator.constraints.Length;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isBetween3To4Digits;
import static uk.gov.pay.connector.common.validator.AuthCardDetailsValidator.isValidCardNumberLength;
import static uk.gov.service.payments.commons.model.CardExpiryDate.CARD_EXPIRY_DATE_PATTERN;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuthoriseRequest {

    @NotBlank(message = "Missing mandatory attribute: one_time_token")
    private String oneTimeToken;

    @NotBlank(message = "Missing mandatory attribute: card_number")
    private String cardNumber;

    @NotBlank(message = "Missing mandatory attribute: cvc")
    private String cvc;

    @NotBlank(message = "Missing mandatory attribute: expiry_date")
    private String expiryDate;

    @NotBlank(message = "Missing mandatory attribute: cardholder_name")
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

    @ValidationMethod(message = "Invalid attribute value: cvc. Must be between 3 and 4 characters long and contain numbers only")
    @JsonIgnore
    public boolean isValidCVC() {
        return isBetween3To4Digits(cvc);
    }

    @ValidationMethod(message = "Invalid attribute value: card_number. Must be between 12 and 19 characters long")
    @JsonIgnore
    public boolean isValidCardNumber() {
        return isValidCardNumberLength(cardNumber);
    }

    @ValidationMethod(message = "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY")
    @JsonIgnore
    public boolean isValidExpiryDate() {
        return isNotEmpty(expiryDate) && CARD_EXPIRY_DATE_PATTERN.matcher(expiryDate).matches();
    }

    @ValidationMethod(message = "Invalid attribute value: expiry_date. Must be a valid date in the future")
    @JsonIgnore
    public boolean isExpiryDateInFuture() {
        return isValidExpiryDate() && isMonthAndYearInTheFuture(expiryDate);
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
