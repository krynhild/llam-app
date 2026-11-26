package com.krynhild.polishwritinglab.ingestion.parse.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.clean.Boilerplate;
import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Page;
import com.krynhild.polishwritinglab.ingestion.model.Criterion;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.Points;
import com.krynhild.polishwritinglab.ingestion.parse.Numbers;
import com.krynhild.polishwritinglab.ingestion.parse.Roman;
import com.krynhild.polishwritinglab.ingestion.parse.ScoreLine;
import com.krynhild.polishwritinglab.ingestion.parse.Skills;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Splits an exam sheet into modules and tasks.
 *
 * <p>Every module starts with a cover page whose title is printed in large type and which states the time, the
 * points and (for RS, RTP and PG) the number of tasks. The module's tasks follow until the next cover. A task starts
 * with the next Roman numeral at the left margin, followed by its instruction and its score line.
 */
public final class SheetParser {

	private static final Pattern MONTH = Pattern.compile(
			"(?i)^(styczeń|luty|marzec|kwiecień|maj|czerwiec|lipiec|sierpień|wrzesień|październik|listopad|grudzień)\\s+(\\d{4})$");
	private static final Pattern LEVEL = Pattern.compile("^(A1|A2|B1|B2|C1|C2)$");
	private static final Pattern DECLARED_TASKS = Pattern.compile("LICZBA ZADAŃ\\s*:\\s*(\\d+)");
	private static final Pattern TIME = Pattern.compile("CZAS\\s*:\\s*(.+)");
	private static final Pattern MODULE_POINTS = Pattern.compile("Liczba punktów.*?/\\s*(\\d+(?:,\\d+)?)\\s*p");
	private static final Pattern CRITERION = Pattern.compile(
			"^[•●▪◦-]?\\s*(.+?)\\s+(\\d+(?:,\\d+)?)\\s*[–-]\\s*(\\d+(?:,\\d+)?)\\s*p\\.?$");
	private static final Pattern CRITERIA_GROUP = Pattern.compile("^(.+?)\\s*\\((\\d+(?:,\\d+)?)\\s*p\\.?\\)$");
	private static final Pattern TASK_START = Pattern.compile("^([IVX]{1,5})\\.\\s*(.*)$");
	private static final Pattern INSTRUCTION_WORD = Pattern.compile(
			"(?U)^(Proszę|Uwaga|Przeczytaj|Posłuchaj|Popatrz|Zaznacz|Podkreśl|Wpisz|Napisz|Dopasuj|Uzupełnij|Wybierz|Połącz|Zdecyduj)\\b");

	public static final List<String> MONTHS = List.of("styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
			"lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień");

	public record ParsedSheet(String level, String month, int monthNumber, int year, boolean childrenAndYouth,
			List<ParsedModule> modules) {
	}

	/**
	 * @param content the module's lines without its cover page and boilerplate
	 * @param cover the lines of the cover page
	 */
	public record ParsedModule(String skill, String title, Integer declaredTasks, String time, Double maxPoints,
			List<Integer> pages, String instruction, List<Criterion> criteria, List<Line> cover, List<Line> content,
			List<ParsedTask> tasks) {
	}

	/**
	 * @param instruction the instruction without the numeral, including instruction sentences printed after the
	 *     score line
	 * @param body the task's material after the instruction
	 */
	public record ParsedTask(String numeral, int ordinal, String instruction, Points points, List<Line> body,
			List<Integer> pages) {
	}

	public ParsedSheet parse(ExtractedDocument document, String examId, List<Issue> issues) {
		Page first = document.page(1);
		String level = null;
		String month = null;
		int monthNumber = 0;
		int year = 0;
		for (Line line : first.lines()) {
			String text = Text.collapse(line.text());
			Matcher monthMatcher = MONTH.matcher(text);
			if (monthMatcher.matches()) {
				month = text.toLowerCase();
				monthNumber = MONTHS.indexOf(monthMatcher.group(1).toLowerCase()) + 1;
				year = Integer.parseInt(monthMatcher.group(2));
			}
			if (LEVEL.matcher(text).matches() && line.fontSize() >= 30) {
				level = text;
			}
		}
		boolean childrenAndYouth = first.lines().stream()
				.anyMatch(line -> Text.collapse(line.text()).toUpperCase().contains("DZIECI I MŁODZIEŻY"));
		if (level == null) {
			issues.add(Issue.warning("sheet-level", examId, "The cover page doesn't show the level"));
		}
		if (month == null) {
			issues.add(Issue.warning("sheet-month", examId, "The cover page doesn't show the session month"));
		}

		List<Line> content = Boilerplate.examContent(document);
		Map<Integer, String> covers = new TreeMap<>();
		for (Page page : document.pages()) {
			String skill = coverSkill(page);
			if (skill != null) {
				covers.put(page.number(), skill);
			}
		}

		List<ParsedModule> modules = new ArrayList<>();
		List<Integer> coverPages = new ArrayList<>(covers.keySet());
		for (int i = 0; i < coverPages.size(); i++) {
			int coverPage = coverPages.get(i);
			int endPage = i + 1 < coverPages.size() ? coverPages.get(i + 1) : document.pages().size() + 1;
			List<Line> cover = content.stream().filter(line -> line.page() == coverPage).toList();
			List<Line> moduleLines = content.stream()
					.filter(line -> line.page() > coverPage && line.page() < endPage)
					.toList();
			modules.add(module(examId, covers.get(coverPage), document.page(coverPage), cover, moduleLines,
					coverPage, endPage, issues));
		}
		if (modules.isEmpty()) {
			issues.add(Issue.error("sheet-modules", examId, "No module cover pages found"));
		}
		return new ParsedSheet(level, month, monthNumber, year, childrenAndYouth, modules);
	}

	private static String coverSkill(Page page) {
		for (Line line : page.lines()) {
			if (line.fontSize() >= 16) {
				String skill = Skills.ofTitle(line.text());
				if (skill != null) {
					return skill;
				}
			}
		}
		return null;
	}

	private ParsedModule module(String examId, String skill, Page coverPage, List<Line> cover, List<Line> lines,
			int firstPage, int endPage, List<Issue> issues) {
		StringBuilder title = new StringBuilder();
		Integer declaredTasks = null;
		String time = null;
		Double maxPoints = null;
		for (Line line : coverPage.lines()) {
			String text = Text.collapse(line.text());
			if (line.fontSize() >= 16 && (Skills.ofTitle(text) != null || !title.isEmpty())
					&& !text.matches("[A-C][12]")) {
				title.append(title.isEmpty() ? "" : " ").append(text);
			}
			Matcher declared = DECLARED_TASKS.matcher(text);
			if (declared.find()) {
				declaredTasks = Integer.parseInt(declared.group(1));
			}
			Matcher timeMatcher = TIME.matcher(text);
			if (timeMatcher.find() && time == null) {
				time = timeMatcher.group(1).strip();
			}
			Matcher points = MODULE_POINTS.matcher(text);
			if (points.find()) {
				maxPoints = Numbers.parse(points.group(1));
			}
		}

		String target = examId + "/" + skill;
		if (maxPoints == null) {
			issues.add(Issue.warning("module-points", target, "The module cover doesn't state the points"));
		}

		List<String> instruction = new ArrayList<>();
		List<Criterion> criteria = new ArrayList<>();
		readInstructionAndCriteria(cover, instruction, criteria);

		List<Integer> pages = new ArrayList<>();
		for (int page = firstPage; page < endPage; page++) {
			pages.add(page);
		}

		List<ParsedTask> tasks = Skills.hasKey(skill) ? tasks(target, lines, issues) : List.of();
		return new ParsedModule(skill, title.toString(), declaredTasks, time, maxPoints, pages,
				instruction.isEmpty() ? null : Text.joinLines(instruction), criteria, cover, lines, tasks);
	}

	/** Reads "INSTRUKCJA WYKONANIA ZADAŃ" and "KRYTERIA OCENY" from a writing or speaking cover page. */
	static void readInstructionAndCriteria(List<Line> cover, List<String> instruction, List<Criterion> criteria) {
		String section = null;
		String group = null;
		for (Line line : cover) {
			String text = Text.collapse(line.text());
			if (text.startsWith("INSTRUKCJA")) {
				section = "instruction";
				continue;
			}
			if (text.startsWith("KRYTERIA OCENY")) {
				section = "criteria";
				continue;
			}
			if ("instruction".equals(section)) {
				instruction.add(text);
			}
			else if ("criteria".equals(section)) {
				if (text.startsWith("RAZEM")) {
					section = null;
					continue;
				}
				Matcher criterion = CRITERION.matcher(text);
				Matcher groupMatcher = CRITERIA_GROUP.matcher(text);
				if (criterion.matches()) {
					criteria.add(new Criterion(criterion.group(1).strip(), group, Numbers.parse(criterion.group(3))));
				}
				else if (groupMatcher.matches()) {
					group = groupMatcher.group(1).strip();
				}
			}
		}
	}

	private List<ParsedTask> tasks(String target, List<Line> lines, List<Issue> issues) {
		if (lines.isEmpty()) {
			issues.add(Issue.error("module-empty", target, "The module has no content pages"));
			return List.of();
		}
		double margin = lines.stream().mapToDouble(Line::x0).sorted().skip(lines.size() / 10).findFirst().orElse(0);

		List<Integer> starts = new ArrayList<>();
		int expected = 1;
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			Matcher start = TASK_START.matcher(Text.collapse(line.text()));
			if (start.matches() && Roman.parse(start.group(1)) == expected && line.x0() <= margin + 25) {
				starts.add(i);
				expected++;
			}
		}
		if (starts.isEmpty()) {
			issues.add(Issue.error("module-tasks", target, "No numbered tasks found"));
			return List.of();
		}

		List<ParsedTask> tasks = new ArrayList<>();
		for (int t = 0; t < starts.size(); t++) {
			int from = starts.get(t);
			int to = t + 1 < starts.size() ? starts.get(t + 1) : lines.size();
			tasks.add(task(target, t + 1, lines.subList(from, to), margin, issues));
		}
		return tasks;
	}

	private ParsedTask task(String moduleTarget, int ordinal, List<Line> lines, double margin, List<Issue> issues) {
		String numeral = Roman.of(ordinal);
		String target = moduleTarget + "/" + numeral;

		// The instruction is usually 1-3 lines, but a long "Uwaga!" caution can run to several more.
		int scoreLine = -1;
		for (int i = 0; i < Math.min(10, lines.size()); i++) {
			if (ScoreLine.matches(lines.get(i).text())) {
				scoreLine = i;
				break;
			}
		}

		List<String> instruction = new ArrayList<>();
		Points points = null;
		int bodyStart;
		if (scoreLine < 0) {
			issues.add(Issue.error("task-points", target, "The task has no score line"));
			instruction.add(Text.collapse(lines.getFirst().text()));
			bodyStart = 1;
		}
		else {
			for (int i = 0; i < scoreLine; i++) {
				instruction.add(Text.collapse(lines.get(i).text()));
			}
			String score = Text.collapse(lines.get(scoreLine).text());
			points = ScoreLine.parse(score);
			int blankAt = score.replace('−', '_').indexOf('_');
			String beforeScore = score.substring(0, blankAt < 0 ? 0 : blankAt).strip();
			if (!beforeScore.isEmpty()) {
				instruction.add(beforeScore);
			}
			bodyStart = scoreLine + 1;
		}

		// Instruction sentences printed in bold after the score line, such as "Proszę zaznaczyć właściwe odpowiedzi."
		while (bodyStart < lines.size()) {
			Line line = lines.get(bodyStart);
			String text = Text.collapse(line.text());
			String previous = instruction.isEmpty() ? "" : instruction.getLast();
			boolean continues = !previous.isEmpty() && !previous.matches(".*[.!?:]$");
			if (line.bold() && line.x0() <= margin + 15 && !text.startsWith("PRZYKŁAD")
					&& (INSTRUCTION_WORD.matcher(text).find() || continues)) {
				instruction.add(text);
				bodyStart++;
			}
			else {
				break;
			}
		}

		String instructionText = Text.joinLines(instruction).replaceFirst("^" + numeral + "\\.\\s*", "");
		List<Line> body = lines.subList(bodyStart, lines.size());
		List<Integer> pages = lines.stream().map(Line::page).distinct().toList();
		return new ParsedTask(numeral, ordinal, instructionText, points, body, pages);
	}
}
