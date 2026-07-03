package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequestFixture.aWorldpayMotoAuthoriseRequestFixture;

@ExtendWith(MockitoExtension.class)
class WorldpayAuthoriseRequestFactoryTest {

    @Mock
    private WorldpayMotoAuthoriseRequestFactory mockWorldpayMotoAuthoriseRequestFactory;
    
    private WorldpayAuthoriseRequestFactory worldpayAuthoriseRequestFactory;

    @BeforeEach
    void setUp() {
        worldpayAuthoriseRequestFactory = new WorldpayAuthoriseRequestFactory(mockWorldpayMotoAuthoriseRequestFactory);
    }

    @Test
    void shouldBuildWorldpayMotoAuthoriseRequestIfWebAndMoto() {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withMoto(true)
                .build();

        WorldpayMotoAuthoriseRequest worldpayMotoAuthoriseRequest = aWorldpayMotoAuthoriseRequestFixture().build();

        given(mockWorldpayMotoAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest)).willReturn(worldpayMotoAuthoriseRequest);

        Optional<WorldpayAuthoriseRequest> result = worldpayAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(worldpayMotoAuthoriseRequest));
    }

    @Test
    void shouldBuildNothingIfNotMoto() {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withMoto(false)
                .build();

        Optional<WorldpayAuthoriseRequest> result = worldpayAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(false));
    }

    @ParameterizedTest
    @EnumSource(value = AuthorisationMode.class, mode = EXCLUDE, names = { "WEB" })
    void shouldBuildNothingIfNotWeb(AuthorisationMode authorisationMode) {
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withAuthorisationMode(authorisationMode)
                .withMoto(true)
                .build();

        Optional<WorldpayAuthoriseRequest> result = worldpayAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(result.isPresent(), is(false));
    }

}
