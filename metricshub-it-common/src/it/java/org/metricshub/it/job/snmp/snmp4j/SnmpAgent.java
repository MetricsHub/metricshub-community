package org.metricshub.it.job.snmp.snmp4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import org.metricshub.it.job.ITJobUtils;
import org.snmp4j.CommandResponder;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.agent.AgentConfigManager;
import org.snmp4j.agent.DefaultMOContextScope;
import org.snmp4j.agent.DefaultMOServer;
import org.snmp4j.agent.MOQuery;
import org.snmp4j.agent.MOQueryWithSource;
import org.snmp4j.agent.MOServer;
import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.io.DefaultMOPersistenceProvider;
import org.snmp4j.agent.io.MOInputFactory;
import org.snmp4j.agent.io.prop.PropertyMOInput;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.agent.mo.util.VariableProvider;
import org.snmp4j.agent.request.RequestStatus;
import org.snmp4j.cfg.EngineBootsCounterFile;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Opaque;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.TransportMappings;
import org.snmp4j.util.ThreadPool;

public class SnmpAgent implements VariableProvider {
	static {
		Locale.setDefault(Locale.US);
	}

	private static final String CONFIG_FILE_PATH = "src/it/resources/snmp/SampleAgentConfig.properties";

	private static final Pattern SNMP_LINE_REGEX = Pattern.compile(
		"(.*)\\s+(ASN_INTEGER|ASN_OCTET_STR|" +
		"ASN_NULL|ASN_OBJECT_ID|" +
		"IPADDRESS|COUNTER|" +
		"COUNTER64|GAUGE|" +
		"TIMETICKS|OPAQUE)\\s+(.*)",
		Pattern.MULTILINE
	);

	private static final Pattern HEX_OCTET_STR_PATTERN = Pattern.compile(
		"^[0-9a-f][0-9a-f]( [0-9a-f][0-9a-f])*$",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * ASN_TYPE returned by MATSYA SNMP client to their corresponding function which creates a new {@link MOScalar} object
	 */
	private static final Map<String, BiFunction<String, String, MOScalar<? extends Variable>>> ASN_TYPE_FUNCTIONS;

	public static final Map<String, MOScalar<? extends Variable>> MANAGED_OBJECTS = new HashMap<>();

	static {
		final Map<String, BiFunction<String, String, MOScalar<? extends Variable>>> map = new HashMap<>();
		map.put("ASN_INTEGER", SnmpAgent::createInteger32Object);
		map.put("ASN_OCTET_STR", SnmpAgent::createOctetStringObject);
		map.put("ASN_NULL", SnmpAgent::createNullObject);
		map.put("ASN_OBJECT_ID", SnmpAgent::createOidObject);
		map.put("IPADDRESS", SnmpAgent::createIpAddressObject);
		map.put("COUNTER", SnmpAgent::createCounter32Object);
		map.put("COUNTER64", SnmpAgent::createCounter64Object);
		map.put("GAUGE", SnmpAgent::createGauge32Object);
		map.put("TIMETICKS", SnmpAgent::createTimeTicksObject);
		map.put("OPAQUE", SnmpAgent::createOpaqueObject);

		ASN_TYPE_FUNCTIONS = Collections.unmodifiableMap(map);
	}

	protected AgentConfigManager agent;
	protected MOServer server;
	private ThreadPool threadPool;
	private int port;

	@Getter
	private boolean started;

	public SnmpAgent() throws IOException {
		final File bootCounterFile = File.createTempFile("bootCounterFile", ".bc");
		final File configFile = File.createTempFile("configFile", ".cfg");

		server = new DefaultMOServer();

		final MOServer[] moServers = new MOServer[] { server };

		final Properties props = new Properties();
		try {
			props.load(new FileInputStream(new File(CONFIG_FILE_PATH)));
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		// Create the factory which load our configuration
		final MOInputFactory configurationFactory = () -> new PropertyMOInput(props, SnmpAgent.this);

		// Create a new messageDispatcher
		final MessageDispatcher messageDispatcher = new MessageDispatcherImpl();

		// Listen via UPD layer on the available port
		// Unix-based systems declare ports < 1024 as "privileged" unfortunately unix machines like Jenkins will reject 161...
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			port = serverSocket.getLocalPort();
			addListenAddresses(messageDispatcher, List.of("udp:0.0.0.0/" + port));
		}

		threadPool = ThreadPool.create("snmp4JAgent", 3);

		// Create the AgentConfigManager, this is the overridden default instance
		agent =
			new AgentConfigurationManager(
				new OctetString(MPv3.createLocalEngineID()),
				messageDispatcher,
				null,
				moServers,
				threadPool,
				configurationFactory,
				new DefaultMOPersistenceProvider(moServers, configFile.getAbsolutePath()),
				new EngineBootsCounterFile(bootCounterFile)
			);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Variable getVariable(String name) {
		final OID oid;

		OctetString context = null;

		int pos = name.indexOf(':');

		if (pos >= 0) {
			context = new OctetString(name.substring(0, pos));
			oid = new OID(name.substring(pos + 1));
		} else {
			oid = new OID(name);
		}

		final DefaultMOContextScope scope = new DefaultMOContextScope(context, oid, true, oid, true);
		final MOQuery query = new MOQueryWithSource(scope, false, this);

		@SuppressWarnings("rawtypes")
		final ManagedObject mo = server.lookup(query);

		if (mo != null) {
			final VariableBinding vb = new VariableBinding(oid);
			final RequestStatus status = new RequestStatus();
			final CustomSubRequest<?> customSubRequest = new CustomSubRequest<>(status, scope, vb);
			mo.get(customSubRequest);
			return vb.getVariable();
		}

		return null;
	}

	/**
	 * Add the list of listen addresses to the {@link MessageDispatcher} instance
	 *
	 * @param md        The MessageDispatcher interface defining common services that process incoming SNMP messages and dispatch them to
	 *                  interested {@link CommandResponder} instances.
	 * @param addresses Listener address e.g. udp:0.0.0.0/161
	 */
	protected void addListenAddresses(final MessageDispatcher md, final List<String> addresses) {
		for (String addressString : addresses) {
			Address address = GenericAddress.parse(addressString);

			md.addTransportMapping(TransportMappings.getInstance().createTransportMapping(address));
		}
	}

	/**
	 * Register the managed objects located in the given SNMP walk file paths
	 *
	 * @param snmpWalkFilePaths Paths to the SNMP Walk input files
	 * @throws IOException
	 */
	private void registerManagedObjects(String... snmpWalkFilePaths) throws IOException {
		for (String path : snmpWalkFilePaths) {
			registerManagedObjects(path);
		}
	}

	/**
	 * Register the managed objects located in the given SNMP walk file path
	 *
	 * @param snmpWalkFilePath Path to the SNMP Walk input file
	 * @throws IOException
	 */
	private void registerManagedObjects(String snmpWalkFilePath) throws IOException {
		try (
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(ITJobUtils.getItResourcePath(snmpWalkFilePath))))
			)
		) {
			processManagedObjects(reader.lines().collect(Collectors.joining("\n")));
		}
	}

	/**
	 * Start the SNMP Agent and register the managed objects for the specified context from the given dataPaths
	 *
	 * @param snmpWalkFilePaths Paths of the files containing managed objects
	 * @throws IOException
	 */
	public void start(String... snmpWalkFilePaths) throws IOException {
		SecurityProtocols.getInstance().addDefaultProtocols();

		MANAGED_OBJECTS.clear();

		agent.initialize();

		registerManagedObjects(snmpWalkFilePaths);

		agent.setupProxyForwarder();
		agent.setTableSizeLimits(new Properties(50000));
		agent.run();

		started = true;
	}

	public void stop() {
		agent.shutdown();
		threadPool.stop();
		started = false;

		MANAGED_OBJECTS.clear();
	}

	public static void main(String[] args) throws IOException {
		final SnmpAgent snmpAgent = new SnmpAgent();
		snmpAgent.start("DellOpenManage/input/input.snmp");
	}

	/**
	 * Create a {@link Counter32} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link Counter32}
	 */
	private static MOScalar<Counter32> createCounter32Object(final String oid, final String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new Counter32(Long.valueOf(value)));
	}

	/**
	 * Create a {@link Counter64} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link Counter64}
	 */
	private static MOScalar<Counter64> createCounter64Object(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new Counter64(Long.valueOf(value)));
	}

	/**
	 * Create a {@link Gauge32} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link Gauge32}
	 */
	private static MOScalar<Gauge32> createGauge32Object(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new Gauge32(Long.valueOf(value)));
	}

	/**
	 * Create a {@link Integer32} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link Integer32}
	 */
	private static MOScalar<Integer32> createInteger32Object(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new Integer32(Integer.valueOf(value)));
	}

	/**
	 * Create an {@link IpAddress} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link IpAddress}
	 */
	private static MOScalar<IpAddress> createIpAddressObject(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new IpAddress(value));
	}

	/**
	 * Create a {@link Null} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link Null}
	 */
	private static MOScalar<Null> createNullObject(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, Null.noSuchObject);
	}

	/**
	 * Create an {@link OctetString} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link OctetString}
	 */
	private static MOScalar<OctetString> createOctetStringObject(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, buildOctetString(value));
	}

	/**
	 * Create an {@link OID} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link OID}
	 */
	private static MOScalar<OID> createOidObject(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new OID(value));
	}

	/**
	 * Create an {@link Opaque} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link Opaque}
	 */
	private static MOScalar<Opaque> createOpaqueObject(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new Opaque(value.getBytes()));
	}

	/**
	 * Create an {@link TimeTicks} scalar managed object
	 *
	 * @param oid   The Object Identifier
	 * @param value The value of the OID
	 * @return {@link MOScalar} wrapping a {@link TimeTicks}
	 */
	private static MOScalar<TimeTicks> createTimeTicksObject(String oid, String value) {
		return new MOScalar<>(new OID(oid), MOAccessImpl.ACCESS_READ_ONLY, new TimeTicks(Long.valueOf(value)));
	}

	/**
	 * Build the {@link OctetString} and decide whether we should consider it as Hexadecimal String or normal String
	 *
	 * @param value The {@link String} value we wish to process
	 * @return {@link OctetString}
	 */
	public static OctetString buildOctetString(String value) {
		// Check whether we should create the hex from string
		if (HEX_OCTET_STR_PATTERN.matcher(value).matches()) {
			return OctetString.fromHexString(value.replace(' ', ':'));
		}

		return new OctetString(value);
	}

	/**
	 * Line by line read the walk and register the managed objects in the server registry.
	 *
	 * @param walk SNMP walk
	 */
	private void processManagedObjects(String walk) {
		final Matcher snmpResultMatcher = SNMP_LINE_REGEX.matcher(walk);

		while (snmpResultMatcher.find()) {
			final String oid = snmpResultMatcher.group(1);
			final String type = snmpResultMatcher.group(2);
			// value could be in multiple lines
			final String value = snmpResultMatcher.group(3);

			// Get the function, default OctetString
			final BiFunction<String, String, MOScalar<? extends Variable>> function = ASN_TYPE_FUNCTIONS.getOrDefault(
				type,
				SnmpAgent::createOctetStringObject
			);

			try {
				final MOScalar<? extends Variable> scalar = function.apply(oid, value);

				// Index the object as we need it in the SnmpRequestProcessor for GET requests
				MANAGED_OBJECTS.put(oid, scalar);

				register(scalar);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Register the given {@link ManagedObject} in the {@link MOServer} registry
	 *
	 * @param mo {@link ManagedObject} instance we wish to register
	 */
	private void register(ManagedObject<?> mo) {
		((DefaultMOServer) server).getRegistry().put(mo.getScope(), mo);
	}

	public int getPort() {
		return port;
	}
}
