package org.metricshub.it.a;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

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
