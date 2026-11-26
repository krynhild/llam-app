package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

/**
 * One numbered task of a module.
 *
 * @param id stable ID, for example {@code 2024-06-22_B1/PG/IV}
 * @param type canonical task type from {@code TaskTypes}, {@code unknown} if no rule matched
 * @param focus what the task practices, from the parentheses of the instruction, for example {@code aspekt i tryby}
 * @param text the task's material (texts, items, options) in reading order; gaps are written {@code {{1}}} and the
 *     gap of the worked example {@code {{example}}}
 * @param options options shared by all items, for example fragments A–H or a word bank
 * @param example the worked example ("PRZYKŁAD") and its answer, when it could be recovered
 * @param sources {@code Na podstawie} URLs of the texts the task was adapted from
 * @param requiresVisual the task can't be done from its text alone (pictures, symbols)
 * @param pages sheet pages the task is printed on
 */
public record Task(
		String id,
		String numeral,
		int ordinal,
		String type,
		String instruction,
		List<String> focus,
		Points points,
		String text,
		List<Option> options,
		Example example,
		List<Item> items,
		KeyNotes key,
		Transcript transcript,
		List<String> sources,
		boolean requiresVisual,
		List<Integer> pages) {
}
