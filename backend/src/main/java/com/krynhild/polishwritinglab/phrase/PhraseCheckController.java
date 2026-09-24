package com.krynhild.polishwritinglab.phrase;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/check")
public class PhraseCheckController {

	private final LanguageModelClient languageModelClient;

	public PhraseCheckController(LanguageModelClient languageModelClient) {
		this.languageModelClient = languageModelClient;
	}

	@PostMapping
	public PhraseCheckResponse checkPhrase(@Valid @RequestBody PhraseCheckRequest request) {
		return languageModelClient.checkPhrase(request.phrase());
	}
}
