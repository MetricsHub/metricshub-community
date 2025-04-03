package org.metricshub.hardware.common;

public class Constants {

	public static final String FAN_SPEED_METRIC = "hw.fan.speed";
	public static final String FAN_SPEED_RATIO_METRIC = "hw.fan.speed_ratio";
	public static final String FAN_POWER_METRIC = "hw.power{hw.type=\"fan\"}";
	public static final String FAN_ENERGY_METRIC = "hw.energy{hw.type=\"fan\"}";
	public static final String ROBOTICS_MOVE_COUNT_METRIC = "hw.robotics.moves";
	public static final String ROBOTICS_POWER_METRIC = "hw.power{hw.type=\"robotics\"}";
	public static final String ROBOTICS_ENERGY_METRIC = "hw.energy{hw.type=\"robotics\"}";
	public static final String TAPE_DRIVE_MOUNT_COUNT_METRIC = "hw.tape_drive.operations{type=\"mount\"}";
	public static final String TAPE_DRIVE_UNMOUNT_COUNT_METRIC = "hw.tape_drive.operations{type=\"unmount\"}";
	public static final String TAPE_DRIVE_POWER_METRIC = "hw.power{hw.type=\"tape_drive\"}";
	public static final String TAPE_DRIVE_ENERGY_METRIC = "hw.energy{hw.type=\"tape_drive\"}";
	public static final String DISK_CONTROLLER_POWER_METRIC = "hw.power{hw.type=\"disk_controller\"}";
	public static final String DISK_CONTROLLER_ENERGY_METRIC = "hw.energy{hw.type=\"disk_controller\"}";
	public static final String MEMORY_ENERGY_METRIC = "hw.energy{hw.type=\"memory\"}";
	public static final String MEMORY_POWER_METRIC = "hw.power{hw.type=\"memory\"}";
	public static final String NETWORK_POWER_METRIC = "hw.power{hw.type=\"network\"}";
	public static final String NETWORK_ENERGY_METRIC = "hw.energy{hw.type=\"network\"}";
	public static final String NETWORK_LINK_STATUS_METRIC = "hw.network.up";
	public static final String NETWORK_LINK_SPEED_ATTRIBUTE = "hw.network.bandwidth.limit";
	public static final String NETWORK_TRANSMITTED_BANDWIDTH_UTILIZATION_METRIC =
		"hw.network.bandwidth.utilization{direction=\"transmit\"}";

	public static final String LOCALHOST = "localhost";
	public static final String HW_HOST_AVERAGE_CPU_TEMPERATURE = "__hw.host.average_cpu_temperature";
	public static final String HW_HOST_AMBIENT_TEMPERATURE = "hw.host.ambient_temperature";
	public static final String TEMPERATURE_METRIC = "hw.temperature";
	public static final String PHYSICAL_DISK_POWER_METRIC = "hw.power{hw.type=\"physical_disk\"}";
	public static final String PHYSICAL_DISK_ENERGY_METRIC = "hw.energy{hw.type=\"physical_disk\"}";

	public static final String HW_ENCLOSURE_POWER = "hw.enclosure.power";
	public static final String HW_CPU_POWER = "hw.power{hw.type=\"cpu\"}";
	public static final String HW_MEMORY_POWER = "hw.power{hw.type=\"memory\"}";
	public static final String HW_PHYSICAL_DISK_POWER = "hw.power{hw.type=\"physical_disk\"}";

	public static final String HOST_1 = "host-1";
	public static final String HOST = "host";
	public static final String VM_ONLINE_ZERO_POWER_SHARE_1 = "vmOnlineZeroPowerShare1";
	public static final String VM_1_ONLINE = "vm1Online";
	public static final String VM_OFFLINE_2 = "vmOffline2";
	public static final String VM_ONLINE_3 = "vmOnline3";
	public static final String VM_ONLINE_NO_POWER_SHARE_4 = "vmOnlineNoPowerShare4";
	public static final String VM_ONLINE_BAD_POWER_SHARE_5 = "vmOnlineBadPowerShare5";
	public static final String ON = "on";
	public static final String CPU_POWER_METRIC = "hw.power{hw.type=\"cpu\"}";
	public static final String CPU_ENERGY_METRIC = "hw.energy{hw.type=\"cpu\"}";

	public static final String HW_CONNECTOR = "hw_connector";
	public static final String DISK_CONTROLLER_PRESENT_METRIC =
		"hw.status{hw.type=\"disk_controller\", state=\"present\"}";
	public static final String ENCLOSURE_PRESENT_METRIC = "hw.status{hw.type=\"enclosure\", state=\"present\"}";
}
