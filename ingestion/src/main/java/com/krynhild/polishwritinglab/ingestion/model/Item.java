package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

/**
 * One scored question, gap or writing assignment of a task.
 *
 * @param id the item's label: a number, a letter for matching tasks, or {@code a}/{@code b} for writing assignments
 * @param context text printed before the item that belongs to it only, such as the short text of a reading item
 * @param words the required length of a writing assignment
 * @param scoringNote how partial points are given, from the key
 * @param evidence the part of the transcript or text that justifies the answer, from the key
 */
public record Item(
		String id,
		String context,
		String prompt,
		List<Option> options,
		Integer words,
		Answer answer,
		String scoringNote,
		String evidence) {
}
