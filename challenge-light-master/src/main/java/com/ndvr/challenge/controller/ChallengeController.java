package com.ndvr.challenge.controller;

import lombok.AllArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.ndvr.challenge.model.Asset;
import com.ndvr.challenge.model.Pricing;
import com.ndvr.challenge.service.ChallengeService;

import static java.time.LocalDate.now;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@AllArgsConstructor
@RequestMapping("market-data")
public class ChallengeController {

    private final ChallengeService challengeService;

    @RequestMapping("{asset}/historical")
    public List<Pricing> getHistoricalAssetData(@PathVariable Asset asset, 
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> endDate) {
        
        return challengeService.getHistoricalAssetData(asset, 
                startDate.orElse(now().minusYears(5)),
                endDate.orElse(now()));
    }

    @RequestMapping("{asset}/projected")
    public List<Pricing> getProjectedAssetData(@PathVariable Asset asset,@RequestParam(required=false,defaultValue="240") int numberOfMonths) {
        return challengeService.getProjectedAssetData(asset,numberOfMonths);
    }
}
