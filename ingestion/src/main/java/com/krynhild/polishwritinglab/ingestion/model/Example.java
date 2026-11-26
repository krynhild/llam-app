package com.krynhild.polishwritinglab.ingestion.model;

/** The worked example ("PRZYKŁAD") that precedes the items. The answer is null when the sheet only shows it visually. */
public record Example(String text, String answer) {
}
