package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** The correct answer of an item, from the answer key. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
		@JsonSubTypes.Type(value = Answer.Choice.class, name = "choice"),
		@JsonSubTypes.Type(value = Answer.Choices.class, name = "choices"),
		@JsonSubTypes.Type(value = Answer.Bool.class, name = "boolean"),
		@JsonSubTypes.Type(value = Answer.Text.class, name = "text"),
		@JsonSubTypes.Type(value = Answer.Parts.class, name = "parts") })
public sealed interface Answer {

	/** How many scored answers this counts as; a pair of choices for one recording counts twice. */
	int units();

	/** One option label, for example {@code b} or {@code H}. */
	record Choice(String value) implements Answer {

		@Override
		public int units() {
			return 1;
		}
	}

	/**
	 * Several option labels, for example {@code b, c}.
	 *
	 * @param anyOrder the key marks the item with {@code *}: "kolejność odpowiedzi dowolna"
	 */
	record Choices(List<String> values, boolean anyOrder) implements Answer {

		@Override
		public int units() {
			return values.size();
		}
	}

	/** TAK (true) or NIE (false). */
	record Bool(boolean value) implements Answer {

		@Override
		public int units() {
			return 1;
		}
	}

	/**
	 * A written answer.
	 *
	 * @param raw the answer exactly as the key prints it
	 * @param accept the accepted answers, with alternatives and optional parts expanded
	 * @param partialCredit answers worth fewer points, for example {@code W jakim…? (0,25 p.)}
	 * @param questionWordOnly the key gives only the question word ({@code Komu…?}); the rest of the question isn't graded
	 * @param ambiguous the alternatives could not be split unambiguously and need review
	 */
	record Text(String raw, List<String> accept, List<PartialCredit> partialCredit, boolean questionWordOnly,
			boolean ambiguous) implements Answer {

		@Override
		public int units() {
			return 1;
		}
	}

	/**
	 * An item answered in several parts, each scored on its own: the a) and b) questions about one recording, or a
	 * word to fill in followed by an option to choose.
	 */
	record Parts(List<Answer> parts) implements Answer {

		@Override
		public int units() {
			return parts.stream().mapToInt(Answer::units).sum();
		}
	}

	record PartialCredit(String answer, double points) {
	}
}
