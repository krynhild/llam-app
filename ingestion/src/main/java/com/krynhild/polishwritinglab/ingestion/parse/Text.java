package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;

/** Whitespace, letter spacing and line-joining rules shared by the parsers. */
public final class Text {

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private Text() {
	}

	public static String collapse(String text) {
		return SPACES.matcher(text).replaceAll(" ").strip();
	}

	/**
	 * Joins single letters printed with letter spacing back into words: {@code Z e s t a w II} becomes
	 * {@code Zestaw II}. Only runs of four or more single letters are joined, so ordinary one-letter words like
	 * {@code i}, {@code w} or {@code z} are left alone.
	 */
	public static String unspace(String text) {
		String[] tokens = collapse(text).split(" ");
		List<String> words = new ArrayList<>();
		StringBuilder run = new StringBuilder();
		int runLength = 0;
		for (String token : tokens) {
			if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
				run.append(token);
				runLength++;
				continue;
			}
			flush(words, run, runLength);
			run.setLength(0);
			runLength = 0;
			words.add(token);
		}
		flush(words, run, runLength);
		return String.join(" ", words);
	}

	private static void flush(List<String> words, StringBuilder run, int runLength) {
		if (runLength >= 4) {
			words.add(run.toString());
		}
		else {
			for (char letter : run.toString().toCharArray()) {
				words.add(String.valueOf(letter));
			}
		}
	}

	/**
	 * Joins the lines of a paragraph. A line break inside a sentence becomes a space, and a word broken with a hyphen
	 * is rejoined. Polish typesetting repeats the hyphen at the start of the next line when the word really contains
	 * one, so {@code intencjonalno-} + {@code -pojęciowy} keeps it while {@code podstawo-} + {@code wymi} doesn't.
	 */
	public static String joinLines(List<String> lines) {
		StringBuilder text = new StringBuilder();
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (text.isEmpty()) {
				text.append(line);
				continue;
			}
			int last = text.length() - 1;
			if (text.charAt(last) == '-' && last > 0 && Character.isLetter(text.charAt(last - 1))) {
				if (line.startsWith("-")) {
					text.append(line.substring(1));
				}
				else if (Character.isLetter(line.charAt(0))) {
					text.setLength(last);
					text.append(line);
				}
				else {
					text.append(line);
				}
			}
			else {
				text.append(' ').append(line);
			}
		}
		return text.toString();
	}

	/**
	 * Joins lines that belong to one item, keeping a line break where a new line starts a new sentence or dialogue
	 * turn, so that a question and its answer line ({@code – Swojej matce.}) stay apart.
	 */
	public static String joinItemLines(List<String> lines) {
		StringBuilder text = new StringBuilder();
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (!text.isEmpty()) {
				char end = text.charAt(text.length() - 1);
				boolean sentenceEnd = end == '.' || end == '?' || end == '!' || end == ':' || end == ')';
				boolean newTurn = line.startsWith("–") || line.startsWith("-") || line.startsWith("(");
				text.append(sentenceEnd || newTurn ? "\n" : " ");
			}
			text.append(line);
		}
		return text.toString();
	}

	public static String text(List<Word> words) {
		return Line.join(words);
	}
}
