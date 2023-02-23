package com.sentrysoftware.matrix.connector.model.monitor.task.source.compute;

import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.NEW_LINE;

import static com.sentrysoftware.matrix.common.helpers.StringHelper.addNonNull;

import java.util.StringJoiner;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.sentrysoftware.matrix.connector.model.common.ConversionType;


import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Convert extends Compute {

	private static final long serialVersionUID = 1L;

	@NonNull
	@JsonSetter
	private Integer column;

	@NonNull
	@JsonSetter
	private ConversionType conversion;

	@Builder
	@JsonCreator
	public Convert(
		@JsonProperty("type") String type, 
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty(value = "conversion", required = true) @NonNull ConversionType conversion
	) {
		super(type);
		this.column = column;
		this.conversion = conversion;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- conversion=", conversion != null ? conversion.getName() : null);

		return stringJoiner.toString();
	}

	@Override
	public Convert copy() {
		return Convert
			.builder()
			.type(type)
			.column(column)
			.conversion(conversion)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// Not implemented because this class doesn't define string members
	}

}
