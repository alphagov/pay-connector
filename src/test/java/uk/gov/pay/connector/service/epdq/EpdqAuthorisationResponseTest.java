package uk.gov.pay.connector.service.epdq;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class EpdqAuthorisationResponseTest {

    @Test
    public void shouldMapToAuthorisedSuccess_fromAuthorisedAuth3DResult() {
        EpdqAuthorisationResponse response = EpdqAuthorisationResponse.createPost3dsResponseFor(Auth3dsDetails.Auth3DResult.AUTHORISED);
        assertThat(response.authoriseStatus(),is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
    }

    @Test
    public void shouldMapToAuthorisedRejected_fromDeclinedAuth3DResult() {
        EpdqAuthorisationResponse response = EpdqAuthorisationResponse.createPost3dsResponseFor(Auth3dsDetails.Auth3DResult.DECLINED);
        assertThat(response.authoriseStatus(),is(BaseAuthoriseResponse.AuthoriseStatus.REJECTED));
    }

    @Test
    public void shouldMapToAuthorisedError_fromErrorAuth3DResult() {
        EpdqAuthorisationResponse response = EpdqAuthorisationResponse.createPost3dsResponseFor(Auth3dsDetails.Auth3DResult.ERROR);
        assertThat(response.authoriseStatus(),is(BaseAuthoriseResponse.AuthoriseStatus.ERROR));
    }

    @Test
    public void shouldMapToAuthorisedError_fromNullAuth3DResult() {
        EpdqAuthorisationResponse response = EpdqAuthorisationResponse.createPost3dsResponseFor(null);
        assertThat(response.authoriseStatus(),is(BaseAuthoriseResponse.AuthoriseStatus.ERROR));
    }
}
