package uk.gov.pay.connector.unit.service;

import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;

import static org.assertj.core.util.Maps.newHashMap;
import static org.mockito.Mockito.mock;

public abstract class CardServiceTest {
    protected final String providerName = "providerName";
    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    protected PaymentProviders mockedProviders = mock(PaymentProviders.class);

    protected GatewayAccountDao mockedAccountDao = mock(GatewayAccountDao.class);
    protected ChargeDao mockedChargeDao = mock(ChargeDao.class);

    protected GatewayAccountEntity createNewAccount() {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountEntity.setId(RandomUtils.nextLong());
        gatewayAccountEntity.setGatewayName(providerName);
        gatewayAccountEntity.setCredentials(newHashMap());
        return gatewayAccountEntity;
    }

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeId);
        chargeEntity.setAmount(500L);
        chargeEntity.setStatus(status);
        chargeEntity.setGatewayAccount(createNewAccount());
        return chargeEntity;

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