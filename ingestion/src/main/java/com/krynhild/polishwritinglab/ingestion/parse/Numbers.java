package com.krynhild.polishwritinglab.ingestion.parse;

/** Polish number formatting: decimal commas, as in {@code 2,5 p.}. */
public final class Numbers {

	private Numbers() {
	}

	public static double parse(String number) {
		return Double.parseDouble(number.strip().replace(',', '.'));
	}
}
