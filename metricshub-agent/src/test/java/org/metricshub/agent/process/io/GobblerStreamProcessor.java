package org.metricshub.agent.process.io;

/**
 * Saves received blocks
 */
public class GobblerStreamProcessor implements StreamProcessor {

	private StringBuilder blocks = new StringBuilder();

	@Override
	public void process(String block) {
		blocks.append(block);
	}

	public String getBlocks() {
		return blocks.toString();
	}
}
