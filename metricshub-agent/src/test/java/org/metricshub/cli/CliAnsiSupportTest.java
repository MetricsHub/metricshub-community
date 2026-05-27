package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

class CliAnsiSupportTest {

	private static final String DISABLE_ANSI_PROPERTY = "metricshub.cli.disable.ansi";

	@AfterEach
	void cleanup() {
		System.clearProperty(DISABLE_ANSI_PROPERTY);
		CliAnsiSupport.uninstall();
	}

	@Test
	void testAnsiIsStrippedWhenFallbackIsEnabled() {
		System.setProperty(DISABLE_ANSI_PROPERTY, "true");

		final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
		final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
		final CommandLine cli = new CommandLine(new ColoredOutputCommand());
		cli.setOut(new PrintWriter(outBuffer, true, StandardCharsets.UTF_8));
		cli.setErr(new PrintWriter(errBuffer, true, StandardCharsets.UTF_8));

		CliAnsiSupport.install(cli);
		final int exitCode = cli.execute();

		assertEquals(CommandLine.ExitCode.OK, exitCode);
		final String out = outBuffer.toString(StandardCharsets.UTF_8);
		final String err = errBuffer.toString(StandardCharsets.UTF_8);
		assertTrue(out.contains("nassim"));
		assertTrue(err.contains("read"));
		assertFalse(out.contains("\u001B["));
		assertFalse(err.contains("\u001B["));
	}

	@Command(name = "colored-output")
	static class ColoredOutputCommand implements Runnable {

		@Spec
		CommandSpec spec;

		@Override
		public void run() {
			spec.commandLine().getOut().println(Ansi.ansi().fgGreen().a("nassim").reset());
			spec.commandLine().getErr().println(Ansi.ansi().fgRed().a("read").reset());
		}
	}
}
