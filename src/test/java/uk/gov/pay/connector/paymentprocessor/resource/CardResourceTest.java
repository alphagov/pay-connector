package uk.gov.pay.connector.paymentprocessor.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.exception.OneTimeTokenAlreadyUsedException;
import uk.gov.pay.connector.charge.exception.OneTimeTokenAlreadyUsedExceptionMapper;
import uk.gov.pay.connector.charge.exception.OneTimeTokenInvalidException;
import uk.gov.pay.connector.charge.exception.OneTimeTokenInvalidExceptionMapper;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;
import uk.gov.pay.connector.token.TokenService;
import uk.gov.pay.connector.wallets.applepay.ApplePayService;
import uk.gov.pay.connector.wallets.googlepay.GooglePayService;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.YearMonth;

import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.temporal.ChronoUnit.MONTHS;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ONE_TIME_TOKEN_ALREADY_USED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ONE_TIME_TOKEN_INVALID;

@ExtendWith(DropwizardExtensionsSupport.class)
class CardResourceTest {

    private static final CardAuthoriseService mockCardAuthoriseService = mock(CardAuthoriseService.class);
    private static final Card3dsResponseAuthService mockCard3dsResponseAuthService = mock(Card3dsResponseAuthService.class);
    private static final CardCaptureService mockCardCaptureService = mock(CardCaptureService.class);
    private static final ChargeCancelService mockChargeCancelService = mock(ChargeCancelService.class);
    private static final ApplePayService mockApplePayService = mock(ApplePayService.class);
    private static final GooglePayService mockGooglePayService = mock(GooglePayService.class);
    private static final TokenService mockTokenService = mock(TokenService.class);

    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new CardResource(mockCardAuthoriseService,
                    mockCard3dsResponseAuthService,
                    mockCardCaptureService,
                    mockChargeCancelService,
                    mockApplePayService,
                    mockGooglePayService,
                    mockTokenService))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(OneTimeTokenInvalidExceptionMapper.class)
            .addProvider(OneTimeTokenAlreadyUsedExceptionMapper.class)
            .build();

    private static Object[] authoriseMotoApiPaymentInvalidInput() {
        return new Object[]{
                // one-time-token 
                new Object[]{null, "4242424242424242", "123", "11/99", "Joe Bogs", "Missing mandatory attribute: one_time_token"},
                new Object[]{"", "4242424242424242", "123", "11/99", "Joe Bogs", "Missing mandatory attribute: one_time_token"},
                // card number
                new Object[]{"one-time-token-123", null, "123", "11/99", "Joe Bogs", "Missing mandatory attribute: card_number"},
                new Object[]{"one-time-token-123", "", "123", "11/99", "Joe Bogs", "Missing mandatory attribute: card_number"},
                new Object[]{"one-time-token-123", "card-number-123", "123", "11/99", "Joe Bogs", "Invalid attribute value: card_number. Must be between 12 and 19 characters long"},
                new Object[]{"one-time-token-123", "12345678901234567890", "123", "11/99", "Joe Bogs", "Invalid attribute value: card_number. Must be between 12 and 19 characters long"},
                // cvc
                new Object[]{"one-time-token-123", "4242424242424242", null, "11/99", "Joe Bogs", "Missing mandatory attribute: cvc"},
                new Object[]{"one-time-token-123", "4242424242424242", "", "11/99", "Joe Bogs", "Invalid attribute value: cvc. Must be between 3 and 4 characters long and contain numbers only"},
                new Object[]{"one-time-token-123", "4242424242424242", "12345", "11/99", "Joe Bogs", "Invalid attribute value: cvc. Must be between 3 and 4 characters long and contain numbers only"},
                new Object[]{"one-time-token-123", "4242424242424242", "12", "11/99", "Joe Bogs", "Invalid attribute value: cvc. Must be between 3 and 4 characters long and contain numbers only"},
                new Object[]{"one-time-token-123", "4242424242424242", "xyz", "11/99", "Joe Bogs", "Invalid attribute value: cvc. Must be between 3 and 4 characters long and contain numbers only"},
                // expiry date
                new Object[]{"one-time-token-123", "4242424242424242", "123", null, "Joe Bogs", "Missing mandatory attribute: expiry_date"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "1109", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "109", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "asdf", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "11/21", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date in the future"},
                // cardholder_name
                new Object[]{"one-time-token-123", "4242424242424242", "123", "11/99", null, "Missing mandatory attribute: cardholder_name"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "11/99", "", "Invalid attribute value: cardholder_name. Must be less than or equal to 255 characters length"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "", StringUtils.repeat("*", 256),
                        "Invalid attribute value: cardholder_name. Must be less than or equal to 255 characters length"}
        };
    }

    @ParameterizedTest
    @MethodSource("authoriseMotoApiPaymentInvalidInput")
    void authoriseMotoApiPaymentShouldReturn422ForInvalidPayload(String oneTimeToken,
                                                                 String cardNumber,
                                                                 String cvc,
                                                                 String expiryDate,
                                                                 String cardHolderName,
                                                                 String expectedMessage) {
        AuthoriseRequest request =
                new AuthoriseRequest(oneTimeToken, cardNumber, cvc, expiryDate, cardHolderName);

        Response response = resources.target("/v1/api/charges/authorise")
                .request().post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));

        assertThat(response.getStatus(), is(422));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), hasItem(expectedMessage));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }

    private static Object[] expiryDateMonthOffsetAndExpectedResponseStatus() {
        return new Object[]{
                new Object[]{0, 204, "Expiry date is same as current month and year"},
                new Object[]{1, 204, "Expiry date is in the future"},
                new Object[]{10000, 204, "Expiry date is in the future"},
                new Object[]{-1, 422, "Expiry date is in the past (one month before current month and year)"}
        };
    }

    @ParameterizedTest
    @MethodSource("expiryDateMonthOffsetAndExpectedResponseStatus")
    void authoriseMotoApiPaymentShouldReturnCorrectResponsesForExpiryDate(int monthsToAddOrSubstractFromCurrentMonthAndYear,
                                                                          int expectedResponseCode,
                                                                          String description) {
        doNothing().when(mockTokenService).validateToken("one-time-token-123");
        YearMonth expiryMonthAndDate = YearMonth.now(UTC).plus(monthsToAddOrSubstractFromCurrentMonthAndYear, MONTHS);

        AuthoriseRequest request = new AuthoriseRequest("one-time-token-123", "4242424242424242", "123",
                expiryMonthAndDate.format(ofPattern("MM/yy")), "Job Bogs");

        Response response = resources.target("/v1/api/charges/authorise")
                .request().post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));

        assertThat(response.getStatus(), is(expectedResponseCode));

        if (expectedResponseCode == 422) {
            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(errorResponse.getMessages(), hasItem("Invalid attribute value: expiry_date. Must be a valid date in the future"));
            assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
        }
    }

    @Test
    void authoriseMotoApiPaymentShouldReturn204ForValidPayload() {
        AuthoriseRequest request =
                new AuthoriseRequest("one-time-token-123", "4242424242424242", "123", "11/99", "Job Bogs");

        doNothing().when(mockTokenService).validateToken("one-time-token-123");

        Response response = resources.target("/v1/api/charges/authorise")
                .request().post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));

        assertThat(response.getStatus(), is(204));
    }

    @Test
    void authoriseMotoApiPaymentShouldReturn400ForInvalidOneTimeTaken() {
        AuthoriseRequest request =
                new AuthoriseRequest("one-time-token-123", "4242424242424242", "123", "11/99", "Job Bogs");

        doThrow(new OneTimeTokenInvalidException()).when(mockTokenService).validateToken("one-time-token-123");

        Response response = resources.target("/v1/api/charges/authorise")
                .request().post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));

        assertThat(response.getStatus(), is(400));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), hasItem("The one_time_token is not valid"));
        assertThat(errorResponse.getIdentifier(), is(ONE_TIME_TOKEN_INVALID));
    }

    @Test
    void authoriseMotoApiPaymentShouldReturn400ForOneTimeTakenAlreadyUsed() {
        AuthoriseRequest request =
                new AuthoriseRequest("one-time-token-123", "4242424242424242", "123", "11/99", "Job Bogs");

        doThrow(new OneTimeTokenAlreadyUsedException()).when(mockTokenService).validateToken("one-time-token-123");

        Response response = resources.target("/v1/api/charges/authorise")
                .request().post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));

        assertThat(response.getStatus(), is(400));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), hasItem("The one_time_token has already been used"));
        assertThat(errorResponse.getIdentifier(), is(ONE_TIME_TOKEN_ALREADY_USED));
    }

}
