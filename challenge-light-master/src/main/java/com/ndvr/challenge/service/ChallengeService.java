package com.ndvr.challenge.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ndvr.challenge.dataprovider.YahooFinanceClient;
import com.ndvr.challenge.model.Asset;
import com.ndvr.challenge.model.Pricing;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class ChallengeService {

    private final YahooFinanceClient dataProvider;

    public List<Pricing> getHistoricalAssetData(Asset asset, LocalDate fromDate, LocalDate toDate) {
        log.info("Fetching historical price data");
        return dataProvider.fetchPriceData(asset.getSymbol(), fromDate, toDate);
    }
    
    public List<Pricing> getProjectedAssetData(Asset asset) {
        log.info("Generating projected price data");
        // TODO Implement getProjectedAssetData()
        return null;
    }

}
