package com.krynhild.polishwritinglab.ingestion.parse.key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;
import com.krynhild.polishwritinglab.ingestion.model.Answer;
import com.krynhild.polishwritinglab.ingestion.parse.Borders;
import com.krynhild.polishwritinglab.ingestion.parse.Cells;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Reads the answers of one task's key. Sessions print the same information in several layouts, tried in this order:
 *
 * <ol>
 * <li>a true/false grid: item numbers with an {@code X} under TAK or NIE;</li>
 * <li>tables: a row of labels over one or more rows of answers. Labels are item numbers ({@code 1. 2.}), letters
 * ({@code A B* C*}, {@code a. b.}) or people ({@code Uczeń 2}); a second answer row makes a two-part answer;</li>
 * <li>lists: item number, then the answer, in one or two columns, with optional sub-answers {@code a}/{@code b} in
 * sub-columns and a column of points or of the transcript fragment that justifies the answer;</li>
 * <li>a set: the numbers of the true statements, in any order ({@code Zdania prawdziwe: 1 4 5 7}).</li>
 * </ol>
 *
 * A task can have parts ({@code A.}, {@code B.}) with their own items; their item IDs get the part as a prefix.
 */
final class AnswerBlockParser {

	private static final Pattern PART = Pattern.compile("^([A-D])\\.$");
	private static final Pattern ITEM_LABEL = Pattern.compile("^(\\d{1,2})(\\*?)\\.?(\\*?)$");
	private static final Pattern LETTER_LABEL = Pattern.compile("^([A-L])(\\*?)\\.?(\\*?)$|^([a-l])(\\*?)\\.(\\*?)$");
	private static final Pattern PERSON_LABEL = Pattern.compile("^(\\p{Lu}\\p{Ll}+)\\s+(\\d{1,2})(\\*?)\\.?(\\*?)$");
	private static final Pattern CHOICE = Pattern.compile("^[a-lA-L](\\s*[,;.]\\s*[a-lA-L])*\\.?$");
	private static final Pattern NUMBERS = Pattern.compile("^\\d{1,2}(\\s*[,;]\\s*\\d{1,2})*$");
	private static final Pattern SUB_LABEL = Pattern.compile("^[a-d]$");
	private static final Pattern NOTE = Pattern.compile("\\(!\\)\\s*(.*)$");

	private AnswerBlockParser() {
	}

	static Map<String, KeyParser.Entry> parse(List<Line> block, Borders borders) {
		Map<String, KeyParser.Entry> entries = new LinkedHashMap<>();
		for (Map.Entry<String, List<Line>> part : parts(block).entrySet()) {
			Map<String, KeyParser.Entry> partEntries = new LinkedHashMap<>();
			List<Line> lines = part.getValue();
			if (!trueFalse(lines, partEntries) && !table(lines, partEntries) && !set(lines, partEntries)
					&& !letterList(lines, partEntries)) {
				list(lines, partEntries, borders);
			}
			String prefix = part.getKey();
			partEntries.forEach((id, entry) -> {
				String full = prefix.isEmpty() ? id : prefix + "." + id;
				entries.put(full, new KeyParser.Entry(full, entry.answer(), entry.scoringNote(), entry.evidence()));
			});
		}
		return entries;
	}

	/** Splits the block at part headings printed alone on a line ({@code A.}, {@code B.}). */
	private static Map<String, List<Line>> parts(List<Line> block) {
		Map<String, List<Line>> parts = new LinkedHashMap<>();
		String current = "";
		parts.put(current, new ArrayList<>());
		for (Line line : block) {
			Matcher part = PART.matcher(Text.collapse(line.text()));
			if (part.matches() && line.bold()) {
				current = part.group(1);
				parts.put(current, new ArrayList<>());
				continue;
			}
			parts.get(current).add(line);
		}
		if (parts.get("").isEmpty() && parts.size() > 1) {
			parts.remove("");
		}
		return parts;
	}

	/**
	 * A grid of item numbers with an X under TAK or NIE, or, in a few reading tasks, a third column such as
	 * {@code BI} ("brak informacji", "not stated"). TAK and NIE still become a plain boolean; any other column
	 * becomes a choice of that column's own heading.
	 */
	private static boolean trueFalse(List<Line> lines, Map<String, KeyParser.Entry> entries) {
		List<Word> header = null;
		for (Line line : lines) {
			List<Word> words = line.words();
			for (int i = 0; i + 1 < words.size(); i++) {
				if (words.get(i).text().equals("TAK") && words.get(i + 1).text().equals("NIE")) {
					header = new ArrayList<>(List.of(words.get(i), words.get(i + 1)));
					for (int j = i + 2; j < words.size() && words.get(j).text().matches("\\p{Lu}{1,4}"); j++) {
						header.add(words.get(j));
					}
				}
			}
		}
		if (header == null) {
			return false;
		}
		double firstX = header.getFirst().centerX();
		double lastX = header.getLast().centerX();
		for (Line line : lines) {
			Cells.Anchor anchor = Cells.firstWord(line, word -> {
				Matcher label = ITEM_LABEL.matcher(word.text());
				return label.matches() && word.x0() < firstX - 20 ? label.group(1) : null;
			});
			if (anchor == null) {
				continue;
			}
			Word mark = null;
			double nearest = 13;
			for (Line other : lines) {
				double distance = Math.abs(other.baseline() - line.baseline());
				if (other.page() != line.page() || distance >= nearest) {
					continue;
				}
				for (Word word : other.words()) {
					if (word.text().equalsIgnoreCase("X") && word.x0() > firstX - 40 && word.x0() < lastX + 40) {
						mark = word;
						nearest = distance;
					}
				}
			}
			if (mark != null) {
				Word column = header.getFirst();
				for (Word candidate : header) {
					if (Math.abs(candidate.centerX() - mark.centerX()) < Math.abs(column.centerX() - mark.centerX())) {
						column = candidate;
					}
				}
				Answer answer = column.text().equals("TAK") ? new Answer.Bool(true)
						: column.text().equals("NIE") ? new Answer.Bool(false) : new Answer.Choice(column.text());
				entries.put(anchor.label(), new KeyParser.Entry(anchor.label(), answer, null, null));
			}
		}
		return !entries.isEmpty();
	}

	private record Label(String id, boolean anyOrder, boolean letter, double centerX) {
	}

	/** A row of labels of one kind, or null. Labels are separated by wide gaps; "Uczeń 2" is one label. */
	private static List<Label> labels(Line line) {
		List<Label> labels = new ArrayList<>();
		for (Line segment : line.segments(10)) {
			String text = Text.collapse(segment.text());
			if (text.startsWith("PRZYKŁAD")) {
				continue;
			}
			Matcher number = ITEM_LABEL.matcher(text);
			Matcher letter = LETTER_LABEL.matcher(text);
			Matcher person = PERSON_LABEL.matcher(text);
			double center = (segment.x0() + segment.x1()) / 2;
			if (number.matches()) {
				labels.add(new Label(number.group(1), !(number.group(2) + number.group(3)).isEmpty(), false, center));
			}
			else if (letter.matches()) {
				String id = letter.group(1) != null ? letter.group(1) : letter.group(4);
				labels.add(new Label(id, text.contains("*"), true, center));
			}
			else if (person.matches()) {
				labels.add(new Label(person.group(2), text.contains("*"), false, center));
			}
			else {
				return null;
			}
		}
		if (labels.size() < 2 || labels.stream().map(Label::letter).distinct().count() > 1) {
			return null;
		}
		return labels;
	}

	private static boolean table(List<Line> lines, Map<String, KeyParser.Entry> entries) {
		boolean found = false;
		for (int i = 0; i < lines.size(); i++) {
			Line header = lines.get(i);
			List<Label> labels = labels(header);
			if (labels == null) {
				continue;
			}
			List<Line> rows = new ArrayList<>();
			double previous = header.baseline();
			int j = i + 1;
			for (; j < lines.size(); j++) {
				Line row = lines.get(j);
				List<Label> rowLabels = labels(row);
				boolean nextHeader = rowLabels != null && rowLabels.getFirst().letter() == labels.getFirst().letter();
				if (nextHeader || row.page() != header.page() || row.baseline() - previous > 32) {
					break;
				}
				rows.add(row);
				previous = row.baseline();
			}
			if (rows.isEmpty()) {
				continue;
			}
			double spacing = labels.size() > 1 ? (labels.getLast().centerX() - labels.getFirst().centerX()) / (labels.size() - 1) : 40;
			for (Label label : labels) {
				List<String> values = new ArrayList<>();
				for (Line row : rows) {
					List<Word> cell = new ArrayList<>();
					for (Word word : row.words()) {
						Label nearest = null;
						for (Label candidate : labels) {
							if (nearest == null || Math.abs(candidate.centerX() - word.centerX()) < Math.abs(nearest.centerX() - word.centerX())) {
								nearest = candidate;
							}
						}
						if (nearest == label && Math.abs(label.centerX() - word.centerX()) < Math.max(40, spacing * 0.75)) {
							cell.add(word);
						}
					}
					if (!cell.isEmpty()) {
						values.add(Text.collapse(Text.text(cell)));
					}
				}
				if (values.isEmpty()) {
					continue;
				}
				Answer answer = values.size() == 1 ? answer(values.getFirst(), label.anyOrder())
						: new Answer.Parts(values.stream().map(value -> answer(value, label.anyOrder())).toList());
				entries.put(label.id(), new KeyParser.Entry(label.id(), answer, null, null));
				found = true;
			}
			i = j - 1;
		}
		return found;
	}

	/**
	 * Numbered answers in one or two columns. Within a column, the answer can be split into sub-answers printed in
	 * sub-columns after the letters {@code a}, {@code b}; and a header such as "możliwe odpowiedzi – punkty –
	 * fragment transkrypcji", or a column of text far to the right, holds points and justifications.
	 */
	private static boolean list(List<Line> lines, Map<String, KeyParser.Entry> entries, Borders borders) {
		List<Double> columns = labelColumns(lines);
		if (columns.isEmpty()) {
			return false;
		}
		for (int c = 0; c < columns.size(); c++) {
			double labelX = columns.get(c);
			double from = labelX - 6;
			double to = c + 1 < columns.size() ? columns.get(c + 1) - 6 : Double.POSITIVE_INFINITY;
			List<Line> sliced = new ArrayList<>();
			for (Line line : lines) {
				Line slice = line.slice(from, to);
				if (slice != null) {
					sliced.add(slice);
				}
			}

			Double pointsX = null;
			Double evidenceX = null;
			for (Line line : new ArrayList<>(sliced)) {
				String text = Text.collapse(line.text()).toLowerCase();
				if (text.contains("odpowied") && (text.contains("punkt") || text.contains("fragment") || text.contains("transkrypcj"))) {
					for (Word word : line.words()) {
						String lower = word.text().toLowerCase();
						if (lower.startsWith("punkt")) {
							pointsX = word.x0() - 15;
						}
						if (lower.startsWith("fragment") || lower.startsWith("uzasadn") || lower.startsWith("transkrypcj") && evidenceX == null) {
							evidenceX = word.x0() - 15;
						}
					}
					sliced.remove(line);
				}
			}
			List<Cells.Anchor> anchors = new ArrayList<>();
			for (Line line : sliced) {
				Word first = line.words().getFirst();
				if (ITEM_LABEL.matcher(first.text()).matches() && Math.abs(first.x0() - labelX) < 12) {
					anchors.add(new Cells.Anchor(first.text(), line, null));
				}
			}
			double headerEnd = pointsX != null ? pointsX : evidenceX != null ? evidenceX : Double.POSITIVE_INFINITY;
			List<Double> subColumns = subColumns(anchors, headerEnd);
			if (pointsX == null && evidenceX == null && subColumns.size() < 2) {
				// Sub-answer columns are not remarks, so a remark column is only looked for without them.
				evidenceX = asideColumn(sliced, labelX);
			}
			double answersEnd = pointsX != null ? pointsX : evidenceX != null ? evidenceX : Double.POSITIVE_INFINITY;

			Map<Cells.Anchor, List<String>> answers = new LinkedHashMap<>();
			anchors.forEach(anchor -> answers.put(anchor, new ArrayList<>()));
			Map<Cells.Anchor, List<List<String>>> subAnswers = new LinkedHashMap<>();
			if (subColumns.size() >= 2) {
				for (int s = 0; s < subColumns.size(); s++) {
					double subFrom = subColumns.get(s);
					double subTo = s + 1 < subColumns.size() ? subColumns.get(s + 1) : answersEnd;
					Map<Cells.Anchor, List<Line>> cells = column(anchors, sliced, subFrom + 8, subTo, borders);
					cells.forEach((anchor, cell) -> subAnswers.computeIfAbsent(anchor, key -> new ArrayList<>())
							.add(cell.stream().map(line -> Text.collapse(line.text())).toList()));
				}
			}
			else {
				column(anchors, sliced, labelX + 6, answersEnd, borders).forEach((anchor, cell) ->
						answers.get(anchor).addAll(cell.stream().map(line -> Text.collapse(line.text())).toList()));
			}
			Map<Cells.Anchor, List<Line>> points = pointsX == null ? Map.of()
					: column(anchors, sliced, pointsX, evidenceX == null ? Double.POSITIVE_INFINITY : evidenceX, borders);
			Map<Cells.Anchor, List<Line>> evidence = evidenceX == null ? Map.of()
					: nearestColumn(anchors, sliced, evidenceX, Double.POSITIVE_INFINITY, borders);

			for (Cells.Anchor anchor : anchors) {
				Matcher label = ITEM_LABEL.matcher(anchor.label());
				label.matches();
				String id = label.group(1);
				boolean anyOrder = !(label.group(2) + label.group(3)).isEmpty();
				List<String> notes = new ArrayList<>();
				Answer answer;
				if (subAnswers.containsKey(anchor)) {
					List<Answer> parts = new ArrayList<>();
					for (List<String> sub : subAnswers.get(anchor)) {
						parts.add(answer(withoutNote(Text.joinLines(sub), notes), anyOrder));
					}
					answer = new Answer.Parts(parts);
				}
				else {
					answer = answer(withoutNote(Text.joinLines(answers.get(anchor)), notes), anyOrder);
				}
				String pointsText = text(points.get(anchor));
				String evidenceText = text(evidence.get(anchor));
				boolean recipe = evidenceText != null && pointsX == null && evidenceText.matches(".*\\d+,\\d+.*")
						&& !evidenceText.contains("(…)");
				if (pointsText != null) {
					notes.addFirst(pointsText);
				}
				if (recipe) {
					notes.add(evidenceText);
					evidenceText = null;
				}
				entries.put(id, new KeyParser.Entry(id, answer, notes.isEmpty() ? null : String.join("; ", notes), evidenceText));
			}
		}
		return !entries.isEmpty();
	}

	/** The lines of a column, assigned to the item anchors by the flow rule. */
	private static Map<Cells.Anchor, List<Line>> column(List<Cells.Anchor> anchors, List<Line> lines, double from, double to,
			Borders borders) {
		List<Line> sliced = new ArrayList<>();
		for (Line line : lines) {
			Line slice = line.slice(from, to);
			if (slice != null) {
				sliced.add(slice);
			}
		}
		List<Cells.Anchor> local = new ArrayList<>();
		List<Line> rest = new ArrayList<>(sliced);
		for (Cells.Anchor anchor : anchors) {
			Line own = sliced.stream().filter(line -> line.page() == anchor.line().page()
					&& Math.abs(line.baseline() - anchor.line().baseline()) < 0.5).findFirst().orElse(null);
			rest.remove(own);
			local.add(new Cells.Anchor(anchor.label(), anchor.line(), own));
		}
		Map<Cells.Anchor, List<Line>> cells = Cells.flow(local, rest, 30, 40, borders);
		Map<Cells.Anchor, List<Line>> byOriginal = new LinkedHashMap<>();
		for (int i = 0; i < anchors.size(); i++) {
			byOriginal.put(anchors.get(i), cells.get(local.get(i)));
		}
		return byOriginal;
	}

	/** Remarks are assigned to the nearest item: a justification is printed level with the answer it supports. */
	private static Map<Cells.Anchor, List<Line>> nearestColumn(List<Cells.Anchor> anchors, List<Line> lines, double from,
			double to, Borders borders) {
		Map<Cells.Anchor, List<Line>> cells = new LinkedHashMap<>();
		anchors.forEach(anchor -> cells.put(anchor, new ArrayList<>()));
		for (Line line : lines) {
			Line slice = line.slice(from, to);
			Cells.Anchor owner = slice == null ? null : Cells.nearest(anchors, slice, borders);
			if (owner != null) {
				cells.get(owner).add(slice);
			}
		}
		return cells;
	}

	/** The x positions of sub-answer letters a, b… that follow the item labels on at least two items. */
	private static List<Double> subColumns(List<Cells.Anchor> anchors, double answersEnd) {
		List<Double> starts = new ArrayList<>();
		for (Cells.Anchor anchor : anchors) {
			for (Word word : anchor.line().words()) {
				if (SUB_LABEL.matcher(word.text()).matches() && word.x0() < answersEnd) {
					starts.add(word.x0());
				}
			}
		}
		List<Double> columns = new ArrayList<>();
		for (double start : starts.stream().sorted().toList()) {
			long count = starts.stream().filter(other -> Math.abs(other - start) < 10).count();
			if (count >= 2 && columns.stream().noneMatch(column -> Math.abs(column - start) < 30)) {
				columns.add(start);
			}
		}
		return columns;
	}

	/**
	 * The x positions of the columns of item labels: positions where a label recurs across several lines (the
	 * usual case, one item per line), plus any position that stands beside an already-known column on even a
	 * single line (catching the last, lone column of an uneven split, such as items 1–4, 5–8 and a lone 9, where
	 * that third column would otherwise show up only once).
	 */
	private static List<Double> labelColumns(List<Line> lines) {
		List<List<Double>> rows = new ArrayList<>();
		for (Line line : lines) {
			List<Word> words = line.words();
			List<Double> rowLabels = new ArrayList<>();
			for (int i = 0; i < words.size(); i++) {
				boolean standsAlone = i == 0 || words.get(i).x0() - words.get(i - 1).x1() > 20;
				boolean points = i + 1 < words.size() && words.get(i + 1).text().matches("p\\.?|pkt\\.?|x");
				if (standsAlone && !points && ITEM_LABEL.matcher(words.get(i).text()).matches()) {
					rowLabels.add(words.get(i).x0());
				}
			}
			if (!rowLabels.isEmpty()) {
				rows.add(rowLabels);
			}
		}

		List<Double> columns = new ArrayList<>();
		for (List<Double> row : rows) {
			for (double x : row) {
				if (rows.stream().filter(other -> other != row)
						.anyMatch(other -> other.stream().anyMatch(y -> Math.abs(y - x) < 12))
						&& columns.stream().noneMatch(column -> Math.abs(column - x) < 60)) {
					columns.add(x);
				}
			}
		}
		// A row's extra label beyond the confirmed columns only counts as a further column if the row already
		// lines up with at least two confirmed columns; one confirmed label plus one extra is more likely a plain
		// number inside that item's own answer, such as "10 metrów", than a genuine fourth column.
		for (List<Double> row : rows) {
			long onConfirmedColumns = row.stream().filter(x -> columns.stream().anyMatch(column -> Math.abs(column - x) < 12)).count();
			if (onConfirmedColumns < 2) {
				continue;
			}
			for (double x : row) {
				if (columns.stream().noneMatch(column -> Math.abs(column - x) < 15)) {
					columns.add(x);
				}
			}
		}
		columns.sort(Double::compareTo);
		if (!columns.isEmpty()) {
			return columns;
		}
		return rows.isEmpty() ? List.of() : List.of(rows.getFirst().getFirst());
	}

	/**
	 * The x where a column of remarks starts: text that begins far to the right of the item labels, after a wide
	 * gap or on a line of its own, on at least two lines at about the same x.
	 */
	private static Double asideColumn(List<Line> lines, double labelX) {
		List<Double> starts = new ArrayList<>();
		for (Line line : lines) {
			List<Line> segments = line.segments(25);
			for (int i = 0; i < segments.size(); i++) {
				double x = segments.get(i).x0();
				if ((i > 0 && x > labelX + 120) || (i == 0 && x > labelX + 180)) {
					starts.add(x);
					break;
				}
			}
		}
		for (double start : starts) {
			List<Double> near = starts.stream().filter(other -> Math.abs(other - start) < 25).toList();
			if (near.size() >= 2) {
				return near.stream().mapToDouble(Double::doubleValue).min().orElse(start) - 4;
			}
		}
		return null;
	}

	/**
	 * The numbers of the true statements of a task, printed as one row of plain numbers in any order, under a
	 * caption such as "Zdania prawdziwe".
	 */
	private static boolean set(List<Line> lines, Map<String, KeyParser.Entry> entries) {
		for (Line line : lines) {
			List<String> numbers = line.words().stream().map(Word::text).map(text -> text.replaceAll("[,;]$", "")).toList();
			boolean plain = numbers.stream().allMatch(text -> text.matches("\\d{1,2}"));
			boolean ascending = true;
			for (int i = 1; i < numbers.size() && plain; i++) {
				ascending &= Integer.parseInt(numbers.get(i)) > Integer.parseInt(numbers.get(i - 1));
			}
			boolean consecutive = plain && Integer.parseInt(numbers.getLast()) - Integer.parseInt(numbers.getFirst()) == numbers.size() - 1;
			if (numbers.size() >= 2 && plain && ascending && !consecutive) {
				entries.put("set", new KeyParser.Entry("set", new Answer.Choices(numbers, true), null, null));
				return true;
			}
		}
		return false;
	}

	/**
	 * A simple list of letter-labelled items, each on its own line: a letter (the fragment or the person, from a
	 * matching task), then one or more numbers, as in {@code C* 1, 2, 4} ("statements 1, 2 and 4 are about C"). This
	 * differs from {@link #table} in that each line carries its own full answer rather than only one column of a
	 * grid, so a line with a lone trailing number isn't mistaken for a second column of labels.
	 */
	private static boolean letterList(List<Line> lines, Map<String, KeyParser.Entry> entries) {
		List<String> letters = new ArrayList<>();
		for (Line line : lines) {
			Matcher letter = LETTER_LABEL.matcher(line.words().getFirst().text());
			if (letter.matches() && line.words().size() >= 2) {
				letters.add(letter.group(1) != null ? letter.group(1) : letter.group(4));
			}
		}
		boolean ascending = letters.size() >= 2;
		for (int i = 1; i < letters.size() && ascending; i++) {
			ascending &= letters.get(i).compareTo(letters.get(i - 1)) > 0;
		}
		if (!ascending) {
			return false;
		}
		for (Line line : lines) {
			Matcher letter = LETTER_LABEL.matcher(line.words().getFirst().text());
			if (!letter.matches() || line.words().size() < 2) {
				continue;
			}
			String id = letter.group(1) != null ? letter.group(1) : letter.group(4);
			boolean anyOrder = line.words().getFirst().text().contains("*");
			String value = Text.collapse(Text.text(line.words().subList(1, line.words().size())));
			entries.put(id, new KeyParser.Entry(id, answer(value, anyOrder), null, null));
		}
		return true;
	}

	/** Removes a "(!) …" remark from an answer, adding it to the notes. */
	private static String withoutNote(String value, List<String> notes) {
		Matcher note = NOTE.matcher(value);
		if (note.find()) {
			notes.add(note.group(1).strip());
			return value.substring(0, note.start()).strip();
		}
		return value;
	}

	private static String text(List<Line> lines) {
		if (lines == null || lines.isEmpty()) {
			return null;
		}
		return Text.joinLines(lines.stream().map(line -> Text.collapse(line.text())).toList());
	}

	static Answer answer(String value, boolean anyOrder) {
		String trimmed = value.strip();
		if (trimmed.endsWith("*")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
			anyOrder = true;
		}
		if (CHOICE.matcher(trimmed).matches() || NUMBERS.matcher(trimmed).matches()) {
			List<String> values = List.of(trimmed.replaceAll("\\.$", "").split("\\s*[,;.]\\s*"));
			return values.size() == 1 && !anyOrder ? new Answer.Choice(values.getFirst()) : new Answer.Choices(values, anyOrder);
		}
		return AnswerTextParser.parse(trimmed);
	}
}
