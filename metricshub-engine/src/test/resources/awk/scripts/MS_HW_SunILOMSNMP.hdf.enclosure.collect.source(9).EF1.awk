BEGIN {FS="[;]";ChassisFound=0}
function AppendStatus(StatusID)
	{ if (Status != "") {PrintArray[StatusID] = 1
	      StatusArray[StatusID] = StatusArray[StatusID] "|" Status
	      SensorArray[StatusID] = SensorArray[StatusID] " " Name
	      if (Status < 7) {StatusInformation[StatusID] = StatusInformation[StatusID] " " Name}
	     }
	# For numeric sensors we initialize Status to ensure that the sensors are printed.
	}

function createMSHWPRINT(MSHWCLASS,ID)
	{
		# Before we start, lets see if we can reclassify MSHW_OTHER devices by their name
		if (MSHWCLASS ~ /MSHW_OTHER/) {
			                             #Unwanted Sensors - Set Status to Null
		                               if (Name ~ /.OK2RM$/ ) { Status = ""}

			                             #CPU
			                             if ( Name ~ /MB.P[0-9]+$/ ) { MSHWCLASS = "MSHW_CPU" }
#			                             if ( Name ~ /MB.CMP[0-9]+.CORE[0-9]+/ ) { MSHWCLASS = "MSHW_CPU" }
			                             #ENC

			                             #NETWORK

			                             #PHYDISK
			                             if ( Name ~ /HDD[0-9]/ ) { MSHWCLASS = "MSHW_PHYDISK" }

			                             #PSU
			                             if ( Name ~ /SYS.PS_FAULT/ ) { MSHWCLASS = "MSHW_PSU" }
			                             if ( Name ~ /SYS.PS[0-9]/ ) { MSHWCLASS = "MSHW_PSU" }

			                             #OTHER

			                             #FAN
			                             if ( Name ~ /FM[0-9]/ ) { MSHWCLASS = "MSHW_FAN" }
			                             if ( Name ~ /SYS.FAN_FAULT]/ ) { MSHWCLASS = "MSHW_FAN" }
			                             if ( Name ~ /FANBD]/ ) { MSHWCLASS = "MSHW_FAN" }

			                             #VOLT

			                             #TEMP
			                             if ( Name ~ /TEMP_FAULT/ ) { MSHWCLASS = "MSHW_TEMP" }

			                             #MEM
			                             if ( Name ~ /DIMM/ ) { MSHWCLASS = "MSHW_MEM" }
			                             if ( Name ~ /MB.P[0-9]+.D[0-9]+.SERVICE/ ) { MSHWCLASS = "MSHW_MEM" }

			                             #LED
			                             if ( Name ~ /LOCATE/ ) { MSHWCLASS = "MSHW_LED" }
			                             if ( Name ~ /^.SYS.OK$]/ ) { MSHWCLASS = "MSHW_LED" }
			                            }
		# Now let's reclassify troublesome sensors
		if (MSHWCLASS ~ /MSHW_ENC/ && Name ~ /SYS.MIDPLANE/) { MSHWCLASS = "MSHW_OTHER" }
		if (MSHWCLASS ~ /MSHW_ENC/ && Name ~ /SYS.IOCFG/) { MSHWCLASS = "MSHW_OTHER" }
		# Print by class
		if (MSHWCLASS ~ /MSHW_CPU/) { functionreturns = "MSHW_CPU;"ID";"Name";" }
		else if (MSHWCLASS ~ /MSHW_ENC/) {
		                                  # The model number for Enclosure is not very good, let's use the PhysDesc
		                                  Model = PhysDesc;
		                                  functionreturns = "MSHW_ENC;"ID";"Name";"Firmware";"SerialNumber";"Manufacturer";"Model";"
		                                 }
		else if (MSHWCLASS ~ /MSHW_NETWORK/) { functionreturns = "MSHW_NETWORK;"ID";"Name";" }
		else if (MSHWCLASS ~ /MSHW_PHYDISK/) { functionreturns = "MSHW_PHYDISK;"ID";"Name";"SerialNumber";"Manufacturer";"Model";"  }
		else if (MSHWCLASS ~ /MSHW_PSU/) { functionreturns = "MSHW_PSU;"ID";"Name";"SerialNumber";"Manufacturer";"Model";" }
		else if (MSHWCLASS ~ /MSHW_OTHER/) { functionreturns = "MSHW_OTHER;"PhysDesc";"ID";"Name";" }
		else if (MSHWCLASS ~ /MSHW_FAN/) { functionreturns = "MSHW_FAN;"ID";"Name";" }
		else if (MSHWCLASS ~ /MSHW_VOLT/) {
		                                    if ( LowerNC != "") { LowerNC = LowerNC * 1000}
		                                    if ( UpperNC != "") { UpperNC = UpperNC * 1000}
		                                    if ( CurrentValue != "") { CurrentValue = CurrentValue * 1000}
		                                    functionreturns = "MSHW_VOLT;"ID";"Name";"CurrentValue";"LowerNC";"UpperNC";"
		                                  }
		else if (MSHWCLASS ~ /MSHW_TEMP/) { functionreturns = "MSHW_TEMP;"ID";"Name";"CurrentValue";"UpperNC";"UpperC";" }
		else if (MSHWCLASS ~ /MSHW_MEM/) { functionreturns = "MSHW_MEM;"ID";"Name";"Size";"SerialNumber";"Manufacturer";"Model";" }
		else if (MSHWCLASS ~ /MSHW_LED/) {}
		else {functionreturns = "ERROR UNKNOWN CLASS"}
		return functionreturns
	}

# MAIN AWK

NF > 8 && $1 ~ /^[0-9]+$/ {
	# Deal with the numeric exponent
	Exponent     = $19
	if (Exponent == -3) { Exponent = 0.001 }
	   else if (Exponent == -2) { Exponent = 0.01 }
	   else if (Exponent == -1) { Exponent = 0.1 }
		 else if (Exponent == 0) { Exponent = 1 }
		 else if (Exponent == 1) { Exponent = 10 }
		 else if (Exponent == 2) { Exponent = 100 }
		 else if (Exponent == 3) { Exponent = 1000 }
		 else {Exponent = 1}

	SensorID     = $1
	PhysDesc     = $2
	ParentID     = $3
	Class        = $4
	Name         = $5
	Firmware     = $6
	SerialNumber = $7
	Manufacturer = $8
	Model        = $9
#	SensorType   = $11
	SensorClass  = $12
	Status       = $13
	CurrentValue = $14 ; CurrentValue = CurrentValue * Exponent
	LowerNC      = $15 ; LowerNC = LowerNC * Exponent ; if (LowerNC==0) {LowerNC = ""}
	UpperNC      = $16 ; UpperNC = UpperNC * Exponent ; if (UpperNC==0) {UpperNC = ""}
	LowerC       = $17 ; LowerC = LowerC * Exponent ; if (LowerC==0) {LowerC = ""}
	UpperC       = $18 ; UpperC = UpperC * Exponent ; if (UpperC==0) {UpperC = ""}

	# 1 - Other
	if (Class == 1) {
		               if ( PhysDesc ~ /^BIOS/ ) { PhysDesc = "BIOS"
		               	                          MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID)
		                                          AppendStatus(SensorID)
		                                        }
	                 else if ( PhysDesc ~ /^Network/ ) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_NETWORK",SensorID)
	                 			                         AppendStatus(SensorID)
		                                           }
	                 else if ( PhysDesc ~ /^Processor/ || PhysDesc ~ /^Host Processor/ ) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_CPU",SensorID)
	                 			                         AppendStatus(SensorID) ;
		                                           }
		               else if ( PhysDesc ~ /^CPU Core/ ) { AppendStatus(ParentID)
	                 		                              	  # Create a Child ID in case the Parent is not found
	                 	                                    MSHWCHILDPRINT[ParentID] = createMSHWPRINT("MSHW_OTHER",ParentID)
	                 	                                    coreParentID[SensorID]=ParentID
	                 	                                  }
		               else if ( PhysDesc ~ /^L1 Bank/ ) { # add another layer of parenting
		               	                                    coreParentID[SensorID]=coreParentID[ParentID]
		               	                                    AppendStatus(coreParentID[ParentID])
		               	                                 }
		               else if ( PhysDesc ~ /^[ID]Cache/ ) {   # add another layer of parenting
		               	                                    coreParentID[SensorID]=coreParentID[ParentID] ;
		               	                                    AppendStatus(coreParentID[ParentID])
		               	                                 }
		               else if ( PhysDesc ~ /^L[0-9]+ Bank/ ) {   # add another layer of parenting
		               	                                    coreParentID[SensorID]=coreParentID[ParentID]  ;
		               	                                    AppendStatus(coreParentID[ParentID]) ;
		               	                                 }

	                 else if ( PhysDesc ~ /^Indicator/ ) {#These should be LEDs, so translate status
	                                                      # 8=Off  9=On 10=Blinking
	                                                      # to 2=Major 5=Warn 7=OK
                                                        if ((Name ~ /FAULT$/) || (Name ~ /SERVICE$/) ) {
                                                            if (Status == 8 ) { Status = 7}
                                                            if (Status == 9 ) { Status = 2}
                                                            if (Status == 10 ) { Status = 2}
                                                            }
                                                        if (Name ~ /OK$/ ) {
                                                            if (Status == 8 ) { Status = 2}
                                                            if (Status == 9 ) { Status = 7}
                                                            if (Status == 10 ) { Status = 7}
                                                            }
                                                        if (Name ~ /OK2RM$/ ) {
                                                            if (Status == 8 ) { Status = 7}
                                                            if (Status == 9 ) { Status = 5}
                                                            if (Status == 10 ) { Status = 5}
                                                            }
	                                                      AppendStatus(ParentID)
	                 		                              	  # Create a Child ID in case the Parent is not found
	                 	                                    MSHWCHILDPRINT[ParentID] = createMSHWPRINT("MSHW_OTHER",ParentID)
		                                                  }
                   else {MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID)
                         AppendStatus(SensorID)
                        }
	                }
	# 2 - Unknown
	if (Class == 2) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID)
                    AppendStatus(SensorID)
	                }
	# 3 - Chassis
	if (Class == 3) {	MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_ENC",SensorID)
		                AppendStatus(SensorID)
		                ChassisFound=1
		                PrintArray[SensorID] = 1
		              }
	# 4 - Backplane
	if (Class == 4) { PhysDesc = "Backplane"
		                MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID)
		                AppendStatus(SensorID)
	                }
	# 5 - Container
	if (Class == 5) {
	                  if ( PhysDesc ~ /Motherboard/ ) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID)
                                                      AppendStatus(SensorID)
	                  	                              }
	                  else if ( PhysDesc ~ /Disk/ ) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_PHYDISK",SensorID)
	                  	                              AppendStatus(SensorID)
	                  	                            }
                    else {MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID) }
	                }

	# 6 - PowerSupply
	if (Class == 6) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_PSU",SensorID)
	                  AppendStatus(SensorID)
	                }
	# 7 - Fan
	if (Class == 7) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_FAN",SensorID)
	                  AppendStatus(SensorID)
	                }
	# 8 - Sensor
	if (Class == 8) {
                   # 1 - Other - Attach it to its parent
	                 if (SensorClass == 1) {AppendStatus(ParentID)
	                 		                 	  # Create a Child ID in case the Parent is not found
	                 	                      MSHWCHILDPRINT[ParentID] = createMSHWPRINT("MSHW_OTHER",ParentID)
	                 	                     }
	                 # 4 - Voltage
	                 if (SensorClass == 4) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_VOLT",SensorID)
	                 	                       PrintArray[SensorID] = 1
	                 	                       AppendStatus(SensorID)
	                 	                     }
	                 # 3 - Temperature
	                 if (SensorClass == 3) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_TEMP",SensorID)
	                 	                       PrintArray[SensorID] = 1
	                 	                       AppendStatus(SensorID)
	                 	                     }
	                 # 6 - Tachometer - Attach it to its parent - Set MSHWNUMERIC to add numeric columns to the end.
	                 if (SensorClass == 6) { MSHWNUMERIC[ParentID] = CurrentValue";"LowerNC";"LowerC";"
	                 	                       PrintArray[ParentID] = 1
	                 	                       AppendStatus(ParentID)
	                 	                       # Create a Child ID in case the Parent is not found
	                 	                       MSHWCHILDPRINT[ParentID] = createMSHWPRINT("MSHW_FAN",ParentID)
	                 	                     }
                   # 12 - Presence - Attach it to its parent
	                 if (SensorClass == 12) {# if there is a coreParentID, then attach it to that
	                 	                       if ( coreParentID[ParentID] != "" ) { AppendStatus(coreParentID[ParentID]) }
	                 	                       else {AppendStatus(ParentID)}
	                 		                 	   # Create a Child ID in case the Parent is not found
		                                       MSHWCHILDPRINT[ParentID] = createMSHWPRINT("MSHW_OTHER",ParentID)
	                 	                      }
	                }
	# 9 - Module
	if (Class == 9) {
		               if ( PhysDesc ~ /DIMM/ ) { MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_MEM",SensorID)
                                                      AppendStatus(SensorID) ;
	                  	                              }
                   else {MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_OTHER",SensorID)}
	                }
	# 10 - Port (Not yet seen in debugs)
	if (Class == 10) {MSHWPRINT[SensorID] = createMSHWPRINT("MSHW_NETWORK",SensorID)
	                 			                         AppendStatus(SensorID)
	                 }
	# 11 - Stack
	if (Class == 11) {
	                 }
	# 12 - CPU (Not yet seen in debugs)
	if (Class == 12) {MSHWPRINT[SensorID] =  createMSHWPRINT("MSHW_CPU",SensorID)
	                  AppendStatus(SensorID)
	                 }
	}

END { for (ID in PrintArray) {
	                            # If we did not find a parent for a sensor
	                            if (MSHWPRINT[ID] == "") { MSHWPRINT[ID] = MSHWCHILDPRINT[ID] }
	                            print MSHWPRINT[ID] StatusArray[ID] ";Alerting Sensors:" StatusInformation[ID] ";Sensors:" SensorArray[ID] ";" MSHWNUMERIC[ID]
	                           }
      if (ChassisFound == 0) {print "MSHW_ENC;1;/SYS;;;;;"}
    }
