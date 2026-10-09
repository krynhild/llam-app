package com.krynhild.polishwritinglab.phrase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(RunPodProperties.class)
class LanguageModelConfiguration {

	private static final String PROVIDER = "app.language-model.provider";

	@Bean
	@ConditionalOnProperty(name = PROVIDER, havingValue = "runpod")
	LanguageModelClient runPodLanguageModelClient(RunPodProperties properties, JsonMapper jsonMapper) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(properties.timeout());
		return new RunPodLanguageModelClient(
				RestClient.builder().requestFactory(requestFactory), properties, jsonMapper);
	}

	@Bean
	@ConditionalOnProperty(name = PROVIDER, havingValue = "mock", matchIfMissing = true)
	LanguageModelClient mockLanguageModelClient() {
		return new MockLanguageModelClient();
	}
}
