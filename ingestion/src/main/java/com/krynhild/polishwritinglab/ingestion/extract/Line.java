package com.krynhild.polishwritinglab.ingestion.extract;

import java.util.ArrayList;
import java.util.List;

/** Words that share a baseline on one page, ordered left to right. */
public record Line(int page, double baseline, List<Word> words) {

	public Line {
		words = List.copyOf(words);
	}

	public double x0() {
		return words.getFirst().x0();
	}

	public double x1() {
		return words.getLast().x1();
	}

	public double fontSize() {
		double size = 0;
		for (Word word : words) {
			if (!word.superscript()) {
				size = Math.max(size, word.fontSize());
			}
		}
		return size > 0 ? size : words.getFirst().fontSize();
	}

	public boolean bold() {
		int bold = 0;
		int total = 0;
		for (Word word : words) {
			bold += word.bold() ? word.text().length() : 0;
			total += word.text().length();
		}
		return bold * 2 > total;
	}

	/** The words joined by single spaces; superscripts are attached to the preceding word. */
	public String text() {
		return join(words);
	}

	/**
	 * Splits the line where the horizontal gap between words is wider than {@code minGap}, which separates table
	 * cells and columns printed on the same baseline.
	 */
	public List<Line> segments(double minGap) {
		List<Line> segments = new ArrayList<>();
		List<Word> current = new ArrayList<>();
		for (Word word : words) {
			if (!current.isEmpty() && word.x0() - current.getLast().x1() > minGap) {
				segments.add(new Line(page, baseline, current));
				current = new ArrayList<>();
			}
			current.add(word);
		}
		segments.add(new Line(page, baseline, current));
		return segments;
	}

	/** The part of the line whose words start within {@code [from, to)}. */
	public Line slice(double from, double to) {
		List<Word> sliced = words.stream().filter(word -> word.x0() >= from && word.x0() < to).toList();
		return sliced.isEmpty() ? null : new Line(page, baseline, sliced);
	}

	public static String join(List<Word> words) {
		StringBuilder text = new StringBuilder();
		Word previous = null;
		for (Word word : words) {
			if (previous != null && !(word.superscript() && word.x0() - previous.x1() < 1.5)) {
				text.append(' ');
			}
			text.append(word.superscript() ? "^" + word.text() : word.text());
			previous = word;
		}
		return text.toString();
	}

	@Override
	public String toString() {
		return "p" + page + "@" + Math.round(baseline) + " " + text();
	}
}
