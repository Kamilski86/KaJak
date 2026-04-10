package com.example.epcis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EpcisApplicationTests {

    @Test
    void contextLoads() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EpcisApplication.class)
                .web(WebApplicationType.NONE)
                .properties("epcis.output.directory=target/test-output/events")
                .run()) {

            assertThat(context.getBean(EpcisApplication.class)).isNotNull();
        }
    }

}
