package org.metricshub.cli.service;

import java.io.PrintWriter;

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

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

/**
 * 
 */
//CHECKSTYLE:OFF
@Command(name = "apikey",
	subcommands = { 
		ApiKeyCliService.CreateCommand.class,
		ApiKeyCliService.ListCommand.class,
		ApiKeyCliService.RevokeCommand.class
	},
	sortOptions = false,
	usageHelpAutoWidth = true,
	headerHeading = "%n",
	header = "Manage MetricsHub API Keys.",
	synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
	descriptionHeading = "%n@|bold,underline Description|@:%n%n",
	description = "This tool allows you to securely create, list, and revoke API keys used to authenticate with MetricsHub services.%n%n", parameterListHeading = "%n@|bold,underline Parameters|@:%n",
	optionListHeading = "%n@|bold,underline Options|@:%n"
	)
//CHECKSTYLE:ON
public class ApiKeyCliService {

	@Spec
	static CommandSpec spec;

	@Command(name = "create", description = "Generate a new API key for a given name.")
	public static class CreateCommand implements Callable<Integer> {
		@Option(names = { "--name" }, required = true, description = "The name associated with the API key.")
		private String name;

		@Override
		public Integer call() throws Exception {
			final PrintWriter printWriter = spec.commandLine().getOut();
			printWriter.printf("API key created for '%s': %s%n", name, "**********EXAMPLETOKEN123");
			return 0;
		}
	}

	@Command(name = "list", description = "List all stored API keys.")
	public static class ListCommand implements Callable<Integer> {
		@Override
		public Integer call() throws Exception {
			System.out.println("mcp-client1 *****************21");
			System.out.println("mcp-client2 *****************22");
			return 0;
		}
	}

	@Command(name = "revoke", description = "Revoke an existing API key by name.")
	public static class RevokeCommand implements Callable<Integer> {
		@Option(names = { "--name" }, required = true, description = "The name of the API key to revoke.")
		String name;

		@Override
		public Integer call() throws Exception {
			System.out.printf("API key '%s' has been revoked.%n", name);
			return 0;
		}
	}
}
