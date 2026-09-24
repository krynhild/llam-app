package com.krynhild.polishwritinglab.phrase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MockLanguageModelClientTests {

	private final MockLanguageModelClient client = new MockLanguageModelClient();

	@Test
	void correctsKnownPhrase() {
		PhraseCheckResponse response = client.checkPhrase("Szukam nową pracę.");

		assertFalse(response.correct());
		assertEquals("Szukam nowej pracy.", response.correctedPhrase());
		assertEquals("mock", response.provider());
	}

	@Test
	void acceptsPhraseWithoutKnownError() {
		PhraseCheckResponse response = client.checkPhrase("Dzień dobry!");

		assertTrue(response.correct());
		assertEquals("Dzień dobry!", response.correctedPhrase());
	}
}
