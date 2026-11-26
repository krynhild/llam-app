package com.krynhild.polishwritinglab.ingestion.validate;

import java.util.List;
import java.util.Locale;

import com.krynhild.polishwritinglab.ingestion.enrich.TaskTypes;
import com.krynhild.polishwritinglab.ingestion.model.Answer;
import com.krynhild.polishwritinglab.ingestion.model.Exam;
import com.krynhild.polishwritinglab.ingestion.model.ExamModule;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.Item;
import com.krynhild.polishwritinglab.ingestion.model.Option;
import com.krynhild.polishwritinglab.ingestion.model.Task;
import com.krynhild.polishwritinglab.ingestion.parse.Skills;

/**
 * Checks the invariants every exam obeys. Errors mean the corpus would be wrong (a missing or misaligned key, points
 * that don't add up) and block the release; warnings point at things to review.
 */
public final class ExamValidator {

	private static final double EPSILON = 0.001;

	public void validate(Exam exam, boolean hasKey, List<Issue> issues) {
		for (ExamModule module : exam.modules()) {
			String moduleId = exam.id() + "/" + module.skill();
			if (Skills.hasKey(module.skill())) {
				modulePoints(moduleId, module, issues);
				// A C2 task that itself says "wykonać dwa zadania" counts as two of the cover's declared tasks.
				long declaredAsComposite = module.tasks().stream()
						.mapToLong(task -> "reading_composite".equals(task.type()) ? 2 : 1).sum();
				if (module.declaredTasks() != null && module.declaredTasks() != module.tasks().size()
						&& module.declaredTasks() != declaredAsComposite) {
					issues.add(Issue.warning("task-count", moduleId, "The cover declares " + module.declaredTasks()
							+ " tasks but " + module.tasks().size() + " were found"));
				}
			}
			for (Task task : module.tasks()) {
				task(task, module.skill(), hasKey, issues);
			}
		}
	}

	private static void modulePoints(String moduleId, ExamModule module, List<Issue> issues) {
		if (module.maxPoints() == null || module.tasks().stream().anyMatch(task -> task.points() == null)) {
			return;
		}
		double sum = module.tasks().stream().mapToDouble(task -> task.points().total()).sum();
		if (Math.abs(sum - module.maxPoints()) > EPSILON) {
			issues.add(Issue.error("module-points-sum", moduleId, "The tasks add up to " + format(sum)
					+ " points but the module is worth " + format(module.maxPoints())));
		}
	}

	private static void task(Task task, String skill, boolean hasKey, List<Issue> issues) {
		if (TaskTypes.UNKNOWN.equals(task.type())) {
			issues.add(Issue.warning("type-unknown", task.id(), "No task type rule matches: " + task.instruction()));
		}
		if (task.requiresVisual()) {
			issues.add(Issue.warning("requires-visual", task.id(), "The task depends on pictures that aren't in the text"));
		}
		if (!Skills.hasKey(skill)) {
			return;
		}
		if (task.points() != null && task.points().count() != null && task.points().perItem() != null) {
			double product = task.points().count() * task.points().perItem();
			if (Math.abs(product - task.points().total()) > EPSILON) {
				issues.add(Issue.error("task-points-product", task.id(), task.points().count() + " x "
						+ format(task.points().perItem()) + " isn't " + format(task.points().total())));
			}
		}
		if (!hasKey) {
			return;
		}

		// A multi-part answer (several numbers matched to one letter, a two-part open answer…) is sometimes scored
		// as one unit per item and sometimes as one unit per part; sessions aren't consistent about which, so a
		// task is accepted under either count rather than guessing one convention and flagging the other as wrong.
		int unitsByPart = task.items().stream().filter(item -> item.answer() != null).mapToInt(item -> item.answer().units()).sum();
		int unitsByItem = task.items().stream().filter(item -> item.answer() != null)
				.mapToInt(item -> item.answer() instanceof Answer.Parts ? 1 : item.answer().units()).sum();
		Integer expected = task.points() == null ? null : task.points().count();
		if (expected != null && unitsByPart != expected && unitsByItem != expected && !"reading_composite".equals(task.type())) {
			issues.add(Issue.error("key-count", task.id(),
					"The score line counts " + expected + " answers but the key has " + unitsByPart));
		}
		if (Skills.LISTENING.equals(skill) && task.transcript() == null) {
			issues.add(Issue.error("transcript-missing", task.id(), "The listening task has no transcript"));
		}

		for (Item item : task.items()) {
			String itemId = task.id() + "/" + item.id();
			if (item.answer() instanceof Answer.Text text && text.ambiguous()) {
				issues.add(Issue.warning("answer-ambiguous", itemId, "Check how the alternatives were split: " + text.raw()));
			}
			List<Option> options = item.options().isEmpty() ? task.options() : item.options();
			if (options.isEmpty() || !(item.answer() instanceof Answer.Choice || item.answer() instanceof Answer.Choices)) {
				continue;
			}
			List<String> values = item.answer() instanceof Answer.Choice choice ? List.of(choice.value())
					: ((Answer.Choices) item.answer()).values();
			for (String value : values) {
				boolean known = options.stream().anyMatch(option -> option.id().equalsIgnoreCase(value));
				if (!known) {
					issues.add(Issue.error("answer-not-an-option", itemId,
							"The key's answer " + value + " isn't one of the options " + ids(options)));
				}
			}
		}

		if (task.key() != null && !task.key().distractors().isEmpty() && !task.options().isEmpty()) {
			// The worked example also uses up one bank word, but a handful of word-bank tasks mark its gap below
			// the blank rather than as a superscript right after it, so the example isn't always recovered; the
			// count is accepted either way rather than warning on a parsing quirk that doesn't affect any answer.
			int unused = task.options().size() - task.items().size();
			int distractors = task.key().distractors().size();
			if (unused != distractors && unused - 1 != distractors) {
				issues.add(Issue.warning("distractor-count", task.id(), task.options().size() + " options for "
						+ task.items().size() + " items don't leave the " + distractors
						+ " unused option(s) the key names"));
			}
		}
	}

	private static String ids(List<Option> options) {
		return String.join(",", options.stream().map(Option::id).toList());
	}

	private static String format(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
	}
}
