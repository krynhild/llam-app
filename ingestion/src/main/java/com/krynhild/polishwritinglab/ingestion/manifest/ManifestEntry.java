package com.krynhild.polishwritinglab.ingestion.manifest;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * One exam listed in {@code input-docs/exam-sheets.tsv}.
 *
 * @param session the first day of the exam session
 * @param variant {@code standard}, or {@code DiM} for the group adapted to children and young people
 */
public record ManifestEntry(String level, int year, LocalDate session, String variant, String sheetUrl) {

	public boolean childrenAndYouth() {
		return "DiM".equalsIgnoreCase(variant);
	}

	/** The exam's stable ID, also the base name of its files: {@code 2024-06-22_B1_DiM}. */
	public String examId() {
		return session + "_" + level + (childrenAndYouth() ? "_DiM" : "");
	}

	public Path sheet(Path inputDir) {
		return folder(inputDir).resolve(examId() + ".pdf");
	}

	/** The "Transkrypcje i klucz do zadań" document: listening transcripts and the answer key. */
	public Path key(Path inputDir) {
		return folder(inputDir).resolve(examId() + "_transcript.pdf");
	}

	private Path folder(Path inputDir) {
		return inputDir.resolve(level).resolve(Integer.toString(year));
	}
}
