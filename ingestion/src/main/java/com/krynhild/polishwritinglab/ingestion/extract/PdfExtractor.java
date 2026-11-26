package com.krynhild.polishwritinglab.ingestion.extract;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

/**
 * Reads the positioned text, images and rules of every page with PDFBox.
 *
 * <p>Glyphs are grouped into words in content-stream order and words into lines by baseline. Rotated glyphs, such
 * as the vertical {@code www.rcl.gov.pl} watermark of the Dziennik Ustaw, are dropped. Small glyphs raised next to a
 * word are kept as superscripts on that word's line instead of forming a line of their own.
 */
public class PdfExtractor {

	private static final Map<String, String> LIGATURES = Map.of(
			"ﬀ", "ff", "ﬁ", "fi", "ﬂ", "fl", "ﬃ", "ffi", "ﬄ", "ffl");

	public ExtractedDocument extract(Path path) {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			List<Page> pages = new ArrayList<>();
			for (int index = 0; index < document.getNumberOfPages(); index++) {
				pages.add(extractPage(document, index));
			}
			String producer = document.getDocumentInformation().getProducer();
			return new ExtractedDocument(path, sha256(path), producer, pages);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read " + path, ex);
		}
	}

	private Page extractPage(PDDocument document, int index) throws IOException {
		PDPage pdPage = document.getPage(index);
		PDRectangle box = pdPage.getCropBox();

		GlyphCollector glyphs = new GlyphCollector();
		glyphs.setStartPage(index + 1);
		glyphs.setEndPage(index + 1);
		glyphs.getText(document);

		GraphicsCollector graphics = new GraphicsCollector(pdPage, box.getHeight() + box.getLowerLeftY());
		graphics.processPage(pdPage);

		List<Line> lines = groupLines(index + 1, words(glyphs.positions));
		return new Page(index + 1, box.getWidth(), box.getHeight(), lines, graphics.images, graphics.rules);
	}

	private static List<Word> words(List<TextPosition> positions) {
		List<Word> words = new ArrayList<>();
		WordBuilder current = null;
		for (TextPosition position : positions) {
			String text = normalize(position.getUnicode());
			if (text.isBlank() || rotated(position)) {
				if (current != null) {
					words.add(current.build());
					current = null;
				}
				continue;
			}
			if (current != null && current.continuesWith(position)) {
				current.add(position, text);
			}
			else {
				if (current != null) {
					words.add(current.build());
				}
				current = new WordBuilder(position, text);
			}
		}
		if (current != null) {
			words.add(current.build());
		}
		return mergeSplitMarkers(words);
	}

	/**
	 * Rejoins {@code P RZYKŁAD}: some sheets kern the P of the example marker so far that it reads as a word of its
	 * own, and the parsers look for {@code PRZYKŁAD}.
	 */
	private static List<Word> mergeSplitMarkers(List<Word> words) {
		List<Word> merged = new ArrayList<>();
		for (Word word : words) {
			Word previous = merged.isEmpty() ? null : merged.getLast();
			if (previous != null && previous.text().equals("P") && word.text().startsWith("RZYKŁAD")
					&& Math.abs(previous.baseline() - word.baseline()) < 1 && word.x0() - previous.x1() < word.fontSize()) {
				merged.set(merged.size() - 1, new Word("P" + word.text(), previous.x0(), word.x1(),
						Math.min(previous.top(), word.top()), word.baseline(), word.fontSize(), word.bold(), word.superscript()));
			}
			else {
				merged.add(word);
			}
		}
		return merged;
	}

	/** Groups words into lines by baseline, keeping superscripts on the line of the word they belong to. */
	static List<Line> groupLines(int page, List<Word> words) {
		List<Word> placed = new ArrayList<>();
		List<Double> baselines = new ArrayList<>();
		for (Word word : words) {
			Word base = superscriptBase(word, words);
			placed.add(base == null ? word : word.asSuperscript());
			baselines.add(base == null ? word.baseline() : base.baseline());
		}

		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < placed.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingDouble((Integer i) -> baselines.get(i)).thenComparingDouble(i -> placed.get(i).x0()));

		List<Line> lines = new ArrayList<>();
		List<Word> current = new ArrayList<>();
		double currentBaseline = 0;
		for (int i : order) {
			Word word = placed.get(i);
			double baseline = baselines.get(i);
			double tolerance = Math.max(1.5, 0.25 * word.fontSize());
			if (!current.isEmpty() && Math.abs(baseline - currentBaseline) > tolerance) {
				lines.add(line(page, currentBaseline, current));
				current = new ArrayList<>();
			}
			if (current.isEmpty()) {
				currentBaseline = baseline;
			}
			current.add(word);
		}
		if (!current.isEmpty()) {
			lines.add(line(page, currentBaseline, current));
		}
		return lines;
	}

	private static Line line(int page, double baseline, List<Word> words) {
		List<Word> sorted = new ArrayList<>(words);
		sorted.sort(Comparator.comparingDouble(Word::x0));
		return new Line(page, baseline, sorted);
	}

	/** The normal-size word that {@code word} is a superscript of, or null. */
	private static Word superscriptBase(Word word, List<Word> words) {
		for (Word other : words) {
			if (other == word || other.fontSize() * 0.85 < word.fontSize()) {
				continue;
			}
			double raised = other.baseline() - word.baseline();
			boolean adjacent = Math.abs(word.x0() - other.x1()) < 2.5 || Math.abs(other.x0() - word.x1()) < 2.5;
			if (adjacent && raised > 0.5 && raised < 0.6 * other.fontSize()) {
				return other;
			}
		}
		return null;
	}

	private static boolean rotated(TextPosition position) {
		Matrix matrix = position.getTextMatrix();
		double angle = Math.toDegrees(Math.atan2(matrix.getValue(0, 1), matrix.getValue(0, 0)));
		return Math.abs(angle) > 2;
	}

	static String normalize(String unicode) {
		if (unicode == null) {
			return "";
		}
		String text = unicode.replace(' ', ' ').replace("­", "");
		for (Map.Entry<String, String> ligature : LIGATURES.entrySet()) {
			text = text.replace(ligature.getKey(), ligature.getValue());
		}
		return Normalizer.normalize(text, Normalizer.Form.NFC);
	}

	private static String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static boolean bold(PDFont font) {
		String name = font.getName() == null ? "" : font.getName().toLowerCase();
		if (name.contains("bold") || name.contains("black") || name.contains("heavy")) {
			return true;
		}
		return font.getFontDescriptor() != null
				&& (font.getFontDescriptor().isForceBold() || font.getFontDescriptor().getFontWeight() >= 700);
	}

	private static final class WordBuilder {

		private final StringBuilder text = new StringBuilder();
		private final double size;
		private final boolean bold;
		private final double baseline;
		private final double x0;
		private double x1;
		private double top;

		WordBuilder(TextPosition position, String text) {
			this.size = position.getFontSizeInPt();
			this.bold = bold(position.getFont());
			this.baseline = position.getYDirAdj();
			this.x0 = position.getXDirAdj();
			this.x1 = x0;
			this.top = baseline;
			add(position, text);
		}

		boolean continuesWith(TextPosition position) {
			double gap = position.getXDirAdj() - x1;
			double sizeRatio = position.getFontSizeInPt() / size;
			return Math.abs(position.getYDirAdj() - baseline) <= 0.6
					&& sizeRatio > 0.8 && sizeRatio < 1.25
					&& gap > -0.3 * size && gap < 0.12 * size;
		}

		void add(TextPosition position, String glyph) {
			text.append(glyph);
			x1 = Math.max(x1, position.getXDirAdj() + position.getWidthDirAdj());
			top = Math.min(top, position.getYDirAdj() - position.getHeightDir());
		}

		Word build() {
			return new Word(text.toString(), x0, x1, top, baseline, size, bold, false);
		}
	}

	private static final class GlyphCollector extends PDFTextStripper {

		private final List<TextPosition> positions = new ArrayList<>();

		@Override
		protected void processTextPosition(TextPosition text) {
			positions.add(text);
		}
	}

	/** Records where images are drawn and which thin horizontal shapes are filled or stroked. */
	private static final class GraphicsCollector extends PDFGraphicsStreamEngine {

		private final double pageTop;
		private final List<Box> images = new ArrayList<>();
		private final List<Box> rules = new ArrayList<>();
		private final List<double[]> subpaths = new ArrayList<>();
		private Point2D current = new Point2D.Double();

		GraphicsCollector(PDPage page, double pageTop) {
			super(page);
			this.pageTop = pageTop;
		}

		@Override
		public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
			double[] bounds = { p0.getX(), p0.getY(), p0.getX(), p0.getY() };
			for (Point2D point : List.of(p1, p2, p3)) {
				extend(bounds, point);
			}
			subpaths.add(bounds);
			current = p0;
		}

		@Override
		public void drawImage(PDImage image) {
			Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
			double[] bounds = { ctm.getTranslateX(), ctm.getTranslateY(), ctm.getTranslateX(), ctm.getTranslateY() };
			for (double[] corner : new double[][] { { 1, 0 }, { 0, 1 }, { 1, 1 } }) {
				Point2D.Float point = ctm.transformPoint((float) corner[0], (float) corner[1]);
				extend(bounds, point);
			}
			images.add(box(bounds));
		}

		@Override
		public void moveTo(float x, float y) {
			subpaths.add(new double[] { x, y, x, y });
			current = new Point2D.Float(x, y);
		}

		@Override
		public void lineTo(float x, float y) {
			if (subpaths.isEmpty()) {
				moveTo(x, y);
			}
			extend(subpaths.getLast(), new Point2D.Float(x, y));
			current = new Point2D.Float(x, y);
		}

		@Override
		public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
			lineTo(x3, y3);
		}

		@Override
		public Point2D getCurrentPoint() {
			return current;
		}

		@Override
		public void closePath() {
		}

		@Override
		public void endPath() {
			subpaths.clear();
		}

		@Override
		public void strokePath() {
			collectRules();
		}

		@Override
		public void fillPath(int windingRule) {
			collectRules();
		}

		@Override
		public void fillAndStrokePath(int windingRule) {
			collectRules();
		}

		@Override
		public void clip(int windingRule) {
		}

		@Override
		public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) {
		}

		private void collectRules() {
			for (double[] bounds : subpaths) {
				Box box = box(bounds);
				if (box.height() <= 2.5 && box.width() >= 4) {
					rules.add(box);
				}
			}
			subpaths.clear();
		}

		private Box box(double[] bounds) {
			return new Box(bounds[0], pageTop - bounds[3], bounds[2], pageTop - bounds[1]);
		}

		private static void extend(double[] bounds, Point2D point) {
			bounds[0] = Math.min(bounds[0], point.getX());
			bounds[1] = Math.min(bounds[1], point.getY());
			bounds[2] = Math.max(bounds[2], point.getX());
			bounds[3] = Math.max(bounds[3], point.getY());
		}
	}
}
