package com.krynhild.polishwritinglab.ingestion.parse.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.Item;
import com.krynhild.polishwritinglab.ingestion.parse.Cells;
import com.krynhild.polishwritinglab.ingestion.parse.GapText;
import com.krynhild.polishwritinglab.ingestion.parse.Roman;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Reads the writing module: alternative sets ("Zestaw I–III", each with a short task {@code a.} and a long task
 * {@code b.}) up to C1, alternative single tasks ("Zadanie I–II") at C2, and the examiners' scoring card.
 */
public final class WritingParser {

	private static final Pattern SET = Pattern.compile("^(Zestaw|Zadanie)\\s*([IVX]{1,4})$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SUBTASK = Pattern.compile("^([a-c])[.)]$");
	private static final Pattern WORDS = Pattern.compile("(\\d{2,4})\\s+słów$");

	public record WritingSet(String numeral, int ordinal, String heading, List<Item> items, List<Integer> pages) {
	}

	public record ParsedWriting(String instruction, List<WritingSet> sets, String scoringCard) {
	}

	public ParsedWriting parse(String target, List<Line> lines, List<Issue> issues) {
		List<String> instruction = new ArrayList<>();
		List<WritingSet> sets = new ArrayList<>();
		List<String> card = new ArrayList<>();

		String heading = null;
		List<Line> setLines = new ArrayList<>();
		boolean inCard = false;
		boolean beforeSets = true;
		for (Line line : lines) {
			String text = Text.unspace(line.text());
			if (text.startsWith("KARTA OCENY")) {
				inCard = true;
			}
			if (inCard) {
				card.add(Text.collapse(line.text()));
				continue;
			}
			Matcher set = SET.matcher(text);
			if (set.matches()) {
				if (heading != null) {
					sets.add(set(target, heading, sets.size() + 1, setLines, issues));
				}
				heading = text;
				setLines = new ArrayList<>();
				beforeSets = false;
				continue;
			}
			if (beforeSets) {
				instruction.add(text);
			}
			else {
				setLines.add(line);
			}
		}
		if (heading != null) {
			sets.add(set(target, heading, sets.size() + 1, setLines, issues));
		}
		if (sets.isEmpty()) {
			issues.add(Issue.error("writing-tasks", target, "No writing sets or tasks found"));
		}
		String instructionText = Text.joinLines(instruction.stream()
				.filter(text -> !text.matches("^PISANIE$") && !text.matches("^(Jeden|Jedno).*Liczba punktów.*"))
				.toList());
		return new ParsedWriting(instructionText.isEmpty() ? null : instructionText, sets,
				card.isEmpty() ? null : String.join("\n", card));
	}

	private WritingSet set(String target, String heading, int ordinal, List<Line> lines, List<Issue> issues) {
		String numeral = Roman.of(ordinal);
		List<Item> items = new ArrayList<>();
		double margin = lines.stream().mapToDouble(Line::x0).min().orElse(0);
		Map<Cells.Anchor, List<Line>> cells = Cells.group(lines, line -> line.x0() > margin + 30 ? null
				: Cells.firstWord(line, word -> {
					Matcher subtask = SUBTASK.matcher(word.text());
					return subtask.matches() ? subtask.group(1) : null;
				}), 40);
		if (cells.isEmpty()) {
			items.add(item("1", lines));
		}
		else {
			cells.forEach((anchor, cell) -> items.add(item(anchor.label(), cell)));
		}
		if (items.stream().anyMatch(item -> item.words() == null)) {
			issues.add(Issue.warning("writing-words", target + "/" + numeral, "A writing task doesn't state its length"));
		}
		return new WritingSet(numeral, ordinal, heading, items, lines.stream().map(Line::page).distinct().toList());
	}

	private static final Pattern TRAILING_COUNT = Pattern.compile("^(.*\\S)\\s+(\\d{2,4})$");
	private static final Pattern TRAILING_SLOW = Pattern.compile("^(.*\\S)\\s+słów$");

	private static Item item(String id, List<Line> lines) {
		List<String> rendered = lines.stream().map(line -> GapText.render(line).strip()).collect(Collectors.toCollection(ArrayList::new));
		// A long task floats its word count in the right margin; on a short task it reads "30 słów" on one line,
		// but a long task needs the number and "słów" on two different lines of the wrapped instruction text.
		Integer words = null;
		for (int i = 0; i < rendered.size(); i++) {
			Matcher count = WORDS.matcher(rendered.get(i));
			if (count.find()) {
				words = Integer.parseInt(count.group(1));
				rendered.set(i, rendered.get(i).substring(0, count.start()).strip());
				break;
			}
			Matcher trailingCount = TRAILING_COUNT.matcher(rendered.get(i));
			Matcher trailingSlow = i + 1 < rendered.size() ? TRAILING_SLOW.matcher(rendered.get(i + 1)) : null;
			if (trailingCount.matches() && trailingSlow != null && trailingSlow.matches()) {
				words = Integer.parseInt(trailingCount.group(2));
				rendered.set(i, trailingCount.group(1));
				rendered.set(i + 1, trailingSlow.group(1));
				break;
			}
		}
		// A handful of sessions float just the bare number in the margin, without "słów" anywhere beside it.
		if (words == null) {
			for (int i = 0; i < lines.size(); i++) {
				List<Word> lineWords = lines.get(i).words();
				Word last = lineWords.getLast();
				boolean floated = lineWords.size() == 1 || last.x0() - lineWords.get(lineWords.size() - 2).x1() > 30;
				int value = last.text().matches("\\d{2,4}") ? Integer.parseInt(last.text()) : -1;
				if (floated && value >= 20 && value <= 600) {
					words = value;
					rendered.set(i, rendered.get(i).replaceAll("\\s*\\d{2,4}$", ""));
					break;
				}
			}
		}
		List<String> prompt = rendered.stream().filter(text -> !text.isEmpty()).toList();
		return new Item(id, null, Text.joinLines(prompt), List.of(), words, null, null, null);
	}
}
