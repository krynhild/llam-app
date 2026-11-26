package com.krynhild.polishwritinglab.ingestion.parse;

import org.junit.jupiter.api.Test;

import com.krynhild.polishwritinglab.ingestion.model.Points;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreLineTests {

	@Test
	void parsesTotalCountAndPerItem() {
		Points points = ScoreLine.parse("_____ / 7 p. (14 x 0,5 p.)");

		assertThat(points.total()).isEqualTo(7);
		assertThat(points.count()).isEqualTo(14);
		assertThat(points.perItem()).isEqualTo(0.5);
	}

	@Test
	void toleratesExtraSpaceBeforeTheCount() {
		Points points = ScoreLine.parse("______ / 9 p. ( 9 x 1 p.)");

		assertThat(points.count()).isEqualTo(9);
		assertThat(points.perItem()).isEqualTo(1.0);
	}

	@Test
	void toleratesAMissingPeriodAfterP() {
		Points points = ScoreLine.parse("_____ / 5 p. (10 x 0,5 p)");

		assertThat(points.perItem()).isEqualTo(0.5);
	}

	@Test
	void parsesADecimalTotalWithoutTheBreakdown() {
		Points points = ScoreLine.parse("_______ / 2,5 (5 x 0,5 p.)");

		assertThat(points.total()).isEqualTo(2.5);
	}

	@Test
	void parsesATotalWithNoBreakdownAtAll() {
		Points points = ScoreLine.parse("_____ / 8 p.");

		assertThat(points.total()).isEqualTo(8);
		assertThat(points.count()).isNull();
		assertThat(points.perItem()).isNull();
	}

	@Test
	void acceptsTheMinusSignUsedAsABlankInsteadOfUnderscores() {
		Points points = ScoreLine.parse("−−−−−− / 5 p. (10 x 0,5 p.)");

		assertThat(points.total()).isEqualTo(5);
		assertThat(points.count()).isEqualTo(10);
	}

	@Test
	void returnsNullWhenTheLineHasNoBlank() {
		assertThat(ScoreLine.parse("Proszę przeczytać tekst.")).isNull();
	}
}
