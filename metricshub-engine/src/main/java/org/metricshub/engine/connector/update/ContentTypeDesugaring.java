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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.COLUMN_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeReportEntry;
import org.metricshub.engine.connector.model.ContentTypeReportEntry.Kind;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.common.ITranslationTable;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.AbstractConcat;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToText;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;

/**
 * Makes the content-type conversions of every compute chain explicit, and reports what it did and the legacy idioms
 * it found (the worklist of stage 2 of the content-type migration, #1508).
 * <p>
 * The chain of a source is walked while tracking the current content type, starting from the type the source
 * produces ({@link Source#getOutputContentType()}):
 * <ul>
 * <li>an <code>awk</code> compute fed a table gets an implicit <code>toText</code> before it (the compute keeps
 * serializing the table itself, the implicit conversion only types the chain);</li>
 * <li>a table compute fed text by a legacy dual producer (<code>commandLine</code>, the <code>awk</code> source,
 * the <code>awk</code> compute, which all build their table as well) gets an implicit <code>toTable</code>
 * before it (a no-op on the table those producers already built);</li>
 * <li>a chain ending in text from a legacy dual producer gets an implicit <code>toTable</code> appended, so that
 * the mapping reads a table;</li>
 * <li>a table compute fed by a pure text producer (<code>http</code>, <code>file</code>, <code>ipmi</code>)
 * finds an empty table today and keeps doing so: nothing is inserted, the case is reported;</li>
 * <li>a text compute reading the raw text only (<code>json2Csv</code>, <code>xml2Csv</code>) fed by a table
 * producer keeps reading whatever raw text the producer left: nothing is inserted, the case is reported;</li>
 * <li>a <code>copy</code> source produces what its origin's chain produces: its type and producer are resolved from
 * the origin (declared as a table when the origin cannot be resolved).</li>
 * </ul>
 * The implicit computes are flagged ({@code ToText#isImplicit()}, {@code ToTable#isImplicit()}) and leave the
 * results of the existing connectors byte for byte identical. Every inserted compute, the executable toTable
 * included, is flagged as generated ({@code ToTable#isGenerated()}): a later pass removes them first, so the
 * chain and the report are rebuilt from what is written.
 * <p>
 * The findings are stored on the connector ({@code Connector#getContentTypeReport()}).
 */
public class ContentTypeDesugaring extends AbstractConnectorUpdateChain {

	/**
	 * <code>FS = ";"</code> in an AWK script, the sign of a script written for the <code>;</code> serialization.
	 */
	private static final Pattern FS_SEMICOLON = Pattern.compile("\\bFS\\s*=\\s*\"\\s*;\\s*\"");

	/**
	 * A <code>printf</code> call in an AWK script, looked for once the comments and the string literals are gone.
	 */
	private static final Pattern PRINTF = Pattern.compile("\\bprintf\\b");

	/**
	 * The AWK keywords an expression, so a regex literal, can follow.
	 */
	private static final Set<String> REGEX_KEYWORDS = Set.of(
		"print",
		"printf",
		"return",
		"if",
		"while",
		"for",
		"do",
		"else",
		"in",
		"case",
		"getline"
	);

	/**
	 * Who produced the current text of a chain.
	 */
	private enum TextProducer {
		/** No text: the current content is a table. */
		NONE,
		/** A pure text source: <code>http</code>, <code>file</code>, <code>ipmi</code>. */
		PURE,
		/** A legacy producer filling both the text and the table: <code>commandLine</code>, <code>awk</code>. */
		LEGACY_DUAL,
		/** An explicit <code>toText</code> compute. */
		EXPLICIT
	}

	@Override
	void doUpdate(final Connector connector) {
		final List<ContentTypeReportEntry> report = new ArrayList<>();
		// Every source by key, to resolve what a copy source copies
		final Map<String, Source> sources = new LinkedHashMap<>();
		forEachSource(connector, source -> sources.put(source.getKey(), source));
		sources.values().forEach(source -> desugar(connector, source, sources, report));
		connector.setContentTypeReport(report);
	}

	/**
	 * Desugars the compute chain of one source and records the findings.
	 *
	 * @param connector The connector, for its translation tables and embedded files
	 * @param source    The source
	 * @param report    The report to add the findings to
	 */
	void desugar(
		final Connector connector,
		final Source source,
		final Map<String, Source> sources,
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
		// An awk source writing a table: each printf is a single-cell row, as for the compute
		if (
			source instanceof JawkSource jawk &&
				jawk.getOutputContentType() == ContentType.TABLE &&
				PRINTF.matcher(code(scriptContent(connector, jawk.getScript()))).find()
		) {
			report.add(new ContentTypeReportEntry(key, Kind.PRINTF_IN_TABLE_OUTPUT, "the source"));
		}

		if (source.getComputes() == null) {
			return;
		}
		// Start again from the chain as written: a previous pass may have inserted implicit conversions
		final List<Compute> computes = source.getComputes().stream().filter(compute -> !isGenerated(compute)).toList();
		// A copy source produces what it copies: the type and the producer at the end of its origin's chain
		final State start = source instanceof CopySource copy
			? effectiveOutput(copy, sources, new HashSet<>())
			: startOf(source);
		ContentType current = start.type();
		TextProducer producer = start.producer();

		final List<Compute> desugared = new ArrayList<>();
		for (int index = 0; index < computes.size(); index++) {
			final Compute compute = computes.get(index);
			final String where = compute.getType() + " #" + index;
			lint(connector, key, compute, where, report);

			final ContentType input = compute.getInputContentType();
			if (input != current) {
				if (input == ContentType.TEXT) {
					if (compute instanceof Awk awk) {
						desugared.add(ToText.implicit());
						report.add(new ContentTypeReportEntry(key, Kind.TO_TEXT_INSERTED, "before " + where));
						report.add(
							new ContentTypeReportEntry(key, Kind.AWK_ON_TABLE, where + " " + describeScript(connector, awk))
						);
					} else {
						report.add(new ContentTypeReportEntry(key, Kind.TEXT_COMPUTE_ON_TABLE_SOURCE, where));
					}
				} else if (producer == TextProducer.PURE && !(compute instanceof Awk)) {
					report.add(new ContentTypeReportEntry(key, Kind.TABLE_COMPUTE_ON_TEXT_SOURCE, where));
				} else if (producer == TextProducer.PURE || producer == TextProducer.EXPLICIT) {
					// Text only (a pure text source before an awk reading a table, an explicit toText): a real
					// conversion is needed to get a table
					desugared.add(ToTable.generated());
					report.add(
						new ContentTypeReportEntry(
							key,
							Kind.TO_TABLE_INSERTED,
							"before " +
								where +
								(producer == TextProducer.PURE ? " (on a text source)" : " (after an explicit toText)")
						)
					);
				} else {
					desugared.add(ToTable.implicit());
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

		if (current == ContentType.TEXT && producer == TextProducer.LEGACY_DUAL) {
			desugared.add(ToTable.implicit());
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
	 * Records the legacy idioms a compute relies on: a value containing the separator or a line feed.
	 */
	private static void lint(
		final Connector connector,
		final String key,
		final Compute compute,
		final String where,
		final List<ContentTypeReportEntry> report
	) {
		if (compute instanceof Awk awk) {
			if (
				awk.getOutputContentType() == ContentType.TABLE &&
					PRINTF.matcher(code(scriptContent(connector, awk.getScript()))).find()
			) {
				report.add(new ContentTypeReportEntry(key, Kind.PRINTF_IN_TABLE_OUTPUT, where));
			}
		} else if (compute instanceof AbstractConcat concat) {
			// A pure $n reference never round-trips, whatever the referenced cell holds
			if (concat.getValue() != null && !COLUMN_PATTERN.matcher(concat.getValue()).matches()) {
				lintValue(key, where, "value", concat.getValue(), report);
			}
		} else if (compute instanceof Replace replace) {
			lintValue(key, where, "newValue", replace.getNewValue(), report);
		} else if (compute instanceof Translate translate) {
			final Map<String, String> translations = translations(connector, translate.getTranslationTable());
			if (translations != null) {
				translations.forEach((from, to) -> lintValue(key, where, from, to, report));
			}
		}
	}

	private static void lintValue(
		final String key,
		final String where,
		final String name,
		final String value,
		final List<ContentTypeReportEntry> report
	) {
		if (contains(value, TABLE_SEP)) {
			report.add(new ContentTypeReportEntry(key, Kind.SEPARATOR_IN_VALUE, where + " " + name + "=" + quote(value)));
		}
		if (contains(value, NEW_LINE)) {
			report.add(new ContentTypeReportEntry(key, Kind.LINE_FEED_IN_VALUE, where + " " + name + "=" + quote(value)));
		}
	}

	/**
	 * The translations of a translation table, resolved in the connector when referenced.
	 */
	private static Map<String, String> translations(final Connector connector, final ITranslationTable table) {
		if (table instanceof TranslationTable inline) {
			return inline.getTranslations();
		}
		if (table instanceof ReferenceTranslationTable reference && connector.getTranslations() != null) {
			final TranslationTable referenced = connector.getTranslations().get(reference.getTableId());
			return referenced == null ? null : referenced.getTranslations();
		}
		return null;
	}

	/**
	 * Describes the script of an <code>awk</code> compute: its reference and whether it sets <code>FS</code> to
	 * <code>;</code>.
	 */
	private static String describeScript(final Connector connector, final Awk awk) {
		final String script = awk.getScript();
		if (script == null) {
			return "script=null";
		}
		final String fs = FS_SEMICOLON.matcher(scriptContent(connector, script)).find() ? " (sets FS to \";\")" : "";
		return (FILE_PATTERN.matcher(script).find() ? "script=" + script : "inline script") + fs;
	}

	/**
	 * The code of an AWK script without its string literals, regex literals and comments, so that a word inside
	 * them is not taken for a statement.
	 *
	 * @param script The script
	 * @return the script with every string literal and comment replaced by a space
	 */
	static String code(final String script) {
		final StringBuilder code = new StringBuilder(script.length());
		char previous = '\n';
		for (int i = 0; i < script.length(); i++) {
			final char c = script.charAt(i);
			if (c == '"' || (c == '/' && regexCanStart(previous, code))) {
				// A string or a regex literal: skipped up to its unescaped closing delimiter
				int end = i + 1;
				while (end < script.length() && script.charAt(end) != c && script.charAt(end) != '\n') {
					end += script.charAt(end) == '\\' ? 2 : 1;
				}
				i = Math.min(end, script.length() - 1);
				code.append(' ');
				previous = c == '"' ? '"' : ')';
			} else if (c == '#') {
				// A comment up to the end of the line
				while (i + 1 < script.length() && script.charAt(i + 1) != '\n') {
					i++;
				}
				code.append(' ');
			} else {
				code.append(c);
				// A line feed ends a rule or a statement: what follows can start a regex (a pattern-only rule after
				// another one); other blanks are skipped
				if (c == '\n' || !Character.isWhitespace(c)) {
					previous = c;
				}
			}
		}
		return code.toString();
	}

	/**
	 * Whether a slash after the given (last non-blank) character starts a regex literal rather than a division:
	 * at the start of the script or of a rule (after the closing brace of the previous action), after an operator,
	 * a separator or an opening bracket.
	 */
	private static boolean regexCanStart(final char previous, final CharSequence code) {
		// "@" opens a typed regexp (@/re/) in Jawk 7
		if ("\n(,~!{};&|=?:[+-*%<>@".indexOf(previous) >= 0) {
			return true;
		}
		// After a keyword taking an expression, such as "print /re/" or "return /re/"
		int end = code.length();
		while (end > 0 && Character.isWhitespace(code.charAt(end - 1))) {
			end--;
		}
		int start = end;
		while (start > 0 && Character.isLetter(code.charAt(start - 1))) {
			start--;
		}
		return REGEX_KEYWORDS.contains(code.subSequence(start, end).toString());
	}

	/**
	 * The text of the script of an <code>awk</code> compute: the embedded file it references, or the script itself.
	 */
	private static String scriptContent(final Connector connector, final String script) {
		if (script == null) {
			return "";
		}
		final Matcher matcher = FILE_PATTERN.matcher(script);
		if (!matcher.find()) {
			return script;
		}
		if (connector.getEmbeddedFiles() == null) {
			return "";
		}
		try {
			final EmbeddedFile embeddedFile = connector.getEmbeddedFiles().get(Integer.parseInt(matcher.group(1)));
			return embeddedFile == null ? "" : embeddedFile.getContentAsString();
		} catch (NumberFormatException e) {
			return "";
		}
	}

	/**
	 * Whether the compute was inserted by this step: an implicit conversion, or the executable toTable inserted
	 * after an explicit toText. A later pass starts from the chain without them.
	 */
	private static boolean isGenerated(final Compute compute) {
		return (compute instanceof ToTable toTable && toTable.isGenerated()) ||
			(compute instanceof ToText toText && toText.isImplicit());
	}

	/**
	 * The content type and the producer of a chain at some point.
	 */
	private record State(ContentType type, TextProducer producer) {}

	/**
	 * What a source produces before its computes run.
	 */
	private static State startOf(final Source source) {
		final ContentType type = source.getOutputContentType();
		return new State(
			type,
			type != ContentType.TEXT
				? TextProducer.NONE
				: isPureTextSource(source) ? TextProducer.PURE : TextProducer.LEGACY_DUAL
		);
	}

	/**
	 * What a copy source produces: the result of its origin's chain, as written. An origin that cannot be resolved,
	 * or a cycle of copies, leaves the copy to its declared type (a table).
	 */
	private static State effectiveOutput(
		final CopySource copy,
		final Map<String, Source> sources,
		final Set<Source> visited
	) {
		final Source origin = copy.getFrom() == null ? null : sources.get(copy.getFrom().trim());
		if (origin == null || !visited.add(copy)) {
			return startOf(copy);
		}
		State state = origin instanceof CopySource nested ? effectiveOutput(nested, sources, visited) : startOf(origin);
		for (final Compute compute : origin.getComputes()) {
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

	private static boolean isPureTextSource(final Source source) {
		return source instanceof HttpSource || source instanceof FileSource || source instanceof IpmiSource;
	}

	/**
	 * Whether the source is a legacy producer filling both the raw text and the table.
	 *
	 * @param source The source
	 * @return <code>true</code> for <code>commandLine</code> and the <code>awk</code> source
	 */
	public static boolean isLegacyDualSource(final Source source) {
		return source instanceof CommandLineSource || source instanceof JawkSource;
	}

	private static boolean contains(final String value, final String token) {
		return value != null && value.contains(token);
	}

	private static String quote(final String value) {
		return "'" + value.replace("\n", "\\n") + "'";
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
