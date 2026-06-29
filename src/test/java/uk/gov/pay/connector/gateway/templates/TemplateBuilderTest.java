package uk.gov.pay.connector.gateway.templates;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequestFixture.aWorldpayMotoAuthoriseRequestFixture;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_MOTO_AUTHORISATION_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;
import static uk.gov.pay.connector.util.XmlAssertions.assertThat;

class TemplateBuilderTest {

    @Nested
    class MotoAuthorisationRequest {
        @Test
        void shouldGenerateValidAuthoriseOrderRequestForWorldpayMotoAuthorisationRequest() {
            TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/WorldpayAuthoriseMotoOrderTemplate.ftlx");
            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture().build();

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

            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture()
                    .withMerchantCode("MERCHANT\"\"CODE")
                    .withCardholderName("Alec & Barley").build();
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
            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture().build();

            var thrown = assertThrows(RuntimeException.class, () -> {
                templateBuilder.buildWith(motoOrder);
            });
            assertThat(thrown.getMessage(), is("Could not render template worldpay/WorldpayCancelOrderTemplate.xml"));
        }
    }

}
