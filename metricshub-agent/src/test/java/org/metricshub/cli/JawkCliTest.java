package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class JawkCliTest {

	@Test
	void testJawkCliMain() throws Exception {
		final CustomOutputStream customOutputStream = new CustomOutputStream();
		final PrintStream out = new PrintStream(customOutputStream);
		// Given a script that prints the second column
		final String awkScript = "{ print $2 }";

		// And an input stream
		final String inputLine = "hello world";
		final InputStream in = new ByteArrayInputStream(inputLine.getBytes());

		// When executing the Jawk CLI
		JawkCli.main(new String[] { awkScript }, in, out, System.err);

		// Then the output should be "world" (second field in the input)
		assertEquals("world", customOutputStream.toString().trim());
	}

	/**
	 * CustomOutputStream is a simple OutputStream that stores the written data in a StringBuilder.
	 */
	static class CustomOutputStream extends OutputStream {

		StringBuilder stringBuilder;

		public CustomOutputStream() {
			stringBuilder = new StringBuilder();
		}

		@Override
		public void write(int i) {
			stringBuilder.append((char) i);
		}

		@Override
		public String toString() {
			return stringBuilder.toString();
		}
	}
}
