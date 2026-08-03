package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.CardAuthoriseRequestFactory;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCardAuthoriseRequestFactory;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthoriseRequestFixture.aWorldpayMotoAuthoriseRequestFixture;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class CardAuthoriseRequestFactoryTest {

    @Mock
    private WorldpayCardAuthoriseRequestFactory mockWorldpayCardAuthoriseRequestFactory;

    private CardAuthoriseRequestFactory cardAuthoriseRequestFactory;

    @BeforeEach
    void setUp() {
        cardAuthoriseRequestFactory = new CardAuthoriseRequestFactory(mockWorldpayCardAuthoriseRequestFactory);
    }

    @Test
    void shouldBuildWorldpayAuthoriseRequestIfWorldpay() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withGatewayAccount(gatewayAccountEntity)
                .build();

        WorldpayCardAuthoriseRequest worldpayAuthoriseRequest = aWorldpayMotoAuthoriseRequestFixture().build();

        given(mockWorldpayCardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest))
                .willReturn(Optional.of(worldpayAuthoriseRequest));

        Optional<? extends CardAuthoriseRequest> authoriseRequest = cardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(authoriseRequest.isPresent(), is(true));
        assertThat(authoriseRequest.get(), is(worldpayAuthoriseRequest));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentGatewayName.class,  mode = EXCLUDE, names = { "WORLDPAY" })
    void shouldBuildNothingIfNotWorldpay(PaymentGatewayName paymentGatewayName) {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(paymentGatewayName.getName())
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = aCardAuthorisationGatewayRequest()
                .withGatewayAccount(gatewayAccountEntity)
                .build();

        Optional<? extends CardAuthoriseRequest> authoriseRequest = cardAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);

        assertThat(authoriseRequest.isEmpty(), is(true));
    }

}
