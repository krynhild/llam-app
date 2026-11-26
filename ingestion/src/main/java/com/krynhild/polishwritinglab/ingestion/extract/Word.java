package com.krynhild.polishwritinglab.ingestion.extract;

/**
 * A run of glyphs printed together, in page coordinates with the origin at the top left.
 *
 * @param baseline y of the baseline
 * @param top y of the top of the tallest glyph
 * @param superscript printed smaller and above the baseline of its line, like gap numbers in {@code (jechać)¹}
 */
public record Word(
		String text,
		double x0,
		double x1,
		double top,
		double baseline,
		double fontSize,
		boolean bold,
		boolean superscript) {

	public double width() {
		return x1 - x0;
	}

	public double centerX() {
		return (x0 + x1) / 2;
	}

	Word asSuperscript() {
		return new Word(text, x0, x1, top, baseline, fontSize, bold, true);
	}
}
