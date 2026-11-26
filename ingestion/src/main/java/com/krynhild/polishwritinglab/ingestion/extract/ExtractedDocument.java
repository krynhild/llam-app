package com.krynhild.polishwritinglab.ingestion.extract;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The positioned text of a PDF, the input of every parser. */
public record ExtractedDocument(Path path, String sha256, String producer, List<Page> pages) {

	public ExtractedDocument {
		pages = List.copyOf(pages);
	}

	public Stream<Line> lines() {
		return pages.stream().flatMap(page -> page.lines().stream());
	}

	public Page page(int number) {
		return pages.get(number - 1);
	}
}
