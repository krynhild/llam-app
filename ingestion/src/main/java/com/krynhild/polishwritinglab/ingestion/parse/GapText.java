package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;

/**
 * Renders lines as text, turning the sheet's gap markers into tokens.
 *
 * <p>Gaps are printed as dotted or underlined blanks followed by a superscript number ({@code …………1},
 * {@code [______]1}), or as a superscript number after the bracketed options ({@code (duże, inne, różne)1}). The
 * worked example uses a superscript {@code PRZYKŁAD}. These become {@code {{1}}} and {@code {{example}}}. A
 * superscript that repeats the number of a gap already on the line, as in {@code …………1 (jechać)1}, marks the cue
 * word and is dropped.
 */
public final class GapText {

	public static final String EXAMPLE = "{{example}}";

	private static final Pattern BLANK = Pattern.compile("[\\[(]?[_.…]{3,}[\\])]?([.,;:!?]*)");
	private static final Pattern FILLED_BLANK = Pattern.compile("\\[_*([\\p{L}\\d]{1,3})_*\\]([.,;:!?]*)");
	private static final Pattern GAP_NUMBER = Pattern.compile("\\d{1,2}");
	private static final Pattern GAP_TOKEN = Pattern.compile("\\{\\{(\\d{1,2})}}");

	private GapText() {
	}

	public static String render(List<Line> lines) {
		StringBuilder text = new StringBuilder();
		for (Line line : lines) {
			if (!text.isEmpty()) {
				text.append('\n');
			}
			text.append(render(line));
		}
		return text.toString();
	}

	public static String render(Line line) {
		StringBuilder text = new StringBuilder();
		Set<String> gaps = new HashSet<>();
		List<Word> words = line.words();
		for (int i = 0; i < words.size(); i++) {
			Word word = words.get(i);
			if (word.superscript()) {
				String marker = word.text().strip();
				if (marker.toUpperCase().startsWith("PRZYKŁAD")) {
					text.append(EXAMPLE);
				}
				else if (GAP_NUMBER.matcher(marker).matches()) {
					boolean cue = gaps.contains(marker) && text.toString().stripTrailing().endsWith(")");
					if (!cue) {
						if (text.toString().endsWith("___")) {
							text.setLength(text.length() - 3);
						}
						text.append("{{").append(marker).append("}}");
						gaps.add(marker);
					}
				}
				else {
					text.append('^').append(marker);
				}
				continue;
			}
			if (!text.isEmpty()) {
				text.append(' ');
			}
			var blank = BLANK.matcher(word.text());
			var filled = FILLED_BLANK.matcher(word.text());
			if (blank.matches()) {
				text.append("___").append(blank.group(1));
			}
			else if (filled.matches()) {
				text.append('[').append(filled.group(1)).append(']').append(filled.group(2));
			}
			else {
				text.append(word.text());
			}
		}
		return text.toString();
	}

	/** The gap numbers in rendered text, in order of appearance. */
	public static List<String> gaps(String text) {
		return GAP_TOKEN.matcher(text).results().map(result -> result.group(1)).distinct().toList();
	}
}
