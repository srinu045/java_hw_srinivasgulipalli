package com.ndvr.challenge.dataprovider;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ndvr.challenge.model.Pricing;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Stable client libraries (e.g. https://financequotes-api.com/) are broken since Yahoo discontinued
 * support for the public API.
 * The below code works for now -- if not, please reach out to us.
 */
@Service
@Slf4j
public class YahooFinanceClient {

    private static final String PRICE_FORMAT_URL = "https://query1.finance.yahoo.com/v7/finance/download/%s?period1=%d&period2=%d&interval=1d&events=history&interval=1d&crumb=%s";

    @Setter
    private YahooFinanceSession session;
    private HttpHandler httpHandler;

    public YahooFinanceClient(HttpHandler httpHandler) {
        this.httpHandler = httpHandler;

        this.session = new YahooFinanceSession(httpHandler);
    }

    private String constructURL(String formatURL, String ticker, LocalDate from, LocalDate to) {
        long fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long toEpoch = to.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        String crumb = (session.getCrumb() != null) ? HttpHandler.urlEncodeString(session.getCrumb()) : "";
        String encodedTicker = HttpHandler.urlEncodeString(ticker);
        return String.format(formatURL, encodedTicker, fromEpoch, toEpoch, crumb);
    }

    private HttpEntity fetchURL(String url, String symbol, LocalDate fromDate, LocalDate toDate) {

        HttpGet request = new HttpGet(url);
        HttpResponse response = httpHandler.fetchResponse(request);
        HttpStatus statusCode = HttpStatus.valueOf(response.getStatusLine().getStatusCode());
        if (statusCode == HttpStatus.UNAUTHORIZED) {
            log.debug("Unauthorized response using crumb and cookies:");
            log.debug("crumb: {} cookies: {}", session.getCrumb(), httpHandler.getCookieStore().getCookies());
            session.invalidate();
            session.acquireCrumbWithTicker(symbol);
            log.info("Retrying connection after unauthorized response");

            request.setURI(URI.create(constructURL(PRICE_FORMAT_URL, symbol, fromDate, toDate))); // Acquire new crumb
            request.reset();
            EntityUtils.consumeQuietly(response.getEntity());
            response = httpHandler.fetchResponse(request);
        } else if (statusCode == HttpStatus.NOT_FOUND) {
            EntityUtils.consumeQuietly(response.getEntity());
            return null;
        }
        return response.getEntity();
    }


    public List<Pricing> fetchPriceData(String symbol, LocalDate fromDate, LocalDate toDate) {
        log.info("Acquiring price data for {} from {} to {}", symbol, fromDate, toDate);
        session.acquireCrumbWithTicker(symbol);

        String priceURL = constructURL(PRICE_FORMAT_URL, symbol, fromDate, toDate);
        HttpEntity entity = fetchURL(priceURL, symbol, fromDate, toDate);
        if (entity == null) {
            log.warn("No price data available for {} from {} to {}", symbol, fromDate, toDate);
            return emptyList();
        }
        
        try {
            return parsePriceDataList(entity.getContent());
        } catch(IOException e) {
            log.error("Could not fetch price data for {} from {} to {}", symbol, fromDate, toDate, e);
            return emptyList();
        }
    }
    
    private List<Pricing> parsePriceDataList(InputStream inputStream) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream))) {
            buffer.readLine(); // skip header line
            return buffer.lines()
                    .map(this::parsePriceData)
                    .filter(Optional::isPresent).map(Optional::get)
                    .collect(toList());
        }
    }
    
    private Optional<Pricing> parsePriceData(String line) {
        
        String[] parts = line.split(",");
        
        try {
            return Optional.of(Pricing.builder()
                    .tradeDate(LocalDate.parse(parts[0]))
                    .openPrice(new BigDecimal(parts[1]))
                    .highPrice(new BigDecimal(parts[2]))
                    .lowPrice(new BigDecimal(parts[3]))
                    .closePrice(new BigDecimal(parts[4]))
                    .build());
        } catch(Exception ex) {
            log.warn("Failed to parse price data line {}: {}", line, ex.getMessage());
            return Optional.empty();
        }
    }
    
}
