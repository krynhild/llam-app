package com.krynhild.polishwritinglab.phrase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

class RunPodLanguageModelClientTests {

	private final RestClient.Builder restClientBuilder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
	private final RunPodLanguageModelClient client = new RunPodLanguageModelClient(
			restClientBuilder,
			new RunPodProperties("https://pod.example", "test-key", "Qwen/Qwen3-8B", Duration.ofSeconds(25)),
			JsonMapper.builder().build());

	@Test
	void returnsCorrectionAndTokenUsage() {
		server.expect(requestTo("https://pod.example/v1/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("Qwen/Qwen3-8B"))
				.andExpect(jsonPath("$.messages[1].content").value("szukam nową pracę"))
				.andRespond(withSuccess(completion("stop", """
						{\\"correct\\": false, \\"correctedPhrase\\": \\"Szukam nowej pracy.\\", \\"explanation\\": \\"Genitive after szukać.\\"}"""),
						MediaType.APPLICATION_JSON));

		PhraseCheckResponse response = client.checkPhrase("  szukam nową pracę ");

		assertThat(response.originalPhrase()).isEqualTo("szukam nową pracę");
		assertThat(response.correct()).isFalse();
		assertThat(response.correctedPhrase()).isEqualTo("Szukam nowej pracy.");
		assertThat(response.explanation()).isEqualTo("Genitive after szukać.");
		assertThat(response.provider()).isEqualTo("Qwen/Qwen3-8B");
		assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(95, 74, 169));
		server.verify();
	}

	@Test
	void reportsBadGatewayWhenModelServerFails() {
		server.expect(requestTo("https://pod.example/v1/chat/completions")).andRespond(withServerError());

		assertThatThrownBy(() -> client.checkPhrase("Dzień dobry"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void reportsBadGatewayWhenAnswerIsCutOff() {
		server.expect(requestTo("https://pod.example/v1/chat/completions"))
				.andRespond(withSuccess(completion("length", "{\\\"correct\\\": tr"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.checkPhrase("Dzień dobry"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}

	private static String completion(String finishReason, String escapedContent) {
		return """
				{
				  "model": "Qwen/Qwen3-8B",
				  "choices": [{"message": {"role": "assistant", "content": "%s"}, "finish_reason": "%s"}],
				  "usage": {"prompt_tokens": 95, "completion_tokens": 74, "total_tokens": 169}
				}""".formatted(escapedContent, finishReason);
	}
}
