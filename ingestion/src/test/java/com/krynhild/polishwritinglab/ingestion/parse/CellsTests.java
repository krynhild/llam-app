package com.krynhild.polishwritinglab.ingestion.parse;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.extract.Word;

import static org.assertj.core.api.Assertions.assertThat;

class CellsTests {

	private static final Pattern LABEL = Pattern.compile("^(\\d)\\.$");

	private static Line line(double baseline, String... words) {
		List<Word> built = new java.util.ArrayList<>();
		double x = 0;
		for (String text : words) {
			built.add(new Word(text, x, x + text.length() * 6, baseline - 10, baseline, 12, false, false));
			x += text.length() * 6 + 4;
		}
		return new Line(1, baseline, built);
	}

	private static Cells.Anchor anchor(Line line) {
		return Cells.firstWord(line, word -> {
			Matcher label = LABEL.matcher(word.text());
			return label.matches() ? label.group(1) : null;
		});
	}

	@Test
	void groupAssignsALineToTheAnchorWithTheNearestBaseline() {
		Line anchor1 = line(10, "1.");
		Line anchor2 = line(30, "2.");
		Line content = line(19, "tekst");

		Map<Cells.Anchor, List<Line>> cells = Cells.group(List.of(anchor1, content, anchor2),
				candidate -> anchor(candidate), 100);

		assertThat(cells.get(anchor(anchor1))).containsExactly(content);
	}

	@Test
	void groupBreaksATieInFavourOfALabelPrintedAlone() {
		// "1." carries its own text on the same line (not printed alone); "2." is printed alone, the way a
		// vertically centred table label is. A line equally close to both goes to "2.".
		Line anchor1 = line(0, "1.", "coś");
		Line anchor2 = line(20, "2.");
		Line content = line(10, "tekst");

		Map<Cells.Anchor, List<Line>> cells = Cells.group(List.of(anchor1, content, anchor2),
				candidate -> anchor(candidate), 100);

		assertThat(cells.get(anchor(anchor2))).containsExactly(content);
	}

	@Test
	void flowAssignsContinuationLinesToTheAnchorAboveThem() {
		// "2." carries its own text, so it isn't a lone label that could pull a nearby preceding line to itself.
		Line anchor1 = line(0, "1.");
		Line first = line(16, "pierwsza linia");
		Line second = line(32, "druga linia");
		Line anchor2 = line(48, "2.", "coś");

		Map<Cells.Anchor, List<Line>> cells = Cells.flow(List.of(anchor1, first, second, anchor2),
				candidate -> anchor(candidate), 30, 30);

		assertThat(cells.get(anchor(anchor1))).containsExactly(first, second);
	}

	@Test
	void flowPullsTheLastLineBeforeALoneLabelToThatLabel() {
		// "1." is followed by its own text; "2." is printed alone (a table label with nothing beside it). A line
		// slightly closer to "1." than to "2.", but within the tie tolerance, still belongs to "2.".
		Line anchor1 = line(0, "1.", "coś");
		Line anchor2 = line(20, "2.");
		Line content = line(9, "tekst");

		Map<Cells.Anchor, List<Line>> cells = Cells.flow(List.of(anchor1, content, anchor2),
				candidate -> anchor(candidate), 30, 30);

		assertThat(cells.get(anchor(anchor2))).containsExactly(content);
	}
}
