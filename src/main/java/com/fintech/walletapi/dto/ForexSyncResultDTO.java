package com.fintech.walletapi.dto;

import java.time.LocalDate;
import java.util.List;

public class ForexSyncResultDTO {

    private String provider;

    private LocalDate rateDate;

    private List<ExchangeRateDTO> updatedRates;

    private List<String> failedCurrencies;

    public ForexSyncResultDTO() {
    }

    public ForexSyncResultDTO(String provider, LocalDate rateDate,
                              List<ExchangeRateDTO> updatedRates, List<String> failedCurrencies) {
        this.provider = provider;
        this.rateDate = rateDate;
        this.updatedRates = updatedRates;
        this.failedCurrencies = failedCurrencies;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    public List<ExchangeRateDTO> getUpdatedRates() {
        return updatedRates;
    }

    public void setUpdatedRates(List<ExchangeRateDTO> updatedRates) {
        this.updatedRates = updatedRates;
    }

    public List<String> getFailedCurrencies() {
        return failedCurrencies;
    }

    public void setFailedCurrencies(List<String> failedCurrencies) {
        this.failedCurrencies = failedCurrencies;
    }
}
