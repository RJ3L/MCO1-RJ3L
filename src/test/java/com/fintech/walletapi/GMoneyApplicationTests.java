package com.fintech.walletapi;

import com.fintech.walletapi.controller.TransferController;
import com.fintech.walletapi.service.ExchangeRateService;
import com.fintech.walletapi.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GMoneyApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("TC-APP-01 | GMoneyApplication.main | Spring context loads")
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    @DisplayName("TC-APP-02 | GMoneyApplication.main | transfer and forex beans are wired; application name is gmoney")
    void coreBeansPresent() {
        assertNotNull(context.getBean(TransferService.class));
        assertNotNull(context.getBean(ExchangeRateService.class));
        assertNotNull(context.getBean(TransferController.class));
        assertEquals("gmoney", context.getEnvironment().getProperty("spring.application.name"));
    }

    @Test
    @DisplayName("TC-APP-03 | GMoneyApplication.main | main() boots a separate app instance without errors")
    void mainRuns() {
        assertDoesNotThrow(() -> GMoneyApplication.main(new String[]{
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:maintest;DB_CLOSE_DELAY=-1",
                "--spring.jmx.enabled=false"}));
    }
}
