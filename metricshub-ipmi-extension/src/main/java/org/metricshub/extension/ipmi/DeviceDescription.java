package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub IPMI Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;

/**
 * {@link EntityId} mapping to match the IPMIUtil C code.
 */
public final class DeviceDescription {

	private DeviceDescription() {}

	private static final Map<EntityId, String> ENTITY_ID_TO_ENTITY_DESCRIPTION;

	static {
		Map<EntityId, String> entityIdMap = new EnumMap<>(EntityId.class);

		entityIdMap.put(EntityId.Unspecified, EntityId.Unspecified.name());
		entityIdMap.put(EntityId.Other, EntityId.Other.name());
		entityIdMap.put(EntityId.Unknown, "Unknown");
		entityIdMap.put(EntityId.Processor, EntityId.Processor.name());
		entityIdMap.put(EntityId.Disk, "Disk or Disk Bay");
		entityIdMap.put(EntityId.PeripheralBay, "Peripheral Bay");
		entityIdMap.put(EntityId.SystemManagementModule, "System Management Module");
		entityIdMap.put(EntityId.SystemBoard, "System Board");
		entityIdMap.put(EntityId.MemoryModule, "Memory Module");
		entityIdMap.put(EntityId.ProcesorModule, "Processor Module");
		entityIdMap.put(EntityId.PowerSupply, "Power Supply");
		entityIdMap.put(EntityId.AddInCard, "Add-in Card");
		entityIdMap.put(EntityId.FrontPanelBoard, "Front Panel Board");
		entityIdMap.put(EntityId.BackPanelBoard, "Back Panel Board");
		entityIdMap.put(EntityId.PowerSystemBoard, "Power System Board");
		entityIdMap.put(EntityId.DriveBackplane, "Drive Backplane");
		entityIdMap.put(EntityId.SystemInternalExpansionBoard, "System Internal Expansion Board");
		entityIdMap.put(EntityId.OtherSystemBoard, "Other System Board");
		entityIdMap.put(EntityId.ProcessorBoard, "Processor Board");
		entityIdMap.put(EntityId.PowerUnit, "Power Unit");
		entityIdMap.put(EntityId.PowerModule, "Power Module");
		entityIdMap.put(EntityId.PowerManagement, "Power Management");
		entityIdMap.put(EntityId.ChassisBackPanelBoard, "Chassis Back Panel Board");
		entityIdMap.put(EntityId.SystemChassis, "System Chassis");
		entityIdMap.put(EntityId.SubChassis, "Sub-Chassis");
		entityIdMap.put(EntityId.OtherChassis, "Other Chassis Board");
		entityIdMap.put(EntityId.DiskDriveBay, "Disk Drive Bay");
		entityIdMap.put(EntityId.PeripheralBay2, "Peripheral Bay");
		entityIdMap.put(EntityId.DeviceBay, "Device Bay");
		entityIdMap.put(EntityId.Fan, "Fan Device");
		entityIdMap.put(EntityId.CoolingUnit, "Cooling Unit");
		entityIdMap.put(EntityId.CableInterconnect, "Cable/Interconnect");
		entityIdMap.put(EntityId.MemoryDevice, "Memory Device");
		entityIdMap.put(EntityId.SystemManagementSoftware, "System Management Software");
		entityIdMap.put(EntityId.SystemFirmware, "BIOS");
		entityIdMap.put(EntityId.OperatingSystem, "Operating System");
		entityIdMap.put(EntityId.SystemBus, "System Bus");
		entityIdMap.put(EntityId.Group, EntityId.Group.name());
		entityIdMap.put(EntityId.RemoteManagementCommunicationDevice, "Remote Management Device");
		entityIdMap.put(EntityId.ExternalEnvironment, "External Environment");
		entityIdMap.put(EntityId.Battery, EntityId.Battery.name());
		entityIdMap.put(EntityId.ProcessingBlade, "Processing Blade");
		entityIdMap.put(EntityId.ConnectivitySwitch, "Connectivity Switch");
		entityIdMap.put(EntityId.ProcessorMemoryModule, "Processor/Memory Module");
		entityIdMap.put(EntityId.IoModule, "I/O Module");
		entityIdMap.put(EntityId.ProcessorIoModule, "Processor/IO Module");
		entityIdMap.put(EntityId.ManagementControllerFirmware, "Management Controller Firmware");
		entityIdMap.put(EntityId.IpmiChannel, "IPMI Channel");
		entityIdMap.put(EntityId.PciBus, "PCI Bus");
		entityIdMap.put(EntityId.PciExpressBus, "PCI Express Bus");
		entityIdMap.put(EntityId.ScsiBus, "SCSI Bus (parallel)");
		entityIdMap.put(EntityId.SataBus, "SATA/SAS Bus");
		entityIdMap.put(EntityId.FrontSideBus, "Processor/Front-Side Bus");
		entityIdMap.put(EntityId.RealTimeClock, "Real-time Clock");
		entityIdMap.put(EntityId.AirInlet, "Air Inlet");
		entityIdMap.put(EntityId.AirInlet2, "Air Inlet");
		entityIdMap.put(EntityId.Processor2, EntityId.Processor.name());
		entityIdMap.put(EntityId.Baseboard, "System Board");

		ENTITY_ID_TO_ENTITY_DESCRIPTION = Collections.unmodifiableMap(entityIdMap);
	}

	/**
	 * @param entityId The {@link EntityId} key
	 * @return {@link String} value extracted from the internal lookup matching the output of IPMIUtil and the java verax
	 *         IPMI lib
	 */
	public static String getDeviceType(EntityId entityId) {
		return entityId != null ? ENTITY_ID_TO_ENTITY_DESCRIPTION.getOrDefault(entityId, entityId.name()) : null;
	}
}
