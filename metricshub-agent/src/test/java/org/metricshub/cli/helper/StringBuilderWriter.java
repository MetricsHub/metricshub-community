package org.metricshub.cli.helper;

import java.io.IOException;
import java.io.Writer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class StringBuilderWriter extends Writer {

	@NonNull
	private StringBuilder builder;

	@Override
	public void write(char[] buffer, int start, int end) throws IOException {
		builder.append(buffer, start, end);
	}

	@Override
	public void flush() throws IOException {
		// NOOP
	}

	@Override
	public void close() throws IOException {
		// NOOP
	}
}
