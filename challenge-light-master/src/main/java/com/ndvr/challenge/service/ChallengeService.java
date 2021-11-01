package com.ndvr.challenge.service;

import static java.time.LocalDate.now;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

	public List<Pricing> getProjectedAssetData(Asset asset, int numberOfMonths) {
		log.info("Generating projected price data");
		List<Pricing> historicalList = getHistoricalAssetData(asset, now().minusYears(5), now());
		NavigableMap<LocalDate, BigDecimal> eomReturnMap = new TreeMap<LocalDate, BigDecimal>();
		NavigableMap<LocalDate, BigDecimal> historicalMap = historicalList.stream().collect(Collectors
				.toMap(Pricing::getTradeDate, Pricing::getClosePrice, (oldValue, newValue) -> newValue, TreeMap::new));

		// 1. Month over Month Price Changes.
		Map.Entry<LocalDate, BigDecimal> entry = historicalMap.firstEntry();
		while (entry != null) {
			Map.Entry<LocalDate, BigDecimal> eomReturn = getEOMDate(entry.getKey(), historicalMap);
			BigDecimal startPrice = entry.getValue();
			BigDecimal endPrice = eomReturn.getValue();
			eomReturnMap.put(eomReturn.getKey(), ((endPrice.subtract(startPrice)).multiply(BigDecimal.valueOf(100)))
					.divide(startPrice, 2, RoundingMode.HALF_UP));
			entry = historicalMap.ceilingEntry(eomReturn.getKey().plusDays(1));
		}
		log.info("Fetching historical eom price data:" + eomReturnMap);

		// 2. Run Single Scenario using historical data and month over month price
		// change.
		projectSingleScenario(historicalMap, eomReturnMap, numberOfMonths);

		// 3. Run multiple scenarios using historical data and month over month price
		// change and pick the scenario with max closing price.
		return projectMaxPriceScenario(historicalMap, eomReturnMap, numberOfMonths);
	}

	/*
	 * 1. Run Single Scenario using historical data and month over month price
	 * change.
	 */
	private void projectSingleScenario(NavigableMap<LocalDate, BigDecimal> historicalMap,
			NavigableMap<LocalDate, BigDecimal> eomReturnMap, int numberOfMonths) {

		Map.Entry<LocalDate, BigDecimal> currentPriceEntry = historicalMap.lastEntry();
		List<BigDecimal> eomReturnList = eomReturnMap.values().stream().collect(Collectors.toList());

		List<Pricing> pricingList = new ArrayList<Pricing>();
		Random ran = new Random();
		BigDecimal curMonEndPrice = BigDecimal.ZERO;
		BigDecimal curMonStartPrice = currentPriceEntry.getValue();
		LocalDate curMonStartDate = currentPriceEntry.getKey();
		LocalDate projEndDate = curMonStartDate.plusMonths(numberOfMonths);

		while (curMonStartDate.isBefore(projEndDate)) {
			BigDecimal eomReturn = eomReturnList.get(ran.nextInt(eomReturnList.size()));
			LocalDate curMonEndDate = curMonStartDate
					.withDayOfMonth(curMonStartDate.getMonth().length(curMonStartDate.isLeapYear()));
			curMonEndPrice = curMonStartPrice.add(curMonStartPrice.multiply(eomReturn.divide(BigDecimal.valueOf(100))));
			curMonStartDate = curMonEndDate.plusDays(1);
			pricingList.add(Pricing.builder().closePrice(curMonEndPrice).tradeDate(curMonEndDate).build());
		}

		log.info("Scenario > ClosingPrice={},projEndDate={}", curMonEndPrice, curMonEndPrice);
	}

	/*
	 * 1. Run multiple scenarios using historical data and month over month price
	 * change and pick the scenario with max closing price. 2. Sample size is 1000
	 * scenario projecting forward the input number of months.
	 * 
	 */
	private List<Pricing> projectMaxPriceScenario(NavigableMap<LocalDate, BigDecimal> historicalMap,
			NavigableMap<LocalDate, BigDecimal> eomReturnMap, int numberOfMonths) {

		Map.Entry<LocalDate, BigDecimal> currentPriceEntry = historicalMap.lastEntry();
		List<BigDecimal> eomReturnList = eomReturnMap.values().stream().collect(Collectors.toList());
		NavigableMap<String, List<Pricing>> scenarioPrices = new TreeMap<String, List<Pricing>>();
		BigDecimal highestClosingPrice = BigDecimal.ZERO;
		BigDecimal lowestClosingPrice = BigDecimal.ZERO;
		BigDecimal medianClosingPrice = BigDecimal.ZERO;

		List<BigDecimal> sortedList = new ArrayList<BigDecimal>();

		for (int i = 0; i < 1000; i++) {
			List<Pricing> pricingList = new ArrayList<Pricing>();
			Random ran = new Random();
			BigDecimal curMonEndPrice = BigDecimal.ZERO;
			BigDecimal curMonStartPrice = currentPriceEntry.getValue();
			LocalDate curMonStartDate = currentPriceEntry.getKey();
			LocalDate projEndDate = curMonStartDate.plusMonths(numberOfMonths);

			while (curMonStartDate.isBefore(projEndDate)) {
				BigDecimal eomReturn = eomReturnList.get(ran.nextInt(eomReturnList.size()));
				LocalDate curMonEndDate = curMonStartDate
						.withDayOfMonth(curMonStartDate.getMonth().length(curMonStartDate.isLeapYear()));
				curMonEndPrice = curMonStartPrice
						.add(curMonStartPrice.multiply(eomReturn.divide(BigDecimal.valueOf(100))));
				curMonStartDate = curMonEndDate.plusDays(1);
				pricingList.add(Pricing.builder().closePrice(curMonEndPrice).tradeDate(curMonEndDate).build());
			}
			sortedList.add(curMonEndPrice);

			if ((i == 0) || curMonEndPrice.compareTo(highestClosingPrice) == 1) {
				highestClosingPrice = curMonEndPrice;
				scenarioPrices.put("MAX", pricingList);
			}

			if ((i == 0) || curMonEndPrice.compareTo(lowestClosingPrice) == -1) {
				lowestClosingPrice = curMonEndPrice;
				scenarioPrices.put("MIN", pricingList);
			}
		}

		sortedList.sort(Comparator.naturalOrder());
		int size = sortedList.size();
		if (size % 2 != 0)
			medianClosingPrice = sortedList.get(size / 2);
		else
			medianClosingPrice = (sortedList.get(size / 2 - 1).add(sortedList.get(size / 2)))
					.divide(BigDecimal.valueOf(2));

		log.info("highestClosingPrice={},lowestClosingPrice={},medianClosingPrice={}", highestClosingPrice,
				lowestClosingPrice, medianClosingPrice);

		return scenarioPrices.get("MAX");
	}

	private Map.Entry<LocalDate, BigDecimal> getEOMDate(LocalDate startDate,
			NavigableMap<LocalDate, BigDecimal> historicalMap) {
		LocalDate eomDate = startDate.withDayOfMonth(startDate.getMonth().length(startDate.isLeapYear()));
		while (!historicalMap.containsKey(eomDate)) {
			eomDate = eomDate.minusDays(1);
		}
		return historicalMap.ceilingEntry(eomDate);
	}

}
