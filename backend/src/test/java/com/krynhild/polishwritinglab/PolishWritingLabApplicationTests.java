package com.krynhild.polishwritinglab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "management.endpoint.health.group.readiness.show-components=always")
@AutoConfigureMockMvc
class PolishWritingLabApplicationTests {

	@Autowired
	private MockMvcTester mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void readinessProbeIncludesDatabase() {
		assertThat(mockMvc.get().uri("/actuator/health/readiness"))
				.hasStatus(HttpStatus.OK)
				.bodyJson()
				.extractingPath("$.components.db.status")
				.isEqualTo("UP");
	}

}
