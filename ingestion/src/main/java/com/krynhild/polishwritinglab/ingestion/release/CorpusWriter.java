package com.krynhild.polishwritinglab.ingestion.release;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.krynhild.polishwritinglab.ingestion.Pipeline;
import com.krynhild.polishwritinglab.ingestion.model.Exam;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.Item;
import com.krynhild.polishwritinglab.ingestion.model.Task;
import com.krynhild.polishwritinglab.ingestion.review.Overrides;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes the corpus: one JSON file per exam, the standards, a report of every issue, and a manifest with the
 * SHA-256 of each file. The output has no timestamps, so building the same inputs twice gives identical files and
 * a diff of two builds shows only real changes.
 */
public final class CorpusWriter {

	private final JsonMapper json = JsonMapper.builder()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
			.build();

	private final Overrides overrides = new Overrides();

	public record ExamStats(String id, int tasks, int items, int answered, int withText, long errors, long warnings) {
	}

	/** What changed compared with the corpus that was in the output folder before. */
	public record Changes(List<String> added, List<String> changed, List<String> removed) {
	}

	public Changes write(Pipeline.Result result, Path inputDir, Path outputDir, boolean complete) {
		try {
			Map<String, String> previous = previousHashes(outputDir);
			Path examsDir = outputDir.resolve("exams");
			if (complete && Files.isDirectory(examsDir)) {
				try (Stream<Path> files = Files.list(examsDir)) {
					for (Path file : files.toList()) {
						Files.delete(file);
					}
				}
			}
			Files.createDirectories(examsDir);

			List<Issue> issues = new ArrayList<>(result.issues());
			List<ExamStats> stats = new ArrayList<>();
			for (Exam exam : result.exams()) {
				ObjectNode tree = json.valueToTree(exam);
				int applied = overrides.apply(tree, inputDir.resolve("overrides").resolve(exam.id() + ".yaml"), json, issues);
				if (applied > 0) {
					tree.put("overridesApplied", applied);
				}
				write(examsDir.resolve(exam.id() + ".json"), tree);
				stats.add(stats(exam, issues));
			}
			if (result.standards() != null) {
				write(outputDir.resolve("standards.json"), result.standards());
			}
			write(outputDir.resolve("report.json"), report(stats, issues));

			Map<String, String> hashes = new TreeMap<>();
			try (Stream<Path> files = Files.walk(outputDir)) {
				for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
					String name = outputDir.relativize(file).toString();
					if (!name.equals("manifest.json") && !name.equals("report.json")) {
						hashes.put(name, sha256(Files.readAllBytes(file)));
					}
				}
			}
			Map<String, Object> manifest = new LinkedHashMap<>();
			manifest.put("pipelineVersion", Pipeline.VERSION);
			manifest.put("corpusHash", sha256(String.join("\n", hashes.entrySet().stream()
					.map(entry -> entry.getKey() + " " + entry.getValue()).toList()).getBytes(StandardCharsets.UTF_8)));
			manifest.put("files", hashes);
			write(outputDir.resolve("manifest.json"), manifest);
			return changes(previous, hashes, complete);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static ExamStats stats(Exam exam, List<Issue> issues) {
		List<Task> tasks = exam.modules().stream().flatMap(module -> module.tasks().stream()).toList();
		List<Item> items = tasks.stream().flatMap(task -> task.items().stream()).toList();
		long errors = issues.stream().filter(issue -> issue.isError() && belongsTo(issue, exam)).count();
		long warnings = issues.stream().filter(issue -> !issue.isError() && belongsTo(issue, exam)).count();
		return new ExamStats(exam.id(), tasks.size(), items.size(),
				(int) items.stream().filter(item -> item.answer() != null).count(),
				(int) items.stream().filter(item -> item.prompt() != null && !item.prompt().isBlank()).count(),
				errors, warnings);
	}

	private static boolean belongsTo(Issue issue, Exam exam) {
		return issue.target().equals(exam.id()) || issue.target().startsWith(exam.id() + "/");
	}

	private static Map<String, Object> report(List<ExamStats> stats, List<Issue> issues) {
		Map<String, Long> byCode = new TreeMap<>();
		for (Issue issue : issues) {
			byCode.merge(issue.severity() + " " + issue.code(), 1L, Long::sum);
		}
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("pipelineVersion", Pipeline.VERSION);
		report.put("errors", issues.stream().filter(Issue::isError).count());
		report.put("warnings", issues.stream().filter(issue -> !issue.isError()).count());
		report.put("issuesByCode", byCode);
		report.put("exams", stats);
		report.put("issues", issues);
		return report;
	}

	private Map<String, String> previousHashes(Path outputDir) {
		Path manifest = outputDir.resolve("manifest.json");
		Map<String, String> hashes = new TreeMap<>();
		if (Files.exists(manifest)) {
			JsonNode files = json.readTree(manifest.toFile()).path("files");
			for (Map.Entry<String, JsonNode> entry : files.properties()) {
				hashes.put(entry.getKey(), entry.getValue().asString());
			}
		}
		return hashes;
	}

	private static Changes changes(Map<String, String> previous, Map<String, String> current, boolean complete) {
		List<String> added = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		List<String> removed = new ArrayList<>();
		current.forEach((file, hash) -> {
			if (!previous.containsKey(file)) {
				added.add(file);
			}
			else if (!previous.get(file).equals(hash)) {
				changed.add(file);
			}
		});
		if (complete) {
			previous.keySet().stream().filter(file -> !current.containsKey(file)).forEach(removed::add);
		}
		return new Changes(added, changed, removed);
	}

	private void write(Path file, Object value) throws IOException {
		Files.writeString(file, json.writeValueAsString(value) + "\n");
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
