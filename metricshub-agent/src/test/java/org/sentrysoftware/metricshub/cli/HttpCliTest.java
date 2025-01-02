package org.sentrysoftware.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

class HttpCliTest {

	HttpCli httpCli;
	CommandLine commandLine;
	PrintWriter printWriter = new PrintWriter(new StringWriter());

	private static final String HTTP_GET = "GET";
	private static final String URL = "https://hostname:443/www.test.com";
	private static final String WRONG_URL = "WrongUrl";
	private static final String WRONG_HEADER_FILE_PATH = "wrong/path/header.txt";
	private static final String HEADER_FILE_PATH = "src/test/resources/cli/header.txt";
	private static final Map<String, String> HEADERS = Map.of("Content-Type", "application/xml");
	private static final String BODY = "<aaaLogin inName=\" inPassword=\" />";
	private static final String BODY_FILE_PATH = "src/test/resources/cli/body.txt";
	private static final String WRONG_BODY_FILE_PATH = "wrong/path/body.txt";
	private static final String RESULT_CONTENT = "all_with_status";
	private static final String FILE_HEADER =
		"""
		Content-Type: application/xml
		User-Agent: Mozilla/5.0
		Accept: text/html
		Accept-Language: en-US
		Cache-Control: no-cache\
		""";

	@BeforeEach
	void initCli() {
		httpCli = new HttpCli();
		commandLine = new CommandLine(httpCli);
	}

	@Test
	void testGetQuery() throws IOException {
		httpCli.setMethod(HTTP_GET);
		httpCli.setUrl(URL);
		httpCli.setHeaders(HEADERS);
		httpCli.setBody(BODY);

		String header = HEADERS
			.entrySet()
			.stream()
			.map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue()))
			.collect(Collectors.joining("\n"));

		httpCli.populateHeaderContent();
		httpCli.populateBodyContent();

		ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("method", new TextNode(HTTP_GET));
		queryNode.set("url", new TextNode(URL));
		queryNode.set("header", new TextNode(header));
		queryNode.set("body", new TextNode(BODY));
		queryNode.set("resultContent", new TextNode(RESULT_CONTENT));
		assertEquals(queryNode, httpCli.getQuery());
	}

	@Test
	void testGetHeader() throws Exception {
		httpCli.setHeaders(HEADERS);
		httpCli.populateHeaderContent();
		assertEquals("Content-Type: application/xml", httpCli.getHeaderContent());
		httpCli.setHeaders(null);
		httpCli.setHeaderFile(HEADER_FILE_PATH);
		httpCli.populateHeaderContent();
		assertEquals(FILE_HEADER, httpCli.getHeaderContent());
	}

	@Test
	void testGetBody() throws Exception {
		httpCli.setBody(BODY);
		httpCli.populateBodyContent();
		assertEquals(BODY, httpCli.getBody());
		httpCli.setBody(null);
		httpCli.setBodyFile(BODY_FILE_PATH);
		httpCli.populateBodyContent();
		assertEquals(BODY, httpCli.getBodyContent());
	}

	@Test
	void testValidateUrl() {
		{
			httpCli.setUrl("https://hostname:443/www.test.com/path 1/");
			ParameterException exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validateUrl());
			assertTrue(exceptionMessage.getMessage().contains("URL contains invalid characters:"));
			httpCli.setUrl(URL);
			assertDoesNotThrow(() -> httpCli.validateUrl());
		}
		{
			httpCli.setUrl("://hostname:443/www.test.com/path1/");
			ParameterException exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validateUrl());
			assertTrue(exceptionMessage.getMessage().contains("Malformed URL:"));
			httpCli.setUrl(URL);
			assertDoesNotThrow(() -> httpCli.validateUrl());
		}
	}

	@Test
	void testResolvePortFromUrl() throws MalformedURLException {
		httpCli.parsedUrl = new java.net.URL(URL);
		assertEquals(443, httpCli.resolvePortFromUrl());

		httpCli.parsedUrl = new java.net.URL("https://www.test.com");
		assertEquals(443, httpCli.resolvePortFromUrl());

		httpCli.parsedUrl = new java.net.URL("http://www.test.com");
		assertEquals(80, httpCli.resolvePortFromUrl());

		httpCli.parsedUrl = new java.net.URL("http://hostname:555/subdomain");
		assertEquals(555, httpCli.resolvePortFromUrl());
	}

	@Test
	void testValidate() {
		// URL must be valid (contains the protocol at the beginning).
		httpCli.setUrl(WRONG_URL);
		ParameterException exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validate());
		assertTrue(exceptionMessage.getMessage().contains("Malformed URL:"));
		httpCli.setUrl(URL);

		// Wrong headerFile path
		httpCli.setHeaderFile(WRONG_HEADER_FILE_PATH);
		exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validate());
		assertTrue(exceptionMessage.getMessage().contains("Error while reading header file"));

		// Only one header (headers or headerFile) must be specified.
		httpCli.setHeaders(HEADERS);
		httpCli.setHeaderFile(HEADER_FILE_PATH);
		exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validate());
		assertEquals(
			"Conflict - Two headers have been configured: --header and --header-file.",
			exceptionMessage.getMessage()
		);
		httpCli.setHeaders(null);

		// Wrong bodyFile path
		httpCli.setBodyFile(WRONG_BODY_FILE_PATH);
		exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validate());
		assertTrue(exceptionMessage.getMessage().contains("Error while reading body file"));

		// Only one body (body or bodyFile) must be specified.
		httpCli.setBody(BODY);
		httpCli.setBodyFile(BODY_FILE_PATH);
		exceptionMessage = assertThrows(ParameterException.class, () -> httpCli.validate());
		assertEquals("Conflict - Two bodies have been configured: --body and --body-file.", exceptionMessage.getMessage());
		httpCli.setBody(null);

		assertDoesNotThrow(() -> httpCli.validate());
	}
}
