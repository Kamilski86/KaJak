package com.canda.epcis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "epcis.output.directory=target/test-output/events"
        }
)
class EpcisApplicationTests {

    @Autowired
    EpcisApplication application;

    @Test
    void contextLoads() {
        assertThat(application).isNotNull();
    }
}
