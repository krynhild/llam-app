package com.krynhild.polishwritinglab.ingestion.model;

/** A scoring criterion of the writing or speaking module, for example "Środki językowe 0 – 10 p.". */
public record Criterion(String name, String group, double maxPoints) {
}
