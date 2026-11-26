package com.krynhild.polishwritinglab.ingestion.parse;

import com.krynhild.polishwritinglab.ingestion.extract.Box;
import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;

/**
 * The row borders of tables: drawn horizontal rules. Where a table has them, a line never belongs to an item on the
 * other side of a border, however close it is to that item's label.
 */
public final class Borders {

	public static final Borders NONE = new Borders(null);

	private final ExtractedDocument document;

	public Borders(ExtractedDocument document) {
		this.document = document;
	}

	/** Whether a rule crossing the column of {@code line} lies between the baselines of the two lines. */
	public boolean between(Line anchor, Line line) {
		if (document == null || anchor.page() != line.page()) {
			return false;
		}
		Line upper = anchor.baseline() < line.baseline() ? anchor : line;
		Line lower = upper == anchor ? line : anchor;
		double x = line.x0() + 5;
		for (Box rule : document.page(line.page()).rules()) {
			boolean crosses = rule.x0() < x && rule.x1() > x;
			if (crosses && rule.top() > upper.baseline() + 0.5 && rule.top() < lower.baseline() - 0.5 * lower.fontSize()) {
				return true;
			}
		}
		return false;
	}
}
