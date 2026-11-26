package com.krynhild.polishwritinglab.ingestion.parse.standards;

/**
 * One numbered paragraph or list entry of the requirement standards.
 *
 * @param id stable ID, for example {@code std/adult/B1/PG/2.2.1/1/a}
 * @param level the level, or {@code general} for the principles that precede the level descriptions
 * @param skill {@code RS}, {@code RTP}, {@code PG}, {@code P}, {@code M}, {@code A} (catalog of communicative
 *     intentions) or {@code B} (catalog of topics)
 * @param kind {@code descriptor}, {@code text-type}, {@code role}, {@code register}, {@code form}, {@code grammar},
 *     {@code intention}, {@code topic} or {@code general}
 * @param marker the number or bullet the entry is printed with, for example {@code 2.2.1.} or {@code a)}
 */
public record StandardNode(String id, String parentId, String audience, String level, String skill, String kind,
		String marker, String text) {
}
