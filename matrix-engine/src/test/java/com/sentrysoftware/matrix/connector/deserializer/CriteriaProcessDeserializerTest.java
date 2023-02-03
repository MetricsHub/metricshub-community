package com.sentrysoftware.matrix.connector.deserializer;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.identity.criterion.Criterion;
import com.sentrysoftware.matrix.connector.model.identity.criterion.Process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

class CriteriaProcessDeserializerTest extends DeserializerTest {

    @Override
    public String getResourcePath() {
        return "src/test/resources/test-files/connector/detection/criteria/process/";
    }

    @Test
    /**
     * Checks input properties for detection criteria
     *
     * @throws IOException
     */
    void testDeserializeProcess() throws IOException {
        final String testResource = "processCriterion";
        final Connector process = getConnector(testResource);

        List<Criterion> expected = new ArrayList<>();

        final String commandLine = "naviseccli -help";

        expected.add(
                new Process("process", true, commandLine));

        compareCriterion(testResource, process, expected);
    }

    @Test
    /**
     * Checks that null commandline is rejected
     *
     * @throws IOException
     */
    void testProcessNullCommandLine() throws IOException {
        // commandLine is null
        try {
            getConnector("processCriterionNullCommandLine");
            Assert.fail(INVALID_NULL_EXCEPTION_MSG);
        } catch (InvalidNullException e) {
            final String message = "Invalid `null` value encountered for property \"commandLine\"";
            checkMessage(e, message);
        }
    }

    @Test
    /**
     * Checks that blanks are rejected
     *
     * @throws IOException
     */
    void testProcessBlankCommandLine() throws IOException {
        // commandLine is blank
        try {
            getConnector("processCriterionBlankCommandLine");
            Assert.fail(INVALID_FORMAT_EXCEPTION_MSG);
        } catch (InvalidFormatException e) {
            final String message = "Invalid blank value encountered for property 'commandLine'.";
            checkMessage(e, message);
        }
    }

    @Test
    /**
     * Checks that command line is declared
     *
     * @throws IOException
     */
    void testProcessNoCommandLine() throws IOException {
        // no commandline defined
        try {
            getConnector("processCriterionNoCommandLine");
            Assert.fail(INVALID_NULL_EXCEPTION_MSG);
        } catch (InvalidNullException e) {
            final String message = "Invalid `null` value encountered for property \"commandLine\"";
            checkMessage(e, message);
        }
    }
}
