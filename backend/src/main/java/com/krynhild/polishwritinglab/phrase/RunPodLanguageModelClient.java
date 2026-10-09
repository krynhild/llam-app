package com.krynhild.polishwritinglab.phrase;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Calls a vLLM server on RunPod through its OpenAI-compatible chat completions API. */
public class RunPodLanguageModelClient implements LanguageModelClient {

	private static final String SYSTEM_PROMPT = """
			You are a Polish language tutor. Check the user's Polish phrase for grammar, spelling, \
			punctuation and case errors. If the phrase is correct, set "correct" to true and repeat \
			the phrase as "correctedPhrase". Otherwise set "correct" to false and give the corrected \
			phrase. Write "explanation" in English, in one or two sentences, naming the grammar rule.""";

	private static final Map<String, Object> RESPONSE_FORMAT = Map.of(
			"type", "json_schema",
			"json_schema", Map.of(
					"name", "phrase_check",
					"strict", true,
					"schema", Map.of(
							"type", "object",
							"properties", Map.of(
									"correct", Map.of("type", "boolean"),
									"correctedPhrase", Map.of("type", "string"),
									"explanation", Map.of("type", "string")),
							"required", List.of("correct", "correctedPhrase", "explanation"),
							"additionalProperties", false)));

	private final RestClient restClient;
	private final RunPodProperties properties;
	private final JsonMapper jsonMapper;

	RunPodLanguageModelClient(RestClient.Builder restClientBuilder, RunPodProperties properties, JsonMapper jsonMapper) {
		Assert.hasText(properties.baseUrl(), "app.runpod.base-url must be set");
		Assert.hasText(properties.apiKey(), "app.runpod.api-key must be set");
		Assert.hasText(properties.model(), "app.runpod.model must be set");

		this.restClient = restClientBuilder
				.baseUrl(properties.baseUrl())
				.defaultHeader("Authorization", "Bearer " + properties.apiKey())
				.build();
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public PhraseCheckResponse checkPhrase(String phrase) {
		String trimmedPhrase = phrase.trim();
		ChatCompletion completion = requestCompletion(trimmedPhrase);

		Choice choice = completion.choices().getFirst();
		if (!"stop".equals(choice.finishReason())) {
			throw unavailable("The language model did not finish its answer (" + choice.finishReason() + ")", null);
		}

		Answer answer;
		try {
			answer = jsonMapper.readValue(choice.message().content(), Answer.class);
		}
		catch (JacksonException ex) {
			throw unavailable("The language model returned an invalid answer", ex);
		}

		Usage usage = completion.usage();
		return new PhraseCheckResponse(
				trimmedPhrase,
				answer.correct(),
				answer.correctedPhrase(),
				answer.explanation(),
				completion.model(),
				new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()));
	}

	private ChatCompletion requestCompletion(String phrase) {
		Map<String, Object> request = Map.of(
				"model", properties.model(),
				"temperature", 0.2,
				"max_tokens", 400,
				// Qwen3 otherwise spends tokens and time on a reasoning block before answering.
				"chat_template_kwargs", Map.of("enable_thinking", false),
				"response_format", RESPONSE_FORMAT,
				"messages", List.of(
						Map.of("role", "system", "content", SYSTEM_PROMPT),
						Map.of("role", "user", "content", phrase)));

		try {
			return restClient.post()
					.uri("/v1/chat/completions")
					.body(request)
					.retrieve()
					.body(ChatCompletion.class);
		}
		catch (RestClientException ex) {
			throw unavailable("The language model is unavailable", ex);
		}
	}

	private static ResponseStatusException unavailable(String reason, Throwable cause) {
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, cause);
	}

	record ChatCompletion(String model, List<Choice> choices, Usage usage) {
	}

	record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
	}

	record Message(String content) {
	}

	record Usage(
			@JsonProperty("prompt_tokens") int promptTokens,
			@JsonProperty("completion_tokens") int completionTokens,
			@JsonProperty("total_tokens") int totalTokens) {
	}

	record Answer(boolean correct, String correctedPhrase, String explanation) {
	}
}
