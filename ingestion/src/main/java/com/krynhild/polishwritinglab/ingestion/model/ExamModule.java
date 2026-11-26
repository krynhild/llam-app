package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

/**
 * One part of the exam.
 *
 * @param skill {@code RS} listening, {@code RTP} reading, {@code PG} grammar, {@code P} writing, {@code M} speaking
 * @param title the title printed on the module's cover page
 * @param declaredTasks {@code LICZBA ZADAŃ} from the cover page
 * @param time {@code CZAS} from the cover page, for example {@code do 30 minut}
 * @param instruction general instructions for the module (writing and speaking)
 * @param criteria scoring criteria (writing and speaking)
 * @param scoringCard the text of the examiners' scoring card ("Karta oceny"), if the sheet has one
 */
public record ExamModule(
		String skill,
		String title,
		Integer declaredTasks,
		String time,
		Double maxPoints,
		List<Integer> pages,
		String instruction,
		List<Criterion> criteria,
		String scoringCard,
		List<Task> tasks) {
}
