package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

/**
 * One exam session for one level and variant: the sheet merged with its transcripts and answer key.
 *
 * @param id stable ID, for example {@code 2024-06-22_B1_DiM}
 * @param variant {@code standard}, or {@code DiM} for the group adapted to children and young people
 * @param session the first day of the session, ISO formatted
 * @param sessionMonth the month printed on the sheet, for example {@code czerwiec 2024}
 * @param standards the part of the requirement standards the exam is based on: {@code adult} or {@code dim}
 */
public record Exam(
		String id,
		String level,
		String variant,
		String session,
		int year,
		String sessionMonth,
		String standards,
		List<SourceDocument> sources,
		List<ExamModule> modules) {
}
