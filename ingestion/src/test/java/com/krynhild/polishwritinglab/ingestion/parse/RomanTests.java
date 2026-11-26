package com.krynhild.polishwritinglab.ingestion.parse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RomanTests {

	@Test
	void convertsValuesToNumerals() {
		assertThat(Roman.of(1)).isEqualTo("I");
		assertThat(Roman.of(4)).isEqualTo("IV");
		assertThat(Roman.of(8)).isEqualTo("VIII");
		assertThat(Roman.of(9)).isEqualTo("IX");
	}

	@Test
	void parsesNumeralsBackToValues() {
		assertThat(Roman.parse("IV")).isEqualTo(4);
		assertThat(Roman.parse("viii")).isEqualTo(8);
		assertThat(Roman.parse(" IX ")).isEqualTo(9);
	}

	@Test
	void returnsZeroForSomethingThatIsNotANumeral() {
		assertThat(Roman.parse("a")).isZero();
		assertThat(Roman.parse("1")).isZero();
	}
}
