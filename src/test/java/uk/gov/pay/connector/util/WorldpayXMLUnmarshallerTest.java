package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayDeleteTokenResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayNotification;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayRefundResponse;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_FLEX_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_CANCELLED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_DELETE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REFUND_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_REFUND_SUCCESS_RESPONSE;

class WorldpayXMLUnmarshallerTest {

    @Test
    void shouldUnmarshallACancelSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_CANCEL_SUCCESS_RESPONSE);
        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCancelResponse.class);
        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    void shouldUnmarshallACancelErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(WORLDPAY_CANCEL_ERROR_RESPONSE);

        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayCancelResponse.class);
        assertThat(response.getTransactionId(), is(nullValue()));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    void shouldUnmarshallANotification() throws Exception {
        String transactionId = "MyUniqueTransactionId!";
        String status = "CAPTURED";
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status)
                .replace("{{refund-ref}}", "REFUND-REF")
                .replace("{{refund-authorisation-reference}}", "REFUND-AUTHORISATION-REFERENCE")
                .replace("{{refund-response-reference}}", "REFUND-RESPONSE-REFERENCE")
                .replace("{{bookingDateDay}}", "10")
                .replace("{{bookingDateMonth}}", "01")
                .replace("{{bookingDateYear}}", "2017");
        WorldpayNotification response = XMLUnmarshaller.unmarshall(successPayload, WorldpayNotification.class);
        assertThat(response.getStatus(), is(status));
        assertThat(response.getTransactionId(), is(transactionId));
        assertThat(response.getMerchantCode(), is("MERCHANTCODE"));
        assertThat(response.getReference(), is("REFUND-REF"));
        assertThat(response.getRefundAuthorisationReference(), is("REFUND-AUTHORISATION-REFERENCE"));
        assertThat(response.getRefundResponseReference(), is("REFUND-RESPONSE-REFERENCE"));
        assertThat(response.getBookingDate(), is(LocalDate.parse("2017-01-10")));
    }

    @Test
    void shouldUnmarshallACaptureSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_CAPTURE_SUCCESS_RESPONSE);
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    void shouldUnmarshallACaptureErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(WORLDPAY_CAPTURE_ERROR_RESPONSE);
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayCaptureResponse.class);
        assertThat(response.getTransactionId(), is(nullValue()));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    void shouldUnmarshallAAuthorisationSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);
        assertTrue(response.getLastEvent().isPresent());
        assertThat(response.getLastEvent().get(), is("AUTHORISED"));
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.AUTHORISED));
        assertThat(response.getTransactionId(), is("transaction-id"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    void shouldUnmarshall3dsResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_3DS_RESPONSE);
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);

        assertTrue(response.getLastEvent().isEmpty());
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());
        assertNull(response.getErrorCode());
        assertNull(response.getErrorMessage());

        assertThat(response.getGatewayParamsFor3ds().isPresent(), is(true));
        assertThat(response.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is("eJxVUsFuwjAM/ZWK80aSUgpFJogNpHEo2hjTzl"));
        assertThat(response.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is("https://secure-test.worldpay.com/jsp/test/shopper/ThreeDResponseSimulator.jsp"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.REQUIRES_3DS));
    }

    @Test
    void shouldUnmarshall3dsFlexResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_3DS_FLEX_RESPONSE);
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);

        assertTrue(response.getLastEvent().isEmpty());
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());
        assertNull(response.getErrorCode());
        assertNull(response.getErrorMessage());

        assertThat(response.getGatewayParamsFor3ds().isPresent(), is(true));
        assertThat(response.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeAcsUrl(), is("https://worldpay.com"));
        assertThat(response.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeTransactionId(), is("rUT8fLKDviHXr8aUn3l1"));
        assertThat(response.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengePayload(), is("P.25de9db33221a55eedc6ac352b927a8c3a08d747643c592dd8f8ab7d3..."));
        assertThat(response.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getThreeDsVersion(), is("2.1.0"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.REQUIRES_3DS));
    }

    @Test
    void shouldUnmarshallAAuthorisationFailedResponse() throws Exception {
        String failedPayload = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(failedPayload, WorldpayOrderStatusResponse.class);
        assertTrue(response.getLastEvent().isPresent());
        assertThat(response.getLastEvent().get(), is("REFUSED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("REFUSED"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.REJECTED));
        assertThat(response.getTransactionId(), is("MyUniqueTransactionId!12"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    void shouldUnmarshallCanceledAuthorisations() throws Exception {
        String failedPayload = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_CANCELLED_RESPONSE);
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(failedPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.cancelStatus(), is(BaseCancelResponse.CancelStatus.CANCELLED));
        assertTrue(response.getLastEvent().isPresent());
        assertThat(response.getLastEvent().get(), is("CANCELLED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("CANCELLED"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.CANCELLED));
        assertThat(response.getTransactionId(), is("MyUniqueTransactionId!12"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    void shouldUnmarshallAAuthorisationErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_ERROR_RESPONSE);
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayOrderStatusResponse.class);

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.ERROR));
        assertThat(response.getTransactionId(), is(nullValue()));

        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    void shouldUnmarshallARefundSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_REFUND_SUCCESS_RESPONSE);
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().isPresent(), is(false));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    void shouldUnmarshallARefundErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(WORLDPAY_REFUND_ERROR_RESPONSE);
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().isPresent(), is(false));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    void shouldUnmarshallADeleteTokenSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(WORLDPAY_DELETE_TOKEN_SUCCESS_RESPONSE);
        WorldpayDeleteTokenResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayDeleteTokenResponse.class);

        assertThat(response.getPaymentTokenID(), is("payment-token-123"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }
}
