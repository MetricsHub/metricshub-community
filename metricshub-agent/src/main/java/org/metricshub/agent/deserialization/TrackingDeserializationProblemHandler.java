package org.metricshub.agent.deserialization;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import java.io.IOException;

public class TrackingDeserializationProblemHandler extends DeserializationProblemHandler {

	private final DeserializationFailure failure;

	public TrackingDeserializationProblemHandler(DeserializationFailure failure) {
		this.failure = failure;
	}

	private void register(JsonParser p, String message) {
		JsonLocation loc = p.currentLocation();
		failure.addError(message, loc.getLineNr(), loc.getColumnNr());
	}

	private void register(DeserializationContext ctxt, String message) {
		JsonLocation loc = ctxt.getParser().currentLocation();
		failure.addError(message, loc.getLineNr(), loc.getColumnNr());
	}

	@Override
	public Object handleWeirdStringValue(
		DeserializationContext ctxt,
		Class<?> targetType,
		String valueToConvert,
		String failureMsg
	) throws IOException {
		register(
			ctxt,
			"Weird string value for type " +
			targetType.getSimpleName() +
			": " +
			failureMsg +
			" (value=" +
			valueToConvert +
			")"
		);
		return NOT_HANDLED;
	}

	@Override
	public Object handleWeirdNumberValue(
		DeserializationContext ctxt,
		Class<?> targetType,
		Number valueToConvert,
		String failureMsg
	) throws IOException {
		register(
			ctxt,
			"Weird number value for type " +
			targetType.getSimpleName() +
			": " +
			failureMsg +
			" (value=" +
			valueToConvert +
			")"
		);
		return NOT_HANDLED;
	}

	@Override
	public Object handleWeirdNativeValue(
		DeserializationContext ctxt,
		JavaType targetType,
		Object valueToConvert,
		JsonParser p
	) throws IOException {
		register(p, "Weird native value for type " + targetType + ": " + valueToConvert);
		return NOT_HANDLED;
	}

	@Override
	public Object handleUnexpectedToken(
		DeserializationContext ctxt,
		JavaType targetType,
		JsonToken t,
		JsonParser p,
		String failureMsg
	) throws IOException {
		register(p, "Unexpected token " + t + " for type " + targetType + ": " + failureMsg);
		return NOT_HANDLED;
	}

	@Override
	public Object handleInstantiationProblem(
		DeserializationContext ctxt,
		Class<?> instClass,
		Object argument,
		Throwable t
	) throws IOException {
		register(ctxt, "Instantiation problem for type " + instClass.getSimpleName() + ": " + t.getMessage());
		return NOT_HANDLED;
	}

	@Override
	public Object handleMissingInstantiator(
		DeserializationContext ctxt,
		Class<?> instClass,
		ValueInstantiator valueInsta,
		JsonParser p,
		String msg
	) throws IOException {
		register(p, "Missing instantiator for " + instClass.getSimpleName() + ": " + msg);
		return NOT_HANDLED;
	}

	@Override
	public JavaType handleUnknownTypeId(
		DeserializationContext ctxt,
		JavaType baseType,
		String subTypeId,
		TypeIdResolver idResolver,
		String failureMsg
	) throws IOException {
		register(ctxt, "Unknown type id '" + subTypeId + "' for base type " + baseType + ": " + failureMsg);
		return null;
	}

	@Override
	public JavaType handleMissingTypeId(
		DeserializationContext ctxt,
		JavaType baseType,
		TypeIdResolver idResolver,
		String failureMsg
	) throws IOException {
		register(ctxt, "Missing type id for base type " + baseType + ": " + failureMsg);
		return null;
	}
}
