package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;

import static org.assertj.core.api.Assertions.assertThat;

class GapTextTests {

	private static Word word(String text, double x0, double x1, boolean superscript) {
		return new Word(text, x0, x1, 0, 10, superscript ? 7 : 12, false, superscript);
	}

	@Test
	void turnsADottedBlankFollowedByASuperscriptNumberIntoAGapToken() {
		Line line = new Line(1, 10, List.of(word("…………", 0, 50, false), word("1", 52, 58, true)));

		assertThat(GapText.render(line)).isEqualTo("{{1}}");
	}

	@Test
	void dropsASuperscriptThatRepeatsTheGapsNumberAsACueWord() {
		// "…………1 (jechać)1": the second "1" marks (jechać) as the cue word for gap 1, not a second gap.
		Line line = new Line(1, 10, List.of(
				word("…………", 0, 50, false), word("1", 52, 58, true),
				word("(jechać)", 60, 100, false), word("1", 102, 108, true)));

		assertThat(GapText.render(line)).isEqualTo("{{1}} (jechać)");
	}

	@Test
	void turnsASuperscriptPrzykladIntoTheExampleToken() {
		Line line = new Line(1, 10, List.of(word("że", 0, 20, false), word("PRZYKŁAD", 22, 60, true)));

		assertThat(GapText.render(line)).isEqualTo("że" + GapText.EXAMPLE);
	}

	@Test
	void readsTheGapNumbersOfARenderedTextInOrderWithoutDuplicates() {
		assertThat(GapText.gaps("a {{1}} b {{2}} c {{1}} d")).containsExactly("1", "2");
	}

	@Test
	void turnsAFilledBracketIntoABracketedAnswer() {
		Line line = new Line(1, 10, List.of(word("[___3___]", 0, 50, false)));

		assertThat(GapText.render(line)).isEqualTo("[3]");
	}
}
