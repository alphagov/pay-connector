package uk.gov.pay.connector.unit.service;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardExecutorService;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;

import static org.mockito.Mockito.mock;

public abstract class CardServiceTest {
    protected final String providerName = "provider";
    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    protected PaymentProviders mockedProviders = mock(PaymentProviders.class);

    protected ChargeDao mockedChargeDao = mock(ChargeDao.class);
    protected CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        return ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .build();
    }

    protected Matcher<GatewayResponse> aSuccessfulResponse() {
        return new TypeSafeMatcher<GatewayResponse>() {
            private GatewayResponse gatewayResponse;

            @Override
            protected boolean matchesSafely(GatewayResponse gatewayResponse) {
                this.gatewayResponse = gatewayResponse;
                return gatewayResponse.isSuccessful() && gatewayResponse.getError() == null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Success, but response was not successful: " + gatewayResponse.getError().getMessage());
            }
        };
    }

    protected Matcher<GatewayResponse> anUnSuccessfulResponse() {
        return new TypeSafeMatcher<GatewayResponse>() {
            private GatewayResponse gatewayResponse;

            @Override
            protected boolean matchesSafely(GatewayResponse gatewayResponse) {
                this.gatewayResponse = gatewayResponse;
                return !gatewayResponse.isSuccessful() && gatewayResponse.getError() != null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Response Error : " + gatewayResponse.getError().getMessage());
            }
        };
    }
}
