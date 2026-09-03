package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayCardAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthorisePayload;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthorisePayloadFixture.aWorldpayMotoAuthorisePayloadFixture;

@ExtendWith(MockitoExtension.class)
class WorldpayCardAuthoriseRequestFactoryTest {

    @Mock
    private WorldpayMotoAuthorisePayloadFactory mockWorldpayMotoAuthorisePayloadFactory;
    
    private WorldpayCardAuthoriseRequestFactory worldpayCardAuthoriseRequestFactory;

    @BeforeEach
    void setUp() {
        worldpayCardAuthoriseRequestFactory = new WorldpayCardAuthoriseRequestFactory(mockWorldpayMotoAuthorisePayloadFactory);
    }

    @Test
    void shouldBuildWorldpayMotoAuthoriseRequestIfWebAndMotoAndNotSavePaymentInstrumentToAgreement() {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withMoto(true)
                .withSavePaymentInstrumentToAgreement(false)
                .build();

        WorldpayMotoAuthorisePayload worldpayMotoAuthorisePayload = aWorldpayMotoAuthorisePayloadFixture().build();

        given(mockWorldpayMotoAuthorisePayloadFactory.create(cardAuthorisationGatewayRequest)).willReturn(worldpayMotoAuthorisePayload);

        Optional<WorldpayCardAuthoriseRequest> result = worldpayCardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(worldpayMotoAuthorisePayload));
    }

    @Test
    void shouldBuildNothingIfNotMoto() {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withMoto(false)
                .withSavePaymentInstrumentToAgreement(false)
                .build();

        Optional<WorldpayCardAuthoriseRequest> result = worldpayCardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(false));
    }

    @Test
    void shouldBuildNothingIfSavePaymentInstrumentToAgreement() {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withMoto(true)
                .withSavePaymentInstrumentToAgreement(true)
                .build();

        Optional<WorldpayCardAuthoriseRequest> result = worldpayCardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(false));
    }

    @ParameterizedTest
    @EnumSource(value = AuthorisationMode.class, mode = EXCLUDE, names = { "WEB" })
    void shouldBuildNothingIfNotWeb(AuthorisationMode authorisationMode) {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(authorisationMode)
                .withMoto(true)
                .withSavePaymentInstrumentToAgreement(false)
                .build();

        Optional<WorldpayCardAuthoriseRequest> result = worldpayCardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(false));
    }

}
