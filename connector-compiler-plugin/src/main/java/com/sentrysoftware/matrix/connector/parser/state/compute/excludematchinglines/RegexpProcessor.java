package com.sentrysoftware.matrix.connector.parser.state.compute.excludematchinglines;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ExcludeMatchingLines;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexpProcessor extends ExcludeMatchingLinesProcessor {

	private static final Pattern REGEXP_KEY_PATTERN = Pattern.compile(
			"^\\s*((.*)\\.(discovery|collect)\\.source\\(([1-9]\\d*)\\))\\.compute\\(([1-9]\\d*)\\)\\.regexp\\s*$",
			Pattern.CASE_INSENSITIVE);

	@Override
	public Matcher getMatcher(String key) {
		return REGEXP_KEY_PATTERN.matcher(key);
	}

	@Override
	public void parse(final String key, final String value, final Connector connector) {

		super.parse(key, value, connector);

		((ExcludeMatchingLines) getCompute(key, connector)).setRegExp(value);
	}
}
