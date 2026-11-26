package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.model.Points;

/**
 * Parses the score line printed under a task's instruction. The format varies between sessions:
 * {@code _____ / 7 p. (14 x 0,5 p.)}, {@code ______ / 9 p. ( 9 x 1 p.)}, {@code _____ / 5 p. (10 x 0,5 p)},
 * {@code _______ / 2,5 (5 x 0,5 p.)} and {@code _____ / 8 p.}. One task (C2 2021-11, PG VIII) prints its blank
 * with the minus sign U+2212 instead of underscores, so both count as the blank.
 */
public final class ScoreLine {

	private static final Pattern SCORE = Pattern.compile(
			"[_−]{2,}\\s*/\\s*(\\d+(?:,\\d+)?)\\s*(?:p\\.?|pkt\\.?)?\\s*(?:\\(\\s*(\\d+)\\s*[x×]\\s*(\\d+(?:,\\d+)?)\\s*(?:p\\.?|pkt\\.?)?\\s*\\))?");

	private ScoreLine() {
	}

	public static boolean matches(String text) {
		return SCORE.matcher(text).find();
	}

	/** The points of the score line, or null if the text has none. */
	public static Points parse(String text) {
		Matcher matcher = SCORE.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		double total = Numbers.parse(matcher.group(1));
		if (matcher.group(2) == null) {
			return new Points(total, null, null);
		}
		return new Points(total, Integer.parseInt(matcher.group(2)), Numbers.parse(matcher.group(3)));
	}
}
