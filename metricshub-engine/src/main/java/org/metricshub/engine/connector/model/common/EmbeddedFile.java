package org.metricshub.engine.connector.model.common;

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

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;

/**
 * Represents an embedded file within a connector.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmbeddedFile implements Serializable {

	private static final long serialVersionUID = -197665338834839387L;

	/**
	 * The content of the embedded file as byte array.
	 */
	private byte[] content;
	/**
	 * The filename of the embedded file (e.g., script.bat).
	 */
	private String filename;
	/**
	 * A reference to the embedded file, providing additional information for identification.
	 */
	private Integer id;

	/**
	 * Creates a deep copy of the current embedded file.
	 *
	 * @return A new instance of {@link EmbeddedFile} with the same content, filename, and reference.
	 */
	public EmbeddedFile copy() {
		return EmbeddedFile.builder().content(content != null ? content.clone() : null).filename(filename).id(id).build();
	}

	/**
	 * Gets a string containing the embedded file reference.
	 *
	 * @return A string representing the reference to the embedded file.
	 */
	public String description() {
		return String.format("EmbeddedFile %d: %s", id, filename != null ? filename : "<inline>");
	}

	/**
	 * Applies a specified transformation to the content of the embedded file, if content is present. The file content
	 * is initially decoded using UTF-8 charset into a {@link String}, transformed by the provided {@link UnaryOperator},
	 * and then re-encoded back into bytes using UTF-8.
	 *
	 * <b>Note:</b> This method assumes that the content is text-based and can be correctly encoded and decoded using UTF-8.
	 * Non-text content or content that does not conform to UTF-8 encoding may result in data corruption or loss.
	 *
	 * @param updater A {@link UnaryOperator<String>} that takes the current content as a {@link String} and returns the modified content.
	 *                The operation must not return {@code null}; if no modification is needed, the original string should be returned.
	 */
	public void update(UnaryOperator<String> updater) {
		if (content != null) {
			content = updater.apply(getContentAsString()).getBytes(StandardCharsets.UTF_8);
		}
	}

	/**
	 * Creates an {@link EmbeddedFile} from a provided string, encoding the content
	 *  using the specified {@link Charset}.
	 *
	 * @param value   The string content to be converted into an embedded file.
	 * @param charset The {@link Charset} used to encode the string into bytes.
	 * @return An {@link EmbeddedFile} with the provided string content encoded into bytes.
	 */
	public static EmbeddedFile fromString(@NonNull final String value, @NonNull final Charset charset) {
		return EmbeddedFile.builder().content(value.getBytes(charset)).build();
	}

	/**
	 * Provides a convenient way to create an embedded file from a string without
	 * specifying a character set, assuming UTF-8.
	 *
	 * @param value  The string content to be converted into an embedded file.
	 * @return An {@link EmbeddedFile} with the content encoded as UTF-8 bytes.
	 */
	public static EmbeddedFile fromString(@NonNull final String value) {
		return fromString(value, StandardCharsets.UTF_8);
	}

	/**
	 * Converts the byte array content of this embedded file into a string using the specified {@link Charset}.
	 *
	 * @param charset The {@link Charset} to be used for decoding the byte content.
	 * @return The string representation of the embedded file's content.
	 */
	public String getContentAsString(@NonNull final Charset charset) {
		return new String(content, charset);
	}

	/**
	 * Provides a convenient way to convert content to a string when UTF-8 encoding is assumed.
	 *
	 * @return The string representation of the embedded file's content, decoded using UTF-8.
	 */
	public String getContentAsString() {
		return new String(content, StandardCharsets.UTF_8);
	}

	/**
	 * Retrieves the file extension from the filename of this object.
	 * If the filename is not available an empty string is returned.
	 *
	 * @return The file extension as a string. Returns an empty string if the filename is null or does not have an extension.
	 */
	public String getFileExtension() {
		if (filename != null) {
			return FileHelper.getExtension(filename);
		}
		return MetricsHubConstants.EMPTY;
	}

	/**
	 * Retrieves the filename without its extension.
	 * If the filename is not available an empty string is returned.
	 *
	 * @return The base name of the file as a string. Returns an empty string if the filename is null.
	 */
	public String getBaseName() {
		if (filename != null) {
			return FileHelper.getBaseName(filename);
		}
		return MetricsHubConstants.EMPTY;
	}
}
