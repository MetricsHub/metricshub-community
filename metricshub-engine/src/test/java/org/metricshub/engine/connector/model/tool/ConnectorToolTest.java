package org.metricshub.engine.connector.model.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ArrayTranslate;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Encode;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Substring;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;

class ConnectorToolTest {

	private static final Set<DeviceKind> LINUX = Set.of(DeviceKind.LINUX, DeviceKind.AIX, DeviceKind.TRU64);
	private static final Set<DeviceKind> WINDOWS = Set.of(DeviceKind.WINDOWS);

	private static final Map<String, ToolParameter> PARAMETERS = Map.of(
		"service",
		new ToolParameter(null, "A systemd unit", true, null),
		"lines",
		new ToolParameter("integer", "How many lines", false, "100"),
		"unit",
		new ToolParameter(null, "A constrained unit", true, null, "[a-z$()]{5}", null)
	);

	private static ConnectorTool tool(final Source source) {
		return ConnectorTool.builder()
			.sources(Map.of("logs", source))
			.parameters(PARAMETERS)
			.result(new ToolResult("${source::logs}", null))
			.build();
	}

	private static ToolDefinition assertAccepted(final Source source, final Set<DeviceKind> appliesTo) {
		return assertDoesNotThrow(() -> tool(source).toDefinition("logs", appliesTo));
	}

	private static String assertRefused(final Source source, final Set<DeviceKind> appliesTo) {
		return assertThrows(IllegalArgumentException.class, () ->
			tool(source).toDefinition("logs", appliesTo)
		).getMessage();
	}

	private static CommandLineSource commandLine(final String commandLine) {
		return CommandLineSource.builder().commandLine(commandLine).build();
	}

	@Test
	void testEscapedOrConstrainedParametersAreAccepted() {
		// esc(bash) is assigned to a variable, expanded as one word in or out of quotes
		assertAccepted(
			commandLine(
				"journalctl -n ${param::lines} -u ${esc(bash)::param::service} ${param::unit} -g \"${esc(bash)::param::service}\""
			),
			LINUX
		);
		assertAccepted(
			SqlSource.builder().query("SELECT * FROM t WHERE name LIKE '%${esc(sql)::param::service}%'").build(),
			WINDOWS
		);
		assertAccepted(
			WmiSource.builder().query("SELECT Name FROM Win32_Service WHERE Name=\"${esc(wql)::param::service}\"").build(),
			WINDOWS
		);
		assertAccepted(HttpSource.builder().body("{\"name\": \"${esc(json)::param::service}\"}").build(), LINUX);
		assertAccepted(
			HttpSource.builder().path("/api/units/${esc(url)::param::service}").header("X-Limit: ${param::lines}").build(),
			LINUX
		);
		// Numbers and enums go anywhere as they are
		assertAccepted(SqlSource.builder().query("SELECT * FROM t LIMIT ${param::lines}").build(), LINUX);
	}

	@Test
	void testAPatternedStringIsRefusedUnescapedInAQueryOrARequest() {
		// 1-- passes [a-z0-9-]+ and comments out the rest of the query
		assertRefused(
			SqlSource.builder().query("SELECT * FROM t WHERE id = ${param::unit} AND tenant = 42").build(),
			LINUX
		);
		assertRefused(
			WmiSource.builder().query("SELECT * FROM Win32_Service WHERE Name = ${param::unit}").build(),
			WINDOWS
		);
		assertRefused(HttpSource.builder().path("/api/${param::unit}").build(), LINUX);
	}

	@Test
	void testShellEscapeWhoseQuotingCannotBeToldIsRefused() {
		final String message = assertRefused(
			commandLine("echo ok # it's\njournalctl -u ${esc(bash)::param::service}"),
			LINUX
		);
		assertTrue(message.contains("mere argument"), message);
		assertRefused(commandLine("cat <<EOF\n${esc(bash)::param::service}\nEOF"), LINUX);
	}

	@Test
	void testShellValueThatWouldNotBeAMereArgumentIsRefused() {
		// Commands that parse their argument again, the name of a command, a redirection
		for (final String commandLine : List.of(
			"eval ${esc(bash)::param::service}",
			"sh -c ${esc(bash)::param::service}",
			"%{SUDO:/bin/sh}/bin/sh -c \"${esc(bash)::param::service}\"",
			"sudo bash -c '${esc(bash)::param::service}'",
			"${esc(bash)::param::service} --version",
			"ls; ${esc(bash)::param::service}",
			"cat < ${esc(bash)::param::service}",
			"$(which ls) ${esc(bash)::param::service}"
		)) {
			assertRefused(commandLine(commandLine), LINUX);
		}
		// A string constrained by a pattern only is no safer there: reboot needs no metacharacter
		assertRefused(commandLine("eval ${param::unit}"), LINUX);
		assertRefused(commandLine("${param::unit} --help"), LINUX);
		// An argument, even after a substitution evaluating its own
		assertAccepted(commandLine("echo $(eval date) ${esc(bash)::param::service} | sort"), LINUX);
		assertAccepted(commandLine("if true; then journalctl -u ${esc(bash)::param::service}; fi"), LINUX);
	}

	@Test
	void testAttributeReferencesAreParameters() {
		// A tool's arguments are its attributes: ${attribute::x} is checked as ${param::x}
		assertRefused(commandLine("echo ${attribute::service}"), LINUX);
		assertRefused(commandLine("echo ${attribute::nope}"), LINUX);
		assertAccepted(commandLine("echo ${esc(bash)::attribute::service}"), LINUX);
	}

	@Test
	void testFindExecOperandIsACommand() {
		assertRefused(commandLine("find /tmp -exec ${esc(bash)::param::service} {} +"), LINUX);
		assertAccepted(commandLine("find /var/log -name ${esc(bash)::param::service}"), LINUX);
	}

	@Test
	void testLineFiltersAreRegexesButNotSeparators() {
		assertAccepted(
			CommandLineSource.builder().commandLine("ls").keep("^${esc(regex)::param::service}$").build(),
			LINUX
		);
		// Inside a character class, an escaped a-z is still a range
		assertRefused(CommandLineSource.builder().commandLine("ls").keep("[${esc(regex)::param::service}]").build(), LINUX);
		assertRefused(
			CommandLineSource.builder().commandLine("ls").separators("${esc(regex)::param::service}").build(),
			LINUX
		);
	}

	@Test
	void testEscapeAfterABackslashOrInXmlMarkupIsRefused() {
		// The backslash would escape the backslash the escape puts before a quote
		assertRefused(HttpSource.builder().body("{\"x\": \"\\${esc(json)::param::service}\"}").build(), LINUX);
		assertAccepted(HttpSource.builder().body("{\"x\": \"\\\\${esc(json)::param::service}\"}").build(), LINUX);
		// XML escaping protects character data and quoted attribute values, not names
		assertRefused(HttpSource.builder().body("<${esc(xml)::param::service}>v</x>").build(), LINUX);
		assertRefused(HttpSource.builder().body("<!-- ${esc(xml)::param::service} --><a/>").build(), LINUX);
		assertAccepted(
			HttpSource.builder()
				.body("<?xml version=\"1.0\"?><a b=\"${esc(xml)::param::service}\">${esc(xml)::param::service}</a>")
				.build(),
			LINUX
		);
	}

	@Test
	void testAFieldWithoutARuleTakesOnlyEnumsAndNumbers() {
		// No escape covers the * of a JMX object name pattern
		assertRefused(
			JmxSource.builder().objectName("domain:type=${esc(json)::param::service}").attributes(List.of("Name")).build(),
			LINUX
		);
		assertRefused(StaticSource.builder().value("${param::unit}").build(), LINUX);
		assertAccepted(StaticSource.builder().value("${param::lines}").build(), LINUX);
	}

	@Test
	void testAStringInAWindowsCommandLineIsRefused() {
		// Nothing tells whether a word is a command in cmd.exe: only enums and numbers
		assertRefused(commandLine("sc query ${param::unit}"), WINDOWS);
		assertAccepted(commandLine("ping -n ${param::lines} localhost"), WINDOWS);
	}

	@Test
	void testParameterInAFieldNeverSubstitutedIsRefused() {
		assertRefused(
			InternalDbQuerySource.builder().tables(List.of()).query("SELECT * FROM t WHERE id = ${param::lines}").build(),
			LINUX
		);
		assertRefused(TableUnionSource.builder().tables(List.of("${source::${param::unit}}")).build(), LINUX);
	}

	@Test
	void testShellEscapeInArithmeticIsRefused() {
		// bash evaluates x[$(cmd)] in arithmetic, quoted or not
		assertRefused(commandLine("echo $(( ${esc(bash)::param::service} + 1 ))"), LINUX);
		assertRefused(commandLine("[[ ${esc(bash)::param::service} -eq 1 ]] && echo one"), LINUX);
		assertRefused(commandLine("let n=${esc(bash)::param::service}"), LINUX);
		// A number goes there as is
		assertAccepted(commandLine("echo $(( ${param::lines} + 1 ))"), LINUX);
	}

	@Test
	void testAFilePathTakesOnlyEnumsAndNumbers() {
		final Map<String, ToolParameter> parameters = Map.of(
			"log",
			new ToolParameter(null, "A log", true, null, null, List.of("syslog", "messages")),
			"unit",
			new ToolParameter(null, "A unit", true, null, "[./a-z]+", null)
		);
		final Function<String, ConnectorTool> fileTool = path ->
			ConnectorTool.builder()
				.sources(Map.of("file", FileSource.builder().paths(Set.of(path)).build()))
				.parameters(parameters)
				.result(new ToolResult("${source::file}", null))
				.build();

		assertDoesNotThrow(() -> fileTool.apply("/var/log/${param::log}").toDefinition("tail", LINUX));
		// ../../etc/passwd passes [./a-z]+, and no escape keeps it in the directory
		assertThrows(IllegalArgumentException.class, () ->
			fileTool.apply("/var/log/${param::unit}").toDefinition("tail", LINUX)
		);
		assertThrows(IllegalArgumentException.class, () ->
			fileTool.apply("/var/log/${esc(url)::param::unit}").toDefinition("tail", LINUX)
		);
	}

	@Test
	void testParameterInAnAuthenticationTokenIsRefused() {
		// The token names a source, looked up before the parameters are replaced
		assertRefused(HttpSource.builder().authenticationToken("${source::${param::lines}}").build(), LINUX);
	}

	@Test
	void testAnHttpFieldTakesItsOwnEscapes() {
		// No escape fits a header: xml and regex keep line breaks, url changes the value (not URL-decoded there)
		assertRefused(HttpSource.builder().header("X-Unit: ${esc(xml)::param::service}").build(), LINUX);
		assertRefused(HttpSource.builder().header("X-Unit: ${esc(regex)::param::service}").build(), LINUX);
		assertRefused(HttpSource.builder().header("X-Unit: ${esc(url)::param::service}").build(), LINUX);
		assertRefused(HttpSource.builder().header("X-Unit: ${param::unit}").build(), LINUX);
		assertRefused(HttpSource.builder().path("/api/${esc(json)::param::service}").build(), LINUX);
	}

	@Test
	void testAStringInsertedUnescapedRefusesTheMetacharacters() {
		final ToolDefinition definition = assertAccepted(commandLine("journalctl -u ${param::unit}"), LINUX);

		// The pattern lets $(id) through, the definition does not
		final ToolParameter unit = definition.parameters().get("unit");
		assertThrows(IllegalArgumentException.class, () -> unit.validate("unit", "$(id)"));
		assertDoesNotThrow(() -> unit.validate("unit", "sshda"));
		// Not inserted unescaped: unchanged
		assertEquals(PARAMETERS.get("service"), definition.parameters().get("service"));
	}

	@Test
	void testUnconstrainedParameterInsertedUnescapedIsRefused() {
		final String message = assertRefused(commandLine("journalctl -u ${param::service}"), LINUX);
		assertTrue(message.contains("${param::service}"), message);
	}

	@Test
	void testUnconstrainedParameterInAnAwkScriptIsRefused() {
		final CommandLineSource source = commandLine("journalctl -n 10");
		source.setComputes(List.of(Awk.builder().script("/${param::service}/ { print }").build()));
		assertRefused(source, LINUX);

		// Its variables are data, passed as such, but must name declared parameters
		source.setComputes(
			List.of(Awk.builder().script("{ print limit }").variables(Map.of("limit", "${param::service}")).build())
		);
		assertAccepted(source, LINUX);
		source.setComputes(
			List.of(Awk.builder().script("{ print limit }").variables(Map.of("limit", "${param::limt}")).build())
		);
		assertRefused(source, LINUX);

		// No escape is safe in an awk script
		source.setComputes(List.of(Awk.builder().script("{ print \"${esc(json)::param::service}\" }").build()));
		assertRefused(source, LINUX);

		// Nor a pattern: / + - = are awk operators. A number goes there as is
		source.setComputes(List.of(Awk.builder().script("NR > ${param::unit} { print }").build()));
		assertRefused(source, LINUX);
		source.setComputes(List.of(Awk.builder().script("NR > ${param::lines} { print }").build()));
		assertAccepted(source, LINUX);
	}

	@Test
	void testAnEscapedShellArgumentRefusesALeadingDash() {
		final ToolDefinition definition = assertAccepted(
			commandLine("find /srv -name ${esc(bash)::param::service}"),
			LINUX
		);

		// One quoted word, but find would read -delete as an action
		final ToolParameter service = definition.parameters().get("service");
		assertThrows(IllegalArgumentException.class, () -> service.validate("service", "-delete"));
		assertDoesNotThrow(() -> service.validate("service", "a b; $(c)"));
	}

	@Test
	void testRestrictionsOfEachUseAddUp() {
		final CommandLineSource source = commandLine("find /srv -name ${esc(bash)::param::service}");
		source.setComputes(List.of(Append.builder().column(1).value("-${param::service}").build()));
		final ToolParameter service = assertAccepted(source, LINUX).parameters().get("service");

		// Data in an append, but ; would add a column; and a shell argument, where - would be an option
		assertThrows(IllegalArgumentException.class, () -> service.validate("service", "a;b"));
		assertThrows(IllegalArgumentException.class, () -> service.validate("service", "$2"));
		assertThrows(IllegalArgumentException.class, () -> service.validate("service", "-delete"));
		assertDoesNotThrow(() -> service.validate("service", "sshd service"));
	}

	@Test
	void testUrlAuthorityTakesOnlyEnums() {
		final ConnectorTool tool = ConnectorTool.builder()
			.sources(
				Map.of("status", HttpSource.builder().url("https://${param::host}/status/${esc(url)::param::service}").build())
			)
			.parameters(
				Map.of(
					"host",
					new ToolParameter(null, "A host", true, null, null, List.of("ilo1.local", "ilo2.local")),
					"service",
					new ToolParameter(null, "A service", true, null)
				)
			)
			.result(new ToolResult("${source::status}", null))
			.build();
		assertDoesNotThrow(() -> tool.toDefinition("status", LINUX));

		// The assistant would choose where the request, credentials included, goes
		assertRefused(HttpSource.builder().url("http://${esc(url)::param::service}/status").build(), LINUX);
		assertRefused(HttpSource.builder().url("http://host:${param::lines}/status").build(), LINUX);
	}

	@Test
	void testParameterInTheSourceToRunForEachEntryOfIsRefused() {
		// Looked up before the parameters are replaced
		final CommandLineSource source = CommandLineSource.builder()
			.commandLine("ls")
			.executeForEachEntryOf(ExecuteForEachEntryOf.builder().source("${source::${param::unit}}").build())
			.build();
		assertRefused(source, LINUX);
	}

	@Test
	void testDdAndSedOperandsAreRefused() {
		// of=/dev/sda needs no -, a sed script runs e or writes w
		assertRefused(commandLine("dd if=/dev/zero ${esc(bash)::param::service}"), LINUX);
		assertRefused(commandLine("sed ${esc(bash)::param::service} file"), LINUX);
	}

	@Test
	void testParameterInAnEncodingIsRefused() {
		final CommandLineSource source = commandLine("cat f");
		source.setComputes(List.of(Encode.builder().column(1).encoding("${param::service}").build()));
		assertRefused(source, LINUX);
	}

	@Test
	void testComputeFieldsWithoutARuleTakeOnlyEnumsAndNumbers() {
		final CommandLineSource source = commandLine("journalctl -n 10");
		// The separator of arrayTranslate is a regular expression
		source.setComputes(
			List.of(
				ArrayTranslate.builder()
					.column(1)
					.translationTable(new TranslationTable())
					.arraySeparator("${param::service}")
					.build()
			)
		);
		assertRefused(source, LINUX);
		// Values appended or replaced are data
		source.setComputes(
			List.of(
				Append.builder().column(1).value("-${param::service}").build(),
				Replace.builder().column(1).existingValue("${param::service}").newValue("x").build()
			)
		);
		assertAccepted(source, LINUX);
		// A number goes anywhere
		source.setComputes(List.of(Substring.builder().column(1).start("1").length("${param::lines}").build()));
		assertAccepted(source, LINUX);
	}

	@Test
	void testUnconstrainedParameterInADataComputeIsAccepted() {
		final CommandLineSource source = commandLine("journalctl -n 10");
		source.setComputes(List.of(KeepOnlyMatchingLines.builder().column(1).valueList("${param::service}").build()));
		assertAccepted(source, LINUX);

		// Its regular expression is not data: a value there could backtrack without end
		source.setComputes(List.of(KeepOnlyMatchingLines.builder().column(1).regExp("${param::service}").build()));
		assertRefused(source, LINUX);
		source.setComputes(List.of(KeepOnlyMatchingLines.builder().column(1).regExp("${param::unit}").build()));
		assertRefused(source, LINUX);
		source.setComputes(
			List.of(KeepOnlyMatchingLines.builder().column(1).regExp("${esc(regex)::param::service}").build())
		);
		assertAccepted(source, LINUX);
	}

	@Test
	void testOptionalParameterWithoutDefaultOrMalformedEscapeIsRefused() {
		final ConnectorTool tool = ConnectorTool.builder()
			.sources(Map.of("logs", commandLine("journalctl -u ${esc(bash)::param::since}")))
			.parameters(Map.of("since", new ToolParameter(null, "Optional, no default", false, null)))
			.result(new ToolResult("${source::logs}", null))
			.build();
		final String message = assertThrows(IllegalArgumentException.class, () ->
			tool.toDefinition("logs", LINUX)
		).getMessage();
		assertTrue(message.contains("default"), message);

		assertRefused(commandLine("journalctl -u ${esc(power-shell)::param::service}"), LINUX);
		assertRefused(HttpSource.builder().body("{\"x\": \"${esc(json_value)::param::service}\"}").build(), LINUX);
	}

	@Test
	void testEscapesOfACommandLineDependOnItsShell() {
		// cmd and PowerShell escapes protect nothing reliably: refused even on Windows
		assertRefused(commandLine("sc query ${esc(cmd)::param::service}"), WINDOWS);
		assertRefused(commandLine("powershell -Command Get-Service '${esc(powershell)::param::service}'"), WINDOWS);
		// A POSIX escape in a command line that may run in cmd.exe
		assertRefused(commandLine("echo ${esc(bash)::param::service}"), WINDOWS);
		assertRefused(commandLine("echo ${esc(bash)::param::service}"), Set.of(DeviceKind.LINUX, DeviceKind.WINDOWS));
		assertRefused(commandLine("echo ${esc(bash)::param::service}"), Set.of());
		// cmd in a POSIX shell
		assertRefused(commandLine("echo ${esc(cmd)::param::service}"), LINUX);
	}

	@Test
	void testEscapeOutsideItsStringIsRefused() {
		assertRefused(SqlSource.builder().query("SELECT * FROM t WHERE id = ${esc(sql)::param::service}").build(), LINUX);
		assertRefused(HttpSource.builder().body("{\"name\": ${esc(json)::param::service}}").build(), LINUX);
		// A comment or a backslash may hide a quote from one SQL dialect and not another
		assertRefused(
			SqlSource.builder().query("SELECT a -- it's\nFROM t WHERE n = '${esc(sql)::param::service}'").build(),
			LINUX
		);
		assertRefused(
			SqlSource.builder().query("SELECT a # 'x\nFROM t WHERE n = '${esc(sql)::param::service}'").build(),
			LINUX
		);
		assertRefused(
			SqlSource.builder().query("SELECT 'a\\' FROM t WHERE n = '${esc(sql)::param::service}'").build(),
			LINUX
		);
	}

	@Test
	void testWqlEscapeInALikePatternIsRefused() {
		// WQL does not read \ as an escape in a LIKE pattern: O\'Brien would close the string
		assertRefused(
			WmiSource.builder().query("SELECT * FROM Win32_Service WHERE Name LIKE '%${esc(wql)::param::service}%'").build(),
			WINDOWS
		);
		assertAccepted(
			WmiSource.builder()
				.query("SELECT * FROM Win32_Service WHERE Caption LIKE '%x%' AND Name = '${esc(wql)::param::service}'")
				.build(),
			WINDOWS
		);
	}

	@Test
	void testEscapeOfAnotherLanguageIsRefused() {
		assertRefused(commandLine("echo \"${esc(json)::param::service}\""), LINUX);
		assertRefused(
			SqlSource.builder().query("SELECT * FROM t WHERE name = '${esc(bash)::param::service}'").build(),
			LINUX
		);
		assertRefused(
			WmiSource.builder().query("SELECT * FROM Win32_Service WHERE Name = '${esc(sql)::param::service}'").build(),
			WINDOWS
		);
		assertRefused(HttpSource.builder().body("x=${esc(bash)::param::service}").build(), LINUX);
	}

	@Test
	void testUnknownEscapeTypeOrParameterIsRefused() {
		final String message = assertRefused(commandLine("journalctl -n ${esc(bsh)::param::lines}"), LINUX);
		assertTrue(message.contains("bsh"), message);
		assertRefused(commandLine("journalctl -n ${param::line}"), LINUX);
		// Even in a compute that is data
		final CommandLineSource source = commandLine("journalctl -n 10");
		source.setComputes(List.of(KeepOnlyMatchingLines.builder().column(1).regExp("${param::servce}").build()));
		assertRefused(source, LINUX);
	}
}
