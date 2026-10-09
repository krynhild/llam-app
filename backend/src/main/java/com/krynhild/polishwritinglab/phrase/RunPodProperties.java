package com.krynhild.polishwritinglab.phrase;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.runpod")
public record RunPodProperties(
		String baseUrl,
		String apiKey,
		String model,
		// API Gateway gives up after 30 seconds, so the model call must fail before that.
		@DefaultValue("25s") Duration timeout) {
}
