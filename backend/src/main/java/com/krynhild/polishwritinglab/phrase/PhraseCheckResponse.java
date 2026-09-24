package com.krynhild.polishwritinglab.phrase;

public record PhraseCheckResponse(
		String originalPhrase,
		boolean correct,
		String correctedPhrase,
		String explanation,
		String provider) {
}
