package com.krynhild.polishwritinglab.ingestion.parse.standards;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.krynhild.polishwritinglab.ingestion.clean.Boilerplate;
import com.krynhild.polishwritinglab.ingestion.extract.ExtractedDocument;
import com.krynhild.polishwritinglab.ingestion.extract.Line;
import com.krynhild.polishwritinglab.ingestion.model.Issue;
import com.krynhild.polishwritinglab.ingestion.model.SourceDocument;
import com.krynhild.polishwritinglab.ingestion.parse.Text;

/**
 * Parses the requirement standards (Dz.U. 2016 poz. 405, Załącznik nr 1) into a tree.
 *
 * <p>Part I describes the levels of the group adapted to children and young people (A1–B2), part II the adults'
 * levels (A1–C2). Each level section (C.–H.) has the same numbered skills: 1. listening, 2. grammar, 3. reading,
 * 4. writing, 5. speaking, 6. catalog A (communicative intentions) and 7. catalog B (topics). Inside a skill, entries
 * are numbered {@code 2.2.1.}, {@code 1)}, {@code a)}, {@code –} and {@code •}; their nesting follows their
 * indentation. Lines without a marker continue the previous entry, with words broken by hyphenation rejoined.
 */
public final class StandardsParser {

	public static final String VERSION = "Dz.U. 2016 poz. 405, załącznik nr 1";

	private static final Pattern SECTION = Pattern.compile("^([A-H])\\.\\s+(.+)$");
	private static final Pattern LEVEL = Pattern.compile("–\\s*(A1|A2|B1|B2|C1|C2)\\b");
	private static final Pattern SKILL = Pattern.compile("^(?:(\\d)\\.\\s+)?(ROZUMIENIE ZE SŁUCHU|POPRAWNOŚĆ GRAMATYCZNA|ROZUMIENIE TEKSTÓW PISANYCH|PISANIE|MÓWIENIE|KATALOG\\s+A\\b.*|KATALOG\\s+B\\b.*)$");
	private static final Pattern BARE_NUMBER = Pattern.compile("^\\d\\.$");
	private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.\\s+(.*)$");
	private static final Pattern LIST = Pattern.compile("^(\\d+)\\)\\s*(.*)$");
	private static final Pattern SUBLIST = Pattern.compile("^([a-z])\\)\\s*(.*)$");
	private static final Pattern DASH = Pattern.compile("^[–-]\\s+(.*)$");
	private static final Pattern BULLET = Pattern.compile("^[•●▪]\\s*(.*)$");
	private static final List<String> LEVELS = List.of("A1", "A2", "B1", "B2", "C1", "C2");

	private static final Map<String, String> SKILLS = Map.of(
			"ROZUMIENIE ZE SŁUCHU", "RS",
			"POPRAWNOŚĆ GRAMATYCZNA", "PG",
			"ROZUMIENIE TEKSTÓW PISANYCH", "RTP",
			"PISANIE", "P",
			"MÓWIENIE", "M");

	private static final class Node {

		private final String id;
		private final String parentId;
		private final String audience;
		private final String level;
		private final String skill;
		private final String marker;
		private final double x0;
		private final List<String> lines = new ArrayList<>();
		private String kind;

		Node(String id, String parentId, String audience, String level, String skill, String marker, double x0) {
			this.id = id;
			this.parentId = parentId;
			this.audience = audience;
			this.level = level;
			this.skill = skill;
			this.marker = marker;
			this.x0 = x0;
		}
	}

	public StandardsDocument parse(ExtractedDocument document, String path, List<Issue> issues) {
		List<Line> lines = Boilerplate.withoutRunningText(document);
		List<Node> nodes = new ArrayList<>();
		Map<String, Integer> ids = new HashMap<>();
		Map<String, StandardsDocument.LevelScope> levels = new LinkedHashMap<>();

		String audience = null;
		String level = null;
		String sectionTitle = null;
		String skill = null;
		Node skillNode = null;
		Deque<Node> stack = new ArrayDeque<>();
		Map<String, Integer> ordinals = new HashMap<>();

		for (Line line : lines) {
			String text = Text.collapse(line.text());
			String compact = text.replace(" ", "");
			if (compact.equals("CZĘŚĆI") || compact.equals("CZĘŚĆII")) {
				audience = compact.equals("CZĘŚĆI") ? "dim" : "adult";
				level = null;
				skill = null;
				sectionTitle = null;
				stack.clear();
				continue;
			}
			Matcher section = SECTION.matcher(text);
			if (audience == null || sectionTitle == null && !section.matches()) {
				// The title page of a part, before its first lettered section.
				continue;
			}

			if (section.matches() && text.equals(text.toUpperCase())) {
				sectionTitle = section.group(2);
				level = levelOf(sectionTitle);
				skill = null;
				skillNode = null;
				stack.clear();
				if (level == null && !sectionTitle.startsWith("ZASADY") && !sectionTitle.startsWith("OGÓLNY")) {
					// The title continues on the next line ("… EFEKTYWNEJ BIEGŁOŚCI" / "UŻYTKOWEJ – C1").
					level = "pending";
				}
				continue;
			}
			if ("pending".equals(level)) {
				if (BARE_NUMBER.matcher(text).matches()) {
					continue;
				}
				sectionTitle = sectionTitle + " " + text;
				String found = levelOf(sectionTitle);
				if (found != null) {
					level = found;
					String name = sectionTitle.replaceFirst("^OPIS KOMPETENCJI W JĘZYKU POLSKIM JAKO OBCYM NA POZIOMIE\\s*", "")
							.replaceFirst("\\s*–\\s*[ABC][12]$", "").toLowerCase();
					levels.put(audience + "/" + level, new StandardsDocument.LevelScope(audience, level, name, List.of()));
				}
				continue;
			}
			if (level != null && sectionTitle != null && !levels.containsKey(audience + "/" + level)) {
				String name = sectionTitle.replaceFirst("^OPIS KOMPETENCJI W JĘZYKU POLSKIM JAKO OBCYM NA POZIOMIE\\s*", "")
						.replaceFirst("\\s*–\\s*[ABC][12]$", "").toLowerCase();
				levels.put(audience + "/" + level, new StandardsDocument.LevelScope(audience, level, name, List.of()));
			}

			String levelId = level == null ? "general" : level;
			Matcher skillMatcher = SKILL.matcher(text);
			if (skillMatcher.matches() && level != null) {
				String title = skillMatcher.group(2);
				skill = title.startsWith("KATALOG") ? title.substring(8, 9) : SKILLS.get(title);
				String id = unique(ids, "std/" + audience + "/" + levelId + "/" + skill);
				skillNode = new Node(id, null, audience, levelId, skill, skillMatcher.group(1) == null ? "" : skillMatcher.group(1) + ".", line.x0());
				skillNode.lines.add(title);
				skillNode.kind = kindOfSkill(skill);
				nodes.add(skillNode);
				stack.clear();
				continue;
			}
			if (level == null && skill == null) {
				// Sections A and B: principles and the general description of each level.
				skill = "general";
				String id = unique(ids, "std/" + audience + "/general/" + (sectionTitle.startsWith("ZASADY") ? "A" : "B"));
				skillNode = new Node(id, null, audience, "general", null, "", line.x0());
				skillNode.lines.add(sectionTitle);
				skillNode.kind = "general";
				nodes.add(skillNode);
				stack.clear();
			}
			if (skillNode == null) {
				continue;
			}

			String marker = null;
			String key = null;
			String rest = null;
			Matcher numbered = NUMBERED.matcher(text);
			Matcher list = LIST.matcher(text);
			Matcher sublist = SUBLIST.matcher(text);
			Matcher dash = DASH.matcher(text);
			Matcher bullet = BULLET.matcher(text);
			if (numbered.matches()) {
				marker = numbered.group(1) + ".";
				key = numbered.group(1);
				rest = numbered.group(2);
			}
			else if (list.matches()) {
				marker = list.group(1) + ")";
				key = list.group(1);
				rest = list.group(2);
			}
			else if (sublist.matches()) {
				marker = sublist.group(1) + ")";
				key = sublist.group(1);
				rest = sublist.group(2);
			}
			else if (dash.matches() || bullet.matches()) {
				marker = dash.matches() ? "–" : "•";
				rest = dash.matches() ? dash.group(1) : bullet.group(1);
			}

			if (marker == null) {
				Node current = stack.isEmpty() ? skillNode : stack.peek();
				current.lines.add(text);
				continue;
			}
			while (!stack.isEmpty() && stack.peek().x0 >= line.x0() - 2) {
				stack.pop();
			}
			Node parent = stack.isEmpty() ? skillNode : stack.peek();
			if (key == null) {
				int ordinal = ordinals.merge(parent.id + marker, 1, Integer::sum);
				key = (marker.equals("–") ? "d" : "b") + ordinal;
			}
			String id = unique(ids, parent.id + "/" + key);
			Node node = new Node(id, parent.id, audience, levelId, parent.skill, marker, line.x0());
			node.lines.add(rest);
			nodes.add(node);
			stack.push(node);
		}

		List<StandardNode> result = new ArrayList<>();
		Map<String, Node> byId = new HashMap<>();
		for (Node node : nodes) {
			byId.put(node.id, node);
		}
		for (Node node : nodes) {
			String text = Text.joinLines(node.lines);
			result.add(new StandardNode(node.id, node.parentId, node.audience, node.level, node.skill,
					kind(node, byId), node.marker, text));
		}

		List<StandardsDocument.LevelScope> scopes = new ArrayList<>();
		for (StandardsDocument.LevelScope scope : levels.values()) {
			List<String> lower = LEVELS.subList(0, LEVELS.indexOf(scope.level())).stream()
					.filter(other -> levels.containsKey(scope.audience() + "/" + other))
					.toList();
			scopes.add(new StandardsDocument.LevelScope(scope.audience(), scope.level(), scope.name(), lower));
		}
		if (scopes.size() < 10) {
			issues.add(Issue.error("standards-levels", path, "Expected 10 level sections (4 + 6), found " + scopes.size()));
		}
		SourceDocument source = new SourceDocument("standards", path, document.sha256(), document.pages().size(),
				document.producer(), null);
		return new StandardsDocument(VERSION, source, scopes, result);
	}

	private static String levelOf(String title) {
		Matcher matcher = LEVEL.matcher(title);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String kindOfSkill(String skill) {
		return switch (skill) {
			case "A" -> "intention";
			case "B" -> "topic";
			default -> "descriptor";
		};
	}

	/** The kind of an entry, from the subsection it is in ("Rodzaje tekstów", "Lista zagadnień gramatycznych"…). */
	private static String kind(Node node, Map<String, Node> byId) {
		if ("general".equals(node.kind) || "general".equals(node.level)) {
			return "general";
		}
		for (Node current = node; current != null; current = current.parentId == null ? null : byId.get(current.parentId)) {
			if ("A".equals(current.skill)) {
				return "intention";
			}
			if ("B".equals(current.skill)) {
				return "topic";
			}
			String title = current.lines.isEmpty() ? "" : current.lines.getFirst().toLowerCase();
			if (current.marker.matches("\\d+\\.\\d+\\.")) {
				if (title.startsWith("rodzaje tekstów") || title.startsWith("rodzaje wypowiedzi")) {
					return "text-type";
				}
				if (title.startsWith("role komunikacyjne")) {
					return "role";
				}
				if (title.startsWith("rejestr")) {
					return "register";
				}
				if (title.startsWith("formy wypowiedzi")) {
					return "form";
				}
				if (title.startsWith("lista zagadnień gramatycznych")) {
					return "grammar";
				}
				if (title.startsWith("ogólny opis")) {
					return "descriptor";
				}
			}
		}
		return "PG".equals(node.skill) && node.parentId != null ? "grammar" : "descriptor";
	}

	private static String unique(Map<String, Integer> ids, String id) {
		int count = ids.merge(id, 1, Integer::sum);
		return count == 1 ? id : id + "~" + count;
	}
}
