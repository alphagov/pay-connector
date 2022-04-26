package uk.gov.pay.connector.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenAlreadyUsedException;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenInvalidException;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenUsageInvalidForMotoApiException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    TokenService tokenService;

    @Mock
    TokenDao tokenDao;

    @Test
    void validateTokenForMotoApiShouldSetTokenAsUsedForValidOneTimeToken() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withAuthorisationMode(MOTO_API)
                .build();

        TokenEntity tokenEntity = new TokenEntity();
        tokenEntity.setUsed(false);
        tokenEntity.setChargeEntity(chargeEntity);

        when(tokenDao.findByTokenId(any())).thenReturn(Optional.of(tokenEntity));

        tokenService.validateAndMarkTokenAsUsedForMotoApi("one-time-token-123");

        assertThat(tokenEntity.isUsed(), is(true));
    }

    @Test
    void validateTokenForMotoApiShouldThrowOneTimeTokenInvalidExceptionIfTokenDoesNotExist() {
        when(tokenDao.findByTokenId(any())).thenReturn(Optional.empty());

        assertThrows(OneTimeTokenInvalidException.class, () -> tokenService.validateAndMarkTokenAsUsedForMotoApi("one-time-token-123"),
                "Should throw OneTimeTokenInvalidException if token is not found");
    }

    @Test
    void validateTokenForMotoApiShouldThrowOneTimeTokenAlreadyUsedExceptedIfTokenIsMarkedAsUsed() {
        TokenEntity tokenEntity = new TokenEntity();
        tokenEntity.setUsed(true);
        when(tokenDao.findByTokenId(any())).thenReturn(Optional.of(tokenEntity));

        assertThrows(OneTimeTokenAlreadyUsedException.class, () -> tokenService.validateAndMarkTokenAsUsedForMotoApi("one-time-token-123"),
                "Should throw OneTimeTokenAlreadyUsedException if token is already user");
    }

    @Test
    void validateTokenForMotoApiShouldThrowExceptionIfChargeAssociateIsNotCreatedWithMotoApiAuthorisationMode() {
        TokenEntity tokenEntity = new TokenEntity();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withAuthorisationMode(null)
                .build();

        tokenEntity.setUsed(false);
        tokenEntity.setChargeEntity(chargeEntity);

        when(tokenDao.findByTokenId(any())).thenReturn(Optional.of(tokenEntity));

        assertThrows(OneTimeTokenUsageInvalidForMotoApiException.class, () -> tokenService.validateAndMarkTokenAsUsedForMotoApi("one-time-token-123"),
                "Should throw exception if token is not for charge with authorisation_mode=MOTO_API");
    }
}
