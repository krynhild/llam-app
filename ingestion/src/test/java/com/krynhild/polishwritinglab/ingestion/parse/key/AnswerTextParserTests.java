package com.krynhild.polishwritinglab.ingestion.parse.key;

import org.junit.jupiter.api.Test;

import com.krynhild.polishwritinglab.ingestion.model.Answer;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerTextParserTests {

	@Test
	void splitsWholeAlternativesSeparatedBySpacedSlashes() {
		Answer.Text answer = AnswerTextParser.parse("użyła / używała");

		assertThat(answer.accept()).containsExactly("użyła", "używała");
		assertThat(answer.ambiguous()).isFalse();
	}

	@Test
	void expandsWordVariantsJoinedWithoutSpaces() {
		Answer.Text answer = AnswerTextParser.parse("przeniesione/wyjęte/wzięte z bloków");

		assertThat(answer.accept()).containsExactly("przeniesione z bloków", "wyjęte z bloków", "wzięte z bloków");
	}

	@Test
	void treatsAParenthesisAsAnOptionalPart() {
		Answer.Text answer = AnswerTextParser.parse("(Na drodze) zapadł się asfalt.");

		assertThat(answer.accept()).containsExactlyInAnyOrder("Na drodze zapadł się asfalt.", "zapadł się asfalt.");
	}

	@Test
	void splitsOffPointsInParenthesesAsPartialCredit() {
		Answer.Text answer = AnswerTextParser.parse("W jakim…? (0,25 p.)");

		assertThat(answer.partialCredit()).hasSize(1);
		assertThat(answer.partialCredit().getFirst().points()).isEqualTo(0.25);
		assertThat(answer.partialCredit().getFirst().answer()).isEqualTo("W jakim…?");
	}

	@Test
	void marksAnAnswerThatGivesOnlyTheQuestionWord() {
		Answer.Text answer = AnswerTextParser.parse("Komu…?");

		assertThat(answer.questionWordOnly()).isTrue();
	}

	@Test
	void expandsADigitInParenthesesAsAnAlternativeSpelling() {
		Answer.Text answer = AnswerTextParser.parse("dwadzieścia trzy (23)");

		assertThat(answer.accept()).contains("dwadzieścia trzy", "23");
	}

	@Test
	void keepsASimpleAnswerUnambiguous() {
		Answer.Text answer = AnswerTextParser.parse("metrów");

		assertThat(answer.accept()).containsExactly("metrów");
		assertThat(answer.ambiguous()).isFalse();
	}
}
