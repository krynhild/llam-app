package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTests {

	@Test
	void collapsesRunsOfWhitespace() {
		assertThat(Text.collapse("  a   b\tc  ")).isEqualTo("a b c");
	}

	@Test
	void unspacesALetterSpacedWord() {
		assertThat(Text.unspace("Z e s t a w II")).isEqualTo("Zestaw II");
	}

	@Test
	void leavesOrdinaryShortWordsAlone() {
		// "i", "w", "z" are real one-letter Polish words, not letter-spaced text, so a short run isn't joined.
		assertThat(Text.unspace("i w z tym")).isEqualTo("i w z tym");
	}

	@Test
	void joinsHyphenatedWordsAcrossALineBreak() {
		assertThat(Text.joinLines(List.of("intencjonalno-", "-pojęciowy"))).isEqualTo("intencjonalno-pojęciowy");
	}

	@Test
	void joinsAWordBrokenAtALineEndWithoutARealHyphen() {
		assertThat(Text.joinLines(List.of("podstawo-", "wymi"))).isEqualTo("podstawowymi");
	}

	@Test
	void joinsPlainWrappedLinesWithASpace() {
		assertThat(Text.joinLines(List.of("Zdający rozumieją", "najważniejsze treści"))).isEqualTo("Zdający rozumieją najważniejsze treści");
	}

	@Test
	void joinItemLinesKeepsADialogueTurnOnItsOwnLine() {
		String joined = Text.joinItemLines(List.of("1. Pobyt na niej możliwy jest tylko w określonych porach.",
				"– O której godzinie...", "– Nie pamiętam dobrze."));

		assertThat(joined).isEqualTo(
				"1. Pobyt na niej możliwy jest tylko w określonych porach.\n– O której godzinie...\n– Nie pamiętam dobrze.");
	}
}
