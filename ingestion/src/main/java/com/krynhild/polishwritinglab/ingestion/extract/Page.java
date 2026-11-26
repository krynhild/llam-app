package com.krynhild.polishwritinglab.ingestion.extract;

import java.util.List;

/**
 * The text and graphics of one page.
 *
 * @param number 1-based page number
 * @param lines lines from top to bottom
 * @param images where raster images are drawn
 * @param rules thin horizontal strokes and fills, such as underlines and table borders
 */
public record Page(int number, double width, double height, List<Line> lines, List<Box> images, List<Box> rules) {

	public Page {
		lines = List.copyOf(lines);
		images = List.copyOf(images);
		rules = List.copyOf(rules);
	}

	public int characterCount() {
		return lines.stream().mapToInt(line -> line.text().length()).sum();
	}
}
