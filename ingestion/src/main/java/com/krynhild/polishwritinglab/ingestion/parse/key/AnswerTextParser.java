package com.krynhild.polishwritinglab.ingestion.parse.key;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.model.Answer;
import com.krynhild.polishwritinglab.ingestion.parse.Numbers;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Parses a written answer from the key into the answers to accept.
 *
 * <ul>
 * <li>A slash with a space on either side separates whole alternatives: {@code użyła / używała}.</li>
 * <li>A slash without spaces separates either words ({@code przeniesione/wyjęte/wzięte z bloków} accepts three
 * phrases) or, when the parts are phrases of similar length, phrases ({@code będę szła/będę iść}).</li>
 * <li>Parentheses mark an optional part, {@code (Na drodze) zapadł się asfalt.}, unless they hold points
 * ({@code W jakim…? (0,25 p.)} is worth fewer points) or a number written in digits ({@code dwadzieścia trzy (23)}
 * accepts both).</li>
 * <li>An enumeration {@code a) Holandia b) Norwegia} lists separate acceptable answers.</li>
 * <li>An answer ending in {@code …?} gives only the question word.</li>
 * </ul>
 *
 * The key isn't always consistent ({@code uświadomić /uzmysłowić/ przedstawić}), so answers that could be split
 * more than one way are marked ambiguous for review.
 */
public final class AnswerTextParser {

	private static final Pattern WHOLE_SEPARATOR = Pattern.compile("\\s+/\\s*|\\s*/\\s+");
	private static final Pattern ENUMERATION = Pattern.compile("(?:^|\\s)[a-h]\\)\\s+");
	private static final Pattern POINTS = Pattern.compile("\\(\\s*(\\d+(?:,\\d+)?)\\s*p(?:kt)?\\.?\\s*\\)");
	private static final Pattern DIGITS = Pattern.compile("\\s*\\((\\d+)\\)");
	private static final Pattern OPTIONAL = Pattern.compile("\\(([^()]+)\\)");
	private static final int MAX_EXPANSIONS = 64;

	private AnswerTextParser() {
	}

	public static Answer.Text parse(String raw) {
		String text = Text.collapse(raw);
		List<String> accept = new ArrayList<>();
		List<Answer.PartialCredit> partial = new ArrayList<>();
		boolean ambiguous = false;

		List<String> alternatives = new ArrayList<>();
		boolean enumeration = ENUMERATION.matcher(text).find() && text.matches("^[a-h]\\)\\s.*");
		if (enumeration) {
			for (String item : ENUMERATION.split(text)) {
				if (!item.isBlank()) {
					alternatives.addAll(List.of(WHOLE_SEPARATOR.split(item.strip())));
				}
			}
		}
		else {
			alternatives.addAll(List.of(WHOLE_SEPARATOR.split(text)));
		}

		for (int i = 0; i < alternatives.size(); i++) {
			String alternative = alternatives.get(i).strip();
			if (alternative.isEmpty()) {
				continue;
			}
			Matcher points = POINTS.matcher(alternative);
			Double worth = null;
			if (points.find()) {
				worth = Numbers.parse(points.group(1));
				alternative = (alternative.substring(0, points.start()) + alternative.substring(points.end())).strip();
			}
			Matcher digits = DIGITS.matcher(alternative);
			if (digits.find()) {
				add(accept, digits.group(1));
				alternative = (alternative.substring(0, digits.start()) + alternative.substring(digits.end())).strip();
			}
			// A one-word alternative followed by a longer one is often a word variant written with spaces:
			// "Nakazał / polecił zamknięcie drogi" means "nakazał zamknięcie drogi" too.
			if (!enumeration && i + 1 < alternatives.size() && !alternative.contains(" ")
					&& alternatives.get(i + 1).strip().contains(" ") && worth == null) {
				String next = alternatives.get(i + 1).strip();
				for (String expanded : expand(alternative + next.substring(next.indexOf(' ')))) {
					add(accept, expanded);
				}
				ambiguous = true;
			}
			Expansion expansion = expandAlternative(alternative);
			ambiguous |= expansion.ambiguous();
			for (String expanded : expansion.answers()) {
				if (worth != null) {
					partial.add(new Answer.PartialCredit(expanded, worth));
				}
				else {
					add(accept, expanded);
				}
			}
		}
		if (text.contains("(…)") || text.contains("(...)")) {
			ambiguous = true;
		}
		boolean questionWordOnly = text.matches(".*(…|\\.\\.\\.)\\s*\\?.*");
		return new Answer.Text(text, accept, partial, questionWordOnly, ambiguous);
	}

	private record Expansion(List<String> answers, boolean ambiguous) {
	}

	/** Decides whether the slashes of one alternative separate words or phrases, then expands it. */
	private static Expansion expandAlternative(String alternative) {
		String withoutOptional = OPTIONAL.matcher(alternative).replaceAll("");
		String[] parts = withoutOptional.split("/");
		if (parts.length < 2) {
			return new Expansion(expand(alternative), false);
		}
		boolean middlePhrase = false;
		for (int i = 1; i < parts.length - 1; i++) {
			middlePhrase |= parts[i].strip().contains(" ");
		}
		int firstWords = parts[0].strip().split("\\s+").length;
		int lastWords = parts[parts.length - 1].strip().split("\\s+").length;
		boolean similarPhrases = parts.length == 2 && firstWords >= 2 && lastWords >= 2 && Math.abs(firstWords - lastWords) <= 1;
		// "USA/Stany Zjednoczone" (phrases) and "jest/był zielony" (words) look alike; review decides.
		boolean unclear = parts.length == 2 && firstWords == 1 && lastWords >= 2;
		if (middlePhrase || similarPhrases) {
			List<String> answers = new ArrayList<>();
			for (String part : alternative.split("/")) {
				for (String expanded : expand(part.replace('/', ' '))) {
					if (!answers.contains(expanded)) {
						answers.add(expanded);
					}
				}
			}
			return new Expansion(answers, similarPhrases);
		}
		return new Expansion(expand(alternative), unclear);
	}

	/** Expands word variants ({@code a/b}) and optional parts ({@code (x)}) of one alternative. */
	static List<String> expand(String alternative) {
		Set<String> results = new LinkedHashSet<>();
		results.add("");
		for (String token : withOptionalParts(alternative)) {
			Set<String> next = new LinkedHashSet<>();
			for (String prefix : results) {
				for (String variant : token.split("/")) {
					next.add(Text.collapse(prefix + " " + variant));
					if (next.size() >= MAX_EXPANSIONS) {
						break;
					}
				}
			}
			results = next;
		}
		return results.stream().map(String::strip).filter(result -> !result.isEmpty()).distinct().toList();
	}

	/**
	 * Splits an alternative into tokens at spaces, turning each optional part {@code (x y)} into a token
	 * {@code /x y}: "nothing, or the part".
	 */
	private static List<String> withOptionalParts(String alternative) {
		List<String> tokens = new ArrayList<>();
		Matcher optional = OPTIONAL.matcher(alternative);
		int position = 0;
		while (optional.find()) {
			tokens.addAll(words(alternative.substring(position, optional.start())));
			String part = optional.group(1).strip();
			if (part.equals("…") || part.equals("...")) {
				tokens.add("(…)");
			}
			else {
				tokens.add("/" + part.replace('/', '|'));
			}
			position = optional.end();
		}
		tokens.addAll(words(alternative.substring(position)));
		return tokens.stream().map(token -> token.replace('|', '/')).toList();
	}

	private static List<String> words(String text) {
		List<String> words = new ArrayList<>();
		for (String word : text.strip().split("\\s+")) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}
		return words;
	}

	private static void add(List<String> accept, String answer) {
		if (!accept.contains(answer)) {
			accept.add(answer);
		}
	}
}
