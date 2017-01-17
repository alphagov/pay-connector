package uk.gov.pay.connector.service.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.domain.Card;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.service.epdq.EpdqPayloadDefinitionForNewOrder.*;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPayloadDefinitionForNewOrderTest {

    private static final String CARD_NO = "42";
    private static final String CVC = "321";
    private static final String END_DATE = "01/18";

    private static final String AMOUNT = "500";
    private static final String OPERATION_TYPE = "RES";
    private static final String ORDER_ID = "OrderId";
    private static final String PSP_ID = "PspId";
    private static final String PASSWORD = "password";
    private static final String USER_ID = "User";

    @Mock
    private EpdqOrderRequestBuilder.EpdqTemplateData mockTemplateData;
    @Mock
    private Card mockCard;

    private final EpdqPayloadDefinitionForNewOrder epdqPayloadDefinitionForNewOrder = new EpdqPayloadDefinitionForNewOrder();

    @Test
    public void shouldExtractParametersFromTemplate() {
        when(mockTemplateData.getCard()).thenReturn(mockCard);
        when(mockCard.getCardNo()).thenReturn(CARD_NO);
        when(mockCard.getCvc()).thenReturn(CVC);
        when(mockCard.getEndDate()).thenReturn(END_DATE);

        when(mockTemplateData.getAmount()).thenReturn(AMOUNT);
        when(mockTemplateData.getOperationType()).thenReturn(OPERATION_TYPE);
        when(mockTemplateData.getOrderId()).thenReturn(ORDER_ID);
        when(mockTemplateData.getPspId()).thenReturn(PSP_ID);
        when(mockTemplateData.getPassword()).thenReturn(PASSWORD);
        when(mockTemplateData.getUserId()).thenReturn(USER_ID);

        ImmutableList<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(mockTemplateData);

        assertThat(result, is(ImmutableList.builder().add(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CURRENCY_KEY, "GBP"),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(ED_KEY, END_DATE),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
                .build()));
    }


}