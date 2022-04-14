package uk.gov.pay.connector.token;

import uk.gov.pay.connector.charge.exception.OneTimeTokenAlreadyUsedException;
import uk.gov.pay.connector.charge.exception.OneTimeTokenInvalidException;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import javax.inject.Inject;

public class TokenService {

    private final TokenDao tokenDao;

    @Inject
    public TokenService(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    public void validateToken(String oneTimeToken) {
        tokenDao.findByTokenId(oneTimeToken)
                .map((TokenEntity tokenEntity) -> {
                    if (tokenEntity.isUsed()) {
                        throw new OneTimeTokenAlreadyUsedException();
                    }
                    return tokenEntity;
                })
                .orElseThrow(OneTimeTokenInvalidException::new);
    }
}
