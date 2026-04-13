package org.metricshub.engine.connector.model.monitor.task.source.compute;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * The abstract base class for various compute operations used in MetricsHub engine's data processing tasks.
 * Subclasses define specific compute operations such as addition, subtraction, and more.
 * This class provides a foundation for polymorphic deserialization and common behavior.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes(
	{
		@JsonSubTypes.Type(value = Add.class, name = "add"),
		@JsonSubTypes.Type(value = And.class, name = "and"),
		@JsonSubTypes.Type(value = ArrayTranslate.class, name = "arrayTranslate"),
		@JsonSubTypes.Type(value = Awk.class, name = "awk"),
		@JsonSubTypes.Type(value = Convert.class, name = "convert"),
		@JsonSubTypes.Type(value = Decode.class, name = "decode"),
		@JsonSubTypes.Type(value = Divide.class, name = "divide"),
		@JsonSubTypes.Type(value = DuplicateColumn.class, name = "duplicateColumn"),
		@JsonSubTypes.Type(value = Encode.class, name = "encode"),
		@JsonSubTypes.Type(value = ExcludeMatchingLines.class, name = "excludeMatchingLines"),
		@JsonSubTypes.Type(value = Extract.class, name = "extract"),
		@JsonSubTypes.Type(value = ExtractPropertyFromWbemPath.class, name = "extractPropertyFromWbemPath"),
		@JsonSubTypes.Type(value = Json2Csv.class, name = "json2Csv"),
		@JsonSubTypes.Type(value = KeepColumns.class, name = "keepColumns"),
		@JsonSubTypes.Type(value = KeepOnlyMatchingLines.class, name = "keepOnlyMatchingLines"),
		@JsonSubTypes.Type(value = Prepend.class, name = "prepend"),
		@JsonSubTypes.Type(value = Multiply.class, name = "multiply"),
		@JsonSubTypes.Type(value = PerBitTranslation.class, name = "perBitTranslation"),
		@JsonSubTypes.Type(value = Replace.class, name = "replace"),
		@JsonSubTypes.Type(value = Append.class, name = "append"),
		@JsonSubTypes.Type(value = Subtract.class, name = "subtract"),
		@JsonSubTypes.Type(value = Substring.class, name = "substring"),
		@JsonSubTypes.Type(value = Translate.class, name = "translate"),
		@JsonSubTypes.Type(value = Xml2Csv.class, name = "xml2Csv")
	}
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class Compute implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The type of the compute operation.
	 */
	protected String type;

	/**
	 * Creates a copy of the current compute operation.
	 *
	 * @return A new instance of the concrete compute operation.
	 */
	public abstract Compute copy();

	/**
	 * Updates the compute operation using the provided updater function.
	 *
	 * @param updater The unary operator to apply updates to the compute operation.
	 */
	public abstract void update(UnaryOperator<String> updater);

	/**
	 * Returns a formatted string representation of the compute operation, indicating its type.
	 *
	 * @return A string representation of the compute operation.
	 */
	@Override
	public String toString() {
		return new StringBuilder("- type=").append(this.getClass().getSimpleName()).toString();
	}

	/**
	 * Accepts a compute processor for further processing.
	 *
	 * @param computeProcessor The compute processor to accept.
	 */
	public abstract void accept(IComputeProcessor computeProcessor);
}
