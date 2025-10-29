package org.metricshub.engine.connector.deserializer.criterion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;

class HttpCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/http/";
	}

	@Test
	/**
	 * Checks input properties for Http detection criteria
	 *
	 * @throws IOException
	 */
	void testDeserializeHttpCriterion() throws Exception {
		final Connector httpCriterion = getConnector("httpCriterion");

		final List<Criterion> expected = new ArrayList<>();

		final HttpCriterion http1 = HttpCriterion
			.builder()
			.type("http")
			.method(HttpMethod.GET) // The connector doesn't define the method (default: Get)
			.url("test")
			.header("$embedded.http-header")
			.authenticationToken("$embedded.authenticationToken")
			.body("test-body")
			.resultContent(ResultContent.ALL)
			.expectedResult("result")
			.errorMessage("error")
			.build();
		final HttpCriterion http2 = HttpCriterion
			.builder()
			.type("http")
			.method(HttpMethod.GET) // The connector doesn't define the method (default: Get)
			.url("test/path1")
			.header("$embedded.http-header")
			.authenticationToken("$embedded.authenticationToken")
			.body("test-body")
			.resultContent(ResultContent.BODY) // The connector doesn't define the resultConent
			.expectedResult("result")
			.errorMessage("error")
			.build();
		final HttpCriterion http3 = HttpCriterion
			.builder()
			.type("http")
			.method(HttpMethod.POST)
			.url("test/path2")
			.header("$embedded.http-header")
			.authenticationToken("$embedded.authenticationToken")
			.body("test-body")
			.resultContent(ResultContent.BODY) // The Connector defines a null resultContent
			.expectedResult("result")
			.errorMessage("error")
			.build();

		expected.addAll(List.of(http1, http2, http3));

		compareCriterion(httpCriterion, expected);
	}

	@Test
	/**
	 * Checks that httpMethod if defined is GET, POST or DELETE
	 *
	 * @throws IOException
	 */
	void testDeserializeHttpMethodEnum() throws Exception {
		// fail on not enum value
		{
			try {
				getConnector("httpCriterionHttpMethodEnum");
				Assertions.fail(JSON_MAPPING_EXCEPTION_MSG);
			} catch (JsonMappingException e) {
				String message = "not one of the values accepted for Enum class: [POST, DELETE, GET, PUT]";
				checkMessage(e, message);
			}
		}

		// pass on Enum values
		{
			for (HttpMethod method : HttpMethod.values()) {
				String testResource = String.format("httpCriterionHttpMethodEnum%s", method);
				Connector connector = getConnector(testResource);

				assertNotNull(connector);

				assertNotNull(connector.getConnectorIdentity().getDetection().getCriteria());
				assertEquals(1, connector.getConnectorIdentity().getDetection().getCriteria().size());

				assertEquals(
					method,
					((HttpCriterion) connector.getConnectorIdentity().getDetection().getCriteria().get(0)).getMethod()
				);
			}
		}
	}

	@Test
	/**
	 * Checks that resultContent if defined is httpStatus, header, body, or all
	 *
	 * @throws IOException
	 */
	void testDeserializeResultContentEnum() throws Exception {
		// fail on not enum value
		{
			try {
				getConnector("httpCriterionResultContentEnum");
				Assertions.fail(JSON_MAPPING_EXCEPTION_MSG);
			} catch (JsonMappingException e) {
				String message =
					"not one of the values accepted for Enum class: [all, body, HTTP_STATUS, httpStatus, header, ALL, BODY, http_status, HEADER, all_with_status, ALL_WITH_STATUS]";
				checkMessage(e, message);
			}
		}

		// pass on Enum values
		{
			for (ResultContent resultContent : ResultContent.values()) {
				String testResource = String.format("httpCriterionResultContentEnum%s", resultContent);
				Connector connector = getConnector(testResource);

				assertNotNull(connector);

				assertNotNull(connector.getConnectorIdentity().getDetection().getCriteria());
				assertEquals(1, connector.getConnectorIdentity().getDetection().getCriteria().size());

				assertEquals(
					resultContent,
					((HttpCriterion) connector.getConnectorIdentity().getDetection().getCriteria().get(0)).getResultContent()
				);
			}
		}
	}
}
