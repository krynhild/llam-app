package com.krynhild.polishwritinglab.ingestion.parse.sheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.Box;
import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Page;
import com.krynhild.polishwritinglab.ingestion.extract.Word;
import com.krynhild.polishwritinglab.ingestion.model.Example;
import com.krynhild.polishwritinglab.ingestion.model.Item;
import com.krynhild.polishwritinglab.ingestion.model.Option;
import com.krynhild.polishwritinglab.ingestion.parse.Cells;
import com.krynhild.polishwritinglab.ingestion.parse.Columns;
import com.krynhild.polishwritinglab.ingestion.parse.GapText;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Reads a task's material: its text, its items and their options, the worked example and the source URLs.
 *
 * <p>Items are found by the first layout that fits: numbered gaps in a text ({@code {{3}}}), numbered questions
 * with options {@code a)}–{@code e)}, or numbered rows (true/false statements, sentences to transform). Shared
 * options are read from a column of letters A–L or from a word bank. Answers come from the key, so an item whose
 * text can't be read still has its answer.
 */
public final class TaskBodyParser {

	private static final Pattern SOURCE = Pattern.compile("^(Na podstawie|Źródło|Tekst na podstawie)\\s*:?\\s*(.*)$");
	private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,2})\\.\\s*(.*)$");
	private static final Pattern NUMBER_ONLY = Pattern.compile("^(\\d{1,2})\\.?$");
	private static final Pattern OPTION = Pattern.compile("^([a-e])\\)\\s*(.*)$");
	// A paragraph letter is sometimes printed with a trailing period, like a dropped-capital label: "A.".
	private static final Pattern LETTER = Pattern.compile("^([A-L])\\.?$");
	// A task split into sub-tasks of its own, each with its own instruction, text and numbered items:
	// "A. Proszę wyjaśnić znaczenie podanych fraz…", "B. Proszę…".
	private static final Pattern SUB_TASK = Pattern.compile("^([A-D])\\.\\s+Proszę\\b");
	private static final Pattern VISUAL = Pattern.compile("(?i)(symbol|obraz|ilustracj|zdjęci|fotografi|rysun)");

	private final ExtractedDocument document;

	public TaskBodyParser(ExtractedDocument document) {
		this.document = document;
	}

	public record ParsedBody(String text, List<Option> options, Example example, List<Item> items, List<String> sources,
			boolean requiresVisual) {
	}

	public ParsedBody parse(String instruction, List<Line> body) {
		Map<String, List<Line>> parts = parts(body);
		if (parts.size() < 2) {
			return parseOne(instruction, body, "");
		}
		// A task with sub-tasks of its own ("A. Proszę wyjaśnić…", "B. Proszę…", each with its own text and
		// numbered items): parse each sub-task on its own, then prefix its item IDs with the part's letter, just
		// as the key does for the same structure.
		List<String> text = new ArrayList<>();
		List<Option> options = new ArrayList<>();
		List<Item> items = new ArrayList<>();
		List<String> sources = new ArrayList<>();
		boolean requiresVisual = false;
		for (Map.Entry<String, List<Line>> part : parts.entrySet()) {
			ParsedBody parsed = parseOne(instruction, part.getValue(), part.getKey());
			text.add(parsed.text());
			options.addAll(parsed.options());
			items.addAll(parsed.items());
			sources.addAll(parsed.sources());
			requiresVisual |= parsed.requiresVisual();
		}
		return new ParsedBody(String.join("\n\n", text), options, null, items, sources, requiresVisual);
	}

	/** Splits a task's body at sub-task headings such as "A. Proszę wyjaśnić znaczenie…", "B. Proszę…". */
	private static Map<String, List<Line>> parts(List<Line> body) {
		Map<String, List<Line>> parts = new LinkedHashMap<>();
		String current = "";
		parts.put(current, new ArrayList<>());
		for (Line line : body) {
			Matcher part = SUB_TASK.matcher(Text.collapse(line.text()));
			if (part.find() && line.bold()) {
				current = part.group(1);
				parts.put(current, new ArrayList<>());
			}
			parts.get(current).add(line);
		}
		if (parts.get("").isEmpty()) {
			parts.remove("");
		}
		return parts;
	}

	private ParsedBody parseOne(String instruction, List<Line> body, String part) {
		List<String> sources = new ArrayList<>();
		List<Line> lines = withoutSources(body, sources);
		List<Line> ordered = Columns.readingOrder(lines);
		String text = GapText.render(ordered).strip();

		List<Option> options = new ArrayList<>();
		Double letterColumn = letteredOptions(lines, options);
		if (options.isEmpty()) {
			wordBank(lines, options);
		}

		List<Item> items = new ArrayList<>();
		Example example;
		List<String> gaps = GapText.gaps(text);
		if (gaps.size() >= 2) {
			example = gapItems(text, lines, gaps, items);
		}
		else if (ordered.stream().filter(line -> OPTION.matcher(Text.collapse(line.text())).matches()).count() >= 2) {
			example = choiceItems(ordered, items);
		}
		else {
			example = rowItems(lines, letterColumn, items);
		}
		if (!part.isEmpty()) {
			items = items.stream().map(item -> new Item(part + "." + item.id(), item.context(), item.prompt(),
					item.options(), item.words(), item.answer(), item.scoringNote(), item.evidence())).toList();
		}

		boolean requiresVisual = VISUAL.matcher(instruction).find() || hasImages(body);
		return new ParsedBody(text, options, example, items, sources, requiresVisual);
	}

	private static List<Line> withoutSources(List<Line> body, List<String> sources) {
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < body.size(); i++) {
			String text = Text.collapse(body.get(i).text());
			Matcher source = SOURCE.matcher(text);
			if (!source.matches()) {
				lines.add(body.get(i));
				continue;
			}
			StringBuilder url = new StringBuilder(source.group(2));
			// A long URL wraps onto a right-aligned line that holds only the rest of the URL.
			while (i + 1 < body.size()) {
				String next = Text.collapse(body.get(i + 1).text());
				boolean continuation = !next.contains(" ") && next.matches("[\\w\\-./?=&%#~+:,]+")
						&& (url.toString().matches(".*[-/_=.]$") || next.contains("-") || next.contains("."))
						&& !next.matches("\\d{1,2}\\.");
				if (!continuation || url.isEmpty()) {
					break;
				}
				url.append(next);
				i++;
			}
			sources.add(url.toString());
		}
		return lines;
	}

	/**
	 * Options in one or more columns of capital letters, such as the fragments A–H of a gapped text or the
	 * paragraphs of a matching table. A long list (more than about ten fragments) is often printed as two columns
	 * side by side, continuing the alphabet (A–E, then F–J), so every column whose letters increase one at a time
	 * counts, not just the one that starts at A. Returns the x of the leftmost letter column, or null.
	 */
	private static Double letteredOptions(List<Line> lines, List<Option> options) {
		Map<Long, List<Word>> byColumn = new LinkedHashMap<>();
		for (Line line : lines) {
			List<Word> words = line.words();
			for (int i = 0; i < words.size(); i++) {
				Word word = words.get(i);
				boolean standsAlone = i == 0 || word.x0() - words.get(i - 1).x1() > 15;
				// A letter immediately followed by "Proszę…" isn't a fragment label: it's the heading of a
				// sub-task of its own, such as "A. Proszę wyjaśnić znaczenie podanych fraz…".
				boolean subTaskHeading = i + 1 < words.size() && words.get(i + 1).text().equals("Proszę");
				if (!word.superscript() && LETTER.matcher(word.text()).matches() && standsAlone && !subTaskHeading) {
					byColumn.computeIfAbsent(Math.round(word.x0() / 20), column -> new ArrayList<>()).add(word);
				}
			}
		}
		List<Double> columnXs = new ArrayList<>();
		for (List<Word> column : byColumn.values()) {
			if (isLetterColumn(column)) {
				columnXs.add(column.stream().mapToDouble(Word::x0).min().orElse(0));
			}
		}
		if (columnXs.isEmpty()) {
			return null;
		}
		columnXs.sort(Double::compareTo);

		for (int c = 0; c < columnXs.size(); c++) {
			double columnX = columnXs.get(c);
			double nextColumnX = c + 1 < columnXs.size() ? columnXs.get(c + 1) - 3 : Double.POSITIVE_INFINITY;
			List<Line> sliced = new ArrayList<>();
			for (Line line : lines) {
				Line slice = line.slice(columnX - 3, nextColumnX);
				if (slice != null) {
					sliced.add(slice);
				}
			}
			Map<Cells.Anchor, List<Line>> cells = Cells.group(sliced, line -> Cells.firstWord(line, word -> {
				Matcher letter = LETTER.matcher(word.text());
				return letter.matches() && Math.abs(word.x0() - columnX) < 20 ? letter.group(1) : null;
			}), 35);
			cells.forEach((anchor, cell) -> {
				String text = Text.joinLines(cell.stream().map(GapText::render).toList())
						.replaceFirst("^PRZYKŁAD\\s*", "")
						.replace("PRZYKŁAD", "")
						.strip();
				if (options.stream().noneMatch(option -> option.id().equals(anchor.label()))) {
					options.add(new Option(anchor.label(), text));
				}
			});
		}
		return columnXs.getFirst();
	}

	/**
	 * Whether a candidate column's distinct letters, read top to bottom, increase one letter at a time. {@code
	 * column} is already in reading order, because it was built by appending to it while scanning the lines in
	 * that order; a {@code Word} alone doesn't carry its page, so re-sorting it by baseline would scramble the
	 * order across a page break.
	 */
	private static boolean isLetterColumn(List<Word> column) {
		List<String> letters = column.stream()
				.map(Word::text)
				.distinct()
				.toList();
		if (letters.size() < 2) {
			return false;
		}
		for (int i = 1; i < letters.size(); i++) {
			if (letters.get(i).charAt(0) != letters.get(i - 1).charAt(0) + 1) {
				return false;
			}
		}
		return true;
	}

	/** A framed list of words to fill the gaps with, printed on one line with wide spacing. */
	private static void wordBank(List<Line> lines, List<Option> options) {
		for (Line line : lines) {
			List<Word> words = line.words();
			if (words.size() < 4 || words.stream().anyMatch(Word::superscript)) {
				continue;
			}
			boolean spaced = true;
			for (int i = 1; i < words.size(); i++) {
				if (words.get(i).x0() - words.get(i - 1).x1() < 12) {
					spaced = false;
				}
			}
			boolean plain = words.stream().allMatch(word -> word.text().matches("[\\p{L}-]{1,20}"));
			if (spaced && plain) {
				for (Word word : words) {
					options.add(new Option(word.text(), word.text()));
				}
				return;
			}
		}
	}

	/** Items of a text with numbered gaps; options are the bracketed words just before each gap. */
	private Example gapItems(String text, List<Line> lines, List<String> gaps, List<Item> items) {
		String flat = text.replace('\n', ' ');
		for (String gap : gaps) {
			String token = "{{" + gap + "}}";
			int at = flat.indexOf(token);
			List<Option> options = new ArrayList<>();
			Matcher before = Pattern.compile("\\(([^()]*)\\)\\s*" + Pattern.quote(token)).matcher(flat);
			if (before.find()) {
				for (String option : before.group(1).split(",\\s*")) {
					options.add(new Option(option.strip(), option.strip()));
				}
			}
			items.add(new Item(gap, null, sentence(flat, at, token.length()), options, null, null, null, null));
		}
		return gapExample(flat, lines);
	}

	private static String sentence(String text, int at, int length) {
		int start = at;
		while (start > 0 && ".?!".indexOf(text.charAt(start - 1)) < 0) {
			start--;
		}
		int end = at + length;
		while (end < text.length() && ".?!".indexOf(text.charAt(end)) < 0) {
			end++;
		}
		return text.substring(start, Math.min(text.length(), end + 1)).strip();
	}

	/** The worked example of a gap text, answered by an underlined or bold word before the {@code PRZYKŁAD} mark. */
	private Example gapExample(String flat, List<Line> lines) {
		int at = flat.indexOf(GapText.EXAMPLE);
		if (at < 0) {
			return null;
		}
		String answer = null;
		Matcher filled = Pattern.compile("\\[([\\p{L}\\d]{1,3})]\\s*" + Pattern.quote(GapText.EXAMPLE)).matcher(flat);
		if (filled.find()) {
			answer = filled.group(1);
		}
		for (Line line : answer != null ? List.<Line>of() : lines) {
			List<Word> words = line.words();
			for (int i = 0; i < words.size(); i++) {
				if (words.get(i).superscript() && words.get(i).text().toUpperCase().startsWith("PRZYKŁAD")) {
					answer = emphasizedBefore(line, i);
					break;
				}
			}
			if (answer != null) {
				break;
			}
		}
		return new Example(sentence(flat, at, GapText.EXAMPLE.length()), answer);
	}

	/** An underlined or bold word among the few words before position {@code end} of the line. */
	private String emphasizedBefore(Line line, int end) {
		Page page = document.page(line.page());
		for (int i = end - 1; i >= Math.max(0, end - 8); i--) {
			Word word = line.words().get(i);
			String clean = word.text().replaceAll("[(),.;:!?]", "");
			if (clean.isEmpty()) {
				continue;
			}
			if (underlined(page, word) || word.bold()) {
				return clean;
			}
		}
		return null;
	}

	private static boolean underlined(Page page, Word word) {
		for (Box rule : page.rules()) {
			boolean below = rule.top() >= word.baseline() - 1 && rule.top() <= word.baseline() + 4;
			double overlap = Math.min(rule.x1(), word.x1()) - Math.max(rule.x0(), word.x0());
			if (below && overlap > word.width() * 0.5) {
				return true;
			}
		}
		return false;
	}

	/** Numbered questions followed by options a)–e); text between items belongs to the next item. */
	private static Example choiceItems(List<Line> lines, List<Item> items) {
		String exampleAnswer = null;
		List<String> exampleText = new ArrayList<>();
		boolean inExample = false;

		String id = null;
		List<String> prompt = new ArrayList<>();
		List<String> context = new ArrayList<>();
		List<String> pendingContext = new ArrayList<>();
		List<Option> options = new ArrayList<>();

		for (Line line : lines) {
			String text = GapText.render(line).strip();
			if (text.startsWith("PRZYKŁAD")) {
				inExample = true;
				String rest = text.substring("PRZYKŁAD".length()).strip();
				if (!rest.isEmpty()) {
					exampleText.add(rest);
				}
				continue;
			}
			Matcher option = OPTION.matcher(text);
			Matcher numbered = NUMBERED.matcher(text);
			if (option.matches()) {
				if (inExample) {
					exampleText.add(text);
					if (line.bold()) {
						exampleAnswer = option.group(1);
					}
				}
				else if (id != null) {
					options.add(new Option(option.group(1), option.group(2).strip()));
				}
				continue;
			}
			if (numbered.matches() && !numbered.group(2).isEmpty()) {
				if (id != null) {
					items.add(choiceItem(id, context, prompt, options));
				}
				inExample = false;
				id = numbered.group(1);
				prompt = new ArrayList<>(List.of(numbered.group(2)));
				context = pendingContext;
				pendingContext = new ArrayList<>();
				options = new ArrayList<>();
				continue;
			}
			if (NUMBER_ONLY.matcher(text).matches()) {
				// The label of a short text printed in the margin; the item that asks about it follows.
				continue;
			}
			if (inExample && exampleAnswered(exampleText)) {
				// The example's options are done; this text belongs to the first item.
				inExample = false;
			}
			if (inExample) {
				exampleText.add(text);
			}
			else if (id != null && options.isEmpty()) {
				prompt.add(text);
			}
			else {
				pendingContext.add(text);
			}
		}
		if (id != null) {
			items.add(choiceItem(id, context, prompt, options));
		}
		return exampleText.isEmpty() ? null : new Example(Text.joinItemLines(exampleText), exampleAnswer);
	}

	private static boolean exampleAnswered(List<String> exampleText) {
		return exampleText.stream().anyMatch(text -> OPTION.matcher(text).matches());
	}

	private static Item choiceItem(String id, List<String> context, List<String> prompt, List<Option> options) {
		String contextText = context.isEmpty() ? null : Text.joinLines(context);
		return new Item(id, contextText, Text.joinLines(prompt), List.copyOf(options), null, null, null, null);
	}

	/**
	 * Numbered rows, such as true/false statements or sentences to transform. Rows are grouped by the nearest number,
	 * because a number is centred vertically next to a statement that wraps onto several lines.
	 */
	private Example rowItems(List<Line> lines, Double letterColumn, List<Item> items) {
		double right = Double.POSITIVE_INFINITY;
		Double trueColumn = null;
		Double falseColumn = null;
		for (Line line : lines) {
			Word yes = word(line, "TAK");
			Word no = word(line, "NIE");
			if (yes != null && no != null && no.x0() > yes.x0()) {
				trueColumn = yes.centerX();
				falseColumn = no.centerX();
				right = yes.x0() - 10;
			}
		}
		if (letterColumn != null && letterColumn > 150) {
			right = Math.min(right, letterColumn - 5);
		}

		double margin = lines.stream().mapToDouble(Line::x0).min().orElse(0);
		List<Line> sliced = new ArrayList<>();
		for (Line line : lines) {
			Line slice = line.slice(Double.NEGATIVE_INFINITY, right);
			if (slice != null && !"TAK".equals(slice.text()) && !Text.collapse(slice.text()).startsWith("TAK NIE")) {
				sliced.add(slice);
			}
		}
		java.util.function.Function<Line, Cells.Anchor> anchorOf = line -> {
			if (line.x0() > margin + 40) {
				return null;
			}
			return Cells.firstWord(line, word -> {
				Matcher number = NUMBER_ONLY.matcher(word.text());
				if (number.matches()) {
					return number.group(1);
				}
				return word.text().startsWith("PRZYKŁAD") ? "example" : null;
			});
		};
		// The example comes first and can be taller than the gap to item 1, so the lines between "PRZYKŁAD" and the
		// first item go to the example rather than to the nearest anchor.
		List<Line> exampleLines = new ArrayList<>();
		int exampleAt = -1;
		int firstItemAt = -1;
		for (int i = 0; i < sliced.size(); i++) {
			Cells.Anchor anchor = anchorOf.apply(sliced.get(i));
			if (anchor != null && anchor.label().equals("example") && exampleAt < 0) {
				exampleAt = i;
			}
			else if (anchor != null && exampleAt >= 0 && firstItemAt < 0) {
				firstItemAt = i;
			}
		}
		if (exampleAt >= 0 && firstItemAt > exampleAt + 1) {
			exampleLines.addAll(sliced.subList(exampleAt + 1, firstItemAt));
			sliced = new ArrayList<>(sliced);
			sliced.removeAll(exampleLines);
		}
		Map<Cells.Anchor, List<Line>> cells = Cells.group(sliced, anchorOf, 45);
		for (Map.Entry<Cells.Anchor, List<Line>> cell : cells.entrySet()) {
			if (cell.getKey().label().equals("example")) {
				cell.getValue().addAll(exampleLines);
				cell.getValue().sort((a, b) -> Double.compare(a.baseline(), b.baseline()));
			}
		}

		Example example = null;
		for (Map.Entry<Cells.Anchor, List<Line>> cell : cells.entrySet()) {
			String prompt = Text.joinItemLines(cell.getValue().stream().map(GapText::render).toList());
			if ("example".equals(cell.getKey().label())) {
				String answer = trueColumn == null ? boldText(cell.getValue())
						: mark(lines, cell.getKey().line(), cell.getValue(), trueColumn, falseColumn);
				example = new Example(prompt, answer);
			}
			else if (items.stream().noneMatch(item -> item.id().equals(cell.getKey().label()))) {
				items.add(new Item(cell.getKey().label(), null, prompt, List.of(), null, null, null, null));
			}
		}
		return example;
	}

	private static String boldText(List<Line> cell) {
		List<String> bold = cell.stream().filter(Line::bold).map(GapText::render).toList();
		return bold.isEmpty() ? null : Text.joinItemLines(bold);
	}

	/** TAK or NIE, from the column of the X printed in the rows of a cell. */
	private static String mark(List<Line> lines, Line anchor, List<Line> cell, double trueColumn, double falseColumn) {
		double top = cell.stream().mapToDouble(Line::baseline).min().orElse(anchor.baseline()) - 14;
		double bottom = cell.stream().mapToDouble(Line::baseline).max().orElse(anchor.baseline()) + 14;
		for (Line line : lines) {
			if (line.page() != anchor.page() || line.baseline() < Math.min(top, anchor.baseline() - 14)
					|| line.baseline() > Math.max(bottom, anchor.baseline() + 14)) {
				continue;
			}
			for (Word word : line.words()) {
				if (word.text().equalsIgnoreCase("X") && word.x0() > trueColumn - 40) {
					return Math.abs(word.centerX() - trueColumn) < Math.abs(word.centerX() - falseColumn) ? "TAK" : "NIE";
				}
			}
		}
		return null;
	}

	private static Word word(Line line, String text) {
		return line.words().stream().filter(word -> word.text().equals(text)).findFirst().orElse(null);
	}

	private boolean hasImages(List<Line> body) {
		for (int pageNumber : body.stream().map(Line::page).distinct().toList()) {
			Page page = document.page(pageNumber);
			double top = body.stream().filter(line -> line.page() == pageNumber).mapToDouble(Line::baseline).min().orElse(0) - 20;
			double bottom = body.stream().filter(line -> line.page() == pageNumber).mapToDouble(Line::baseline).max().orElse(0) + 60;
			for (Box image : page.images()) {
				if (image.width() > 25 && image.height() > 25 && image.bottom() > top && image.top() < bottom) {
					return true;
				}
			}
		}
		return false;
	}
}
