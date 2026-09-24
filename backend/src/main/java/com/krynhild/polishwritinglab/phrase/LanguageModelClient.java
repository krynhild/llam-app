package com.krynhild.polishwritinglab.phrase;

public interface LanguageModelClient {

	PhraseCheckResponse checkPhrase(String phrase);
}
