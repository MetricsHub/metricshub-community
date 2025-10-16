package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AwkTest {

	@Test
	void UnitTestPresence() throws URISyntaxException {
		// Do we have our unit tests?
		assertTrue(listUnitTests().size() > 0);
	}

	@ParameterizedTest
	@MethodSource("listUnitTests")
	void testAwkAgainstNAwk(String testName) throws URISyntaxException, RuntimeException, ParseException, AwkException {
		// Get the script, the input to process, and the expected result
		String script = getResourceAsString("/awk/scripts/" + testName + ".awk");
		String input = getResourceAsString("/awk/inputs/" + testName + ".INP.txt");
		String expectedResult = getResourceAsString("/awk/expected-results/" + testName + ".EXP.txt");

		// Do we have everything?
		assertNotNull(script, "Script " + testName + " is null");
		assertNotNull(input, "Input " + testName + " is null");
		assertNotNull(expectedResult, "Expected Result " + testName + " is null");

		// Let's do the actual test
		String result = AwkExecutor.executeAwk(script, input);
		assertEquals(expectedResult, result, "Results don't match for " + testName);
		AwkExecutor.resetCache();
	}

	/**
	 * Lists the Awk unit tests in the `scripts` resource directory
	 * @return The list of unit tests
	 * @throws URISyntaxException when something is wrong with the URL
	 */
	public static ArrayList<String> listUnitTests() throws URISyntaxException {
		ArrayList<String> unitTestList = new ArrayList<String>();

		// Get the scripts resource directory
		URL scriptsUrl = AwkTest.class.getResource("/awk/scripts");
		if (scriptsUrl != null) {
			File scriptsDir = new File(scriptsUrl.toURI());

			if (scriptsDir.isDirectory()) {
				// List the Awk script files in this directory
				for (File scriptFile : scriptsDir.listFiles()) {
					String scriptName = scriptFile.getName();
					if (scriptName.toLowerCase().endsWith(".awk")) {
						unitTestList.add(scriptName.substring(0, scriptName.length() - 4));
					}
				}
			}
		}

		return unitTestList;
	}

	/**
	 * Reads the specified resource file and returns its content as a String
	 *
	 * @param path Path to the resource file
	 * @return The content of the resource file as a String
	 */
	private static String getResourceAsString(String path) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(AwkTest.class.getResourceAsStream(path)));
		StringBuilder builder = new StringBuilder();
		String l;
		try {
			while ((l = reader.readLine()) != null) {
				builder.append(l).append('\n');
			}
		} catch (IOException e) {
			return null;
		}

		return builder.toString();
	}
}
