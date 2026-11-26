package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;

/**
 * Groups lines under the item labels (anchors) they belong to.
 *
 * <p>Two layouts occur. In a table, Word centres a short label vertically in its cell, so the cell's text can be
 * above, on and below the label's line: {@link #group} gives each line to the anchor with the nearest baseline. In
 * a flowing list, an item's text starts on the label's line and continues below it: {@link #flow} gives each line to
 * the anchor above it, unless the next anchor is a label printed alone that the line is at least as close to.
 */
public final class Cells {

	private Cells() {
	}

	/** An anchor found at the start of a line, and the rest of that line. */
	public record Anchor(String label, Line line, Line rest) {
	}

	/**
	 * Assigns lines to the anchor above them, or to a lone label just below them.
	 *
	 * @param maxBefore lines above the first anchor are kept only this close to it
	 * @param maxAfter lines below the last anchor are kept only this close to it
	 */
	public static Map<Anchor, List<Line>> flow(List<Line> lines, Function<Line, Anchor> anchorOf, double maxBefore,
			double maxAfter) {
		List<Anchor> anchors = anchors(lines, anchorOf);
		return flow(anchors, lines.stream().filter(line -> anchors.stream().noneMatch(anchor -> anchor.line() == line))
				.toList(), maxBefore, maxAfter);
	}

	/**
	 * Assigns lines to anchors found elsewhere, for example in the label column of a table whose other columns are
	 * read separately. An anchor's {@code rest} is its own text in the lines being assigned, if any.
	 */
	public static Map<Anchor, List<Line>> flow(List<Anchor> anchors, List<Line> lines, double maxBefore, double maxAfter) {
		return flow(anchors, lines, maxBefore, maxAfter, Borders.NONE);
	}

	/** Like {@link #flow(List, List, double, double)}, never crossing a table's row borders. */
	public static Map<Anchor, List<Line>> flow(List<Anchor> anchors, List<Line> lines, double maxBefore, double maxAfter,
			Borders borders) {
		Map<Anchor, List<Line>> cells = cells(anchors);
		for (Line line : lines) {
			Anchor above = null;
			Anchor below = null;
			for (Anchor anchor : anchors) {
				if (before(anchor.line(), line)) {
					above = anchor;
				}
				else if (below == null) {
					below = anchor;
				}
			}
			double tie = 0.35 * line.fontSize();
			double toAbove = distance(above, line);
			double toBelow = distance(below, line);
			boolean aboveBlocked = above != null && borders.between(above.line(), line);
			boolean belowBlocked = below != null && borders.between(below.line(), line);
			Anchor owner;
			if (aboveBlocked != belowBlocked) {
				owner = aboveBlocked ? below : above;
			}
			else if (above == null) {
				owner = toBelow <= maxBefore ? below : null;
			}
			else if (below != null && below.rest() == null && toBelow <= toAbove + tie) {
				owner = below;
			}
			else {
				owner = below == null && toAbove > maxAfter ? null : above;
			}
			if (owner != null) {
				cells.get(owner).add(line);
			}
		}
		sort(cells);
		return cells;
	}

	/**
	 * Assigns lines to the anchor with the nearest baseline; on a tie, to a label printed alone.
	 *
	 * @param anchorOf returns the label if the line starts with an anchor, otherwise null
	 * @param maxDistance lines farther than this from every anchor are left out
	 */
	public static Map<Anchor, List<Line>> group(List<Line> lines, Function<Line, Anchor> anchorOf, double maxDistance) {
		List<Anchor> anchors = anchors(lines, anchorOf);
		Map<Anchor, List<Line>> cells = cells(anchors);
		for (Line line : lines) {
			if (anchors.stream().anyMatch(anchor -> anchor.line() == line)) {
				continue;
			}
			double tie = 0.35 * line.fontSize();
			Anchor nearest = null;
			double nearestDistance = Double.MAX_VALUE;
			for (Anchor anchor : anchors) {
				if (anchor.line().page() != line.page()) {
					continue;
				}
				double distance = Math.abs(anchor.line().baseline() - line.baseline());
				if (nearest == null || distance < nearestDistance - tie) {
					nearest = anchor;
					nearestDistance = distance;
				}
				else if (Math.abs(distance - nearestDistance) <= tie && anchor.rest() == null && nearest.rest() != null) {
					nearest = anchor;
					nearestDistance = Math.min(distance, nearestDistance);
				}
			}
			if (nearest != null && nearestDistance <= maxDistance) {
				cells.get(nearest).add(line);
			}
		}
		sort(cells);
		return cells;
	}

	/** The anchor whose baseline is nearest to the line's, or null if none is on its page. */
	public static Anchor nearest(List<Anchor> anchors, Line line) {
		return nearest(anchors, line, Borders.NONE);
	}

	/** The nearest anchor not separated from the line by a row border, or the nearest of all if every one is. */
	public static Anchor nearest(List<Anchor> anchors, Line line, Borders borders) {
		Anchor nearest = null;
		Anchor nearestInRow = null;
		for (Anchor anchor : anchors) {
			if (distance(anchor, line) < distance(nearest, line)) {
				nearest = anchor;
			}
			if (!borders.between(anchor.line(), line) && distance(anchor, line) < distance(nearestInRow, line)) {
				nearestInRow = anchor;
			}
		}
		return nearestInRow != null ? nearestInRow : nearest;
	}

	private static List<Anchor> anchors(List<Line> lines, Function<Line, Anchor> anchorOf) {
		List<Anchor> anchors = new ArrayList<>();
		for (Line line : lines) {
			Anchor anchor = anchorOf.apply(line);
			if (anchor != null) {
				anchors.add(anchor);
			}
		}
		return anchors;
	}

	private static Map<Anchor, List<Line>> cells(List<Anchor> anchors) {
		Map<Anchor, List<Line>> cells = new LinkedHashMap<>();
		for (Anchor anchor : anchors) {
			cells.put(anchor, new ArrayList<>());
			if (anchor.rest() != null) {
				cells.get(anchor).add(anchor.rest());
			}
		}
		return cells;
	}

	private static void sort(Map<Anchor, List<Line>> cells) {
		cells.values().forEach(cell -> cell.sort((a, b) -> a.page() != b.page() ? Integer.compare(a.page(), b.page())
				: Double.compare(a.baseline(), b.baseline())));
	}

	private static boolean before(Line a, Line b) {
		return a.page() < b.page() || a.page() == b.page() && a.baseline() < b.baseline() - 0.5;
	}

	private static double distance(Anchor anchor, Line line) {
		if (anchor == null) {
			return Double.MAX_VALUE;
		}
		if (anchor.line().page() != line.page()) {
			return 1000;
		}
		return Math.abs(anchor.line().baseline() - line.baseline());
	}

	/** The text of a cell's lines in reading order. */
	public static String text(List<Line> cell) {
		StringBuilder text = new StringBuilder();
		for (Line line : cell) {
			if (!text.isEmpty()) {
				text.append(' ');
			}
			text.append(GapText.render(line));
		}
		return text.toString().strip();
	}

	/** An anchor label made of the first word of a line, if {@code label} returns one for that word. */
	public static Anchor firstWord(Line line, Function<Word, String> label) {
		Word first = line.words().getFirst();
		String value = label.apply(first);
		if (value == null) {
			return null;
		}
		List<Word> rest = line.words().subList(1, line.words().size());
		return new Anchor(value, line, rest.isEmpty() ? null : new Line(line.page(), line.baseline(), rest));
	}
}
