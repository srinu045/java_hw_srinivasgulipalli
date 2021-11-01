package com.ndvr.challenge.dataprovider;

import static java.math.BigDecimal.valueOf;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import com.ndvr.challenge.dataprovider.HttpHandler;
import com.ndvr.challenge.dataprovider.YahooFinanceClient;
import com.ndvr.challenge.dataprovider.YahooFinanceSession;
import com.ndvr.challenge.model.Pricing;


public class YahooFinanceClientUnitTests {

    @InjectMocks
    private YahooFinanceClient client;

    @Mock
    private YahooFinanceSession session;

    @Mock
    private HttpHandler httpHandler;

    private final String SYMBOL = "LOGM";
    private final LocalDate FROM = LocalDate.parse("2017-01-01");
    private final LocalDate TO = LocalDate.parse("2017-01-31");

    
    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        client.setSession(session);
    }

    @Test
    public void testFetchPriceData() throws IOException {
        LocalDate date1 = LocalDate.parse("2017-01-01");
        LocalDate date2 = LocalDate.parse("2017-01-02");
        BigDecimal close1 = BigDecimal.valueOf(100);
        BigDecimal close2 = BigDecimal.valueOf(101);

        String input = "Date,Open,High,Low,Close,Adj Close,Volume\n"
                     + date1 + ",1,2,3," + close1 + ",5,6\n"
                     + date2 + ",1,2,3," + close2 + ",11,12\n"
                     + "wrongDate,1,2,3,4,5,6\n"
                     + "1,2,3,4,5\n" // invalid number of columns
                     + date2 + ",1,2,3,wrongClose,5,6\n";

        doNothing().when(session).acquireCrumbWithTicker(SYMBOL);

        HttpResponse httpResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);
        
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.OK.value());
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        doReturn(httpResponse).when(httpHandler).fetchResponse(anyObject());
        
        InputStream stream = new ByteArrayInputStream(input.getBytes());

        doReturn(entity).when(httpResponse).getEntity();
        doReturn(stream).when(entity).getContent();

        List<Pricing> dataSet = client.fetchPriceData(SYMBOL, FROM, TO);
        assertEquals(2, dataSet.size());

        assertEquals(new Pricing(valueOf(1), close1, valueOf(3), valueOf(2), date1), dataSet.get(0));
        assertEquals(new Pricing(valueOf(1), close2, valueOf(3), valueOf(2), date2), dataSet.get(1));
    }

    @Test
    public void testFetchPriceData_IOException() throws IOException {

        doNothing().when(session).acquireCrumbWithTicker(SYMBOL);

        HttpResponse httpResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);
        
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.OK.value());
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        doReturn(httpResponse).when(httpHandler).fetchResponse(anyObject());

        doReturn(entity).when(httpResponse).getEntity();
        doThrow(IOException.class).when(entity).getContent();

        List<Pricing> dataSet = client.fetchPriceData(SYMBOL, FROM, TO);

        assertEquals(0, dataSet.size());
    }

    @Test
    public void testFetchPriceData_ExpiredSession() throws IOException {
        LocalDate date1 = LocalDate.parse("2017-01-01");
        LocalDate date2 = LocalDate.parse("2017-01-02");
        BigDecimal close1 = BigDecimal.valueOf(100);
        BigDecimal close2 = BigDecimal.valueOf(101);

        String input = "Date,Open,High,Low,Close,Adj Close,Volume\n"
                     + date1 + ",1,2,3," + close1 + ",5,6\n"
                     + date2 + ",1,2,3," + close2 + ",11,12\n";

        doNothing().when(session).acquireCrumbWithTicker(SYMBOL);

        HttpResponse httpResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);
        
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.UNAUTHORIZED.value());
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        doReturn(httpResponse).when(httpHandler).fetchResponse(anyObject());
        doReturn(new BasicCookieStore()).when(httpHandler).getCookieStore();
        
        InputStream stream = new ByteArrayInputStream(input.getBytes());

        doReturn(entity).when(httpResponse).getEntity();
        doReturn(stream).when(entity).getContent();

        List<Pricing> dataSet = client.fetchPriceData(SYMBOL, FROM, TO);
        assertEquals(2, dataSet.size());

        assertEquals(new Pricing(valueOf(1), close1, valueOf(3), valueOf(2), date1), dataSet.get(0));
        assertEquals(new Pricing(valueOf(1), close2, valueOf(3), valueOf(2), date2), dataSet.get(1));
    }

    @Test
    public void testFetchPriceData_Http404() {
        doNothing().when(session).acquireCrumbWithTicker(SYMBOL);

        HttpResponse httpResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND.value());
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        doReturn(httpResponse).when(httpHandler).fetchResponse(anyObject());

        List<Pricing> dataSet = client.fetchPriceData(SYMBOL, FROM, TO);

        assertEquals(0, dataSet.size());
    }

}
