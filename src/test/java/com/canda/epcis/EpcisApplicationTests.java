package com.canda.epcis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "epcis.output.directory=target/test-output/events",
                // Use create-drop so Hibernate manages the schema in the dev DB for this test.
                // Flyway is disabled to avoid needing the migration baseline on every test run.
                // Full DB integration is covered by Testcontainers tests (Task 10).
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
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
