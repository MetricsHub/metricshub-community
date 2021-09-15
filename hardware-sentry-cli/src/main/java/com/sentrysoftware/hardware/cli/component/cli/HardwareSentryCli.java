package com.sentrysoftware.hardware.cli.component.cli;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sentrysoftware.hardware.cli.component.cli.converters.TargetTypeConverter;
import com.sentrysoftware.hardware.cli.component.cli.protocols.HttpConfig;
import com.sentrysoftware.hardware.cli.component.cli.protocols.IpmiConfig;
import com.sentrysoftware.hardware.cli.component.cli.protocols.SnmpConfig;
import com.sentrysoftware.hardware.cli.component.cli.protocols.WbemConfig;
import com.sentrysoftware.hardware.cli.component.cli.protocols.WmiConfig;
import com.sentrysoftware.hardware.cli.service.ConsoleService;
import com.sentrysoftware.hardware.cli.service.JobResultFormatterService;
import com.sentrysoftware.hardware.cli.service.VersionService;
import com.sentrysoftware.matrix.connector.ConnectorStore;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.engine.EngineConfiguration;
import com.sentrysoftware.matrix.engine.EngineResult;
import com.sentrysoftware.matrix.engine.OperationStatus;
import com.sentrysoftware.matrix.engine.protocol.IProtocolConfiguration;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol.Privacy;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol.SNMPVersion;
import com.sentrysoftware.matrix.engine.strategy.collect.CollectOperation;
import com.sentrysoftware.matrix.engine.strategy.detection.DetectionOperation;
import com.sentrysoftware.matrix.engine.strategy.discovery.DiscoveryOperation;
import com.sentrysoftware.matrix.engine.target.HardwareTarget;
import com.sentrysoftware.matrix.engine.target.TargetType;
import com.sentrysoftware.matrix.model.monitoring.HostMonitoringFactory;
import com.sentrysoftware.matrix.model.monitoring.IHostMonitoring;

import lombok.Data;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

@Component
@Command(
		name = "hws",
		mixinStandardHelpOptions = true,
		abbreviateSynopsis = true,
		sortOptions = false,
		usageHelpAutoWidth = true,
		versionProvider = VersionService.class
)
@Data
public class HardwareSentryCli implements Callable<Integer> {

	@Autowired
	private JobResultFormatterService jobResultFormatterService;

	@Autowired
	private ConsoleService consoleService;

	@Spec
	CommandSpec spec;

	@Parameters(
			index = "0",
			description = "Hostname of IP address of the target to monitor"
	)
	private String hostname;

	@Option(
			names = { "-t", "--type" },
			order = 1,
			required = true,
			description = "Type of the host to monitor (lin, linux, win, windows, mgmt, management, storage, network, aix, hpux, solaris, tru64, vms)",
			converter = TargetTypeConverter.class
	)
	private TargetType deviceType;

	@ArgGroup(exclusive = false, heading = "@|bold SNMP Options|@%n")
	private SnmpConfig snmpConfig;

	@ArgGroup(exclusive = false, heading = "@|bold WBEM Options|@%n")
	private WbemConfig wbemConfig;

	@ArgGroup(exclusive = false, heading = "@|bold WMI Options|@%n")
	private WmiConfig wmiConfig;

	@ArgGroup(exclusive = false, heading = "@|bold HTTP Options|@%n")
	private HttpConfig httpConfig;

	@ArgGroup(exclusive = false, heading = "@|bold IPMI Options|@%n")
	private IpmiConfig ipmiConfig;

	@Option(
			names = { "-u", "--username" },
			order = 2,
			description = "Username for authentication"
	)
	String username;

	@Option(
			names = { "-p", "--password" },
			order = 3,
			description = "Associated password",
			arity = "0..1",
			interactive = true
	)
	char[] password;

	@Option(
			names = { "-f", "--force" },
			order = 4,
			split = ",",
			description = "Force selected hardware connectors to connect to the target"
	)
	private Set<String> connectors;

	@Option(
			names = { "-x", "--exclude" },
			order = 5,
			split = ",",
			description = "Exclude connectors from the automatic detection process"
	)
	private Set<String> excludedConnectors;

	@Option(
			names = "-v",
			order = 6,
			description = "Verbose mode (repeat the option to increase verbosity)"
	)
	private boolean[] verbose;

	@Override
	public Integer call() {

		validate();

		setLogLevel();

		EngineConfiguration engineConf = new EngineConfiguration();

		engineConf.setTarget(new HardwareTarget(hostname, hostname, deviceType));
		engineConf.setProtocolConfigurations(getProtocols());
		if (connectors != null) {
			engineConf.setSelectedConnectors(connectors);
		}
		if (excludedConnectors != null) {
			engineConf.setExcludedConnectors(excludedConnectors);
		}

		// run jobs
		IHostMonitoring hostMonitoring =
				HostMonitoringFactory.getInstance().createHostMonitoring(hostname, engineConf);

		// Detection
		if (consoleService.hasConsole()) {
			System.out.printf("Performing detection on %s...\n", hostname);
			System.out.flush();
		}
		EngineResult engineResult = hostMonitoring.run(new DetectionOperation());
		if (engineResult.getOperationStatus() != OperationStatus.SUCCESS) {
			System.out.println(consoleService.statusToAnsi(engineResult.getOperationStatus()));
			System.out.flush();
			return CommandLine.ExitCode.SOFTWARE;
		}

		// Discovery
		if (consoleService.hasConsole()) {
			System.out.println("Performing discovery... ");
			System.out.flush();
		}
		engineResult = hostMonitoring.run(new DiscoveryOperation());
		if (engineResult.getOperationStatus() != OperationStatus.SUCCESS) {
			System.out.println(consoleService.statusToAnsi(engineResult.getOperationStatus()));
			System.out.flush();
			return CommandLine.ExitCode.SOFTWARE;
		}

		// Collect
		if (consoleService.hasConsole()) {
			System.out.println("Performing collect... ");
			System.out.flush();
		}
		engineResult = hostMonitoring.run(new CollectOperation());
		if (engineResult.getOperationStatus() != OperationStatus.SUCCESS) {
			System.out.println(consoleService.statusToAnsi(engineResult.getOperationStatus()));
			System.out.flush();
			return CommandLine.ExitCode.SOFTWARE;
		}

		// And now the result
		spec.commandLine().getOut().print("\n");
		jobResultFormatterService.printResult(hostMonitoring, spec.commandLine().getOut());
//		spec.commandLine().getOut().print(jobResultFormatterService.format(hostMonitoring));

		return CommandLine.ExitCode.OK;
	}

	/**
	 * Validate the specified arguments, and ask for passwords if needed.
	 * @throws ParameterException in case of invalid parameter
	 */
	private void validate() {

		// Can we ask for passwords interactively?
		final boolean interactive = System.console() != null;

		// Passwords
		if (interactive) {
			if (username != null && password == null) {
				password = System.console().readPassword("%s password: ", username);
			}
			if (snmpConfig != null) {
				if (snmpConfig.getUsername() != null && snmpConfig.getPassword() == null) {
					snmpConfig.setPassword(System.console().readPassword("%s password for SNMP: ", snmpConfig.getUsername()));
				}
				if (snmpConfig.getPrivacy() == Privacy.AES || snmpConfig.getPrivacy() == Privacy.DES) {
					snmpConfig.setPrivacyPassword(System.console().readPassword("SNMP Privacy password: "));
				}
			}
			if (httpConfig != null) {
				if (httpConfig.getUsername() != null && httpConfig.getPassword() == null) {
					httpConfig.setPassword(System.console().readPassword("%s password for HTTP: ", httpConfig.getUsername()));
				}
			}
			if (ipmiConfig != null) {
				if (ipmiConfig.getUsername() != null && ipmiConfig.getPassword() == null) {
					ipmiConfig.setPassword(System.console().readPassword("%s password for IPMI: ", ipmiConfig.getUsername()));
				}
			}
			if (wbemConfig != null) {
				if (wbemConfig.getUsername() != null && wbemConfig.getPassword() == null) {
					wbemConfig.setPassword(System.console().readPassword("%s password for WBEM: ", wbemConfig.getUsername()));
				}
			}
			if (wmiConfig != null) {
				if (wmiConfig.getUsername() != null && wmiConfig.getPassword() == null) {
					wmiConfig.setPassword(System.console().readPassword("%s password for WMI: ", wmiConfig.getUsername()));
				}
			}
		}

		// No protocol at all?
		if (snmpConfig == null && httpConfig == null && ipmiConfig == null
				&& wbemConfig == null && wmiConfig == null) {
			throw new ParameterException(spec.commandLine(), "At least one protocol must be specified: --http[s], --ipmi, --snmp, --wbem, --wmi.");
		}

		// SNMP inconsistencies
		if (snmpConfig != null) {
			SNMPVersion version = snmpConfig.getSnmpVersion();
			if (version == SNMPVersion.V1 || version == SNMPVersion.V2C) {
				if (snmpConfig.getCommunity() == null || snmpConfig.getCommunity().isBlank()) {
					throw new ParameterException(spec.commandLine(), "Community string is required for SNMP " + version);
				}
				if (snmpConfig.getUsername() != null) {
					throw new ParameterException(spec.commandLine(), "Username/password is not supported in SNMP " + version);
				}
				if (snmpConfig.getPrivacy() != null && snmpConfig.getPrivacy() != Privacy.NO_ENCRYPTION
						|| snmpConfig.getPrivacyPassword() != null) {
					throw new ParameterException(spec.commandLine(), "Privacy (encryption) is not supported in SNMP " + version);
				}
			} else {
				if (version == SNMPVersion.V3_MD5 || version == SNMPVersion.V3_SHA) {
					if (snmpConfig.getUsername() == null || snmpConfig.getPassword() == null) {
						throw new ParameterException(spec.commandLine(), "Username and password are required for SNMP " + version);
					}
				}
				if (snmpConfig.getCommunity() != null) {
					throw new ParameterException(spec.commandLine(), "Community string is not supported in SNMP " + version);
				}
				if (snmpConfig.getPrivacy() != null && snmpConfig.getPrivacy() != Privacy.NO_ENCRYPTION) {
					throw new ParameterException(spec.commandLine(), "A privacy password is required for SNMP encryption (--snmp-privacy-password)");
				}
			}
		}

		// Connectors
		Map<String, Connector> allConnectors = ConnectorStore.getInstance().getConnectors();
		if (connectors != null) {
			for (String connectorName : connectors) {
				if (!allConnectors.containsKey(connectorName)) {
					throw new ParameterException(spec.commandLine(), "Unknown connector: " + connectorName);
				}
			}
		}
		if (excludedConnectors != null) {
			for (String connectorName : excludedConnectors) {
				if (!allConnectors.containsKey(connectorName)) {
					throw new ParameterException(spec.commandLine(), "Unknown connector: " + connectorName);
				}
			}
		}

	}


	/**
	 * Set Log4j logging level according to the verbose flags
	 */
	void setLogLevel() {

		// Disable ANSI in the logging if we don't have a console
		ThreadContext.put("disableAnsi", Boolean.toString(!consoleService.hasConsole()));

		if (verbose != null) {

			Level logLevel = Level.ERROR;

			switch (verbose.length) {
			case 0: logLevel = Level.ERROR; break;
			case 1: logLevel = Level.WARN; break;
			case 2: logLevel = Level.INFO; break;
			case 3: logLevel = Level.DEBUG; break;
			default: logLevel = Level.ALL;
			}

			// Update the Log level at the root level
			LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
			Configuration config = loggerContext.getConfiguration();
			LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
			loggerConfig.setLevel(logLevel);
			loggerContext.updateLoggers();

		}

	}

	/**
	 * @param hardwareSentryCli	The {@link HardwareSentryCli} instance calling this service.
	 *
	 * @return A {@link Map} associating the input protocol type to its input credentials.
	 */
	private Map<Class< ? extends IProtocolConfiguration>, IProtocolConfiguration> getProtocols() {

		return Stream.of(httpConfig, ipmiConfig, snmpConfig, wbemConfig, wmiConfig)
				.filter(Objects::nonNull)
				.map(protocolConfig -> protocolConfig.toProtocol(username, password))
				.collect(Collectors.toMap(
						proto -> proto.getClass(),
						Function.identity())
		);

	}


}