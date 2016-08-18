package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static org.mockito.Mockito.mock;

public abstract class CardServiceTest {
    protected final String providerName = "provider";
    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    protected PaymentProviders mockedProviders = mock(PaymentProviders.class);

    protected ConfirmationDetailsService mockConfirmationDetailsService = mock(ConfirmationDetailsService.class);
    protected ChargeDao mockedChargeDao = mock(ChargeDao.class);
    protected CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        return ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .build();
    }
}
