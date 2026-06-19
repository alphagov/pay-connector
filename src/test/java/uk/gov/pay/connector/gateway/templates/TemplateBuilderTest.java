package uk.gov.pay.connector.gateway.templates;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_MOTO_AUTHORISATION_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;
import static uk.gov.pay.connector.util.XmlAssertions.assertThat;

class TemplateBuilderTest {

    private static final String CARD_NUMBER = "4242424242424242";
    private static final String EXPIRY_DATE_MONTH = "11";
    private static final String EXPIRY_DATE_YEAR = "2030";
    private static final String CARDHOLDER_NAME = "Alec Barley";
    private static final String CVC = "123";
    private static final String ORDER_CODE = "MyUniqueTransactionId";
    private static final String DESCRIPTION = "My description";
    private static final String USERNAME = "username"; // pragma: allowlist secret
    private static final String PASSWORD = "password"; // pragma: allowlist secret
    private static final String MERCHANT_CODE = "MERCHANTCODE";
    private static final long AMOUNT_IN_PENCE = 2000L;

    @Nested
    class MotoAuthorisationRequest {
        @Test
        void shouldGenerateValidAuthoriseOrderRequestForWorldpayMotoAuthorisationRequest() {
            TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/WorldpayAuthoriseMotoOrderTemplate.ftlx");
            WorldpayMotoAuthoriseRequest motoOrder = createWorldpayMotoAuthoriseRequest();

            assertThat(templateBuilder.buildWith(motoOrder))
                    .and(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_MOTO_AUTHORISATION_REQUEST))
                    .areIdentical();
        }
    }

    @Nested
    class TemplateBuilderAppliesAutoEscape {
        @Test
        void shouldGenerateValidAuthoriseOrderRequestWithAutoEscape() {
            TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/WorldpayAuthoriseMotoOrderTemplate.ftlx");
            WorldpayMotoAuthoriseRequest motoOrder = new WorldpayMotoAuthoriseRequest(
                    USERNAME,
                    PASSWORD,
                    "MERCHANT\"\"CODE",
                    ORDER_CODE,
                    DESCRIPTION,
                    String.valueOf(AMOUNT_IN_PENCE),
                    CARD_NUMBER,
                    EXPIRY_DATE_MONTH,
                    EXPIRY_DATE_YEAR,
                    "Alec & Barley",
                    CVC);
            String renderedAuthoriseOrder = templateBuilder.buildWith(motoOrder);
            assertThat(renderedAuthoriseOrder, containsString("MERCHANT&quot;&quot;CODE"));
            assertThat(renderedAuthoriseOrder, containsString("Alec &amp; Barley"));
        }
    }

    @Nested
    class InvalidActionsThrowingException {
        @Test
        void shouldThrowRuntimeExceptionWhenTemplateIsNotFound() {
            var thrown = assertThrows(RuntimeException.class, () -> new TemplateBuilder("/worldpay/NonExistentOrderTemplate.xml"));
            assertThat(thrown.getMessage(), is("Could not load template /worldpay/NonExistentOrderTemplate.xml in dir /templates"));
        }

        @Test
        void shouldThrowRuntimeExceptionWhenCannotRenderTemplate() {
            TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/WorldpayCancelOrderTemplate.xml");
            WorldpayMotoAuthoriseRequest motoOrder = createWorldpayMotoAuthoriseRequest();

            var thrown = assertThrows(RuntimeException.class, () -> {
                templateBuilder.buildWith(motoOrder);
            });
            assertThat(thrown.getMessage(), is("Could not render template worldpay/WorldpayCancelOrderTemplate.xml"));
        }
    }

    private WorldpayMotoAuthoriseRequest createWorldpayMotoAuthoriseRequest() {
        return new WorldpayMotoAuthoriseRequest(
                USERNAME,
                PASSWORD,
                MERCHANT_CODE,
                ORDER_CODE,
                DESCRIPTION,
                String.valueOf(AMOUNT_IN_PENCE),
                CARD_NUMBER,
                EXPIRY_DATE_MONTH,
                EXPIRY_DATE_YEAR,
                CARDHOLDER_NAME,
                CVC);
    }
}
