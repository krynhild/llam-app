package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.LinkedHashMap;
import java.util.Map;

/** The exam modules and the titles they are printed under, in exam order. */
public final class Skills {

	public static final String LISTENING = "RS";
	public static final String READING = "RTP";
	public static final String GRAMMAR = "PG";
	public static final String WRITING = "P";
	public static final String SPEAKING = "M";

	private static final Map<String, String> TITLES = new LinkedHashMap<>();

	static {
		TITLES.put("ROZUMIENIE ZE SŁUCHU", LISTENING);
		TITLES.put("ROZUMIENIE TEKSTÓW PISANYCH", READING);
		TITLES.put("POPRAWNOŚĆ GRAMATYCZNA", GRAMMAR);
		TITLES.put("PISANIE", WRITING);
		TITLES.put("MÓWIENIE", SPEAKING);
	}

	private Skills() {
	}

	/**
	 * The skill whose title the text starts with, or null. The children and young people's B1 sheet extends the
	 * reading title: "Rozumienie tekstów pisanych z rozpoznawaniem struktur gramatycznych".
	 */
	public static String ofTitle(String text) {
		String normalized = Text.collapse(text).toUpperCase();
		for (Map.Entry<String, String> title : TITLES.entrySet()) {
			if (normalized.equals(title.getKey()) || normalized.startsWith(title.getKey() + " ")) {
				return title.getValue();
			}
		}
		return null;
	}

	public static boolean hasKey(String skill) {
		return LISTENING.equals(skill) || READING.equals(skill) || GRAMMAR.equals(skill);
	}
}
