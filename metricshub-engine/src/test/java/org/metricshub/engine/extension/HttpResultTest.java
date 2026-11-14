package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.ResultContent;

class HttpResultTest {

	private static final HttpResult RESULT = new HttpResult(
		401,
		"Content-Type: application/json\nX-Request: 12\n",
		"{\"error\":\n\n\"bad token\"}"
	);

	@Test
	void testToText() {
		assertEquals("{\"error\":\n\n\"bad token\"}", RESULT.toText(ResultContent.BODY));
		assertEquals("Content-Type: application/json\nX-Request: 12\n", RESULT.toText(ResultContent.HEADER));
		assertEquals("401", RESULT.toText(ResultContent.HTTP_STATUS));
		assertEquals(
			"Content-Type: application/json\nX-Request: 12\n\n{\"error\":\n\n\"bad token\"}",
			RESULT.toText(ResultContent.ALL)
		);
		assertEquals(
			"Status: 401\nContent-Type: application/json\nX-Request: 12\n\n{\"error\":\n\n\"bad token\"}",
			RESULT.toText(ResultContent.ALL_WITH_STATUS)
		);

		// A response without body
		assertNull(new HttpResult(204, "", null).toText(ResultContent.BODY));
		assertEquals("Status: 204\n\n", new HttpResult(204, "", null).toText(ResultContent.ALL_WITH_STATUS));
	}

	/**
	 * A response is recorded as the text of its status, header and body, and read back from it.
	 */
	@Test
	void testParse() {
		assertEquals(RESULT, HttpResult.parse(RESULT.toText(ResultContent.ALL_WITH_STATUS)));
		assertEquals(
			new HttpResult(204, "", ""),
			HttpResult.parse(new HttpResult(204, "", null).toText(ResultContent.ALL_WITH_STATUS))
		);
		assertEquals(new HttpResult(200, "", "{}"), HttpResult.parse("Status: 200\n\n{}"));

		// The status alone, with or without the line feed a file ends with
		assertEquals(new HttpResult(404, "", ""), HttpResult.parse("Status: 404"));
		assertEquals(new HttpResult(404, "", ""), HttpResult.parse("Status: 404\n"));

		// Not a response with its status
		assertNull(HttpResult.parse(null));
		assertNull(HttpResult.parse(""));
		assertNull(HttpResult.parse("{\"a\":1}"));
		assertNull(HttpResult.parse(RESULT.toText(ResultContent.ALL)));
	}
}
