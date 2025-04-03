package org.sentrysoftware.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.jawk.ExitException;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.frontend.AwkParser;
import org.metricshub.jawk.frontend.AwkSyntaxTree;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptSource;

/**
 * Utility class for working with AWK scripts.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Awk {

	/**
	 * Generates the "Awk Tuples", i.e. the intermediate Awk code
	 * that can be interpreted afterward.
	 *
	 * @param script Awk script source code to be converted to intermediate code
	 * @return The actual AwkTuples to be interpreted
	 * @throws ParseException when the Awk script is wrong
	 */
	public static AwkTuples getIntermediateCode(final String script) throws ParseException {
		// All scripts need to be prefixed with an extra statement that sets the Record Separator (RS)
		// to the "normal" end-of-line (\n), because Jawk uses line.separator System property, which
		// is \r\n on Windows, thus preventing it from splitting lines properly.
		final ScriptSource awkHeader = new ScriptSource("Header", new StringReader("BEGIN { ORS = RS = \"\\n\"; }"), false);
		final ScriptSource awkSource = new ScriptSource("Body", new StringReader(script), false);
		final List<ScriptSource> sourceList = new ArrayList<>();
		sourceList.add(awkHeader);
		sourceList.add(awkSource);

		// Awk Setup
		final AwkSettings settings = new AwkSettings();
		settings.setCatchIllegalFormatExceptions(false);

		// Parse the Awk script
		final AwkTuples tuples = new AwkTuples();
		final AwkParser parser = new AwkParser(false, false, Collections.emptyMap());
		final AwkSyntaxTree ast;
		try {
			ast = parser.parse(sourceList);

			// Produce the intermediate code
			if (ast != null) {
				// 1st pass to tie actual parameters to back-referenced formal parameters
				ast.semanticAnalysis();

				// 2nd pass to tie actual parameters to forward-referenced formal parameters
				ast.semanticAnalysis();
				if (ast.populateTuples(tuples) != 0) {
					throw new RuntimeException("Syntax problem with the Awk script");
				}
				tuples.postProcess();
				parser.populateGlobalVariableNameToOffsetMappings(tuples);
			}
		} catch (IOException e) {
			throw new ParseException(e.getMessage(), 0);
		}

		return tuples;
	}

	/**
	 * Interprets the specified Awk intermediate code against an input string. If something goes wrong with the interpretation of the code, a {@link RuntimeException} is thrown.
	 *
	 * @param input            The text input to be parsed by the Awk script
	 * @param intermediateCode The Awk intermediate code
	 * @param charset          A named mapping between sequences of sixteen-bit Unicode code units
	 *                         and sequences of bytes used to set the input as bytes in the {@link AwkSettings}
	 * @return The result of the Awk script (i.e. what has been printed by the script)
	 */
	public static String interpret(final String input, final AwkTuples intermediateCode, final Charset charset) {
		// Configure the InputStream
		final AwkSettings settings = new AwkSettings();

		settings.setInput(new ByteArrayInputStream(input.getBytes(charset)));

		// Create the OutputStream
		final ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		final UniformPrintStream resultStream = new UniformPrintStream(resultBytesStream);
		settings.setOutputStream(resultStream);

		// We don't want to see error messages because of formatting issues
		settings.setCatchIllegalFormatExceptions(true);

		// We force \n as the Record Separator (RS) because even if running on Windows
		// we're passing Java strings, where end of lines are simple \n
		settings.setDefaultRS("\n");

		// Interpret
		final AVM avm = new AVM(settings, Collections.emptyMap());
		try {
			avm.interpret(intermediateCode);
		} catch (ExitException e) {
			// ExitException code 0 means exit OK
			if (e.getCode() != 0) {
				throw new RuntimeException(e.getMessage());
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

		// Result
		return resultBytesStream.toString();
	}

	/**
	 * Interprets the specified Awk intermediate code against an input string. If something goes wrong with the interpretation of the code, a {@link RuntimeException} is thrown.
	 *
	 * @param input The text input to be parsed by the Awk script
	 * @param intermediateCode The Awk intermediate code
	 * @return The result of the Awk script (i.e. what has been printed by the script)
	 */
	public static String interpret(final String input, final AwkTuples intermediateCode) {
		return interpret(input, intermediateCode, StandardCharsets.UTF_8);
	}

	/**
	 * Interprets the specified Awk script against an input string. If something goes wrong with the interpretation of the code, a {@link RuntimeException} is thrown.
	 *
	 * @param input The text input to be parsed by the Awk script
	 * @param script The Awk script to interpret
	 * @return The result of the Awk script (i.e. what has been printed by the script)
	 * @throws ParseException when the Awk script is wrong
	 */
	public static String interpret(String input, String script) throws ParseException {
		// Get the intermediate code
		AwkTuples intermediateCode = getIntermediateCode(script);

		// Interpret
		return interpret(input, intermediateCode);
	}
}
