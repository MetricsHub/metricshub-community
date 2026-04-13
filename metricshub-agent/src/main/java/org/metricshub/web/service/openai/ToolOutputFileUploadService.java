package org.metricshub.web.service.openai;

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

import com.openai.client.OpenAIClient;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.dto.openai.UploadedToolOutputManifest;
import org.springframework.stereotype.Service;

/**
 * Uploads persisted tool outputs to the OpenAI Files API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolOutputFileUploadService {

	private final Optional<OpenAIClient> openAIClient;

	/**
	 * Uploads the given file to the OpenAI Files API.
	 *
	 * @param filePath path to the file to upload
	 * @return manifest details of the uploaded file
	 */
	public UploadedToolOutputManifest uploadToOpenAi(final Path filePath) {
		try {
			final var uploadedFile = openAIClient
				.orElseThrow(() -> new IllegalStateException("OpenAI Client is not configured"))
				.files()
				.create(FileCreateParams.builder().file(filePath).purpose(FilePurpose.USER_DATA).build());

			return UploadedToolOutputManifest
				.builder()
				.openaiFileId(uploadedFile.id())
				.fileName(uploadedFile.filename())
				.build();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to upload tool output to OpenAI Files API", e);
		}
	}
}
