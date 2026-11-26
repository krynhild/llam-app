package com.krynhild.polishwritinglab.ingestion.extract;

/** A rectangle in page coordinates with the origin at the top left. */
public record Box(double x0, double top, double x1, double bottom) {

	public double width() {
		return x1 - x0;
	}

	public double height() {
		return bottom - top;
	}
}
