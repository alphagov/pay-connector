package uk.gov.pay.connector.charge.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class CorporateCardSurchargeCalculatorTest {
    
    @Mock
    private final FixedSurchargeCalculator fixedSurchargeCalculator = new FixedSurchargeCalculator();
    private final AuthCardDetails authCardDetails = AuthCardDetailsFixture
            .anAuthCardDetails()
            .withCardType(PayersCardType.CREDIT)
            .withCorporateCard(Boolean.TRUE)
            .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.PREPAID)
            .build();
    
    private final ChargeEntity chargeEntity = ChargeEntityFixture
            .aValidChargeEntity()
            .build();

    @Test
    public void shouldReturnOptionalLongForSurchargeGreaterThanZero() {
        given(fixedSurchargeCalculator.calculateSurcharge(any(AuthCardDetails.class), any(ChargeEntity.class)))
                .willReturn(100L);
        Optional<Long> surcharge = CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor(authCardDetails, chargeEntity);
        assertThat(surcharge.isPresent(), is(true));
        assertThat(surcharge.get(), is(100L));
    }

    @Test
    public void shouldNotReturnOptionalLongForSurchargeIsZero() {
        given(fixedSurchargeCalculator.calculateSurcharge(any(AuthCardDetails.class), any(ChargeEntity.class)))
                .willReturn(0L);
        Optional<Long> surcharge = CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor(authCardDetails, chargeEntity);
        assertThat(surcharge.isPresent(), is(false));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeGreaterThanZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(250L).build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().get();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsNull() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(0L).build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }
}
