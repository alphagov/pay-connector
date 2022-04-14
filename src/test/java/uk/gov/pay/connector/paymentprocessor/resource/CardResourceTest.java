package uk.gov.pay.connector.paymentprocessor.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;
import uk.gov.pay.connector.wallets.applepay.ApplePayService;
import uk.gov.pay.connector.wallets.googlepay.GooglePayService;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
class CardResourceTest {

    private static final CardAuthoriseService mockCardAuthoriseService = mock(CardAuthoriseService.class);
    private static final Card3dsResponseAuthService mockCard3dsResponseAuthService = mock(Card3dsResponseAuthService.class);
    private static final CardCaptureService mockCardCaptureService = mock(CardCaptureService.class);
    private static final ChargeCancelService mockChargeCancelService = mock(ChargeCancelService.class);
    private static final ApplePayService mockApplePayService = mock(ApplePayService.class);
    private static final GooglePayService mockGooglePayService = mock(GooglePayService.class);

    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new CardResource(mockCardAuthoriseService,
                    mockCard3dsResponseAuthService,
                    mockCardCaptureService,
                    mockChargeCancelService,
                    mockApplePayService,
                    mockGooglePayService))
            .build();

    private static Object[] authoriseMotoApiPaymentInvalidInput() {
        return new Object[]{
                // one-time-token 
                new Object[]{null, "4242424242424242", "123", "11/99", "Joe Bogs", "Missing mandatory attribute: one_time_token"},
                new Object[]{"", "4242424242424242", "123", "11/99", "Joe Bogs", "Invalid attribute value: one_time_token. Must be a valid one time token"},
                // card number
                new Object[]{"one-time-token-123", null, "123", "11/99", "Joe Bogs", "Missing mandatory attribute: card_number"},
                new Object[]{"one-time-token-123", "", "123", "11/99", "Joe Bogs", "Invalid attribute value: card_number. Must be between 12 and 19 characters long"},
                new Object[]{"one-time-token-123", "card-number-123", "123", "11/99", "Joe Bogs", "Invalid attribute value: card_number. Must contain numbers only"},
                // cvc
                new Object[]{"one-time-token-123", "4242424242424242", null, "11/99", "Joe Bogs", "Missing mandatory attribute: cvc"},
                new Object[]{"one-time-token-123", "4242424242424242", "", "11/99", "Joe Bogs", "Invalid attribute value: card_number. Must be between 3 and 4 characters long"},
                new Object[]{"one-time-token-123", "4242424242424242", "xyz", "11/99", "Joe Bogs", "Invalid attribute value: cvc. Must contain numbers only"},
                // expiry date
                new Object[]{"one-time-token-123", "4242424242424242", "123", null, "Joe Bogs", "Missing mandatory attribute: expiry_date"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "1109", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "109", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "asdf", "Joe Bogs", "Invalid attribute value: expiry_date. Must be a valid date with the format MM/YY"},
                // cardholder_name
                new Object[]{"one-time-token-123", "4242424242424242", "123", "11/99", null, "Missing mandatory attribute: cardholder_name"},
                new Object[]{"one-time-token-123", "4242424242424242", "123", "", "", "Invalid attribute value: cardholder_name. Must be less than or equal to 255 characters length"}
        };
    }

    @ParameterizedTest
    @MethodSource("authoriseMotoApiPaymentInvalidInput")
    public void authoriseMotoApiPaymentShouldReturn422ForInvalidPayload(String oneTimeToken,
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
        assertThat(errorResponse.getIdentifier(), CoreMatchers.is(ErrorIdentifier.GENERIC));
    }

    @Test
    public void authoriseMotoApiPaymentShouldReturn204ForInvalidPayload() {
        AuthoriseRequest request =
                new AuthoriseRequest("one-time-token-123", "4242424242424242", "123", "11/99", "Job Bogs");

        Response response = resources.target("/v1/api/charges/authorise")
                .request().post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));

        assertThat(response.getStatus(), is(204));
    }

}
