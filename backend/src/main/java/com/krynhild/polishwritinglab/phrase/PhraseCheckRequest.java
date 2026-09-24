package com.krynhild.polishwritinglab.phrase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhraseCheckRequest(
		@NotBlank(message = "Phrase must not be blank")
		@Size(max = 500, message = "Phrase must not exceed 500 characters")
		String phrase) {
}
