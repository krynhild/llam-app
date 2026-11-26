package com.krynhild.polishwritinglab.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.PdfExtractor;
import com.krynhild.polishwritinglab.ingestion.link.ExamAssembler;
import com.krynhild.polishwritinglab.ingestion.manifest.Manifest;
import com.krynhild.polishwritinglab.ingestion.manifest.ManifestEntry;
import com.krynhild.polishwritinglab.ingestion.model.Exam;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.parse.key.KeyParser;
import com.krynhild.polishwritinglab.ingestion.parse.sheet.SheetParser;
import com.krynhild.polishwritinglab.ingestion.parse.standards.StandardsDocument;
import com.krynhild.polishwritinglab.ingestion.parse.standards.StandardsParser;
import com.krynhild.polishwritinglab.ingestion.validate.ExamValidator;

/** Runs every stage for the exams of the manifest and the requirement standards. */
public final class Pipeline {

	/** Bumped whenever a change to the parsers can change the corpus. */
	public static final String VERSION = "0.1.0";

	public static final String STANDARDS_FILE = "Standardy-wymagan.pdf";

	private final PdfExtractor extractor = new PdfExtractor();

	public record Result(List<Exam> exams, StandardsDocument standards, List<Issue> issues) {
	}

	public Result run(Path inputDir, Path manifestFile, Predicate<String> examFilter) {
		List<Issue> issues = new ArrayList<>();
		List<ManifestEntry> entries = Manifest.read(manifestFile);
		for (Path unlisted : Manifest.unlisted(inputDir, entries)) {
			issues.add(Issue.warning("manifest-unlisted", inputDir.relativize(unlisted).toString(),
					"The PDF isn't in the manifest and was skipped"));
		}

		List<Exam> exams = new ArrayList<>();
		for (ManifestEntry entry : entries) {
			if (!examFilter.test(entry.examId())) {
				continue;
			}
			Exam exam = exam(inputDir, entry, issues);
			if (exam != null) {
				exams.add(exam);
			}
		}

		StandardsDocument standards = null;
		Path standardsFile = inputDir.resolve(STANDARDS_FILE);
		if (Files.exists(standardsFile)) {
			standards = new StandardsParser().parse(extractor.extract(standardsFile), STANDARDS_FILE, issues);
		}
		else {
			issues.add(Issue.warning("standards-missing", STANDARDS_FILE, "The requirement standards PDF wasn't found"));
		}
		return new Result(exams, standards, issues);
	}

	private Exam exam(Path inputDir, ManifestEntry entry, List<Issue> issues) {
		String examId = entry.examId();
		Path sheetFile = entry.sheet(inputDir);
		Path keyFile = entry.key(inputDir);
		if (!isPdf(sheetFile)) {
			issues.add(Issue.error("sheet-missing", examId, "No PDF at " + inputDir.relativize(sheetFile)));
			return null;
		}

		ExtractedDocument sheetDocument = extractor.extract(sheetFile);
		SheetParser.ParsedSheet sheet = new SheetParser().parse(sheetDocument, examId, issues);

		ExtractedDocument keyDocument = null;
		KeyParser.ParsedKey key = null;
		if (isPdf(keyFile)) {
			keyDocument = extractor.extract(keyFile);
			key = new KeyParser().parse(keyDocument, examId, issues);
		}
		else {
			issues.add(Issue.warning("key-file-missing", examId, "No key at " + inputDir.relativize(keyFile)
					+ "; the exam is usable as an exemplar but not for answer checking"));
		}

		Exam exam = new ExamAssembler().assemble(entry, inputDir, sheetDocument, sheet, keyDocument, key, issues);
		new ExamValidator().validate(exam, key != null, issues);
		return exam;
	}

	private static boolean isPdf(Path file) {
		try {
			if (!Files.isRegularFile(file)) {
				return false;
			}
			byte[] header = new byte[4];
			try (var in = Files.newInputStream(file)) {
				return in.read(header) == 4 && new String(header).equals("%PDF");
			}
		}
		catch (java.io.IOException ex) {
			return false;
		}
	}
}
