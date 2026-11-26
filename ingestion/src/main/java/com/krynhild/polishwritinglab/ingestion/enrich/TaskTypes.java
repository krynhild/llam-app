package com.krynhild.polishwritinglab.ingestion.enrich;

import java.util.List;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.model.Answer;
import com.krynhild.polishwritinglab.ingestion.model.Item;
import com.krynhild.polishwritinglab.ingestion.model.Task;
import com.krynhild.polishwritinglab.ingestion.parse.Skills;

/**
 * Classifies tasks into canonical types by the wording of their instructions, which is formulaic across sessions
 * even where labels change ("(spójniki)" and "(wskaźniki zespolenia)" are the same task). The first matching rule
 * wins; tasks no rule matches are {@value #UNKNOWN} and flagged for review, so a new kind of task can't be filed
 * under a wrong type silently.
 */
public final class TaskTypes {

	public static final String UNKNOWN = "unknown";

	private record Rule(String skill, Pattern instruction, String type) {
	}

	private static final List<Rule> RULES = List.of(
			rule(Skills.LISTENING, "symbol|obraz|ilustracj|zdjęci", "listening_picture_choice"),
			rule(Skills.LISTENING, "TAK\\s*[–-]\\s*gdy|prawd\\p{L}*\\s*/\\s*fałsz", "listening_true_false"),
			rule(Skills.LISTENING, "zdecydować, kto|dopasować osoby|dopasuj osoby|kto mówi|przyporządk", "listening_speaker_matching"),
			rule(Skills.LISTENING, "odpowiedzieć na pytania|krótko odpowiedz|zanotować|uzupełnić notatk|uzupełnij notatk|odpowiedz krótko", "listening_short_answer"),
			rule(Skills.LISTENING, "dopasować|dopasuj|połączyć|połącz|pasuje|pasują", "listening_matching"),
			rule(Skills.LISTENING, ".", "listening_multiple_choice"),

			rule(Skills.READING, "wykonać dwa zadania", "reading_composite"),
			rule(Skills.READING, "TAK\\s*[–-]\\s*gdy", "reading_true_false"),
			rule(Skills.READING, "usunięte z niego fragmenty|brakujące fragmenty|usuniętymi fragmentami|uzupełnić tekst fragmentami|brakującymi zdaniami", "reading_gapped_text"),
			rule(Skills.READING, "z ramki|w ramce", "reading_word_bank"),
			rule(Skills.READING, "połączyć tytuły|dopasować tytuły|nagłówk|tytuł", "reading_heading_matching"),
			rule(Skills.READING, "który fragment tekstu|którym fragmencie|którego fragmentu|którym tekście|który tekst", "reading_paragraph_matching"),
			rule(Skills.READING, "najlepsz\\p{L}* spośród|w nawiasach|najlepszą odpowiedź|właściwy wyraz|właściwe wyrazy", "reading_lexical_cloze"),
			rule(Skills.READING, "dopasować|dopasuj|połączyć|połącz|pasuje|pasują", "reading_matching"),
			rule(Skills.READING, "zaznaczyć|zaznacz|podkreśl|wybierz|wybrać", "reading_multiple_choice"),

			rule(Skills.GRAMMAR, "z ramki|w ramce", "grammar_word_bank"),
			rule(Skills.GRAMMAR, "zapytać o|zadać pytani|zadaj pytani", "grammar_question_formation"),
			rule(Skills.GRAMMAR, "zdania złożone na zdania pojedyncze|zdań złożonych|zdania pojedyncze na", "grammar_clause_transformation"),
			rule(Skills.GRAMMAR, "przekształcić|przekształć", "grammar_sentence_transformation"),
			rule(Skills.GRAMMAR, "słownymi formami|słownie|liczebnik", "grammar_numerals"),
			rule(Skills.GRAMMAR, "wyrażeniami lub zwrotami|frazeolog|związk\\p{L}* frazeologiczn|idiom", "grammar_phraseology"),
			rule(Skills.GRAMMAR, "pojedynczymi wyrazami|jednym wyrazem|brakującymi wyrazami", "grammar_open_cloze"),
			rule(Skills.GRAMMAR, "słowotwór|utworzyć|utwórz|odpowiednie wyrazy pochodne|formy pochodne", "grammar_word_formation"),
			rule(Skills.GRAMMAR, "podkreśl\\p{L}*|wybrać|wybierz|zaznacz", "grammar_form_choice"),
			rule(Skills.GRAMMAR, "odpowiednimi wyrazami|odpowiednimi wyrażeniami|właściwymi wyrazami", "grammar_lexical_gap_fill"),
			rule(Skills.GRAMMAR, "form\\p{L}* wyrazów|formy wyrazów|formami wyrazów|form\\p{L}* czasownik|odpowiedniej formy|właściwymi formami|poprawnymi formami", "grammar_form_gap_fill"),
			rule(Skills.GRAMMAR, "uzupełnić|uzupełnij", "grammar_gap_fill"));

	private TaskTypes() {
	}

	public static String classify(String skill, Task task) {
		if (Skills.WRITING.equals(skill)) {
			return writing(task);
		}
		String instruction = task.instruction() == null ? "" : task.instruction();
		for (Rule rule : RULES) {
			if (rule.skill().equals(skill) && rule.instruction().matcher(instruction).find()) {
				if (rule.type().equals("listening_multiple_choice") && !hasChoices(task)) {
					if (hasBool(task)) {
						return "listening_true_false";
					}
					return hasText(task) ? "listening_short_answer" : UNKNOWN;
				}
				return rule.type();
			}
		}
		return UNKNOWN;
	}

	private static String writing(Task task) {
		List<Item> items = task.items();
		if (items.size() > 1) {
			return "writing_set";
		}
		return "writing_extended";
	}

	private static boolean hasChoices(Task task) {
		return task.items().stream().anyMatch(item -> item.answer() instanceof Answer.Choice
				|| item.answer() instanceof Answer.Choices || !item.options().isEmpty());
	}

	private static boolean hasBool(Task task) {
		return task.items().stream().anyMatch(item -> item.answer() instanceof Answer.Bool);
	}

	private static boolean hasText(Task task) {
		return task.items().stream().anyMatch(item -> item.answer() instanceof Answer.Text);
	}

	private static Rule rule(String skill, String instruction, String type) {
		return new Rule(skill, Pattern.compile(instruction, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), type);
	}
}
