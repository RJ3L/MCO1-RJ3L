package com.fintech.walletapi.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response body of {@code POST /api/v1/forex/rates/sync}: which rates were
 * refreshed from the live provider and which currencies kept their old rate.
 */
public class ForexSyncResultDTO {

    /** Where the rates came from. */
    private String provider;

    /** Publication date of the newest rate received, or {@code null} if none. */
    private LocalDate rateDate;

    /** Rates written to the exchange-rate table. */
    private List<ExchangeRateDTO> updatedRates;

    /** Currencies that could not be refreshed; their stored rate is unchanged. */
    private List<String> failedCurrencies;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public ForexSyncResultDTO() {
    }

    /**
     * Creates a fully populated result.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param provider         where the rates came from
     * @param rateDate         publication date of the newest rate received
     * @param updatedRates     rates written to the table
     * @param failedCurrencies currencies that kept their old rate
     */
    public ForexSyncResultDTO(String provider, LocalDate rateDate,
                              List<ExchangeRateDTO> updatedRates, List<String> failedCurrencies) {
        this.provider = provider;
        this.rateDate = rateDate;
        this.updatedRates = updatedRates;
        this.failedCurrencies = failedCurrencies;
    }

    /**
     * Returns the provider name.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the provider
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Sets the provider name.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> the corresponding getter returns the supplied value.</p>
     *
     * @param provider the provider
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Returns the publication date of the newest rate received.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the date, or {@code null} if nothing was received
     */
    public LocalDate getRateDate() {
        return rateDate;
    }

    /**
     * Sets the publication date.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> the corresponding getter returns the supplied value.</p>
     *
     * @param rateDate the date
     */
    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    /**
     * Returns the rates written to the table.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the updated rates
     */
    public List<ExchangeRateDTO> getUpdatedRates() {
        return updatedRates;
    }

    /**
     * Sets the rates written to the table.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> the corresponding getter returns the supplied value.</p>
     *
     * @param updatedRates the updated rates
     */
    public void setUpdatedRates(List<ExchangeRateDTO> updatedRates) {
        this.updatedRates = updatedRates;
    }

    /**
     * Returns the currencies that could not be refreshed.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the failed currency codes
     */
    public List<String> getFailedCurrencies() {
        return failedCurrencies;
    }

    /**
     * Sets the currencies that could not be refreshed.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> the corresponding getter returns the supplied value.</p>
     *
     * @param failedCurrencies the failed currency codes
     */
    public void setFailedCurrencies(List<String> failedCurrencies) {
        this.failedCurrencies = failedCurrencies;
    }
}
