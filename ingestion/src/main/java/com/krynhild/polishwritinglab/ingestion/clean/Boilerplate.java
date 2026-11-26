package com.krynhild.polishwritinglab.ingestion.clean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Page;

/**
 * Removes what every page repeats and what only the candidate fills in.
 *
 * <p>Running headers and footers ({@code POZIOM B1 / czerwiec 2024}, {@code Dziennik Ustaw – 12 – Poz. 405}) are
 * learned from repetition: text that recurs near the top or bottom edge of most pages. Learning them, rather than
 * listing them, keeps the filter working when a new session changes the wording.
 */
public final class Boilerplate {

	private static final double EDGE = 0.1;

	private static final Pattern PAGE_NUMBER = Pattern.compile("[–-]?\\s*\\d{1,3}\\s*[–-]?");

	private static final List<Pattern> FORM_FURNITURE = List.of(
			Pattern.compile("^Imię( i nazwisko)?\\s*:"),
			Pattern.compile("^Nazwisko\\s*:"),
			Pattern.compile("^Data (i miejsce egzaminu|urodzenia)\\s*:"),
			Pattern.compile("^Numer zestawu"),
			Pattern.compile("PODPISY? OCENIAJĄC"),
			Pattern.compile("IMIĘ I NAZWISKO ZDAJĄCEGO"),
			Pattern.compile("^ZESTAW NR\\s*:"),
			Pattern.compile("^BRUDNOPIS$"),
			Pattern.compile("podpisane arkusze zostaną zebrane"),
			Pattern.compile("^i nastąpi przerwa przed"),
			Pattern.compile("^©?\\s*Państwowa Komisja ds\\. Poświadczania"),
			Pattern.compile("^Języka Polskiego jako Obcego$"),
			Pattern.compile("^[_.…\\s]+$"));

	private Boilerplate() {
	}

	/** The lines of the document without running headers, footers and page numbers. */
	public static List<Line> withoutRunningText(ExtractedDocument document) {
		Set<String> repeated = repeatedEdgeText(document);
		return document.pages().stream()
				.flatMap(page -> page.lines().stream().filter(line -> !running(page, line, repeated)))
				.toList();
	}

	/** Also removes name fields, signature lines and other parts of the form the candidate fills in. */
	public static List<Line> examContent(ExtractedDocument document) {
		return withoutRunningText(document).stream().filter(line -> !formFurniture(line.text())).toList();
	}

	public static boolean formFurniture(String text) {
		String trimmed = text.strip();
		return FORM_FURNITURE.stream().anyMatch(pattern -> pattern.matcher(trimmed).find());
	}

	private static boolean running(Page page, Line line, Set<String> repeated) {
		if (!nearEdge(page, line)) {
			return false;
		}
		String text = line.text().strip();
		return PAGE_NUMBER.matcher(text).matches() || repeated.contains(key(text));
	}

	/** Edge text that recurs at about the same height on at least half of the pages (and at least 3). */
	private static Set<String> repeatedEdgeText(ExtractedDocument document) {
		Map<String, List<double[]>> occurrences = new HashMap<>();
		for (Page page : document.pages()) {
			for (Line line : page.lines()) {
				if (nearEdge(page, line)) {
					occurrences.computeIfAbsent(key(line.text()), text -> new ArrayList<>())
							.add(new double[] { page.number(), line.baseline() });
				}
			}
		}
		// Half the pages: section headings such as "Klucz do zadań" can top a third of a short key document.
		int threshold = Math.max(3, (int) Math.ceil(document.pages().size() * 0.5));
		Set<String> repeated = new HashSet<>();
		occurrences.forEach((text, places) -> {
			double[] baselines = places.stream().mapToDouble(place -> place[1]).sorted().toArray();
			double median = baselines[baselines.length / 2];
			Set<Double> pages = new HashSet<>();
			for (double[] place : places) {
				if (Math.abs(place[1] - median) <= 8) {
					pages.add(place[0]);
				}
			}
			if (pages.size() >= threshold) {
				repeated.add(text);
			}
		});
		return repeated;
	}

	private static boolean nearEdge(Page page, Line line) {
		return line.baseline() < page.height() * EDGE || line.baseline() > page.height() * (1 - EDGE);
	}

	private static String key(String text) {
		return text.strip().toLowerCase().replaceAll("\\d+", "#").replaceAll("\\s+", " ");
	}
}
