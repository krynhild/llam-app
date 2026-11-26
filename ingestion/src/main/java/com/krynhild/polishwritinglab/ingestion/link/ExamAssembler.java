package com.krynhild.polishwritinglab.ingestion.link;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.enrich.TaskTypes;
import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.manifest.ManifestEntry;
import com.krynhild.polishwritinglab.ingestion.model.Answer;
import com.krynhild.polishwritinglab.ingestion.model.Exam;
import com.krynhild.polishwritinglab.ingestion.model.ExamModule;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.Item;
import com.krynhild.polishwritinglab.ingestion.model.KeyNotes;
import com.krynhild.polishwritinglab.ingestion.model.Option;
import com.krynhild.polishwritinglab.ingestion.model.Points;
import com.krynhild.polishwritinglab.ingestion.model.SourceDocument;
import com.krynhild.polishwritinglab.ingestion.model.Task;
import com.krynhild.polishwritinglab.ingestion.model.Transcript;
import com.krynhild.polishwritinglab.ingestion.parse.Skills;
import com.krynhild.polishwritinglab.ingestion.parse.key.KeyParser;
import com.krynhild.polishwritinglab.ingestion.parse.sheet.SheetParser;
import com.krynhild.polishwritinglab.ingestion.parse.sheet.TaskBodyParser;
import com.krynhild.polishwritinglab.ingestion.parse.sheet.WritingParser;

/**
 * Merges a parsed sheet with its key: each task gets the answers, notes and transcript printed under the same module
 * and numeral, and each item the answer with its number or letter.
 */
public final class ExamAssembler {

	private static final Pattern FOCUS = Pattern.compile("\\(([^()]{4,})\\)\\.?(?:\\s+Uwaga.*)?$");

	public Exam assemble(ManifestEntry entry, Path inputDir, ExtractedDocument sheetDocument,
			SheetParser.ParsedSheet sheet, ExtractedDocument keyDocument, KeyParser.ParsedKey key, List<Issue> issues) {
		String examId = entry.examId();
		checkCover(entry, sheet, issues);

		TaskBodyParser bodies = new TaskBodyParser(sheetDocument);
		List<ExamModule> modules = new ArrayList<>();
		for (SheetParser.ParsedModule module : sheet.modules()) {
			String moduleId = examId + "/" + module.skill();
			KeyParser.ModuleKey moduleKey = key == null ? null : key.modules().get(module.skill());
			List<Task> tasks = new ArrayList<>();
			String instruction = module.instruction();
			String scoringCard = null;

			if (Skills.WRITING.equals(module.skill())) {
				WritingParser.ParsedWriting writing = new WritingParser().parse(moduleId, module.content(), issues);
				instruction = join(instruction, writing.instruction());
				scoringCard = writing.scoringCard();
				for (WritingParser.WritingSet set : writing.sets()) {
					Task task = new Task(moduleId + "/" + set.numeral(), set.numeral(), set.ordinal(), null, set.heading(),
							List.of(), module.maxPoints() == null ? null : new Points(module.maxPoints(), null, null),
							null, List.of(), null, set.items(), null, null, List.of(), false, set.pages());
					tasks.add(withType(module.skill(), task));
				}
			}
			else if (Skills.SPEAKING.equals(module.skill())) {
				scoringCard = module.content().isEmpty() ? null : String.join("\n",
						module.content().stream().map(line -> line.text().strip()).toList());
			}

			for (SheetParser.ParsedTask parsed : module.tasks()) {
				String taskId = moduleId + "/" + parsed.numeral();
				TaskBodyParser.ParsedBody body = bodies.parse(parsed.instruction(), parsed.body());
				KeyParser.TaskKey taskKey = moduleKey == null ? null : moduleKey.tasks().get(parsed.numeral());
				Transcript transcript = moduleKey == null ? null : moduleKey.transcripts().get(parsed.numeral());
				if (taskKey == null && key != null) {
					issues.add(Issue.error("key-missing", taskId, "The key has no answers for this task"));
				}

				List<Item> items = merge(taskId, body.items(), body.options(), taskKey, issues);
				List<Option> options = body.options();
				if (taskKey != null && !options.isEmpty() && items.stream().allMatch(item -> isOption(body.options(), item.id()))) {
					// The lettered rows are the items (statements matched to speakers), not shared options.
					options = List.of();
				}
				KeyNotes notes = taskKey == null ? null : taskKey.notes();
				Task task = new Task(taskId, parsed.numeral(), parsed.ordinal(), null, parsed.instruction(),
						focus(parsed.instruction()), parsed.points(), body.text(), options, body.example(), items, notes,
						transcript, body.sources(), body.requiresVisual(), parsed.pages());
				tasks.add(withType(module.skill(), task));
			}

			if (moduleKey != null) {
				for (String numeral : moduleKey.tasks().keySet()) {
					if (module.tasks().stream().noneMatch(task -> task.numeral().equals(numeral))) {
						issues.add(Issue.error("key-orphan", moduleId + "/" + numeral,
								"The key has answers for a task the sheet doesn't have"));
					}
				}
			}
			modules.add(new ExamModule(module.skill(), module.title(), module.declaredTasks(), module.time(),
					module.maxPoints(), module.pages(), instruction, module.criteria(), scoringCard, tasks));
		}

		List<SourceDocument> sources = new ArrayList<>();
		sources.add(source("sheet", inputDir, sheetDocument, entry.sheetUrl()));
		if (keyDocument != null) {
			sources.add(source("key", inputDir, keyDocument, null));
		}
		return new Exam(examId, entry.level(), entry.childrenAndYouth() ? "DiM" : "standard", entry.session().toString(),
				entry.year(), sheet.month(), entry.childrenAndYouth() ? "dim" : "adult", sources, modules);
	}

	private static Task withType(String skill, Task task) {
		String type = TaskTypes.classify(skill, task);
		return new Task(task.id(), task.numeral(), task.ordinal(), type, task.instruction(), task.focus(), task.points(),
				task.text(), task.options(), task.example(), task.items(), task.key(), task.transcript(), task.sources(),
				task.requiresVisual(), task.pages());
	}

	/** The sheet's items with the key's answers; items only the key has are added without text. */
	private static List<Item> merge(String taskId, List<Item> parsed, List<Option> options, KeyParser.TaskKey key,
			List<Issue> issues) {
		if (key == null) {
			return parsed;
		}
		List<Item> items = new ArrayList<>();
		int withoutText = 0;
		for (Map.Entry<String, KeyParser.Entry> answer : key.answers().entrySet()) {
			String id = answer.getKey();
			KeyParser.Entry entry = answer.getValue();
			Item item = parsed.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
			if (item == null) {
				Option option = options.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
				item = new Item(id, null, option == null ? null : option.text(), List.of(), null, null, null, null);
				withoutText += option == null ? 1 : 0;
			}
			items.add(new Item(item.id(), item.context(), item.prompt(), item.options(), item.words(),
					inOptions(entry.answer(), item.options().isEmpty() ? options : item.options()),
					entry.scoringNote(), entry.evidence()));
		}
		for (Item item : parsed) {
			if (!key.answers().containsKey(item.id())) {
				issues.add(Issue.warning("item-without-answer", taskId + "/" + item.id(),
						"The sheet has an item the key doesn't answer"));
			}
		}
		if (withoutText > 0) {
			issues.add(Issue.warning("item-text-missing", taskId,
					withoutText + " of " + items.size() + " items have an answer but no text from the sheet"));
		}
		return items;
	}

	/**
	 * Makes an answer a choice when it names one of the options. The key prints both option letters and word-bank
	 * words as plain text, so "a" can be the letter a) or the conjunction "a" from the bank.
	 */
	private static Answer inOptions(Answer answer, List<Option> options) {
		if (answer instanceof Answer.Text text && options.stream().anyMatch(option -> option.id().equalsIgnoreCase(text.raw()))) {
			return new Answer.Choice(text.raw());
		}
		if (answer instanceof Answer.Choice choice && !options.isEmpty()
				&& options.stream().noneMatch(option -> option.id().equalsIgnoreCase(choice.value()))
				&& options.stream().allMatch(option -> option.id().length() > 1 || option.id().equals(option.text()))) {
			return new Answer.Text(choice.value(), List.of(choice.value()), List.of(), false, false);
		}
		return answer;
	}

	private static boolean isOption(List<Option> options, String id) {
		return options.stream().anyMatch(option -> option.id().equals(id));
	}

	private static List<String> focus(String instruction) {
		Matcher focus = FOCUS.matcher(instruction);
		if (!focus.find()) {
			return List.of();
		}
		String text = focus.group(1).strip();
		if (text.matches("[\\dA-Za-z\\s,–-]+") && !text.matches(".*\\p{Ll}{4,}.*")) {
			return List.of();
		}
		return List.of(text);
	}

	private static void checkCover(ManifestEntry entry, SheetParser.ParsedSheet sheet, List<Issue> issues) {
		String examId = entry.examId();
		if (sheet.level() != null && !sheet.level().equals(entry.level())) {
			issues.add(Issue.error("cover-level", examId,
					"The manifest says " + entry.level() + " but the sheet says " + sheet.level()));
		}
		if (sheet.childrenAndYouth() != entry.childrenAndYouth()) {
			issues.add(Issue.error("cover-variant", examId,
					"The manifest variant is " + entry.variant() + " but the sheet " + (sheet.childrenAndYouth() ? "is" : "isn't")
							+ " for children and young people"));
		}
		if (sheet.monthNumber() > 0 && (sheet.monthNumber() != entry.session().getMonthValue()
				|| sheet.year() != entry.session().getYear())) {
			// Not an error: the May 2021 sheets say "marzec 2021", the session they were printed for before it moved.
			issues.add(Issue.warning("cover-month", examId,
					"The manifest session is " + entry.session() + " but the sheet says " + sheet.month()));
		}
	}

	private static SourceDocument source(String kind, Path inputDir, ExtractedDocument document, String url) {
		String path = inputDir.toAbsolutePath().normalize().relativize(document.path().toAbsolutePath().normalize()).toString();
		return new SourceDocument(kind, path, document.sha256(), document.pages().size(), document.producer(), url);
	}

	private static String join(String first, String second) {
		if (first == null) {
			return second;
		}
		return second == null ? first : first + "\n" + second;
	}
}
