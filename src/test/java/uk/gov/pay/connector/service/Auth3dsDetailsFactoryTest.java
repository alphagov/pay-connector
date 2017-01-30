package uk.gov.pay.connector.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class Auth3dsDetailsFactoryTest {

    private static final String PA_REQUEST = "pa-request";
    private static final String ISSUER_URL = "https://issuerurl.example/3ds";

    @Mock private BaseAuthoriseResponse mockResponse;

    private final Auth3dsDetailsFactory auth3dsDetailsFactory = new Auth3dsDetailsFactory();

    @Test
    public void shouldReturnAuth3dsDetailsIfPaRequestAndIssuerUrlPresent() {
        when(mockResponse.get3dsPaRequest()).thenReturn(PA_REQUEST);
        when(mockResponse.get3dsIssuerUrl()).thenReturn(ISSUER_URL);

        Optional<Auth3dsDetailsEntity> auth3dsDetailsEntity = auth3dsDetailsFactory.create(mockResponse);

        assertThat(auth3dsDetailsEntity.get().getPaRequest(), is(PA_REQUEST));
        assertThat(auth3dsDetailsEntity.get().getIssuerUrl(), is(ISSUER_URL));
    }

    @Test
    public void shouldReturnEmptyIfPaRequestAndIssuerUrlNotPresent() {
        when(mockResponse.get3dsPaRequest()).thenReturn(null);
        when(mockResponse.get3dsIssuerUrl()).thenReturn(null);

        Optional<Auth3dsDetailsEntity> auth3dsDetailsEntity = auth3dsDetailsFactory.create(mockResponse);

        assertThat(auth3dsDetailsEntity, is(Optional.empty()));
    }
}
