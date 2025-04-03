package org.metricshub.it.job;

import java.io.IOException;
import java.nio.file.Path;
import org.sentrysoftware.metricshub.engine.strategy.IStrategy;

public interface ITJob {
	/**
	 * Start the server and load the record data from the given paths
	 *
	 * @param recordDataPaths Record data located under src/it/resources, the path are expected without src/it/resources
	 * @return The actual {@link ITJob}
	 * @throws Exception
	 */
	ITJob withServerRecordData(String... recordDataPaths) throws Exception;

	/**
	 * Run the engine strategies
	 *
	 * @return The actual {@link ITJob}
	 */
	ITJob executeStrategies(IStrategy... strategies);
	/**
	 * Verify the expected result located under the given expectedPath
	 *
	 * @param expectedPath Expected result path located under src/it/resources, the path are expected without src/it/resources
	 * @return actual {@link ITJob}
	 * @throws Exception
	 */
	ITJob verifyExpected(String expectedPath) throws Exception;

	/**
	 * Stop the server
	 */
	void stopServer();

	/**
	 * @return <code>true</code> if the server is started otherwise <code>false</code>
	 */
	boolean isServerStarted();

	/**
	 * Save the hostMonitoring JSON into a file.
	 *
	 * @param path path of the saving file.
	 * @return actual {@link ITJob}
	 * @throws IOException
	 */
	ITJob saveTelemetryManagerJson(final Path path) throws IOException;
}
