package com.krynhild.polishwritinglab.ingestion.parse.standards;

import java.util.List;

import com.krynhild.polishwritinglab.ingestion.model.SourceDocument;

/**
 * The requirement standards as a flat list of nodes that reference their parents.
 *
 * @param version the legal act the standards come from
 * @param levels the levels each part describes, with the lower levels whose grammar they include
 */
public record StandardsDocument(String version, SourceDocument source, List<LevelScope> levels, List<StandardNode> nodes) {

	/**
	 * @param audience {@code dim} (part I, children and young people) or {@code adult} (part II)
	 * @param inherits lower levels of the same part; the grammar list of a level covers theirs too
	 */
	public record LevelScope(String audience, String level, String name, List<String> inherits) {
	}
}
