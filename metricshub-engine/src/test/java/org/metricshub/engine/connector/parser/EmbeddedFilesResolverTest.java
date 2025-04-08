package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.parser.EmbeddedFilesResolver.EmbeddedFileProcessingException;

class EmbeddedFilesResolverTest {

	private static final ObjectMapper OBJECT_MAPPER = JsonHelper.buildYamlMapper();
	private static final String BASE_DIRECTORY = "src/test/resources/test-files/embedded";
	private static final File CONNECTOR_1_FILE = Path.of(BASE_DIRECTORY, "connector1", "connector1.yaml").toFile();
	private static final File CONNECTOR_2_FILE = Path.of(BASE_DIRECTORY, "connector2", "connector2.yaml").toFile();
	private static final Path CONNECTOR_2_DIRECTORY = Path.of(BASE_DIRECTORY, "connector2");
	private static final Path CONNECTOR_1_DIRECTORY = Path.of(BASE_DIRECTORY, "connector1");

	private static final String EXPECTED_YAML =
		"""
		---
		connector:
		  detection:
		    criteria:
		    - type: productRequirements
		      kmVersion: 10.3.00
		    - type: http
		      method: GET
		      url: /redfish/v1/
		      header: "${file::1}"
		      body: "${file::2}"
		      expectedResult: iLO 4
		      errorMessage: Invalid credentials / not an HP iLO 4
		""";

	@Test
	void testProcess() throws IOException {
		final JsonNode connector = OBJECT_MAPPER.readTree(CONNECTOR_1_FILE);

		final Set<URI> parents = Set.of(CONNECTOR_2_DIRECTORY.toUri());

		new EmbeddedFilesResolver(connector, CONNECTOR_1_DIRECTORY, parents).process();

		// Verify the full JsonNode object
		assertEquals(OBJECT_MAPPER.readTree(EXPECTED_YAML), connector);
	}

	@Test
	void testProcessFailureOnFileNotFound() throws IOException {
		final EmbeddedFilesResolver embeddedFilesResolver = new EmbeddedFilesResolver(
			OBJECT_MAPPER.readTree(CONNECTOR_2_FILE),
			CONNECTOR_2_DIRECTORY,
			Collections.emptySet()
		);

		assertThrows(EmbeddedFileProcessingException.class, () -> embeddedFilesResolver.process());
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testProcessFileInZipWindows() throws Exception {
		testProcessFileInZip("jar:file:///");
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testProcessFileInZipLinux() throws Exception {
		testProcessFileInZip("jar:file://");
	}

	/**
	 * Test the {@link EmbeddedFilesResolver#processFile(String, Path)} method.
	 * @param schemePrefix The scheme prefix for the URI.
	 * @throws Exception If an error occurs during the test.
	 */
	private void testProcessFileInZip(final String schemePrefix) throws Exception {
		final EmbeddedFilesResolver embeddedFilesResolver = new EmbeddedFilesResolver(
			OBJECT_MAPPER.readTree(CONNECTOR_2_FILE),
			CONNECTOR_2_DIRECTORY,
			Collections.emptySet()
		);

		final String absolutePath = Paths.get("src/test/resources").toAbsolutePath().toString().replace("\\", "/");

		final String connectorDirUriStr = String.format(
			"%s%s/test-files/connector/zippedConnector/connectors/connectors.zip!/hardware/DiskPart",
			schemePrefix,
			absolutePath
		);

		final URI connectorDirUri = URI.create(connectorDirUriStr);
		final EmbeddedFile result = FileHelper.fileSystemTask(
			connectorDirUri,
			Collections.emptyMap(),
			() -> embeddedFilesResolver.processFile("diskPart.awk", Paths.get(connectorDirUri))
		);

		final String actualAwkScriptNormalized = result.getContentAsString().replaceAll("\r\n", "").replaceAll("\n", "");
		final String expected =
			"""
			BEGIN {	foundHeader = 0}($1 == "Volume" && $2 == "###" && $3 == "Ltr" && $4 == "Label" && $5 == "Fs" && $6 == "Type" && $7 == "Size" && $8 == "Status" && $9 == "Info") {	ltrIndex = index($0, "Ltr")	labelIndex = index($0, "Label")	fsIndex = index($0, "Fs")	typeIndex = index($0, "Type")	sizeIndex = index($0, "Size")	statusIndex = index($0, "Status")	infoIndex = index($0, "Info")	foundHeader = 1}($1 == "Volume" && $2 ~ /^[0-9]+$/ && foundHeader == 1) {	# Get the fields	volumeID = $2	letter = substr($0, ltrIndex, 3)	label = substr($0, labelIndex, fsIndex - labelIndex)	fs = substr($0, fsIndex, typeIndex - fsIndex)	type = substr($0, typeIndex, sizeIndex - typeIndex)	sizeT = substr($0, sizeIndex, statusIndex - sizeIndex)	status = substr($0, statusIndex, infoIndex - statusIndex)	info = substr($0, infoIndex, length($0) - infoIndex + 1)	# Do some processing, remove unnecessary white spaces	gsub(" ", "", letter)	sub("^ +", "", label)	sub(" +$", "", label)	sub("^ +", "", fs)	sub(" +$", "", fs)	sub("^ +", "", type)	sub(" +$", "", type)	gsub(" ", "", sizeT)	sub("^ +", "", status)	sub(" +$", "", status)	sub("^ +", "", info)	sub(" +$", "", info)	# Convert size to bytes	size = ""	if (substr(sizeT, length(sizeT), 1) == "B") {		size = substr(sizeT, 1, length(sizeT) - 1)		# Handle unit multipliers		if (substr(size, length(size), 1) == "K") {			size = substr(size, 1, length(size) - 1) * 1024		} else if (substr(size, length(size), 1) == "M") {			size = substr(size, 1, length(size) - 1) * 1024 * 1024		} else if (substr(size, length(size), 1) == "G") {			size = substr(size, 1, length(size) - 1) * 1024 * 1024 * 1024		} else if (substr(size, length(size), 1) == "T") {			size = substr(size, 1, length(size) - 1) * 1024 * 1024 * 1024 * 1024		}		# Make sure we got a number		if (size !~ /^[0-9]+$/) {			size = ""		}	}	# Add a colon to the drive letter, if any	if (letter ~ /^[A-Z]$/) {		letter = letter ":"	}	# Build the displayID from label and letter	if (letter != "" && label != "") {		displayID = letter " - " label	} else if (letter != "" && label == "") {		displayID = letter	} else if (letter == "" && label != "") {		displayID = label	} else {		displayID = ""	}	# Replace "Partition" type with nothing	if (type == "Partition") {		type = ""	}	print "MSHW;" volumeID ";" displayID ";" letter ";" type ";" fs ";" size ";" status ";" info ";"}""";
		assertEquals(expected, actualAwkScriptNormalized);
		assertEquals(1, result.getId());
		assertEquals("diskPart.awk", result.getFilename());
		assertEquals("diskPart", result.getBaseName());
		assertEquals(".awk", result.getFileExtension());
	}

	@Test
	void testProcessFile() throws IOException {
		final EmbeddedFilesResolver embeddedFilesResolver = new EmbeddedFilesResolver(
			OBJECT_MAPPER.readTree(CONNECTOR_2_FILE),
			CONNECTOR_2_DIRECTORY,
			Collections.emptySet()
		);
		final Path yamlTestPath = Paths.get(
			"src",
			"test",
			"resources",
			"test-files",
			"connector",
			"zippedConnector",
			"connectors",
			"hardware",
			"MIB2"
		);
		assertEquals("exit", embeddedFilesResolver.processFile("exit.txt", yamlTestPath).getContentAsString().trim());
	}
}
