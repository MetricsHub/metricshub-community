package org.metricshub.agent.process.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Custom stream reader which returns the predefined content
 */
public class CustomInputStream extends InputStream {

	char[] data;
	int cursor;

	public CustomInputStream(String input) {
		data = input.toCharArray();
	}

	@Override
	public int read() throws IOException {
		if (cursor == data.length) {
			return -1;
		}

		return data[cursor++];
	}
}
