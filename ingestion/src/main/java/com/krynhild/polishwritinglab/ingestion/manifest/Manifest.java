package com.krynhild.polishwritinglab.ingestion.manifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the exam manifest, a tab-separated file with the header {@code level year session variant url}. Extra
 * columns are ignored, so the manifest can grow without breaking older pipeline versions.
 */
public final class Manifest {

	private static final List<String> REQUIRED = List.of("level", "year", "session", "variant", "url");

	private Manifest() {
	}

	public static List<ManifestEntry> read(Path file) {
		List<String> lines;
		try {
			lines = Files.readAllLines(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the manifest " + file, ex);
		}
		if (lines.isEmpty()) {
			throw new IllegalArgumentException(file + " is empty");
		}

		Map<String, Integer> columns = new HashMap<>();
		String[] header = lines.getFirst().split("\t");
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i].trim(), i);
		}
		for (String column : REQUIRED) {
			if (!columns.containsKey(column)) {
				throw new IllegalArgumentException(file + " has no '" + column + "' column");
			}
		}

		List<ManifestEntry> entries = new ArrayList<>();
		for (int number = 2; number <= lines.size(); number++) {
			String line = lines.get(number - 1);
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] cells = line.split("\t", -1);
			try {
				entries.add(new ManifestEntry(
						cell(cells, columns, "level"),
						Integer.parseInt(cell(cells, columns, "year")),
						LocalDate.parse(cell(cells, columns, "session")),
						cell(cells, columns, "variant"),
						cell(cells, columns, "url")));
			}
			catch (RuntimeException ex) {
				throw new IllegalArgumentException(file + " line " + number + ": " + ex.getMessage(), ex);
			}
		}
		return entries;
	}

	/** Exam PDFs in the input folder that the manifest doesn't list, so new downloads aren't silently skipped. */
	public static List<Path> unlisted(Path inputDir, List<ManifestEntry> entries) {
		List<Path> listed = new ArrayList<>();
		for (ManifestEntry entry : entries) {
			listed.add(entry.sheet(inputDir).normalize());
			listed.add(entry.key(inputDir).normalize());
		}
		try (Stream<Path> files = Files.walk(inputDir)) {
			return files
					.filter(path -> path.toString().endsWith(".pdf"))
					.filter(path -> !path.getParent().equals(inputDir))
					.map(Path::normalize)
					.filter(path -> !listed.contains(path))
					.sorted()
					.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String cell(String[] cells, Map<String, Integer> columns, String column) {
		int index = columns.get(column);
		if (index >= cells.length || cells[index].isBlank()) {
			throw new IllegalArgumentException("missing " + column);
		}
		return cells[index].trim();
	}
}
