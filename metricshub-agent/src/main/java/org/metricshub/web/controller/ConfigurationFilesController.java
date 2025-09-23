package org.metricshub.web.controller;

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

import java.util.List;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.service.ConfigurationFilesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing configuration files.
 */
@RestController
@RequestMapping(value = "/api/config-files")
public class ConfigurationFilesController {

	private ConfigurationFilesService configurationFilesService;

	/**
	 * Constructor for ConfigurationFilesController.
	 *
	 * @param configurationFilesService the ConfigurationFilesService to handle configuration file requests.
	 */
	@Autowired
	public ConfigurationFilesController(final ConfigurationFilesService configurationFilesService) {
		this.configurationFilesService = configurationFilesService;
	}

	/**
	 * Endpoint to list all configuration files with their metadata.
	 *
	 * @return A list of ConfigurationFile representing all configuration files.
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<ConfigurationFile> listConfigurationFiles() {
		return configurationFilesService.getAllConfigurationFiles();
	}
}
