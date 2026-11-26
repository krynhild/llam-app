package com.krynhild.polishwritinglab.ingestion.model;

import java.util.List;

/**
 * The transcript of a listening task's recording.
 *
 * @param text the whole transcript
 * @param segments the transcript split into utterances, items or speaker turns
 */
public record Transcript(String numeral, String text, List<Segment> segments, List<String> sources) {

	/**
	 * @param item the item the segment belongs to, {@code example} for the worked example
	 * @param speaker the speaker's label, for example {@code Dziennikarz} or {@code Osoba 3}
	 * @param gender {@code K} (woman) or {@code M} (man), as marked in the transcript
	 */
	public record Segment(String item, String speaker, String gender, String text) {
	}
}
