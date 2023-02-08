package com.sentrysoftware.matrix.connector.model.identity.criterion;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SnmpGetNext extends Snmp {

	private static final long serialVersionUID = 1L;

	@Builder
	public SnmpGetNext(
			@JsonProperty("type") String type, 
			@JsonProperty("forceSerialization") boolean forceSerialization, 
			@JsonProperty(value = "oid", required = true) @NonNull String oid, 
			@JsonProperty("expectedResult") String expectedResult) {

		super(type, forceSerialization, oid, expectedResult);
	}

}
