package com.krynhild.polishwritinglab.ingestion.model;

/**
 * A problem found while building the corpus.
 *
 * @param severity {@code error} blocks the release, {@code warning} needs review
 * @param code a stable identifier of the kind of problem, for example {@code key-count}
 * @param target the ID of the exam, task or document concerned
 */
public record Issue(String severity, String code, String target, String message) {

	public static Issue error(String code, String target, String message) {
		return new Issue("error", code, target, message);
	}

	public static Issue warning(String code, String target, String message) {
		return new Issue("warning", code, target, message);
	}

	public boolean isError() {
		return "error".equals(severity);
	}
}
