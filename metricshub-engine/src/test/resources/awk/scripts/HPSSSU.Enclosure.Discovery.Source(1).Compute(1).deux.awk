BEGIN {level=0;Enclosure=""}


function setValue(value) {value=$0;
	                        gsub(/^ *<[^<>]+>/,"",value);
	                        gsub(/<\057[^<>]+>$/,"",value);
	                        gsub(/ *$/,"",value);
	                        return value}

function clearArray(level) {class[level]="";
	
	                          objectid[level]="";
	                          objectname[level]="";
	                          objecttype[level]="";	                         
	                          controllername[level]="";	                         
	                          operationalstate[level]="";
	                          
	                          operationalstatedetail[level]="";
	                          objectparentid[level]="";
	                          modelnumber[level]="";
	                          serialnumber[level]="";
	                          cachecondition[level]="";
	                          
	                          wwnodename[level]="";
	                          portname[level]="";
	                          wwid[level]="";
	                          speed[level]="";
	                          portcondition[level]="";
	                          
	                          topology[level]="";
	                          fanname[level]="";
	                          status[level]="";
	                          speed[level]="";
	                          name[level]="";
	                          
	                          tempc[level]="";
	                          type[level]="";
	                          state[level]="";
	                          cachebattery[level]="";
	                          statedetails[level]="";
	                          
	 	                        diskname[level]="";
	                          diskbaynumber[level]="";                         
	                          shelfnumber[level]="";
	                          diskgroupname[level]="";	   
	                          formattedcapacity[level]="";                       
	                          
	 	                        failurepredicted[level]="";
	                          manufacturer[level]="";                         
	                          modelnumber[level]="";
	                          firmwareversion[level]="";	   
	                          disktype[level]="";                       

	                          serialnumber[level]="";
	                          diskgroupname[level]="";	   
	                          srclevelactual[level]=""; 
	                          diskgrouptype[level]="";	   
	                          totalstoragespacegb[level]=""; 
	                          
	                          usedstoragespacegb[level]=""; 
	                          familyname[level]=""; 
	                          allocatedcapacity[level]=""; 
	                          virtualdisktype[level]=""; 
	                          installstatus[level]=""; 
	                          
	                          failprediction[level]="";
	                          outputlink[level]="";
	                          inputlink[level]="";
	                          diskshelfname[level]="";
	                          shelfnumber[level]="";
	                          
	                          XXXXX[level]="";
	                          XXXXX[level]="";
	                          XXXXX[level]="";
	                          XXXXX[level]="";
	                          XXXXX[level]="";
	                          
	                         }
	                         
function printPrimordial (Enclosure,primordialSize,primordialUnallocated){ 
                                                                          if (primordialSize > 0 && Enclosure!="" ) {
#                                                                                                                           MSHW_LOGICALDISK; Enclosure  ;Disk Group;    Location       ;      Name           ;    Status               ;   Status Information         ; Size GB                    ;  Unallocated Space                                     ; AddInfo2 (Disk Group Type) ;
                                                                                                                     print ("MSHW_LOGICALDISK;"Enclosure";Primordial;"Enclosure" - Primordial;"              ";"                       ";"                            ";" primordialSize           ";"primordialUnallocated                                 ";"                          ";0;")
                                                                                                                    }
                                                                         }



$0 ~ /.*. LS CONTROLLER FULL XML$/ || $0 ~ /.*. LS VDISK FULL XML$/ {
#  Print Existing Primordials and Zero variables
                                    printPrimordial(Enclosure,primordialSize,primordialUnallocated)
	                                  primordialSize=0
	                                  primordialUnallocated=0
#  Set the Enclosure
	                                  Enclosure=$0
	                                  gsub (/> LS CONTROLLER FULL XML$/,"",Enclosure)
	                                  gsub (/> LS VDISK FULL XML$/,"",Enclosure)
	                                  print ("MSHW_ENCLOSURE;"Enclosure";")

	                                 }



#  Set the BlockSize for the Enclosure
$0 ~ /<datablocksize>/ { datablocksize[enclosure]=setValue($0)}

#  Go up a level
$0 ~ /^ *<[^\057][^<>]+>$/ {grandParentLevel=level-1;
	                          parentLevel=level;
	                          level=level+1; 
	                          class[level]=$0;
	                          gsub(/(^ *<)|(> *$)/,"",class[level]) ; 
	                         }

$0 ~ /<objectid>/ { objectid[level]=setValue($0)}
$0 ~ /<objectname>/ { objectname[level]=setValue($0)}
$0 ~ /<objecttype>/ { objecttype[level]=setValue($0); if (objecttype[level] ~ /^diskshelf$/) {controllername[1]=objectname[level]; gsub (/.Hardware./,"",controllername[1])} }
$0 ~ /<controllername>/ { controllername[level]=setValue($0)}
$0 ~ /<operationalstate>/ { operationalstate[level]=setValue($0)}

$0 ~ /<operationalstatedetail>/ { operationalstatedetail[level]=setValue($0)}
$0 ~ /<objectparentid>/ { objectparentid[level]=setValue($0)}
$0 ~ /<modelnumber>/ { modelnumber[level]=setValue($0)}
$0 ~ /<serialnumber>/ { serialnumber[level]=setValue($0)}
$0 ~ /<cachecondition>/ { cachecondition[level]=setValue($0)}

$0 ~ /<wwnodename>/ { wwnodename[level]=setValue($0)}
$0 ~ /<portname>/ { portname[level]=setValue($0)}
$0 ~ /<wwid>/ { wwid[level]=setValue($0) ; gsub(/[^A-Fa-f0-9]/,"",wwid[level])}
$0 ~ /<speed>/ { speed[level]=setValue($0)}
$0 ~ /<portcondition>/ { portcondition[level]=setValue($0)}

$0 ~ /<topology>/ { topology[level]=setValue($0)}
$0 ~ /<fanname>/ { fanname[level]=setValue($0)}
$0 ~ /<status>/ { status[level]=setValue($0)}
$0 ~ /<name>/ { name[level]=setValue($0)}

$0 ~ /<tempc>/ { tempc[level]=setValue($0)}
$0 ~ /<type>/ { type[level]=setValue($0)}
$0 ~ /<state>/ { state[level]=setValue($0)}
$0 ~ /<cachebattery>/ { cachebattery[level]=setValue($0)}
$0 ~ /<statedetails>/ { statedetails[level]=setValue($0)}

$0 ~ /<diskname>/ { diskname[level]=setValue($0)}
$0 ~ /<diskbaynumber>/ { diskbaynumber[level]=setValue($0)}
$0 ~ /<shelfnumber>/ { shelfnumber[level]=setValue($0)}
$0 ~ /<diskgroupname>/ { diskgroupname[level]=setValue($0)}
$0 ~ /<formattedcapacity>/ { formattedcapacity[level]=setValue($0);
	                           formattedcapacity[level]=formattedcapacity[level] * datablocksize[enclosure] / 1000000000
	                         }

$0 ~ /<failurepredicted>/ { failurepredicted[level]=setValue($0)}
$0 ~ /<manufacturer>/ { manufacturer[level]=setValue($0)}
$0 ~ /<modelnumber>/ { modelnumber[level]=setValue($0)}
$0 ~ /<firmwareversion>/ { firmwareversion[level]=setValue($0)}
$0 ~ /<disktype>/ { disktype[level]=setValue($0)}

$0 ~ /<serialnumber>/ { serialnumber[level]=setValue($0)}
$0 ~ /<diskgroupname>/ { diskgroupname[level]=setValue($0)}
$0 ~ /<srclevelactual>/ { srclevelactual[level]=setValue($0)
	                        srclevelactual[level]=gsub(/[^0-9]/,"",srclevelactual[level])
	                      }
$0 ~ /<diskgrouptype>/ { diskgrouptype[level]=setValue($0)}
$0 ~ /<totalstoragespacegb>/ { totalstoragespacegb[level]=setValue($0)}

$0 ~ /<usedstoragespacegb>/ { usedstoragespacegb[level]=setValue($0)}
$0 ~ /<familyname>/ { familyname[level]=setValue($0)}
$0 ~ /<allocatedcapacity>/ { allocatedcapacity[level]=setValue($0)}
$0 ~ /<virtualdisktype>/ { virtualdisktype[level]=setValue($0)}
$0 ~ /<installstatus>/ { installstatus[level]=setValue($0)}

$0 ~ /<failprediction>/ { failprediction[level]=setValue($0)}
$0 ~ /<outputlink>/ { outputlink[level]=setValue($0)}
$0 ~ /<inputlink>/ { inputlink[level]=setValue($0)}
$0 ~ /<diskshelfname>/ { diskshelfname[level]=setValue($0)}
$0 ~ /<shelfnumber>/ { shelfnumber[level]=setValue($0)}

$0 ~ /<XXXXXXXX>/ { XXXXXXXX[level]=setValue($0)}
$0 ~ /<XXXXXXXX>/ { XXXXXXXX[level]=setValue($0)}
$0 ~ /<XXXXXXXX>/ { XXXXXXXX[level]=setValue($0)}
$0 ~ /<XXXXXXXX>/ { XXXXXXXX[level]=setValue($0)}
$0 ~ /<XXXXXXXX>/ { XXXXXXXX[level]=setValue($0)}


#  Printing Section

#  Exceptions

$0 ~ /<controllertemperaturestatus>/ {controllertemperaturestatus=setValue($0)
#	                                            MSHW_TEMP; Enclosure ; Name - (Controller Name - TempSensor Name); Temperature     ; Status        ;	 
                                      print ("MSHW_TEMP;"Enclosure"-"controllername[1]";" controllername[1] " - Temperature Status;"               ";"  controllertemperaturestatus ";"Enclosure";")                              
	                                   }

#  Properly formatted sensors & go down a level
$0 ~ /^ *<\057[^<>]+>$/ {	              
#  LS CONTROLLER SECTION
#                                                                                                      MSHW_CONTROLLER; Enclosure ;    Location                    ;      Name             ;    Status               ;   Status Information          ;    Model Number    ;    Serial Number    ; WWN       ;
#	                       if (class[level] ~ /^object$/ && objecttype[level] ~ /^controller$/) {print ("MSHW_CONTROLLER;"Enclosure";"Enclosure objectname[level]";"controllername[level]";"operationalstate[level]";"operationalstatedetail[level]";"modelnumber[level]";"serialnumber[level]";"wwnodename[level]";"Enclosure";")}

#                                                                                                      MSHW_BLADE; Enclosure ;    Location                    ;      Name             ;    Status               ;   Status Information          ;    Model Number    ;    Serial Number    ; WWN       ;
	                       if (class[level] ~ /^object$/ && objecttype[level] ~ /^controller$/) {print ("MSHW_BLADE;"Enclosure";"Enclosure"-"controllername[level]";"controllername[level]";"operationalstate[level]";"operationalstatedetail[level]";"modelnumber[level]";"serialnumber[level]";"wwnodename[level]";")}


#                                                                     MSHW_MEMORY; Enclosure ;MemoryType(cachememory);Name(Controller Name);Status      ; 
	                       if (class[level] ~ /^cachememory$/) {print ("MSHW_MEMORY;"Enclosure"-"controllername[1]";"class[level]";"Enclosure" - "controllername[1]";"cachecondition[level]";"Enclosure";")}

#                                                                                                   MSHW_NETWORKCARD; Enclosure ; NcType(hostport)   ; Name - (Controller Name - Port Name); WWN  ;  Status          ;  Speed Mbit; LinkStatus    ; 
	                       if (class[level] ~ /^hostport$/ || class[level] ~ /^deviceport$/) {print ("MSHW_NETWORKCARD;"Enclosure"-"controllername[1]";"class[level]";"Enclosure" - "controllername[1]" - "portname[level]";"wwid[level]";"operationalstate[level]";"speed[level]*1000";"portcondition[level]";"Enclosure";" )}

#	                                                                                    MSHW_FAN; Enclosure ;  Name - (Controller Name - Fan Name) ; Status        ; Speed        ; DeviceID                                       ; installstatus ; 
	                       if (class[level] ~ /^fan$/ && status[level] != "")  {print ("MSHW_FAN;"Enclosure"-"controllername[1]";"Enclosure" - "controllername[1]" - "fanname[level]";"status[level]";"speed[level]";"Enclosure"-"controllername[1]"-"fanname[level]";"installstatus[level]";"Enclosure";")}

#	                                                                                      MSHW_TEMP; Enclosure ; Name - (Controller Name - TempSensor Name);  Temperature ; Status       ;
	                       if (class[level] ~ /^sensor$/ && tempc[level] != "")  {print ("MSHW_TEMP;"Enclosure"-"controllername[1]";"Enclosure" - " controllername[1]" - "name[level]";" tempc[level] ";;"Enclosure";")}

#	                                                                 MSHW_PSU; Enclosure ;Name - (Controller Name - PSU Name);   Status       ;
	                       if (class[level] ~ /^source$/ )  {print ("MSHW_PSU;"Enclosure"-"controllername[1]";"Enclosure" - "controllername[1]" - "type[level]";" state[level] ";"Enclosure";")}
	                       	
#	                                                                       MSHW_BATTERY; Enclosure ;Name - (Controller Name - Battery Name);   Status       ;Status Information;
	                       if (class[level] ~ /^cachebattery$/ )  {print ("MSHW_BATTERY;"Enclosure"-"controllername[1]";"Enclosure" - "controllername[1]" - CacheBattery;" operationalstate[level] ";;Battery System;"Enclosure";")}

#	                                                                 MSHW_BATTERY; Enclosure ;Name - (Controller Name - Battery Name);Status      ;  Status Information ; 
	                       if (class[level] ~ /^module$/ && class[grandParentLevel] ~ /^cachebattery$/ )  {print ("MSHW_BATTERY;"Enclosure"-"controllername[1]";"Enclosure" - "controllername[1]" - "name[level]";" operationalstate[level] ";"statedetails[level]";Battery Module;"Enclosure";")}

#  LS DISK SECTION
#                                                                                               MSHW_DISK; Enclosure ;    Location       ;      Name             ;    Status               ;   Status Information    ;    Shelf - Disk Bay                                     ;    Disk Group        ;  Size GB                   ; failurepredicted          ;  Vendor               ;    Model             ; firmwareversion          ;  disktype       ; Serial Number       ;  diskgroupname                    ; 
	                       if (class[level] ~ /^object$/ && objecttype[level] ~ /^disk$/) {print ("MSHW_DISK;"Enclosure"-Shelf"shelfnumber[level]";"Enclosure objectname[level]";"diskname[level]";"operationalstate[level]";"operationalstatedetail[level]";Shelf "shelfnumber[level]" - Disk "diskbaynumber[level]";"diskgroupname[level]";" formattedcapacity[level] ";" failurepredicted[level] ";" manufacturer[level] ";" modelnumber[level] ";" firmwareversion[level] ";"disktype[level]";"serialnumber[level]";Disk Group - "diskgroupname[level]";" Enclosure";")
	                       	                                                               primordialSize=primordialSize+formattedcapacity[level] ;
	                       	                                                               if (diskgroupname[level] ~ /^Ungrouped Disks$/) {primordialUnallocated = primordialUnallocated + formattedcapacity[level] };
	                       	                                                              }


#  LS DISK_GROUP SECTION
#                                                                                                           MSHW_LOGICALDISK; Enclosure ;Disk Group;    Location       ;      Name             ;    Status               ;   Status Information         ; Size GB                    ;  Unallocated Space                                     ; AddInfo2 (Disk Group Type) ;
	                       if (class[level] ~ /^object$/ && objecttype[level] ~ /^diskgroupfolder$/) {print ("MSHW_LOGICALDISK;"Enclosure";Disk Group;"Enclosure objectname[level]";"diskgroupname[level]";"operationalstate[level]";"operationalstatedetail[level]";"totalstoragespacegb[level]";"totalstoragespacegb[level] - usedstoragespacegb[level]";Type " diskgrouptype[level]";1;")}


#  LS VDISK SECTION
#                                                                                                           MSHW_LOGICALDISK; Enclosure ;Virtual Disk;    Location       ;      Name         ;    Status               ;   Status Information          ; Size GB                  ;  (Unallocated Space)  ; AddInfo2 (Disk Group Type) ;
	                       if (class[level] ~ /^object$/ && objecttype[level] ~ /^virtualdisk$/) {print ("MSHW_LOGICALDISK;"Enclosure";Virtual Disk;"Enclosure objectname[level]";"familyname[level]";"operationalstate[level]";"operationalstatedetail[level]";"allocatedcapacity[level]";"                     ";Type " virtualdisktype[level]";0;")}

#  LS DISK SHELF SECTION
                       if (class[level] ~ /^bus$/) {if (failprediction[level] ~ /[Yy]es/ && operationalstate[level] ~ /[Gg]ood/) {operationalstate[level] = "bad"} 
#                                                            MSHW_OTHERDEVICE ; Enclosure ;Type;   Name                                          ;   OpState               ; 
                         	                         print ("MSHW_OTHERDEVICE;"Enclosure"-"controllername[1]";Bus;"Enclosure" - " controllername[1] " - " name[level] ";"operationalstate[level] ";"Enclosure";")
                         	                         }
	                       
	                       
                       if (class[level] ~ /^module$/ && class[parentLevel] ~ /^iomodules$/ && name[level] != "" )  {if (failprediction[level] ~ /[Yy]es/ && operationalstate[level] ~ /[Gg]ood/) {operationalstate[level] = "bad"} 	                       
 #                                                                                                                         MSWH_OTHERDEVICE ; Enclosure ;Type;   Name                                          ;   OpState               ; 
                         	                                                                                          print ("MSWH_OTHERDEVICE;"Enclosure controllername[1]";IO Module;"Enclosure" - " controllername[1] " - " name[level] ";"operationalstate[level] ";"Enclosure";")
                         	                                                                                         }


	                       if (class[level] ~ /^port$/) {if (failprediction[level] ~ /[Yy]es/ && operationalstate[level] ~ /[Gg]ood/) {operationalstate[level] = "bad"} 	                       
#                                                              MSHW_NETWORKCARD; Enclosure ; NcType(hostport)   ; Name - (Controller Name - Port Name); WWN  ;  Status          ;  Speed Mbit; LinkStatus    ; 
	                       	                             print ("MSHW_NETWORKCARD;"Enclosure"-"controllername[1]";"class[level]";"Enclosure" - "controllername[1]" - "name[level]" Input Link;;"operationalstate[level]";;"inputlink[level]";" Enclosure";" )
                       	                               print ("MSHW_NETWORKCARD;"Enclosure"-"controllername[1]";"class[level]";"Enclosure" - "controllername[1]" - "name[level]" Output Link;;"operationalstate[level]";;"outputlink[level]";" Enclosure";")
                       	                             }

                       if (class[level] ~ /^emu$/)  {if (failprediction[level] ~ /[Yy]es/ && operationalstate[level] ~ /[Gg]ood/) {operationalstate[level] = "bad"} 	                       
 #                                                   MSHW_OTHERDEVICE ; Enclosure ;Type;   Name                                          ;   OpState               ; 
                         	                           print ("MSHW_OTHERDEVICE;"Enclosure"-"controllername[1]";EMU;"Enclosure" - " controllername[1] " - EMU;"operationalstate[level] ";"Enclosure";")
                                                    }

#	                                                                                    MSHW_FAN; Enclosure ;  Name - (Controller Name - Fan Name) ; Status        ; Speed        ; DeviceID                                       ; installstatus ; 
	                       if (class[level] ~ /^fan$/ && name[level] != "")  {print ("MSHW_FAN;"Enclosure"-"controllername[1]";"Enclosure" - "controllername[1]" - "name[level]";"operationalstate[level]";;"Enclosure"-"controllername[1]"-"name[level]";;"Enclosure";")}

#                                                                                                      MSHW_BLADE; Enclosure ;    Location                    ;      Name             ;    Status               ;   Status Information          ;    (Model Number)  ;    (Serial Number)  ; (WWN)     ;
	                       if (class[level] ~ /^object$/ && objecttype[level] ~ /^diskshelf$/) {print ("MSHW_BLADE;"Enclosure";"Enclosure"-"controllername[level]";"diskshelfname[level]";"operationalstate[level]";"operationalstatedetail[level]";Disk Shelf"       ";"                   ";"         ";")
	                       	                                                                    print ("MSHW_DISK_TO_BLADE_TABLE;"Enclosure"-"controllername[level]";"Enclosure"-Shelf"shelfnumber[level]";")
	                       	                                                                   }

	                       clearArray(level)
	                       level=level-1 ; 
	                      }
	                      

level < 0 {print "Error:  Level is negative";exit}
END {printPrimordial(Enclosure,primordialSize,primordialUnallocated)}