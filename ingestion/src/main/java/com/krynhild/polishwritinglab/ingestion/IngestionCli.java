package com.krynhild.polishwritinglab.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Page;
import com.krynhild.polishwritinglab.ingestion.extract.PdfExtractor;
import com.krynhild.polishwritinglab.ingestion.extract.Word;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.release.CorpusWriter;

/** Command line entry point. Run without arguments for usage. */
public final class IngestionCli {

	private static final String USAGE = """
			Usage: ingestion <command> [options]

			Commands:
			  build [options]                       Build the corpus from the exam PDFs and the standards
			    --input <dir>                       Folder with the PDFs (default: input-docs or ../input-docs)
			    --manifest <file>                   Exam manifest (default: <input>/exam-sheets.tsv)
			    --output <dir>                      Corpus folder (default: corpus next to the input folder)
			    --exam <id>                         Build only this exam, e.g. 2024-06-22_B1 (repeatable)
			    --quiet                             Print only the summary, not each issue
			  dump <pdf> [first-page [last-page]]   Print the positioned lines of a PDF (debugging aid)

			build exits with status 1 when any error-level issue was found; the corpus and report.json are
			written either way, so the problems can be reviewed.
			""";

	// PDFBox and FontBox log every unreadable system font and unsupported font table. Held so they aren't collected.
	private static final Logger PDFBOX_LOGGER = Logger.getLogger("org.apache.pdfbox");
	private static final Logger FONTBOX_LOGGER = Logger.getLogger("org.apache.fontbox");

	private IngestionCli() {
	}

	public static void main(String[] args) {
		PDFBOX_LOGGER.setLevel(Level.SEVERE);
		FONTBOX_LOGGER.setLevel(Level.SEVERE);
		if (args.length == 0) {
			System.out.print(USAGE);
			System.exit(2);
		}
		List<String> rest = Arrays.asList(args).subList(1, args.length);
		int status = switch (args[0]) {
			case "build" -> build(rest);
			case "dump" -> dump(rest);
			default -> {
				System.err.print(USAGE);
				yield 2;
			}
		};
		System.exit(status);
	}

	private static int build(List<String> args) {
		Path input = null;
		Path manifest = null;
		Path output = null;
		List<String> exams = new ArrayList<>();
		boolean quiet = false;
		for (int i = 0; i < args.size(); i++) {
			switch (args.get(i)) {
				case "--input" -> input = Path.of(args.get(++i));
				case "--manifest" -> manifest = Path.of(args.get(++i));
				case "--output" -> output = Path.of(args.get(++i));
				case "--exam" -> exams.add(args.get(++i));
				case "--quiet" -> quiet = true;
				default -> {
					System.err.println("Unknown option " + args.get(i));
					System.err.print(USAGE);
					return 2;
				}
			}
		}
		if (input == null) {
			input = Files.isDirectory(Path.of("input-docs")) ? Path.of("input-docs") : Path.of("..", "input-docs");
		}
		input = input.toAbsolutePath().normalize();
		manifest = manifest == null ? input.resolve("exam-sheets.tsv") : manifest;
		output = output == null ? input.resolveSibling("corpus") : output.toAbsolutePath().normalize();
		if (!Files.isRegularFile(manifest)) {
			System.err.println("No manifest at " + manifest);
			return 2;
		}

		List<String> selected = exams;
		Pipeline.Result result = new Pipeline().run(input, manifest, id -> selected.isEmpty() || selected.contains(id));
		CorpusWriter.Changes changes = new CorpusWriter().write(result, input, output, selected.isEmpty());

		List<Issue> issues = result.issues();
		if (!quiet) {
			for (Issue issue : issues) {
				System.out.printf("%-7s %-22s %-28s %s%n", issue.severity(), issue.code(), issue.target(), issue.message());
			}
		}
		Map<String, Integer> byCode = new TreeMap<>();
		issues.forEach(issue -> byCode.merge(issue.severity() + " " + issue.code(), 1, Integer::sum));
		long errors = issues.stream().filter(Issue::isError).count();
		System.out.println();
		byCode.forEach((code, count) -> System.out.printf("%5d %s%n", count, code));
		System.out.printf("%nBuilt %d exams%s into %s: %d errors, %d warnings%n", result.exams().size(),
				result.standards() == null ? "" : " and the standards", output, errors, issues.size() - errors);
		System.out.printf("Changes since the last build: %d added, %d changed, %d removed%n",
				changes.added().size(), changes.changed().size(), changes.removed().size());
		changes.changed().forEach(file -> System.out.println("  changed " + file));
		return errors > 0 ? 1 : 0;
	}

	private static int dump(List<String> args) {
		ExtractedDocument document = new PdfExtractor().extract(Path.of(args.getFirst()));
		int first = args.size() > 1 ? Integer.parseInt(args.get(1)) : 1;
		int last = args.size() > 2 ? Integer.parseInt(args.get(2)) : (args.size() > 1 ? first : document.pages().size());
		for (Page page : document.pages().subList(first - 1, last)) {
			System.out.printf("=== page %d (%d images, %d rules)%n", page.number(), page.images().size(), page.rules().size());
			for (Line line : page.lines()) {
				StringBuilder text = new StringBuilder();
				double cursor = 0;
				for (Word word : line.words()) {
					int column = (int) Math.round(word.x0() / 4.5);
					text.append(" ".repeat(Math.max(word.superscript() ? 0 : 1, column - (int) cursor)));
					text.append(word.superscript() ? "^" + word.text() : word.text());
					cursor = Math.max(cursor, column) + word.text().length() + (word.superscript() ? 1 : 0);
				}
				System.out.printf("%5.1f %4.1f%s|%s%n", line.baseline(), line.fontSize(), line.bold() ? "B" : " ", text);
			}
		}
		return 0;
	}
}
