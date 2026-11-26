package com.krynhild.polishwritinglab.ingestion.parse.key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.clean.Boilerplate;
import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;
import com.krynhild.polishwritinglab.ingestion.model.Answer;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.KeyNotes;
import com.krynhild.polishwritinglab.ingestion.model.Transcript;
import com.krynhild.polishwritinglab.ingestion.parse.Borders;
import com.krynhild.polishwritinglab.ingestion.parse.Roman;
import com.krynhild.polishwritinglab.ingestion.parse.Skills;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Reads a "Transkrypcje i klucz do zadań" document: the transcripts of the listening module and the answer keys of
 * the listening, reading and grammar modules.
 *
 * <p>The key of each task follows a heading {@code Zadanie <numeral>}. Some sessions print two keys side by side, so
 * headings in the right half of the page start a column of their own. Within a task the answers are a true/false
 * grid (an {@code X} under TAK or NIE), a horizontal table (item numbers over answers) or a list (item number, then
 * the answer), each read by position rather than by token order, because empty cells and multi-line cells would
 * otherwise shift answers between items.
 */
public final class KeyParser {

	private static final Pattern TASK_HEADING = Pattern.compile("^Zadanie$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NUMERAL = Pattern.compile("^[IVX]{1,5}\\.?$");
	// The noun ("wyraz", "fragment"...) can come before or after "zbędny", or be left out: "Wyraz zbędny: więc",
	// "Tytuły zbędne: C, I", "Zbędny fragment: I", "Zbędne: F, K".
	private static final Pattern DISTRACTOR = Pattern.compile(
			"^(?:\\p{L}+\\s+)?zbędn\\p{L}*(?:\\s+\\p{L}+)?\\s*:\\s*(.+)$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern ACCEPT_OTHER = Pattern.compile("^Należy (uznać|zaakceptować)", Pattern.CASE_INSENSITIVE);

	public record ParsedKey(Map<String, ModuleKey> modules) {
	}

	public record ModuleKey(String skill, Map<String, Transcript> transcripts, Map<String, TaskKey> tasks) {
	}

	public record TaskKey(String numeral, Map<String, Entry> answers, KeyNotes notes) {
	}

	/** One item's answer and the remarks printed next to it. */
	public record Entry(String item, Answer answer, String scoringNote, String evidence) {
	}

	public ParsedKey parse(ExtractedDocument document, String target, List<Issue> issues) {
		List<Line> lines = Boilerplate.withoutRunningText(document);
		double pageWidth = document.page(1).width();

		Map<String, ModuleKey> modules = new LinkedHashMap<>();
		String skill = null;
		String section = null;
		Map<String, List<Line>> sections = new LinkedHashMap<>();
		for (Line line : lines) {
			String text = Text.collapse(line.text());
			String heading = Skills.ofTitle(text);
			if (heading != null && line.bold() && text.length() < 40) {
				skill = heading;
				section = null;
				continue;
			}
			String normalized = text.toLowerCase();
			if (skill != null && line.bold() && normalized.matches("^transkrypcj\\p{L}*$")) {
				section = "transcripts";
				continue;
			}
			if (skill != null && line.bold() && normalized.matches("^klucz( do zadań| odpowiedzi)?$")) {
				section = "key";
				continue;
			}
			if (skill != null && section != null) {
				sections.computeIfAbsent(skill + "/" + section, key -> new ArrayList<>()).add(line);
			}
		}

		for (Map.Entry<String, List<Line>> entry : sections.entrySet()) {
			String[] parts = entry.getKey().split("/");
			ModuleKey module = modules.computeIfAbsent(parts[0],
					name -> new ModuleKey(name, new LinkedHashMap<>(), new LinkedHashMap<>()));
			if (parts[1].equals("transcripts")) {
				transcripts(target + "/" + parts[0], entry.getValue(), module.transcripts(), issues);
			}
			else {
				keys(target + "/" + parts[0], entry.getValue(), pageWidth, new Borders(document), module.tasks(), issues);
			}
		}
		if (modules.isEmpty()) {
			issues.add(Issue.error("key-sections", target, "No module sections found in the key"));
		}
		return new ParsedKey(modules);
	}

	private static void transcripts(String target, List<Line> lines, Map<String, Transcript> transcripts,
			List<Issue> issues) {
		String numeral = null;
		List<Line> current = new ArrayList<>();
		for (Line line : lines) {
			String found = transcriptNumeral(line);
			if (found != null) {
				if (numeral != null) {
					transcripts.put(numeral, TranscriptParser.parse(numeral, current));
				}
				numeral = found;
				current = new ArrayList<>();
				Line rest = line.words().size() > 2
						? new Line(line.page(), line.baseline(), line.words().subList(2, line.words().size())) : null;
				if (rest != null) {
					current.add(rest);
				}
				continue;
			}
			if (numeral != null) {
				current.add(line);
			}
		}
		if (numeral != null) {
			transcripts.put(numeral, TranscriptParser.parse(numeral, current));
		}
		if (transcripts.isEmpty()) {
			issues.add(Issue.error("transcripts-missing", target, "No transcripts found"));
		}
	}

	/**
	 * The numeral of a transcript heading. The headings vary in case and spelling ("TRANSKRYPCJA III",
	 * "Transkrypcja III", "TRANKSKRYPCJA IV"), so the first word only has to be close to "transkrypcja".
	 */
	static String transcriptNumeral(Line line) {
		if (line.words().size() < 2) {
			return null;
		}
		String first = line.words().get(0).text().toLowerCase();
		String second = line.words().get(1).text().replace(".", "");
		if (Roman.parse(second) == 0 || distance(first, "transkrypcja") > 2) {
			return null;
		}
		return Roman.of(Roman.parse(second));
	}

	private record Heading(String numeral, int page, double baseline, double x0) {
	}

	private void keys(String target, List<Line> lines, double pageWidth, Borders borders, Map<String, TaskKey> tasks,
			List<Issue> issues) {
		List<Heading> headings = new ArrayList<>();
		for (Line line : lines) {
			List<Word> words = line.words();
			for (int i = 0; i + 1 < words.size(); i++) {
				if (TASK_HEADING.matcher(words.get(i).text()).matches() && NUMERAL.matcher(words.get(i + 1).text()).matches()) {
					String numeral = words.get(i + 1).text().replace(".", "");
					headings.add(new Heading(numeral, line.page(), line.baseline(), words.get(i).x0()));
				}
			}
		}
		if (headings.isEmpty()) {
			issues.add(Issue.error("key-tasks", target, "No 'Zadanie' headings found in the key"));
			return;
		}

		double split = pageWidth * 0.35;
		for (int h = 0; h < headings.size(); h++) {
			Heading heading = headings.get(h);
			boolean right = heading.x0() > split;
			double[] end = blockEnd(headings, h, right, split);
			double from = right ? heading.x0() - 15 : 0;
			double to = right ? Double.POSITIVE_INFINITY : leftLimit(headings, heading, end, split);

			List<Line> block = new ArrayList<>();
			for (Line line : lines) {
				boolean after = line.page() > heading.page()
						|| line.page() == heading.page() && line.baseline() > heading.baseline() + 2;
				boolean before = line.page() < end[0] || line.page() == end[0] && line.baseline() < end[1] - 2;
				if (after && before) {
					Line slice = line.slice(from, to);
					if (slice != null) {
						block.add(slice);
					}
				}
			}
			String taskTarget = target + "/" + heading.numeral();
			tasks.put(heading.numeral(), task(taskTarget, heading.numeral(), block, borders, issues));
		}
	}

	/**
	 * Where a block ends: at the next heading in its own column, normally. The two columns of a page aren't in
	 * lockstep (one task can run on well past where the one beside it ends), so a heading in the other column never
	 * ends a block as long as a further heading of the block's own column is still to come. But once a right
	 * column has no task left to come (its key reverts to one column for the rest of the section), its last task's
	 * block has nothing to end at in its own column, so it ends at the very next heading, left or right.
	 */
	private static double[] blockEnd(List<Heading> headings, int index, boolean right, double split) {
		Heading heading = headings.get(index);
		boolean columnContinues = false;
		for (Heading other : headings) {
			boolean below = other.page() > heading.page()
					|| other.page() == heading.page() && other.baseline() > heading.baseline() + 2;
			boolean sameColumn = (other.x0() > split) == right;
			columnContinues |= below && sameColumn;
		}
		Heading next = null;
		for (Heading other : headings) {
			boolean below = other.page() > heading.page()
					|| other.page() == heading.page() && other.baseline() > heading.baseline() + 2;
			boolean sameColumn = (other.x0() > split) == right;
			boolean ends = sameColumn || right && !columnContinues;
			if (below && ends && (next == null || other.page() < next.page()
					|| other.page() == next.page() && other.baseline() < next.baseline())) {
				next = other;
			}
		}
		if (next == null) {
			return new double[] { right ? heading.page() + 1 : Integer.MAX_VALUE, 0 };
		}
		return new double[] { next.page(), next.baseline() };
	}

	/** The right edge of a left-column block: the start of a right column printed next to it. */
	private static double leftLimit(List<Heading> headings, Heading heading, double[] end, double split) {
		double limit = Double.POSITIVE_INFINITY;
		for (Heading other : headings) {
			boolean beside = other.x0() > split && other.page() == heading.page()
					&& other.baseline() > heading.baseline() - 30
					&& (other.page() < end[0] || other.baseline() < end[1]);
			if (beside) {
				limit = Math.min(limit, other.x0() - 15);
			}
		}
		return limit;
	}

	private TaskKey task(String target, String numeral, List<Line> block, Borders borders, List<Issue> issues) {
		List<String> distractors = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		boolean acceptOther = false;
		String acceptScope = null;
		List<Line> answers = new ArrayList<>();
		for (Line line : block) {
			String text = Text.collapse(line.text());
			Matcher distractor = DISTRACTOR.matcher(text);
			if (distractor.matches()) {
				for (String word : distractor.group(1).split("[,;]")) {
					distractors.add(word.strip().replaceAll("\\.$", ""));
				}
			}
			else if (ACCEPT_OTHER.matcher(text).find()) {
				acceptOther = true;
				acceptScope = text.toLowerCase().contains("transkrypcj") || text.toLowerCase().contains("nagrani") ? "transcript"
						: text.toLowerCase().contains("tekst") ? "text" : "instruction";
				notes.add(text);
			}
			// "*kolejność odpowiedzi dowolna", "Kolejność odpowiedzi jest dowolna.", "Kolejność podawania
			// rozwiązań w obrębie jednego punktu jest dowolna." — all say that the order of answers doesn't matter.
			else if (text.startsWith("*") || text.matches("(?i)^kolejność\\p{L}*\\s.*dowolna\\.?$")) {
				notes.add(text);
			}
			else {
				answers.add(line);
			}
		}

		Map<String, Entry> entries = AnswerBlockParser.parse(answers, borders);
		if (entries.isEmpty()) {
			issues.add(Issue.error("key-empty", target, "No answers could be read from the key"));
		}
		return new TaskKey(numeral, entries, new KeyNotes(distractors, acceptOther, acceptScope, notes));
	}

	/** Levenshtein distance, for headings with typos. */
	static int distance(String a, String b) {
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}
}
