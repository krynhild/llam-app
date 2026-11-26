package com.krynhild.polishwritinglab.ingestion.parse.key;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.model.Transcript;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Splits a transcript into segments: numbered utterances ({@code 3. … (M)}), dialogue turns ({@code – …}), labelled
 * turns ({@code Dziennikarz: …}, {@code Dz.: …}) and speaker headings ({@code Osoba 2 (M)}). {@code (K)} and
 * {@code (M)} mark the speaker as a woman or a man.
 */
final class TranscriptParser {

	private static final Pattern SOURCE = Pattern.compile("^Na podstawie\\s*:?\\s*(.*)$");
	private static final Pattern EXAMPLE = Pattern.compile("^Przykład\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,2})\\.\\s+(.*)$");
	private static final Pattern TURN = Pattern.compile("^[–-]\\s*(.*)$");
	private static final Pattern LABEL = Pattern.compile("^([\\p{Lu}][\\p{L}]*\\.?(?: [\\p{Lu}]?[\\p{L}]*\\.?)?)\\s*:\\s*(.+)$");
	private static final Pattern PERSON = Pattern.compile("^(Osoba \\d+|Rozmówca \\d+|Mówca \\d+)\\s*(?:\\(([KM])\\))?\\s*:?\\s*(.*)$");
	private static final Pattern GENDER = Pattern.compile("\\s*\\(([KM])\\)\\s*$");

	private TranscriptParser() {
	}

	static Transcript parse(String numeral, List<Line> lines) {
		List<String> sources = new ArrayList<>();
		List<String> text = new ArrayList<>();
		List<Segment> segments = new ArrayList<>();
		Segment current = null;
		String item = null;

		for (Line line : lines) {
			String content = Text.collapse(line.text());
			Matcher source = SOURCE.matcher(content);
			if (source.matches()) {
				sources.add(source.group(1));
				continue;
			}
			text.add(content);

			Matcher example = EXAMPLE.matcher(content);
			Matcher numbered = NUMBERED.matcher(content);
			Matcher person = PERSON.matcher(content);
			Matcher turn = TURN.matcher(content);
			Matcher label = LABEL.matcher(content);
			if (example.matches()) {
				item = "example";
				current = segment(segments, item, null, example.group(1));
			}
			else if (numbered.matches()) {
				item = numbered.group(1);
				current = segment(segments, item, null, numbered.group(2));
			}
			else if (person.matches()) {
				current = segment(segments, item, person.group(1), person.group(3));
				current.gender = person.group(2);
			}
			else if (turn.matches()) {
				current = segment(segments, item, null, turn.group(1));
			}
			else if (label.matches() && label.group(1).length() <= 16) {
				current = segment(segments, item, label.group(1).replaceAll("\\.$", ""), label.group(2));
			}
			else if (current != null) {
				current.lines.add(content);
			}
			else {
				current = segment(segments, item, null, content);
			}
		}

		List<Transcript.Segment> result = new ArrayList<>();
		for (Segment segment : segments) {
			String joined = Text.joinLines(segment.lines);
			Matcher gender = GENDER.matcher(joined);
			String genderMark = segment.gender;
			if (gender.find()) {
				genderMark = gender.group(1);
				joined = joined.substring(0, gender.start());
			}
			if (!joined.isBlank() || segment.speaker != null) {
				result.add(new Transcript.Segment(segment.item, segment.speaker, genderMark, joined.strip()));
			}
		}
		return new Transcript(numeral, Text.joinItemLines(text), result, sources);
	}

	private static Segment segment(List<Segment> segments, String item, String speaker, String first) {
		Segment segment = new Segment(item, speaker);
		if (!first.isBlank()) {
			segment.lines.add(first);
		}
		segments.add(segment);
		return segment;
	}

	private static final class Segment {

		private final String item;
		private final String speaker;
		private final List<String> lines = new ArrayList<>();
		private String gender;

		Segment(String item, String speaker) {
			this.item = item;
			this.speaker = speaker;
		}
	}
}
