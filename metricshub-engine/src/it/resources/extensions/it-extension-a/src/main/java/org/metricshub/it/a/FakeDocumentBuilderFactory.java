package org.metricshub.it.a;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * A stand-in {@link DocumentBuilderFactory} used only to prove which factory the JAXP lookup
 * resolves under a given thread context class loader. It is never actually used to build documents.
 */
public class FakeDocumentBuilderFactory extends DocumentBuilderFactory {

	@Override
	public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
		throw new ParserConfigurationException("FakeDocumentBuilderFactory is a test stub");
	}

	@Override
	public void setAttribute(String name, Object value) {
		// no-op
	}

	@Override
	public Object getAttribute(String name) {
		return null;
	}

	@Override
	public void setFeature(String name, boolean value) {
		// no-op
	}

	@Override
	public boolean getFeature(String name) {
		return false;
	}
}
