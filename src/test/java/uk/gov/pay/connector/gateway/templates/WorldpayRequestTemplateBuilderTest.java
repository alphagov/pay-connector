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

class WorldpayRequestTemplateBuilderTest {
    private final WorldpayRequestTemplateBuilder worldpayRequestTemplateBuilder = new WorldpayRequestTemplateBuilder();

    @Nested
    class MotoAuthorisationRequest {
        @Test
        void shouldGenerateValidAuthoriseOrderRequestForWorldpayMotoAuthorisationRequest() {
            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture().build();

            assertThat(worldpayRequestTemplateBuilder.buildWith("/worldpay/WorldpayAuthoriseMotoOrderTemplate.ftlx", motoOrder))
                    .and(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_MOTO_AUTHORISATION_REQUEST))
                    .areIdentical();
        }
    }

    @Nested
    class TemplateBuilderAppliesAutoEscape {
        @Test
        void shouldGenerateValidAuthoriseOrderRequestWithAutoEscape() {
            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture()
                    .withMerchantCode("MERCHANT\"\"CODE")
                    .withCardholderName("Alec & Barley").build();
            String renderedAuthoriseOrder = worldpayRequestTemplateBuilder.buildWith("/worldpay/WorldpayAuthoriseMotoOrderTemplate.ftlx", motoOrder);
            
            assertThat(renderedAuthoriseOrder, containsString("MERCHANT&quot;&quot;CODE"));
            assertThat(renderedAuthoriseOrder, containsString("Alec &amp; Barley"));
        }
    }

    @Nested
    class InvalidActionsThrowingException {
        @Test
        void shouldThrowRuntimeExceptionWhenTemplateIsNotFound() {
            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture().build();
            var thrown = assertThrows(RuntimeException.class, () -> worldpayRequestTemplateBuilder.buildWith("/worldpay/NonExistentOrderTemplate.xml", motoOrder));
            
            assertThat(thrown.getMessage(), is("Could not load template /worldpay/NonExistentOrderTemplate.xml in dir /templates"));
        }

        @Test
        void shouldThrowRuntimeExceptionWhenCannotRenderTemplate() {
            WorldpayMotoAuthoriseRequest motoOrder = aWorldpayMotoAuthoriseRequestFixture().build();

            var thrown = assertThrows(RuntimeException.class, () -> {
                worldpayRequestTemplateBuilder.buildWith("/worldpay/WorldpayCancelOrderTemplate.xml", motoOrder);
            });
            
            assertThat(thrown.getMessage(), is("Could not render template worldpay/WorldpayCancelOrderTemplate.xml"));
        }
    }
}
