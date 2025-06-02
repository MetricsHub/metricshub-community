package org.metricshub.extension.jmx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Given a JmxSource (with host, port, and 0..N MBeanConfig entries), connects and fetches each configured MBean’s attributes.
 */
@Slf4j
public class JmxSourceProcessor {

	public static SourceTable process(JmxSource src) {
		String host = src.getHost();
		int port = src.getPort();
		String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port);

		try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(url))) {
			MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
			List<List<String>> rows = new ArrayList<>();

			// Normalize single‐vs‐multiple MBeanConfig
			List<JmxSource.MBeanConfig> configs = src.getMbeans() != null
				? src.getMbeans()
				: Collections.singletonList(src.getMbean());

			for (JmxSource.MBeanConfig cfg : configs) {
				ObjectName beanPattern = new ObjectName(cfg.getObjectName());
				Set<ObjectName> mbeans = mbsc.queryNames(beanPattern, null);

				for (ObjectName name : mbeans) {
					MBeanInfo info = mbsc.getMBeanInfo(name);
					MBeanAttributeInfo[] allAttrs = info.getAttributes();

					List<String> row = new ArrayList<>();
					if (cfg.getKeysAsAttributes() != null) {
						for (String key : cfg.getKeysAsAttributes()) {
							row.add(name.getKeyProperty(key));
						}
					}

					for (String attr : cfg.getAttributes()) {
						MBeanAttributeInfo ai = findAttribute(allAttrs, attr);
						if (ai == null || !ai.isReadable()) {
							row.add("");
						} else {
							try {
								Object v = mbsc.getAttribute(name, attr);
								row.add(v == null ? "" : v.toString());
							} catch (Exception e) {
								log.warn("Failed reading {} from {}: {}", attr, name, e.getMessage());
								row.add("");
							}
						}
					}
					rows.add(row);
				}
			}

			SourceTable table = new SourceTable();
			table.setTable(rows);
			return table;
		} catch (IOException e) {
			log.error("I/O error connecting to {}: {}", url, e.getMessage());
		} catch (InstanceNotFoundException | ReflectionException | IntrospectionException e) {
			log.error("Error retrieving MBean info: {}", e.getMessage());
		} catch (Exception e) {
			log.error("Unexpected error in JMX source: {}", e.toString());
		}

		return SourceTable.empty();
	}

	private static MBeanAttributeInfo findAttribute(MBeanAttributeInfo[] infos, String name) {
		for (MBeanAttributeInfo info : infos) {
			if (info.getName().equals(name)) {
				return info;
			}
		}
		return null;
	}
}
