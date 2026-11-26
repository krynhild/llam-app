package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.List;

/** Roman numerals I–XX, used to number tasks and transcripts. */
public final class Roman {

	private static final List<String> NUMERALS = List.of(
			"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
			"XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX");

	private Roman() {
	}

	public static String of(int value) {
		return NUMERALS.get(value - 1);
	}

	/** The value of a numeral such as {@code IV}, or 0 if it isn't one. */
	public static int parse(String numeral) {
		return NUMERALS.indexOf(numeral.strip().toUpperCase()) + 1;
	}
}
