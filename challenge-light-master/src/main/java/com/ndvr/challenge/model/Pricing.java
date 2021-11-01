package com.ndvr.challenge.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonInclude( Include.NON_EMPTY )
public class Pricing {

    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal lowPrice;
    private BigDecimal highPrice;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate tradeDate;
}
