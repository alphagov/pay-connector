package uk.gov.pay.connector.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.exception.OneTimeTokenAlreadyUsedException;
import uk.gov.pay.connector.charge.exception.OneTimeTokenInvalidException;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    TokenService tokenService;

    @Mock
    TokenDao tokenDao;

    @Test
    void validateTokenShouldThrowOneTimeTokenInvalidExceptionIfTokenDoesNotExist() {
        when(tokenDao.findByTokenId(any())).thenReturn(Optional.empty());

        assertThrows(OneTimeTokenInvalidException.class, () -> tokenService.validateToken("one-time-token-123"),
                "Should throw OneTimeTokenInvalidException if token is not found");
    }

    @Test
    void validateTokenShouldThrowOneTimeTokenAlreadyUsedExceptedIfTokenIsMarkedAsUsed() {
        TokenEntity tokenEntity = new TokenEntity();
        tokenEntity.setUsed(true);
        when(tokenDao.findByTokenId(any())).thenReturn(Optional.of(tokenEntity));

        assertThrows(OneTimeTokenAlreadyUsedException.class, () -> tokenService.validateToken("one-time-token-123"),
                "Should throw OneTimeTokenAlreadyUsedException if token is already user");
    }
}
