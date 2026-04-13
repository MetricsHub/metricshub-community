package org.metricshub.it.job;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ITJobUtils {

	private ITJobUtils() {}

	/**
	 * Initialize the InputStream on the actual IT resource file path
	 * @param itResourcePath
	 * @return {@link InputStream}
	 * @throws IOException
	 */
	public static InputStream getItResourceAsInputStream(String itResourcePath) throws IOException {
		return new FileInputStream(new File(getItResourcePath(itResourcePath)));
	}

	/**
	 * @param itResourcePath
	 * @return The path under <em>src/it/resources</em>
	 */
	public static String getItResourcePath(final String itResourcePath) {
		return "src/it/resources/" + itResourcePath;
	}
}
