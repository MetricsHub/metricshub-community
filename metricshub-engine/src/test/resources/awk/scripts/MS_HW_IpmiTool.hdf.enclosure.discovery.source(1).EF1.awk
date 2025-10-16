BEGIN {
	FS = ";";
	systemType = "";
	systemVendor = "";
	systemModel = "";
	systemSerialNumber = "";
	foundSystemType = 0;
	systemPowerConsumption = "";
	systemStatus = "";
	systemStatusInformation = "";
	systemSensorNameList = "";
	machineStatus = "ON";
}
($1 == "FRU" && foundSystemType == 0) {
	systemVendor = $2;
	lcaseSystemVendor = tolower(systemVendor);
	systemModel = $3;
	systemSerialNumber = $4;

	if (substr(lcaseSystemVendor, 1, 4) == "sun " || substr(lcaseSystemVendor, 1, 6) == "oracle" )
	{
		if (tolower(systemModel) == "cmm")
		{
			next;
		}
		systemVendor = "Sun";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 7) == "hewlett" || substr(lcaseSystemVendor, 1, 2) == "hp")
	{
		systemVendor = "HP";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 4) == "dell")
	{
		systemVendor = "Dell";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 22) == "international business" || substr(lcaseSystemVendor, 1, 3) == "ibm" || systemModel ~ /^[Ss]ystem x/)
	{
		systemVendor = "IBM";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 5) == "cisco")
	{
		systemVendor = "Cisco";
		systemType = systemVendor;
		systemModel = "UCS " systemModel;
	}
	else if (substr(lcaseSystemVendor, 1, 7) == "fujitsu")
	{
		systemVendor = "Fujitsu-Siemens";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 3) == "nec")
	{
		systemVendor = "NEC";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 4) == "bull")
	{
		systemVendor = "BULL";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 10) == "supermicro")
	{
		systemVendor = "SuperMicro";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 4) == "acer")
	{
		systemVendor = "Acer";
		systemType = systemVendor;
	}
	else if (substr(lcaseSystemVendor, 1, 7) == "hitachi" || substr(lcaseSystemVendor, 1, 3) == "hds")
	{
		systemVendor = "Hitachi"
		systemType = systemVendor;
	}

	if (systemType != "")
	{
		foundSystemType = 1;
	}
}
($1 == "PowerConsumption") {

	##############################################################
	#
	# Section to be customized per vendor/model to handle
	# the power consumption of the monitored system
	#
	# (ex.: recognizing input/output currents, adding up the power
	# consumed by several power supplies, etc.)
	#
	##############################################################

	if (systemType == "Cisco")
	{
		if ($3 ~ /^PSU[0-9]_PIN$/ && $5 ~ /^[0-9]/)
		{
			systemPowerConsumption = systemPowerConsumption + $5;
		}
	}
	else
	{
		# By default, take the highest reported value
		if ($5 ~ /^[0-9]/)
		{
			if ($5 > systemPowerConsumption || systemPowerConsumption == "")
			{
				systemPowerConsumption = $5;
			}
		}
	}
}
{
	# Read the device characteristics
	deviceType = $1;
	deviceID = $2;
	entityID = $3;
	vendor = $4;
	model = $5;
	serialNumber = $6;
	sensorList = $7;

	# Skip empty stuff
	if (deviceType == "" || deviceID == "")
	{
		next;
	}

	# Skip SEL Fullness Temperature Devices
	if (deviceType == "Temperature" && entityID == "SEL Fullness")
	{
		next;
	}

	# Simply re-print numeric sensors
	if (deviceType == "Voltage" || deviceType == "Temperature" || deviceType == "Fan" || deviceType == "Current" || deviceType == "PowerConsumption" || deviceType == "EnergyUsage" || deviceType == "FRU")
	{
		print "MSHW;" $0;
		next;
	}

	##############################################################
	#
	# Section where you can customize the device identification
	# on a per-vendor basis
	#
	##############################################################
	if (systemType == "Sun")
	{
		if (sensorList ~ /nem[0-9]\./ || sensorList ~ "NEM[0-9]/")
		{
			deviceType = "NEM";
		}

		if (deviceType == "System Board")
		{
			if (sensorList ~ /bl[0-9]+\./)
			{
				deviceType = "Blade";
			}
		}
	}
	else if (systemType == "IBM")
	{
		if (deviceType == "Add-in Card")
		{
			if (sensorList ~ /SAS Riser/)
			{
				deviceType = "SAS Riser"
			}
			else if (sensorList ~ /PCI Riser/)
			{
				deviceType = "PCI Riser"
			}
		}
		else if (deviceType == "Power Module")
		{
			if (sensorList ~ /VRD Status/)
			{
				deviceType = "Voltage Regulator-Down"
			}
		}
		else if (deviceType == "Group")
		{
			if (sensorList ~ /Mem Card/)
			{
				deviceType = "Memory Device"
			}
		}
	}

	# Split that list into an array for further processing and interpretation
	sensorCount = split(sensorList, sensorArray, "\\|");

	status = "";
	statusInformation = "";
	sensorNameList = "";
	for (i=1 ; i<=sensorCount ; i++)
	{ status_atStart = status
		equalsIndex = index(sensorArray[i], "=");
		if (!equalsIndex)
		{
			continue;
		}
		sensorName = substr(sensorArray[i], 1, equalsIndex - 1);
		sensorReading = substr(sensorArray[i], equalsIndex + 1, length(sensorArray[i]) - equalsIndex);
		lcaseSensorReading = tolower(sensorReading);

		# Add the sensor name to the list of sensor names, so that the user will be able to see the list of
		# sensors associated with one device
		sensorNameList = sensorNameList sensorName ", ";

		# Sensors whose state can only be YES or NO (Asserted or Deasserted)
		# and that, logically, need to be specifically interpreted
		# Like (P0_OK == 1) means OK, while (P0_FAULT == 1) means ALARM, see?
		if (sensorReading == "" || sensorReading == 0 || sensorReading == 1)
		{
			# Specific stuff
			if (systemType == "Hitachi")
			{
				if (sensorName ~ / INTR$/)
				{
					# Skip MB0 INTR and similar sensors, because they only report when a button has been pressed
					next;
				}
				else if (sensorName ~ /PWR MGMT$/ || sensorName ~ /Power Mgmt$/)
				{
					# Skip power management sensors
					next;
				}
				else if (sensorName == "AGT_Notify")
				{
					# Skip AGT_Notify, which we can't do anything with, since we couldn't test it
					# (it's actually on OEM sensor)
					next;
				}
				else
				{
					# For all sensors with boolean value, in Hitachi systems, '1' means 'BAD'
					if (sensorReading == 1)
					{
						status = status "ALARM|";
						statusInformation = statusInformation sensorName " Asserted - ";
					}
					else
					{
						status = status "OK|";
					}
				}
			}


			# Dell Specific stuff
			else if (systemType == "Dell")
      {
				if (sensorName ~ /VCORE/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " VCORE Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /VCACHE [0-9]+ PG/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " VCACHE Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /PROC VTT PG/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " PROC VTT PG Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /[0-9.]+ PG/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Voltage Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /[0-9.]+ VIDEO PG/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " VIDEO Voltage Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
			}

			# Sun Specific stuff
			else if (systemType == "Sun")
			{
				if (sensorName ~ /^PS[0-9]+\057VINOK/)
				{ if (sensorReading == 0)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Voltage In Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
			  if (sensorName ~ /^PS[0-9]+\057PWROK/)
				{ if (sensorReading == 0)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Power In Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057FAN_ERR/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Fan In Fault - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057I_OUT_ERR/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Output Current Error - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057I_OUT_WARN/)
				{ if (sensorReading == 1)
					   {
						  status = status "WARN|";
						  statusInformation = statusInformation sensorName " Output Current Warning - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057T_ERR/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Temperature Error - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057FAN_ERR/)
				{ if (sensorReading == 1)
					   {
						  status = status "WARN|";
						  statusInformation = statusInformation sensorName " Temperature Warning - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057V_IN_ERR/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Input Voltage Error - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057V_IN_WARN/)
				{ if (sensorReading == 1)
					   {
						  status = status "WARN|";
						  statusInformation = statusInformation sensorName " Input Voltage Warning - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057V_OUT_ERR/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Output Voltage Error - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PS[0-9]+\057V_OUT_OK/)
				{ if (sensorReading == 0)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName " Output Voltage Not OK - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^INTSW/)
				{ if (sensorReading == 1)
					   {
						  status = status "OK|";
						  statusInformation = statusInformation sensorName " Intrusion Detected - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
				if (sensorName ~ /^PWRBS/)
				{ if (sensorReading == 1)
					   {
						  status = status "ALARM|";
						  statusInformation = statusInformation sensorName "  A power supply sensor has detected an error - ";
					   }
					    else
					   {
						  status = status "OK|";
					  }
			  }
			}

			# Non vendor-specific stuff
			else
			{
				# Try to be clever
				if (index(tolower(sensorName), "fault") || index(tolower(sensorName), "fail") || tolower(sensorName) ~ /err$/)
				{
					if (sensorReading == 1)
					{
						status = status "ALARM|";
						statusInformation = statusInformation sensorName " Asserted - ";
					}
					else
					{
						status = status "OK|";
					}
				}
				else
				{
					status = status sensorName "=" sensorReading "|";
				}
			}

		}

		# Here come OEM specific sensors, whose value need to be even more
		# specifically interpreted
		else if (substr(sensorReading, 1, 2) == "0x")
		{
			# Very specific stuff
			sensorReading = substr(sensorReading, 3, 4);
			digit1 = substr(sensorReading, 1, 1);
			digit2 = substr(sensorReading, 2, 1);
			digit3 = substr(sensorReading, 3, 1);
			digit4 = substr(sensorReading, 4, 1);

			if (systemType == "Cisco")
			{
				if (substr(sensorName, 1, 4) == "LED_")
				{
					if (digit3 == 1)
					{
						color = "Green";
						onStatus = "OK";
						offStatus = "OK";
						blinkingStatus = "OK";

					}
					else if (digit3 == 2)
					{
						color = "Amber";
						onStatus = "WARN";
						offStatus = "OK";
						blinkingStatus = "WARN";
					}
					else if (digit3 == 4)
					{
						color = "Blue";
						onStatus = "OK";
						offStatus = "OK";
						blinkingStatus = "OK";
					}
					else if (digit3 == 8)
					{
						color = "Red";
						onStatus = "WARN";
						offStatus = "OK";
						blinkingStatus = "WARN";
					}
					else
					{
						color = "";
						onStatus = "OK";
						offStatus = "OK";
						blinkingStatus = "OK";
					}

					if (digit4 == 1)
					{
						ledStatus = "Off";
					}
					else if (digit4 == 2)
					{
						ledStatus = "On";
					}
					else if (digit4 == 4)
					{
						ledStatus = "Blinking";
					}
					else if (digit4 == 8)
					{
						ledStatus = "Blinking";
					}
					else
					{
						ledStatus = "UNKNOWN";
					}
					print "MSHW;LED;" sensorName ";" sensorName ";" deviceID ";" color ";" onStatus ";" offStatus ";" blinkingStatus ";" ledStatus;
					continue;
				}
				else if (substr(sensorName, 1, 5) == "DDR3_")
				{
					if (digit2 == 1)
					{
						status = status "OK|";
					}
					else
					{
						status = status "ALARM|";
						statusInformation = statusInformation sensorName " Failed - ";
					}
				}
			}
			else
			{
				status = status sensorName "=" sensorReading "|";
			}
		}

		# And now, finally, the normal discrete sensors, with standard values
		# These should be easy to interpret, except when vendors screw up their instrumentation
		# chip, which does happen, unfortunately...
		else
		{
			# Cisco-specific mess
			if (systemType == "Cisco")
			{
				if (sensorName ~ /^SAS[0-9]+_LINK_STATUS$/)
				{
					if (lcaseSensorReading == "transition to off line")
					{
						status = status "OK|";
					}
					else
					{
						status = status "ALARM|";
						statusInformation = statusInformation "Disconnected - ";
					}
					continue;
				}
				else if (sensorName == "PSU_REDUNDANCY")
				{
					# Skip this one entirely, because it creates a "fake" power supply
					next;
				}
				else if (sensorName == "BIST_FAIL")
				{
					# Skip this sensor (but keep the device).  Remove name from Sensor List and continue to next sensor.
					gsubregex = sensorName ", "
					gsub(gsubregex,"",sensorNameList);
					continue;
				}
				else if (sensorName ~ /^HDD_[0-9]+_STATUS$/)
				{
					deviceID = substr(sensorName, 1, 6);
					if (lcaseSensorReading == "state 0 asserted" || lcaseSensorReading == "drive present")
					{
						status = status "OK|";
					}
					else
					{
						status = status "ALARM|";
						statusInformation = statusInformation "Faulty - ";
					}
					continue;
				}
			}

			# IBM Specific stuff
			else if (systemType == "IBM")
			{
				if (sensorName ~ /^Cooling Zone/)
				{
					# Skip, because it just says that the fans' redundancy is OK
					next;
				}
			}



			################################
			# IPMI standard states
			################################

			########### Digital states
			if (lcaseSensorReading == "predictive failure deasserted")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "predictive failure asserted")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Predicted Failure - ";
			}
			else if (lcaseSensorReading == "limit not exceeded")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "limit exceeded")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Limit Exceeded - ";
			}
			else if (lcaseSensorReading == "performance met")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "performance lags")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Performance Lags - ";
			}


			########## Availability states
			if (lcaseSensorReading == "device removed/device absent" || lcaseSensorReading == "device absent")
			{
				status = "ALARM|";
				statusInformation = "Missing reported by " sensorName;
				break;
			}
			else if (lcaseSensorReading == "device inserted/device present" || lcaseSensorReading == "device present")
			{
				# Present, well, it doesnt mean much, but at least it s detected
				status = status "OK|";
			}
			else if (lcaseSensorReading == "device enabled")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "device disabled")
			{
				status = status "OK|";
				statusInformation = statusInformation "Disabled - ";
			}
			else if (lcaseSensorReading == "transition to running")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "transition to in test")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": In Test - ";
			}
			else if (lcaseSensorReading == "transition to power off")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Power Off - ";
			}
			else if (lcaseSensorReading == "transition to on line")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Online - ";
			}
			else if (lcaseSensorReading == "transition to off line")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Offline - ";
			}
			else if (lcaseSensorReading == "transition to off duty")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Off Duty - ";
			}
			else if (lcaseSensorReading == "transition to degraded")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Degraded - ";
			}
			else if (lcaseSensorReading == "transition to power save")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Power Save - ";
			}
			else if (lcaseSensorReading == "install error")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Install Error - ";
			}


			########## Power states
			if (lcaseSensorReading == "d0 power state" || lcaseSensorReading == "d1 power state" || lcaseSensorReading == "d2 power state" || lcaseSensorReading == "d3 power state")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}

			########## Redundancy states
			if (lcaseSensorReading == "fully redundant")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "redundancy lost" || lcaseSensorReading == "redundancy degraded" || lcaseSensorReading == "redundancy degraded from fully redundant" || lcaseSensorReading == "redundancy degraded from non-redundant" || lcaseSensorReading == "non-redundant: sufficient from redundant" || lcaseSensorReading == "non-redundant: sufficient from insufficient" || lcaseSensorReading == "non-redundant: insufficient resources" || lcaseSensorReading == "non-redundant:sufficient resources from redundant redundancy has been lost but unit is functioning with minimum resources needed for normal operation" || lcaseSensorReading == "non-redundant:sufficient resources from insufficient resources unit has regained minimum resources needed for normal operation" || lcaseSensorReading == "non-redundant:insufficient resources unit is non-redundant and has insufficient resources to maintain normal operation" || lcaseSensorReading == "redundancy degraded from fully redundant unit has lost some redundant resource(s) but is still in a redundant state" || lcaseSensorReading == "redundancy degraded from non-redundant unit has regained some resource(s) and is redundant but not fully redundant")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}


			######### Physical security
			if (lcaseSensorReading == "general chassis intrusion" || lcaseSensorReading == "drive bay intrusion" || lcaseSensorReading == "i/o card area intrusion" || lcaseSensorReading == "processor area intrusion" || lcaseSensorReading == "system unplugged from lan" || lcaseSensorReading == "unauthorized dock" || lcaseSensorReading == "fan area intrusion")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}


			########## Platform security
			if (lcaseSensorReading == "front panel lockout violation attempted" || lcaseSensorReading == "pre-boot password violation - user password" || lcaseSensorReading == "pre-boot password violation - setup password" || lcaseSensorReading == "pre-boot password violation - network boot password" || lcaseSensorReading == "other pre-boot password violation" || lcaseSensorReading == "out-of-band access password violation")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}


			######### Processor states
			if (lcaseSensorReading == "ierr" || lcaseSensorReading == "thermal trip" || lcaseSensorReading == "frb1/bist failure" || lcaseSensorReading == "frb2/hang in post failure" || lcaseSensorReading == "frb3/processor startup/init failure" || lcaseSensorReading == "frb3/processor startup/initialization failure" || lcaseSensorReading == "configuration error" || lcaseSensorReading == "sm bios uncorrectable cpu-complex error" || lcaseSensorReading == "sm bios 'uncorrectable cpu-complex error'")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}
			else if (lcaseSensorReading == "presence detected" || lcaseSensorReading == "processor presence detected")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "throttled" || lcaseSensorReading == "processor automatically throttled")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Throttled - ";
			}
			else if (lcaseSensorReading == "disabled" || lcaseSensorReading == "terminator presence detected" || lcaseSensorReading == "terminator presence detected" || lcaseSensorReading == "processor disabled")
			{
				# Skip a disabled processor, or a terminator
				next;
			}


			########### Power supply states
			if (lcaseSensorReading == "presence detected")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "power supply failure detected" || lcaseSensorReading == "failure detected")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Failed - ";
			}
			else if (lcaseSensorReading == "predictive failure" || lcaseSensorReading == "predictive failure")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Predicted Failure - ";
			}
			else if (lcaseSensorReading == "power supply input lost (ac/dc)" || lcaseSensorReading == "power supply ac lost")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": AC Input Lost - ";
			}
			else if (lcaseSensorReading == "power supply input lost or out-of-range" || lcaseSensorReading == "ac lost or out-of-range")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": AC Input Lost or Out-of-Range - ";
			}
			else if (lcaseSensorReading == "power supply input out-of-range, but present" || lcaseSensorReading == "ac out-of-range, but present")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": AC Input Out-of-Range - ";
			}
			else if (lcaseSensorReading == "configuration error" || substr(lcaseSensorReading, 1, 12) == "config error")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Configuration Error - ";
			}


			######### Power unit states
			if (lcaseSensorReading == "power off/down" || lcaseSensorReading == "power cycle" || lcaseSensorReading == "240va power down" || lcaseSensorReading == "interlock power down" || lcaseSensorReading == "ac lost" || lcaseSensorReading == "soft-power control failure")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}
			else if (lcaseSensorReading == "failure detected")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Failed - ";
			}
			else if (lcaseSensorReading == "predictive failure")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Predicted Failure - ";
			}


			########## Memory states
			if (lcaseSensorReading == "correctable ecc" || lcaseSensorReading == "correctable ecc/other correctable memory error")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Corrected Errors - ";
			}
			else if (lcaseSensorReading == "uncorrectable ecc" || lcaseSensorReading == "uncorrectable ecc/other uncorrectable memory error")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Uncorrectable Errors - ";
			}
			else if (lcaseSensorReading == "parity")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "memory scrub failed")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Memory Scrub Failed - ";
			}
			else if (lcaseSensorReading == "memory device disabled")
			{
				# Skip this memory module
				next;
			}
			else if (lcaseSensorReading == "correctable ecc logging limit reached" || lcaseSensorReading == "correctable ecc/other correctable memory error logging limit reached")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Too Many Errors - ";
			}
			else if (lcaseSensorReading == "presence detected" || lcaseSensorReading == "presence detected")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "configuration error" || lcaseSensorReading == "configuration error")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Configuration Error - ";
			}
			else if (lcaseSensorReading == "spare")
			{
				status = status "OK|";
				statusInformation = statusInformation "Spare - ";
			}
			else if (lcaseSensorReading == "throttled")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Throttled - ";
			}


			########## Disk states
			if (lcaseSensorReading == "drive present")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "drive fault")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Faulty - ";
			}
			else if (lcaseSensorReading == "predictive failure")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Predicted Failure - ";
			}
			else if (lcaseSensorReading == "hot spare" || lcaseSensorReading == "in critical array" || lcaseSensorReading == "in failed array")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}
			else if (lcaseSensorReading == "parity check in progress" || lcaseSensorReading == "rebuild in progress")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}
			else if (lcaseSensorReading == "rebuild aborted")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Rebuild Aborted - ";
			}

			########### Cable interconnect states
			if (lcaseSensorReading == "connected")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "config error")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Configuration Error - ";
			}

			########### Boot error states
			if (lcaseSensorReading == "no bootable media" || lcaseSensorReading == "non-bootable disk in drive" || lcaseSensorReading == "pxe server not found" || lcaseSensorReading == "invalid boot sector" || lcaseSensorReading == "timeout waiting for selection")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": " lcaseSensorReading " - ";
			}

			########## Slot/connector states
			if (lcaseSensorReading == "fault status")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Faulty - ";
			}
			else if (lcaseSensorReading == "identify status" || lcaseSensorReading == "device installed" || lcaseSensorReading == "ready for device installation" || lcaseSensorReading == "ready for device removal" || lcaseSensorReading == "slot power is off" || lcaseSensorReading == "device removal request" || lcaseSensorReading == "interlock" || lcaseSensorReading == "slot is disabled" || lcaseSensorReading == "spare device")
			{
				status = status "OK|";
			}


			########## Presence states
			if (lcaseSensorReading == "present" || lcaseSensorReading == "entity present")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "absent" || lcaseSensorReading == "disabled" || lcaseSensorReading == "entity absent" || lcaseSensorReading == "entity disabled")
			{
				# Skip
				next;
			}


			########## LAN states
			if (lcaseSensorReading == "heartbeat lost")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Heatbeat Lost - ";
			}
			else if (lcaseSensorReading == "heartbeat")
			{
				status = status "OK|";
			}


			########## Battery states
			if (lcaseSensorReading == "low" || lcaseSensorReading == "battery low")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Low - ";
			}
			else if (lcaseSensorReading == "failed" || lcaseSensorReading == "battery failed")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Failed - ";
			}
			else if (lcaseSensorReading == "presence detected" || lcaseSensorReading == "battery presence detected")
			{
				status = status "OK|";
			}


			########### Threshold states
			if (lcaseSensorReading == "lower non-critical going low")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Lower Non-critical going low - ";
			}
			else if (lcaseSensorReading == "lower non-critical going high")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "lower critical going low")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Lower Critical going low - ";
			}
			else if (lcaseSensorReading == "lower critical going high")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Lower Critical going high - ";
			}
			else if (lcaseSensorReading == "lower non-recoverable going low")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Lower Non-recoverable going low - ";
			}
			else if (lcaseSensorReading == "lower non-recoverable going high")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Lower Non-recoverable going high - ";
			}
			else if (lcaseSensorReading == "upper non-critical going low")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "upper non-critical going high")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Upper Non-critical going high - ";
			}
			else if (lcaseSensorReading == "upper critical going low")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Upper Critical going low - ";
			}
			else if (lcaseSensorReading == "upper critical going high")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Upper Critical going high - ";
			}
			else if (lcaseSensorReading == "upper non-recoverable going low")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Upper Non-recoverable going low - ";
			}
			else if (lcaseSensorReading == "upper non-recoverable going high")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Upper Non-recoverable going high - ";
			}


			########### Usage States
			if (lcaseSensorReading == "transition to idle")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Idle - ";
			}
			else if (lcaseSensorReading == "transition to active")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Active - ";
			}
			else if (lcaseSensorReading == "transition to busy")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Busy - ";
			}


			########## Severity States
			if (lcaseSensorReading == "transition to ok")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "transition to non-critical from ok" || lcaseSensorReading == "transition to non-critical from ok")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Non-critical - ";
			}
			else if (lcaseSensorReading == "transition to critical from less severe")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Critical - ";
			}
			else if (lcaseSensorReading == "transition to non-recoverable from less severe")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Non-recoverable - ";
			}
			else if (lcaseSensorReading == "transition to non-critical from more severe" || lcaseSensorReading == "transition to non-critical from more severe")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Non-critical - ";
			}
			else if (lcaseSensorReading == "transition to critical from non-recoverable")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Critical - ";
			}
			else if (lcaseSensorReading == "transition to non-recoverable")
			{
				status = status "ALARM|";
				statusInformation = statusInformation sensorName ": Non-recoverable - ";
			}
			else if (lcaseSensorReading == "monitor")
			{
				status = status "WARN|";
				statusInformation = statusInformation sensorName ": Monitor - ";
			}
			else if (lcaseSensorReading == "informational")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Informational - ";
			}

			######### System ACPI Power State
			if (lcaseSensorReading == "s0/g0: working" || lcaseSensorReading == "s0/g0 working" || lcaseSensorReading == "legacy on state")
			{
				status = status "OK|";
			}
			else if (lcaseSensorReading == "s1: sleeping with system hw & processor context maintained" || lcaseSensorReading == "s1 sleeping with system h/w and processor context maintained" || lcaseSensorReading == "s2: sleeping, processor context lost" || lcaseSensorReading == "s2 sleeping,processor context lost" || lcaseSensorReading == "s3: sleeping, processor & hw context lost, memory retained" || lcaseSensorReading == "s3 sleeping,processor and h/w context lost, memory maintained" || lcaseSensorReading == "s4: non-volatile sleep/suspend-to-disk" || lcaseSensorReading == "s4 non-volatile sleep/suspend to disk" || lcaseSensorReading == "sleeping in s1/s2/s3 state" || lcaseSensorReading == "sleeping in an s1,s2 or s3 states" || lcaseSensorReading == "g1: sleeping" || lcaseSensorReading == "g1 sleeping")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": Sleeping - ";
				machineStatus = "Sleeping";
			}
			else if (lcaseSensorReading == "s5/g2: soft-off" || lcaseSensorReading == "s5/g2 soft-off" || lcaseSensorReading == "s4/s5: soft-off" || lcaseSensorReading == "s4/s5 soft-off" || lcaseSensorReading == "g3: mechanical off" || lcaseSensorReading == "g3/mechanical off" || lcaseSensorReading == "s5: entered by override" || lcaseSensorReading == "s5 entered by override" || lcaseSensorReading == "legacy off state")
			{
				status = status "OK|";
				statusInformation = statusInformation sensorName ": OFF - ";
				machineStatus = "OFF";
			}

			########### Other unknown states
			else if (status_atStart == status)
			{
				status = status sensorName "=" sensorReading "|"
			}


		}

		#print "MSHW;" sensorName ";" sensorReading;
	}

	# Last phase of processing
	# Make sure we at least got something, otherwise it means we are not able to intepret anything anyway
	if (status == "")
	{
		next;
	}

	# Last question before we go, is this a system board, or BIOS, or stuff like that?
	# In which case, we''re going to attach that to the main enclosure
	if (tolower(deviceType) == "bios" || tolower(deviceType) == "system board")
	{
		systemStatus = systemStatus status;
		systemStatusInformation = systemStatusInformation statusInformation;
		systemSensorNameList = systemSensorNameList sensorNameList;
		next;
	}

	# Remove trailing comma at the end of sensorNameList
	if (length(sensorNameList) > 2)
	{
		sensorNameList = substr(sensorNameList, 1, length(sensorNameList) - 2);
	}

	# Good!
	print "MSHW;" deviceType ";" deviceID ";" entityID ";" vendor ";" model ";" serialNumber ";" status ";" statusInformation ";Monitored by sensors: " sensorNameList;
}
END {
	# At the very end, provide information about the system, including (if possible), power consumption, etc.

	# By the way, if we got no systemPowerConsumption but we know that the machine is sleeping of OFF, provide a low value instead
	# of nothing. Because if we provide no value, the KM (and PM) will try to evaluate the power consumption based on the devices
	# that we have discovered. Unfortunately, this will not take into account the fact that the machine may be not running, and
	# thus calculate a value that is meaningless
	if (systemPowerConsumption == "")
	{
		if (machineStatus == "Sleeping")
		{
			# If sleeping, assume 10 Watts for the system, plus another 10 Watts for the management card
			# which is still running (because able to tell us that the main system is sleeping)
			systemPowerConsumption = 20;
		}
		else if (machineStatus == "OFF")
		{
			# If OFF, assume 0 Watts for the main system, plus another 10 Watts for the management card
			# (again, the management card must be present and running, since it told us the system was OFF)
			systemPowerConsumption = 10;
		}

		# If the machine is ON, leave the powerConsumption empty (as it was before we arrived in this place)
		# so that Hardware will evaluate the power consumption by himself
	}

	# Remove trailing comma at the end of systemSensorNameList
	if (length(systemSensorNameList) > 2)
	{
		systemSensorNameList = substr(systemSensorNameList, 1, length(systemSensorNameList) - 2);
	}

	# Print the enclosure stuff
	print "MSHW;Enclosure;" systemVendor ";" systemModel ";" systemSerialNumber ";" systemStatus ";" systemStatusInformation ";" systemPowerConsumption ";Monitored by sensors: " systemSensorNameList;
}
