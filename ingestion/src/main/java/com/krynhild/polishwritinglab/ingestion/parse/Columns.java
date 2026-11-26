package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;

/**
 * Restores reading order where items are printed in two columns, for example listening items 1–7 next to 8–14.
 *
 * <p>A gutter is assumed only where item anchors ({@code 8.}, {@code a)}) start to the right of other text on at
 * least two lines, so tables whose right column holds marks or letters keep their row order.
 */
public final class Columns {

	private static final Pattern ANCHOR = Pattern.compile("(\\d{1,2}\\.|[a-e]\\))");

	private Columns() {
	}

	public static List<Line> readingOrder(List<Line> lines) {
		Double gutter = gutter(lines);
		if (gutter == null) {
			return lines;
		}
		int first = -1;
		int last = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (rightAnchor(lines.get(i), gutter)) {
				first = first < 0 ? i : first;
				last = i;
			}
		}
		// Extend the region over lines that continue the right column without crossing the gutter.
		while (last + 1 < lines.size() && !crosses(lines.get(last + 1), gutter) && lines.get(last + 1).x1() > gutter
				&& lines.get(last + 1).page() == lines.get(last).page()) {
			last++;
		}
		List<Line> region = lines.subList(first, last + 1);
		long crossing = region.stream().filter(line -> crosses(line, gutter)).count();
		if (crossing * 10 > region.size()) {
			return lines;
		}

		List<Line> ordered = new ArrayList<>(lines.subList(0, first));
		List<Line> right = new ArrayList<>();
		for (Line line : region) {
			Line leftPart = line.slice(Double.NEGATIVE_INFINITY, gutter);
			Line rightPart = line.slice(gutter, Double.POSITIVE_INFINITY);
			if (leftPart != null) {
				ordered.add(leftPart);
			}
			if (rightPart != null) {
				right.add(rightPart);
			}
		}
		ordered.addAll(right);
		ordered.addAll(lines.subList(last + 1, lines.size()));
		return ordered;
	}

	private static Double gutter(List<Line> lines) {
		List<Double> starts = new ArrayList<>();
		for (Line line : lines) {
			List<Word> words = line.words();
			for (int i = 1; i < words.size(); i++) {
				Word word = words.get(i);
				if (ANCHOR.matcher(word.text()).matches() && word.x0() - words.get(i - 1).x1() > 15) {
					starts.add(word.x0());
				}
			}
		}
		Double best = null;
		int bestCount = 1;
		for (double start : starts) {
			int count = (int) starts.stream().filter(other -> Math.abs(other - start) < 12).count();
			if (count > bestCount) {
				bestCount = count;
				best = start;
			}
		}
		if (best == null) {
			return null;
		}
		// Options are indented under their item number, so the column starts at the leftmost anchor near the best.
		double center = best;
		return starts.stream().filter(start -> start >= center - 60 && start <= center + 12)
				.mapToDouble(Double::doubleValue).min().orElse(center) - 6;
	}

	private static boolean rightAnchor(Line line, double gutter) {
		return line.words().stream().anyMatch(word -> word.x0() >= gutter && word.x0() < gutter + 20
				&& ANCHOR.matcher(word.text()).matches());
	}

	private static boolean crosses(Line line, double gutter) {
		return line.words().stream().anyMatch(word -> word.x0() < gutter && word.x1() > gutter + 2);
	}
}
