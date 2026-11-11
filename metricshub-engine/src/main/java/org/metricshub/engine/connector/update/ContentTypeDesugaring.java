package org.metricshub.engine.connector.update;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeReportEntry;
import org.metricshub.engine.connector.model.ContentTypeReportEntry.Kind;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToText;
import org.metricshub.engine.connector.model.tool.ConnectorTool;

/**
 * Makes the content-type conversions of every compute chain explicit, and reports what it did and the legacy idioms
 * it found (the worklist of stage 2 of the content-type migration, #1508).
 * <p>
 * The chain of a source is walked while tracking the current content type, starting from the type the source
 * produces ({@link Source#getOutputContentType()}):
 * <ul>
 * <li>an <code>awk</code> compute reads whatever it gets, a text or a table: nothing is inserted before it, and
 * what flows out of it is its output type;</li>
 * <li>a table compute fed text by a legacy text producer (<code>commandLine</code>, the <code>awk</code> source,
 * the <code>awk</code> compute, which built their table as well before the content types) gets a
 * <code>toTable</code> before it;</li>
 * <li>a chain ending in text from a legacy text producer gets a <code>toTable</code> appended, so that the mapping
 * reads a table, unless the source is read as text only (referenced by <code>copy</code> sources and by nothing
 * else, see {@link #textOnlySources(Connector, Map)});</li>
 * <li>a table compute fed by a pure text producer (<code>http</code>, <code>file</code>, <code>ipmi</code>)
 * reads no rows and gives an empty table: nothing is inserted, the case is reported;</li>
 * <li>a text compute that parses a text (<code>json2Table</code>, <code>xml2Csv</code>) fed by a table producer
 * finds nothing to parse and leaves the table unchanged: nothing is inserted, the case is reported;</li>
 * <li>a <code>copy</code> source produces what its origin's chain produces: its type and producer are resolved from
 * the origin (declared as a table when the origin cannot be resolved).</li>
 * </ul>
 * The inserted computes convert like written ones. They are flagged as generated ({@code ToText#isGenerated()},
 * {@code ToTable#isGenerated()}): a later pass removes them first, so the chain and the report are rebuilt from
 * what is written.
 * <p>
 * The findings are stored on the connector ({@code Connector#getContentTypeReport()}).
 */
@Slf4j
public class ContentTypeDesugaring extends AbstractConnectorUpdateChain {

	/**
	 * Mapper serializing the sources to find their references, shared because a new mapper introspects every source
	 * class again.
	 */
	private static final ObjectMapper MAPPER = JsonHelper.buildObjectMapper();

	/**
	 * A short source reference, <code>${source::name}</code>, with no dot: a source of the same tool or job.
	 */
	private static final Pattern SHORT_SOURCE_REFERENCE = Pattern.compile("^\\$\\{source::([^.}]+)\\}$");

	/**
	 * Who produced the current text of a chain.
	 */
	private enum TextProducer {
		/** No text: the current content is a table. */
		NONE,
		/** A pure text source: <code>http</code>, <code>file</code>, <code>ipmi</code>. */
		PURE,
		/**
		 * A legacy text producer that used to build its table as well: <code>commandLine</code>, <code>awk</code>. Its
		 * table readers get a generated <code>toTable</code>.
		 */
		LEGACY_DUAL,
		/** An explicit <code>toText</code> compute. */
		EXPLICIT
	}

	/**
	 * Logs every entry of the content-type report of a connector at WARN: what the desugaring inserted, and the legacy
	 * idioms it found, so that the author of a connector learns what the engine does on its behalf. The detail, which
	 * may quote a value of the connector or of the configuration, is logged at DEBUG only.
	 *
	 * @param connector   The desugared connector
	 * @param connectorId The identifier of the connector in the messages
	 */
	public static void logReport(final Connector connector, final String connectorId) {
		final List<ContentTypeReportEntry> report = connector.getContentTypeReport();
		if (report != null) {
			report.forEach(entry -> {
				log.warn("Connector {} - Content type: {} [{}]", connectorId, entry.sourceKey(), entry.kind());
				log.debug("Connector {} - Content type: {}", connectorId, entry);
			});
		}
	}

	@Override
	void doUpdate(final Connector connector) {
		final List<ContentTypeReportEntry> report = new ArrayList<>();
		// Every source by key, to resolve what a copy source copies
		final Map<String, Source> sources = new LinkedHashMap<>();
		forEachSource(connector, source -> sources.put(source.getKey(), source));
		final Set<String> textOnly = textOnlySources(connector, sources);
		sources.values().forEach(source -> desugar(source, sources, textOnly, report));
		connector.setContentTypeReport(report);
		// A tool without columns whose result source ends in text returns that text as it is
		if (connector.getTools() != null) {
			connector.getTools().forEach((name, tool) -> {
				final Source result = toolResultSource(tool);
				tool.setTextResult(
					!hasColumns(tool) &&
						result != null &&
						(endsInText(result, sources) || textUnionMembers(tool, sources) != null)
				);
			});
		}
	}

	/**
	 * The source a tool returns: its result reference, short (<code>${source::name}</code>, a source of the tool) or
	 * full.
	 *
	 * @param tool The tool
	 * @return the source, null when the reference resolves to none of the tool's sources
	 */
	static Source toolResultSource(final ConnectorTool tool) {
		if (tool.getResult() == null || tool.getResult().source() == null) {
			return null;
		}
		return toolSource(tool, tool.getResult().source());
	}

	/**
	 * A source of the tool, from a short (<code>${source::name}</code>) or full reference.
	 */
	private static Source toolSource(final ConnectorTool tool, final String ref) {
		if (tool.getSources() == null || ref == null) {
			return null;
		}
		final String reference = ref.trim();
		for (final Map.Entry<String, Source> entry : tool.getSources().entrySet()) {
			if (reference.equals("${source::" + entry.getKey() + "}") || reference.equals(entry.getValue().getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * The members of a tool's result when it declares no columns and returns a tableUnion of sources that all end in
	 * text, as written (command outputs glued together): the union joins their raw texts, which the tool returns.
	 *
	 * @param tool The tool
	 * @return the member sources, null when the result is no such union
	 */
	static List<Source> textUnionMembers(final ConnectorTool tool, final Map<String, Source> sources) {
		if (hasColumns(tool) || !(toolResultSource(tool) instanceof TableUnionSource union) || union.getTables() == null) {
			return null;
		}
		// A union with computes of its own returns what they produce: a table, or text through its own ending
		if (union.getComputes() != null && union.getComputes().stream().anyMatch(compute -> !isGenerated(compute))) {
			return null;
		}
		final List<Source> members = new ArrayList<>();
		for (final String table : union.getTables()) {
			final Source member = toolSource(tool, table);
			if (member == null || !endsInTextAsWritten(member, sources)) {
				return null;
			}
			members.add(member);
		}
		return members.isEmpty() ? null : members;
	}

	/**
	 * Whether the source ends in text as written: its last written compute writes text, or it has none and produces
	 * text itself (a copy: what its origin's chain produces).
	 */
	private static boolean endsInTextAsWritten(final Source source, final Map<String, Source> sources) {
		return chainEnd(source, sources, new HashSet<>(), null).type() == ContentType.TEXT;
	}

	/**
	 * What the source's chain produces, as written: what the source (or, for a copy, its origin's chain) produces,
	 * through the source's written computes. Once the sources read as text only are known, a legacy dual chain that is
	 * not among them produces a table: {@link #desugar} appends its <code>toTable</code>, so that what reads it (a copy,
	 * a union) gets a table, and its own conversion where it needs text.
	 *
	 * @param textOnly The sources read as text only, null while they are being determined
	 */
	private static State chainEnd(
		final Source source,
		final Map<String, Source> sources,
		final Set<Source> visited,
		final Set<String> textOnly
	) {
		final State start = start(source, sources, visited, textOnly);
		final State end = throughWrittenComputes(start, source);
		if (
			textOnly != null &&
				end.type() == ContentType.TEXT &&
				end.producer() == TextProducer.LEGACY_DUAL &&
				!textOnly.contains(source.getKey())
		) {
			return new State(ContentType.TABLE, TextProducer.NONE);
		}
		return end;
	}

	/**
	 * The state after the source's written computes, from the given state.
	 */
	private static State throughWrittenComputes(final State start, final Source source) {
		State state = start;
		for (final Compute compute : source.getComputes() == null ? List.<Compute>of() : source.getComputes()) {
			if (isGenerated(compute)) {
				continue;
			}
			final ContentType type = compute.getOutputContentType();
			final TextProducer producer;
			if (type != ContentType.TEXT) {
				producer = TextProducer.NONE;
			} else if (compute instanceof Awk) {
				producer = TextProducer.LEGACY_DUAL;
			} else if (compute instanceof ToText) {
				producer = TextProducer.EXPLICIT;
			} else {
				producer = state.producer();
			}
			state = new State(type, producer);
		}
		return state;
	}

	/**
	 * Whether the union joins text: every member ends in text as written (a text producer without computes, a source
	 * whose last written compute writes text, a copy of such a chain, another text union). The union's raw text is
	 * then its members' texts joined with a line feed, which is what an <code>awk</code> on the union reads: no
	 * conversion can be written in between without changing those bytes. An unresolved member or a cycle makes it no
	 * text union.
	 *
	 * @param union   The union
	 * @param sources Every source by key
	 * @param visited The unions and copies being resolved, to stop on a cycle
	 * @return whether the union is a text union
	 */
	public static boolean isTextUnion(
		final TableUnionSource union,
		final Map<String, Source> sources,
		final Set<Source> visited
	) {
		return textUnionProducer(union, sources, visited, null) != null;
	}

	/**
	 * Who produced the text of a text union, which tells which conversion its table readers need: a union of legacy
	 * text members only (command outputs, awk results) gets a generated <code>toTable</code>, as a legacy text
	 * producer on its own. A pure text member (<code>http</code>, <code>file</code>, <code>ipmi</code>) makes it a pure
	 * text source, whose table readers are reported; an explicit <code>toText</code> member is reported likewise.
	 *
	 * @return {@link TextProducer#PURE} when a member is pure text, else {@link TextProducer#EXPLICIT} when a member
	 *         ends in an explicit <code>toText</code>, else {@link TextProducer#LEGACY_DUAL}; null when the union is no
	 *         text union
	 */
	private static TextProducer textUnionProducer(
		final TableUnionSource union,
		final Map<String, Source> sources,
		final Set<Source> visited,
		final Set<String> textOnly
	) {
		if (union.getTables() == null || union.getTables().isEmpty() || !visited.add(union)) {
			return null;
		}
		try {
			TextProducer producer = TextProducer.LEGACY_DUAL;
			for (final String table : union.getTables()) {
				final Source member = table == null ? null : sources.get(resolveReference(table, union.getKey(), sources));
				final State end = member == null ? null : chainEnd(member, sources, visited, textOnly);
				if (end == null || end.type() != ContentType.TEXT) {
					return null;
				}
				if (end.producer() == TextProducer.PURE) {
					producer = TextProducer.PURE;
				} else if (end.producer() == TextProducer.EXPLICIT && producer == TextProducer.LEGACY_DUAL) {
					producer = TextProducer.EXPLICIT;
				}
			}
			return producer;
		} finally {
			// A member read twice is no cycle: only the unions being resolved count
			visited.remove(union);
		}
	}

	private static boolean hasColumns(final ConnectorTool tool) {
		return tool.getResult() != null && tool.getResult().columns() != null && !tool.getResult().columns().isEmpty();
	}

	/**
	 * Whether the source, as desugared, ends in text: its last compute writes text, or it has no compute and produces
	 * text (a copy: what its origin's chain produces).
	 */
	private static boolean endsInText(final Source source, final Map<String, Source> sources) {
		if (source.getComputes() != null && !source.getComputes().isEmpty()) {
			return source.getComputes().get(source.getComputes().size() - 1).getOutputContentType() == ContentType.TEXT;
		}
		final State state = start(source, sources, new HashSet<>(), null);
		return state.type() == ContentType.TEXT;
	}

	/**
	 * The sources that are read as text only: referenced by the <code>from</code> of <code>copy</code> sources, by
	 * the <code>input</code> of <code>awk</code> sources (a single reference, which the script reads as it is) or
	 * returned by a tool that declares no columns, and nowhere else. A legacy dual producer among them keeps its text,
	 * which the <code>awk</code> scripts reading it expect, and the copies get their own conversion where they need
	 * one.
	 * <p>
	 * "Nowhere else" is checked on the JSON form of every source (its own key and a copy's <code>from</code> left
	 * out), computes included, plus the mappings and the tool results: a <code>${source::...}</code> reference in any
	 * field (a variable, <code>executeForEachEntryOf</code>, the tables of a union, an internal database query...)
	 * counts as a table consumer. When a source cannot be serialized, no source is text only: the legacy behavior.
	 *
	 * @param connector The connector
	 * @param sources   Every source by key
	 * @return the keys of the sources read as text only
	 */
	static Set<String> textOnlySources(final Connector connector, final Map<String, Source> sources) {
		final Set<String> copied = new HashSet<>();
		final Set<String> readAsTable = new HashSet<>();
		// A tool without columns returning a union of text sources returns their joined text: the members are read as
		// text, and the union's references to them are no table read
		final Set<String> textUnions = new HashSet<>();
		if (connector.getTools() != null) {
			connector.getTools().values().forEach(tool -> {
				final List<Source> members = textUnionMembers(tool, sources);
				if (members != null) {
					textUnions.add(toolResultSource(tool).getKey());
					members.forEach(member -> copied.add(member.getKey()));
				}
			});
		}
		for (final Source source : sources.values()) {
			if (textUnions.contains(source.getKey())) {
				continue;
			}
			final Source scanned = source.copy();
			scanned.setKey(null);
			// A text union reads its members' texts: its tables are no table read (its computes still count)
			if (
				scanned instanceof TableUnionSource union && isTextUnion((TableUnionSource) source, sources, new HashSet<>())
			) {
				union.getTables().forEach(table -> copied.add(resolveReference(table, source.getKey(), sources)));
				union.setTables(List.of());
			}
			if (scanned instanceof CopySource copy) {
				if (copy.getFrom() != null) {
					copied.add(resolveReference(copy.getFrom(), source.getKey(), sources));
				}
				copy.setFrom("");
			}
			// An awk source reads the source of its input as it is: no table read (#1559)
			if (scanned instanceof JawkSource jawk && JawkSource.isSourceReference(jawk.getInput())) {
				copied.add(resolveReference(jawk.getInput(), source.getKey(), sources));
				jawk.setInput("");
			}
			try {
				final Set<String> references = new HashSet<>();
				collectReferences(MAPPER.writeValueAsString(scanned), references);
				references.forEach(reference -> readAsTable.add(resolveReference(reference, source.getKey(), sources)));
			} catch (JsonProcessingException e) {
				return Set.of();
			}
		}
		if (connector.getMonitors() != null) {
			for (final MonitorJob job : connector.getMonitors().values()) {
				if (job instanceof StandardMonitorJob standard) {
					addMappingSource(standard.getDiscovery(), readAsTable, sources);
					addMappingSource(standard.getCollect(), readAsTable, sources);
				} else if (job instanceof SimpleMonitorJob simple) {
					addMappingSource(simple.getSimple(), readAsTable, sources);
				}
			}
		}
		// A tool returns its result source's text when it declares no columns (that source is read as text), its
		// table otherwise
		if (connector.getTools() != null) {
			connector.getTools().values().forEach(tool -> {
				final Source result = toolResultSource(tool);
				if (result != null) {
					(hasColumns(tool) ? readAsTable : copied).add(result.getKey());
				}
			});
		}
		copied.removeAll(readAsTable);
		return copied;
	}

	private static void addMappingSource(
		final AbstractMonitorTask task,
		final Set<String> readAsTable,
		final Map<String, Source> sources
	) {
		if (task == null || task.getMapping() == null) {
			return;
		}
		// A short reference designates a source of the same task: any of them gives the context
		final String context = task.getSources() == null
			? null
			: task.getSources().values().stream().map(Source::getKey).filter(Objects::nonNull).findFirst().orElse(null);
		final Set<String> references = new HashSet<>();
		collectReferences(task.getMapping().getSource(), references);
		references.forEach(reference -> readAsTable.add(resolveReference(reference, context, sources)));
	}

	/**
	 * Adds every <code>${source::...}</code> reference of the value to the set, and returns the value unchanged.
	 */
	/**
	 * The full key a source reference designates. A short reference (<code>${source::name}</code>, as written inside a
	 * tool or a job) designates the source of that name next to the referencing source, as at run time.
	 *
	 * @param reference  The reference, short or full
	 * @param contextKey The key of the referencing source, null when unknown
	 * @param sources    Every source by key
	 * @return the full key of the designated source, or the reference itself when it designates none
	 */
	public static String resolveReference(
		final String reference,
		final String contextKey,
		final Map<String, Source> sources
	) {
		final String trimmed = reference.trim();
		if (sources.containsKey(trimmed) || contextKey == null) {
			return trimmed;
		}
		final Matcher matcher = SHORT_SOURCE_REFERENCE.matcher(trimmed);
		final int dot = contextKey.lastIndexOf('.');
		if (matcher.matches() && dot > 0) {
			final String sibling = contextKey.substring(0, dot + 1) + matcher.group(1) + "}";
			if (sources.containsKey(sibling)) {
				return sibling;
			}
		}
		return trimmed;
	}

	private static String collectReferences(final String value, final Set<String> references) {
		if (value != null) {
			final Matcher matcher = SOURCE_REF_PATTERN.matcher(value);
			while (matcher.find()) {
				references.add(matcher.group().trim());
			}
		}
		return value;
	}

	/**
	 * Desugars the compute chain of one source and records the findings.
	 *
	 * @param source    The source
	 * @param report    The report to add the findings to
	 */
	void desugar(
		final Source source,
		final Map<String, Source> sources,
		final Set<String> textOnly,
		final List<ContentTypeReportEntry> report
	) {
		final String key = source.getKey();
		if (source instanceof TableJoinSource tableJoin && contains(tableJoin.getDefaultRightLine(), TABLE_SEP)) {
			report.add(
				new ContentTypeReportEntry(key, Kind.DEFAULT_RIGHT_LINE, "defaultRightLine=" + tableJoin.getDefaultRightLine())
			);
		}
		if (source instanceof StaticSource staticSource && contains(staticSource.getValue(), TABLE_SEP)) {
			report.add(new ContentTypeReportEntry(key, Kind.STATIC_CSV, "value=" + staticSource.getValue()));
		}

		if (source.getComputes() == null) {
			return;
		}
		// Start again from the chain as written: a previous pass may have inserted conversions
		final List<Compute> computes = source.getComputes().stream().filter(compute -> !isGenerated(compute)).toList();
		// A copy source produces what it copies: the type and the producer at the end of its origin's chain
		final State start = start(source, sources, new HashSet<>(), textOnly);
		ContentType current = start.type();
		TextProducer producer = start.producer();

		final List<Compute> desugared = new ArrayList<>();
		for (int index = 0; index < computes.size(); index++) {
			final Compute compute = computes.get(index);
			final String where = compute.getType() + " #" + index;

			// An awk reads whatever it gets (no input type): nothing is ever inserted before it (#1559)
			final ContentType input = compute.getInputContentType();
			if (input != null && input != current) {
				if (input == ContentType.TEXT) {
					report.add(new ContentTypeReportEntry(key, Kind.TEXT_COMPUTE_ON_TABLE_SOURCE, where));
				} else if (producer == TextProducer.PURE) {
					report.add(new ContentTypeReportEntry(key, Kind.TABLE_COMPUTE_ON_TEXT_SOURCE, where));
				} else if (producer == TextProducer.EXPLICIT) {
					desugared.add(ToTable.generated());
					report.add(
						new ContentTypeReportEntry(key, Kind.TO_TABLE_INSERTED, "before " + where + " (after an explicit toText)")
					);
				} else {
					desugared.add(ToTable.generated());
					report.add(new ContentTypeReportEntry(key, Kind.TO_TABLE_INSERTED, "before " + where));
				}
			}
			desugared.add(compute);

			current = compute.getOutputContentType();
			if (current != ContentType.TEXT) {
				producer = TextProducer.NONE;
			} else if (compute instanceof Awk) {
				producer = TextProducer.LEGACY_DUAL;
			} else if (compute instanceof ToText) {
				producer = TextProducer.EXPLICIT;
			}
		}

		if (current == ContentType.TEXT && producer == TextProducer.LEGACY_DUAL && !textOnly.contains(key)) {
			desugared.add(ToTable.generated());
			report.add(
				new ContentTypeReportEntry(
					key,
					Kind.TO_TABLE_APPENDED,
					"after " +
						(computes.isEmpty()
							? "the source"
							: computes.get(computes.size() - 1).getType() + " #" + (computes.size() - 1))
				)
			);
		}

		source.setComputes(desugared);
	}

	/**
	 * Whether the compute was inserted by this step. A later pass starts from the chain without them.
	 */
	private static boolean isGenerated(final Compute compute) {
		return (compute instanceof ToTable toTable && toTable.isGenerated()) ||
			(compute instanceof ToText toText && toText.isGenerated());
	}

	/**
	 * The content type and the producer of a chain at some point.
	 */
	private record State(ContentType type, TextProducer producer) {}

	/**
	 * What a source produces before its computes run.
	 */
	private static State startOf(final Source source) {
		return startOf(source, Map.of(), new HashSet<>(), null);
	}

	/**
	 * What a source produces before its computes run; a text union produces its members' joined texts, its producer
	 * telling whether the engine built its table too (see {@link #textUnionProducer}); a source run for each entry
	 * of another and concatenated as JSON or custom text produces that text only.
	 *
	 * @param textOnly The sources read as text only, null while they are being determined (see {@link #chainEnd})
	 */
	private static State startOf(
		final Source source,
		final Map<String, Source> sources,
		final Set<Source> visited,
		final Set<String> textOnly
	) {
		if (isConcatenatedAsText(source)) {
			return new State(ContentType.TEXT, TextProducer.PURE);
		}
		if (source instanceof TableUnionSource union) {
			final TextProducer producer = textUnionProducer(union, sources, visited, textOnly);
			if (producer != null) {
				return new State(ContentType.TEXT, producer);
			}
		}
		final ContentType type = source.getOutputContentType();
		return new State(
			type,
			type != ContentType.TEXT
				? TextProducer.NONE
				: isPureTextSource(source) ? TextProducer.PURE : TextProducer.LEGACY_DUAL
		);
	}

	/**
	 * What a source produces before its computes run: a copy produces what its origin's chain produces, unless it is
	 * run for each entry of another source and concatenated as text.
	 */
	private static State start(
		final Source source,
		final Map<String, Source> sources,
		final Set<Source> visited,
		final Set<String> textOnly
	) {
		return source instanceof CopySource copy && !isConcatenatedAsText(copy)
			? effectiveOutput(copy, sources, visited, textOnly)
			: startOf(source, sources, visited, textOnly);
	}

	/**
	 * Whether the source is run for each entry of another source and its results concatenated as JSON or custom text,
	 * which produces text whatever the source produces.
	 */
	private static boolean isConcatenatedAsText(final Source source) {
		return source.isExecuteForEachEntryOf() && source.getEntryConcatMethod() != EntryConcatMethod.LIST;
	}

	/**
	 * What a copy source produces: the result of its origin's chain, as written. An origin that cannot be resolved,
	 * or a cycle of copies, leaves the copy to its declared type (a table).
	 */
	private static State effectiveOutput(
		final CopySource copy,
		final Map<String, Source> sources,
		final Set<Source> visited,
		final Set<String> textOnly
	) {
		final Source origin = copy.getFrom() == null
			? null
			: sources.get(resolveReference(copy.getFrom(), copy.getKey(), sources));
		if (origin == null || !visited.add(copy)) {
			return startOf(copy);
		}
		try {
			return chainEnd(origin, sources, visited, textOnly);
		} finally {
			// A copy read through two members is no cycle: only the copies being resolved count
			visited.remove(copy);
		}
	}

	private static boolean isPureTextSource(final Source source) {
		return source instanceof HttpSource || source instanceof FileSource || source instanceof IpmiSource;
	}

	private static boolean contains(final String value, final String token) {
		return value != null && value.contains(token);
	}

	/**
	 * Applies the action to every source of the connector: beforeAll, the discovery, collect and simple jobs of
	 * every monitor, afterAll.
	 *
	 * @param connector The connector
	 * @param action    The action
	 */
	public static void forEachSource(final Connector connector, final Consumer<Source> action) {
		forEach(connector.getBeforeAll(), action);
		if (connector.getMonitors() != null) {
			for (final MonitorJob job : connector.getMonitors().values()) {
				if (job instanceof StandardMonitorJob standard) {
					if (standard.getDiscovery() != null) {
						forEach(standard.getDiscovery().getSources(), action);
					}
					if (standard.getCollect() != null) {
						forEach(standard.getCollect().getSources(), action);
					}
				} else if (job instanceof SimpleMonitorJob simple && simple.getSimple() != null) {
					forEach(simple.getSimple().getSources(), action);
				}
			}
		}
		forEach(connector.getAfterAll(), action);
		// The tools run their sources through the same compute processor
		if (connector.getTools() != null) {
			connector.getTools().values().forEach(tool -> forEach(tool.getSources(), action));
		}
	}

	private static void forEach(final Map<String, Source> sources, final Consumer<Source> action) {
		if (sources != null) {
			final Collection<Source> values = sources.values();
			values.forEach(action);
		}
	}
}
