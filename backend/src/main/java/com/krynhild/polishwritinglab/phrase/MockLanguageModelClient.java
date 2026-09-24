package com.krynhild.polishwritinglab.phrase;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class MockLanguageModelClient implements LanguageModelClient {

	private static final Map<String, Correction> CORRECTIONS = Map.of(
			"szukam nową pracę",
			new Correction(
					"Szukam nowej pracy.",
					"The verb „szukać” requires the genitive case: „nowej pracy” rather than „nową pracę”."),
			"idę do sklep",
			new Correction(
					"Idę do sklepu.",
					"The preposition „do” requires the genitive case, so „sklep” becomes „sklepu”."),
			"mieszkam w polska",
			new Correction(
					"Mieszkam w Polsce.",
					"Location after „w” uses the locative case, so „Polska” becomes „Polsce”."));

	@Override
	public PhraseCheckResponse checkPhrase(String phrase) {
		String trimmedPhrase = phrase.trim();
		Correction correction = CORRECTIONS.get(normalize(trimmedPhrase));

		if (correction == null) {
			return new PhraseCheckResponse(
					trimmedPhrase,
					true,
					trimmedPhrase,
					"The mock language model found no known error in this phrase.",
					"mock");
		}

		return new PhraseCheckResponse(
				trimmedPhrase,
				false,
				correction.correctedPhrase(),
				correction.explanation(),
				"mock");
	}

	private String normalize(String phrase) {
		return phrase
				.toLowerCase(Locale.forLanguageTag("pl"))
				.replaceFirst("[.!?]+$", "")
				.trim();
	}

	private record Correction(String correctedPhrase, String explanation) {
	}
}
