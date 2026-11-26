package com.krynhild.polishwritinglab.ingestion.review;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.krynhild.polishwritinglab.ingestion.model.Issue;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Applies human corrections from {@code input-docs/overrides/<exam-id>.yaml} to a parsed exam, after every parse, so
 * that fixes survive re-ingestion:
 *
 * <pre>
 * - target: 2024-06-22_B1/PG/IV        # an exam, task or item ID
 *   path: /items/0/answer/accept/0     # a JSON pointer inside the target
 *   expect: jedziemy                   # optional: the value the fix replaces
 *   set: jedziemy
 *   reason: why the parse was wrong
 * </pre>
 *
 * If the parser later produces something other than {@code expect}, the override is reported as stale instead of
 * being applied, because the fix may no longer be needed or may now be wrong.
 */
public final class Overrides {

	private final YAMLMapper yaml = YAMLMapper.builder().build();

	public record Override(String target, String path, JsonNode expect, JsonNode set, String reason) {
	}

	public int apply(ObjectNode exam, Path file, ObjectMapper json, List<Issue> issues) {
		if (!Files.exists(file)) {
			return 0;
		}
		List<Override> overrides = List.of(yaml.readValue(file.toFile(), Override[].class));
		int applied = 0;
		for (Override override : overrides) {
			String where = override.target() + override.path();
			JsonNode target = target(exam, override.target());
			if (target == null) {
				issues.add(Issue.error("override-target", where, "The override's target doesn't exist"));
				continue;
			}
			JsonPointer pointer = JsonPointer.compile(override.path());
			JsonNode current = target.at(pointer);
			if (override.expect() != null && !override.expect().equals(current)) {
				issues.add(Issue.warning("override-stale", where, "Expected " + override.expect() + " but found "
						+ (current.isMissingNode() ? "nothing" : current) + "; the override was not applied"));
				continue;
			}
			JsonNode parent = target.at(pointer.head());
			String last = pointer.last().getMatchingProperty();
			if (parent instanceof ObjectNode object) {
				object.set(last, override.set());
				applied++;
			}
			else if (parent instanceof ArrayNode array && pointer.last().getMatchingIndex() >= 0) {
				int index = pointer.last().getMatchingIndex();
				if (index < array.size()) {
					array.set(index, override.set());
				}
				else {
					array.add(override.set());
				}
				applied++;
			}
			else {
				issues.add(Issue.error("override-path", where, "The override's path doesn't exist"));
			}
		}
		return applied;
	}

	/** The node of an exam, task ({@code exam/skill/numeral}) or item ({@code exam/skill/numeral/item}). */
	private static JsonNode target(ObjectNode exam, String id) {
		String[] parts = id.split("/");
		if (!parts[0].equals(exam.path("id").asString())) {
			return null;
		}
		if (parts.length == 1) {
			return exam;
		}
		for (JsonNode module : exam.path("modules")) {
			if (!module.path("skill").asString().equals(parts[1])) {
				continue;
			}
			if (parts.length == 2) {
				return module;
			}
			for (JsonNode task : module.path("tasks")) {
				if (!task.path("numeral").asString().equals(parts[2])) {
					continue;
				}
				if (parts.length == 3) {
					return task;
				}
				for (JsonNode item : task.path("items")) {
					if (item.path("id").asString().equals(parts[3])) {
						return item;
					}
				}
			}
		}
		return null;
	}
}
