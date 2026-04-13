BEGIN {basetype=""}
$0 ~ /<OBJECT/ && $0 ~ /basetype=/ {for (i=1; i<=NF; i++) {if ($i ~ /^basetype=/) {basetype=$i;gsub(/(^.*=")|("$)/,"",basetype);
	                                                                               }
	                                                        }
	                                 }

function setValue(value) {value=$0
	                        gsub("</PROPERTY>.*","",value);
	                        gsub("^.*<PROPERTY.*>","",value)
	                        return value}


#Extract Enclosure information
# G1 and G2 use "system", G3 uses "system-information"
basetype == "system-information" {basetype = "system"}
($1 ~ /<PROPERTY/ && basetype == "system") {
     if ($0 ~ /name="system-name"/) {DisplayName=setValue($0)}
     if ($0 ~ /name="vendor.name"/) {vendor=setValue($0)}
     if ($0 ~ /name="product.id"/)  {model=setValue($0)}
    }
$1 ~ "/OBJECT" && (basetype == "system" || basetype == "system-information") {print ("MSHW;"basetype";"DisplayName";"vendor";"model";");
	                                         basetype="";DisplayName="";vendor="";model="";
	                                        }

#Extract Blade Information (enclosure-environmental / enclosure-sku)
($1 ~ /<PROPERTY/ && basetype == "enclosure-sku") {basetype="enclosure-environmental"
	                                                 bladeID="System"
	                                                }

($1 ~ /<PROPERTY/ && basetype == "enclosure-environmental") {
	   if ($0 ~ /name="sku.partnumber"/) {productID=setValue($0)}
     if ($0 ~ /name="sku.serialnumber"/) {bladeSerial=setValue($0)}
     if ($0 ~ /name="chassis"/) {bladeSerial=setValue($0)}
     if ($0 ~ /name="id"/) {bladeID=setValue($0)}
     if ($0 ~ /name="status"/)  {healthnumeric=tolower(setValue($0))}
     if ($0 ~ /name="Product ID"/) {productID=setValue($0)}
    }
$1 ~ "/OBJECT" && basetype == "enclosure-environmental" {print ("MSHW;"basetype";"bladeSerial";"bladeID";"healthnumeric";"productID";");
	                                       basetype="";bladeSerial="";bladeID="";healthnumeric="";productID="";
                                                                      }

#Extract Controllers information
($1 ~ /<PROPERTY/ && basetype == "controllers") {
     if ($0 ~ /name="controller.id"/) {ID=setValue($0)}
     if ($0 ~ /name="serial.number"/) {serialnumber=setValue($0)}
     if ($0 ~ /name="sc.fw"/) {firmwareversion=setValue($0)}
     if ($0 ~ /name="health.numeric"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="health.reason"/) {healthreason=setValue($0)}
     if ($0 ~ /name="position"/) {position=setValue($0)}
     if ($0 ~ /name="description"/) {description=setValue($0)}
     }


# Print Must Matchup with Compact Flash
$1 ~ "/OBJECT" && basetype == "controllers" {print ("MSHW;"basetype";CTRL-"ID";"healthnumeric";"healthreason";"position";"description";SN: "serialnumber";SC FW: "firmwareversion";");
	                                         basetype="";ID="";serialnumber="";firmwareversion="";healthnumeric="";healthreason="";position="";description="";
	                                        }

#Extract Management Port information

($1 ~ /<PROPERTY/ && basetype == "network-parameters") {
     if ($0 ~ /name="durable.id"/) {ID=setValue($0)}
     if ($0 ~ /name="network-parameters"/) {ipaddress=setValue($0)}
     if ($0 ~ /name="health-numeric"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="healthreason"/) {healthreason=setValue($0)}
     if ($0 ~ /name="mac-address"/) { macaddress=setValue($0)}
     }
# Print Must Matchup with FC Ports
$1 ~ "/OBJECT" && basetype == "network-parameters" {print ("MSHW;"basetype";"ID";Ethernet;"        ";"           ";"healthnumeric";"healthreason";");
	                                         basetype="";ID="";ipaddress="";healthnumeric="";healthreason="";macaddress="";addresstype="";
	                                        }

#Extract FC Port information
#G1 and G2

($1 ~ /<PROPERTY/ && basetype == "port") {
     if ($0 ~ /name="port"/) {ID=setValue($0)}
     if ($0 ~ /name="media"/) {media=setValue($0)}
     if ($0 ~ /name="target.id"/) {targetid=setValue($0)}
     if ($0 ~ /name="actual.speed"/) {actualspeed=setValue($0);gsub(/[gG][Bb]/,"000",actualspeed);}
     if ($0 ~ /name="health.numeric"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="health.reason"/) {healthreason=setValue($0);gsub(/Host port is OK/,"",healthreason)}
     }
#G3
($1 ~ /<PROPERTY/ && basetype == "name") {
     if ($0 ~ /name="controller"/) {ID="Ctlr-" setValue($0) ID}
     if ($0 ~ /name="channel"/) {ID=ID " Ch-" setValue($0) }
     if ($0 ~ /name="media"/) {media=setValue($0);if (media=="SAS") {basetype="";ID="";media="";targetid="";actualspeed="";healthnumeric="";healthreason="";}}
     if ($0 ~ /name="actual.speed"/) {actualspeed=setValue($0);gsub(/[gG][Bb]*/,"000",actualspeed);gsub(/[\.A-Za-z]/,"",actualspeed);}
     if ($0 ~ /name="status"/) {healthnumeric=setValue($0);if (status=="Not Present") {basetype=""}}
     }
# Print Must Matchup with Management Ports
$1 ~ "/OBJECT" && (basetype == "port" || ( basetype == "name" && media != "") ) {basetype = "port"
	                                         print ("MSHW;"basetype";"ID";"media ";"targetid";"actualspeed";"healthnumeric";"healthreason";");
	                                         basetype="";ID="";media="";targetid="";actualspeed="";healthnumeric="";healthreason="";
	                                        }

#Extract compact-flash information
($1 ~ /<PROPERTY/ && basetype == "compact-flash") {
     if ($0 ~ /name="controller.id"/) {ID=setValue($0)}
     if ($0 ~ /name="name"/) {name=setValue($0)}
     if ($0 ~ /name="cache.flush"/) {cacheflush=setValue($0)}
     if ($0 ~ /name="health.numeric"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="healthreason"/) {healthreason=setValue($0);gsub(/CompactFlash is OK./,"",healthreason);}
     }
# Print Must Matchup with Controllers
$1 ~ "/OBJECT" && basetype == "compact-flash" {print ("MSHW;"basetype";CF-"ID";"healthnumeric";"cacheflush" "healthreason";"        ";"name       ";"            ";");
	                                                            basetype="";ID="";name="";cacheflush="";healthnumeric="";healthreason="";
	                                        }

#Extract drives information
# G1 and G2 use "drives", G3 uses "drive"
basetype == "drive" {basetype = "drives"}
($1 ~ /<PROPERTY/ && basetype == "drives") {
     if ($0 ~ /name="location"/) {ID=setValue($0)}
     if ($0 ~ /name="enclosure.id"/) {enclosureid=setValue($0)}
     if ($0 ~ /name="serial.number"/) {serialnumber=setValue($0)}
     if ($0 ~ /name="vendor"/) {vendor=setValue($0)}
     if ($0 ~ /name="model"/) {model=setValue($0)}
     if ($0 ~ /name="revision"/) {revision=setValue($0)}
     if ($0 ~ /name="type"/) {type=setValue($0)}
     if ($0 ~ /name="size"/) {size=setValue($0)}
     if ($0 ~ /name="size.numeric"/) {sizenumeric=setValue($0)}
     if ($0 ~ /name="health.numeric"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="health.reason"/) {healthreason=setValue($0)}
 #G3 Specific
     if ($0 ~ /name="status"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="size" units="gb"/) {size=setValue($0)}
     }
$1 ~ "/OBJECT" && basetype == "drives" {print ("MSHW;"basetype";"ID";"enclosureid";"serialnumber";"vendor";"model";"revision";"type";"size";"sizenumeric";"healthnumeric";"healthreason";");
	                                                    basetype="";ID="";enclosureid="";serialnumber="";vendor="";model="";revision="";type="";size="";sizenumeric="";healthnumeric="";healthreason="";
	                                        }

#Extract virtual-disks information
# G1 and G2 use "virtual-disks", G3 uses "virtual-disk"
basetype == "virtual-disk" {basetype = "virtual-disks"}
 ($1 ~ /<PROPERTY/ && basetype == "virtual-disks") {
     if ($0 ~ /name="name"/) {name=setValue($0)}
     if ($0 ~ /name="size"/) {size=setValue($0)}
     if ($0 ~ /name="size.numeric"/) {sizenumeric=setValue($0)}
     if ($0 ~ /name="freespace"/) {freespace=setValue($0)}
     if ($0 ~ /name="freespace.numeric"/) {freespacenumeric=setValue($0)}
     if ($0 ~ /name="raidtype"/) {raidtype=setValue($0)}
     if ($0 ~ /name="diskcount"/) {diskcount=setValue($0);diskcount="Disk Count: " diskcount;}
     if ($0 ~ /name="health.numeric"/) {healthnumeric=setValue($0)}
     if ($0 ~ /name="health.reason"/) {healthreason=setValue($0)}
#G3 Specific
        if ($0 ~ /name="status"/) {healthnumeric=setValue($0)
        	                         healthreason=setValue($0)
# Translate the Health Reason Here to avoid conflict with G1/G2 healthreason
                                   gsub("CRIT","Critical",healthreason)
                                   gsub("FTDN","Fault tolerant with down disks",healthreason)
                                   gsub("FTOL","Fault tolerant and online",healthreason)
                                   gsub("OFFL","Offline",healthreason)
                                   gsub("QTCR","Quarantined critical",healthreason)
                                   gsub("QTDN","Quarantined with down disks",healthreason)
                                   gsub("QTOF","Quarantined offline",healthreason)
                                   gsub("STOP","Stopped",healthreason)
                                   gsub("U[Pp]","Up. No Fault Tolerance Attributes",healthreason)
                                   gsub("UNKN","Unknown",healthreason)
        	                         }
    if ($0 ~ /name="blocks"/) {sizenumeric=setValue($0)
    	                         blocksize=$0
    	                         gsub(/^.*blocksize="/,"",blocksize);
	                             gsub(/".*$/,"",blocksize)
                               sizenumeric=sizenumeric * blocksize / 512;
    	                        }

     }
$1 ~ "/OBJECT" && basetype == "virtual-disks" {print ("MSHW;"basetype";"name";"size";"sizenumeric";"freespace";"freespacenumeric";"raidtype";"diskcount";"healthnumeric";"healthreason";");
	                                                    basetype="";name="";size="";sizenumeric="";freespace="";freespacenumeric="";raidtype="";diskcount="";healthnumeric="";healthreason="";
	                                        }

#Extract enclosure-components information (FANS, TEMPERATURES, VOLTAGES, POWER SUPPLIES)
# G1 and G2 use "enclosure-components", G3 uses "patr"
basetype == "enclosure-component" {basetype = "enclosure-components"}
($1 ~ /<PROPERTY/ && basetype == "enclosure-components") {
     if ($0 ~ /name="type"/) {type=setValue($0)}
     if ($0 ~ /name="enclosure.unit.number"/) {enclosureunitnumber=setValue($0)}
     if ($0 ~ /name="status"/) {status=setValue($0);status=tolower(status);if (status=="absent") {basetype=""}}
     if ($0 ~ /name="fru.partnnumber"/) {partnnumber=setValue($0)}
     if ($0 ~ /name="fru.serialnumber"/) {serialnumber=setValue($0)}
     if ($0 ~ /name="additional.data"/) {additionaldata=setValue($0);property=$0;gsub(/[^0-9.]/,"",additionaldata)}
     if ($0 ~ /name="FRU S.N"/) {componentSN=setValue($0)}
     }
$1 ~ "/OBJECT" && basetype == "enclosure-components" {if (type !~ /[Dd]isk/) {
	                                                       print ("MSHW;"basetype";"type";"enclosureunitnumber";"status";PN: "partnnumber";SN: "serialnumber";"additionaldata";"componentSN";");
	                                                       basetype="";type="";name="";enclosureunitnumber="";status="";partnnumber="";serialnumber="";additionaldata="";componentSN="";
	                                                                              }
	                                                      }

# Other G3s have seperate FAN and Power Supply Sections
($1 ~ /<PROPERTY/ && basetype == "fan") {
     if ($0 ~ /name="location"/) {location=setValue($0)}
	   if ($0 ~ /name="status"/) {status=setValue($0);status=tolower(status);if (status=="not present" || status=="disconnected") {basetype=""}}
     if ($0 ~ /name="speed"/) {speed=setValue($0)}
     }
$1 ~ "/OBJECT" && basetype == "fan" {print ("MSHW;"basetype";;"location";"status";;;;system;");
	                                                       basetype="";location="";status="";
	                                                      }

($1 ~ /<PROPERTY/ && basetype == "power-supplies") {
     if ($0 ~ /name="location"/) {location=setValue($0)}
     if ($0 ~ /name="serial.number"/) {serialnumber=setValue($0)}
     if ($0 ~ /name="configuration.serialnumber"/) {componentSN=setValue($0)}
	   if ($0 ~ /name="status"/) {status=setValue($0);status=tolower(status);if (status=="not present" || status=="disconnected") {basetype=""}}
     if ($0 ~ /name="part.number"/) {partnnumber=setValue($0)}
     }
$1 ~ "/OBJECT" && basetype == "power-supplies" {print ("MSHW;"basetype";;"location";"status";PN: "partnnumber";SN: "serialnumber";;system;");
	                                                       basetype="";location="";status="";partnnumber="";serialnumber="";
	                                                      }



#Extract the Component to EnvEnc (Blade) table
# G3s do not use basetype for these variables, using a different test:
$0 ~ /<OBJECT/ && $0 ~ /name="enclosure-fru"/ {basetype = "enclosure-fru"}
$0 ~ /<OBJECT/ && $0 ~ /name="midplane-fru"/ {basetype = "enclosure-fru"}

($1 ~ /<PROPERTY/ && basetype == "enclosure-fru") {
     if ($0 ~ /name="Configuration SN"/) {bladeSN=setValue($0)}
     if ($0 ~ /name="Serial Number"/) {componentSN=setValue($0)}
     if ($0 ~ /name="FRU Location"/) {componentID=setValue($0)}
     }
$1 ~ "/OBJECT" && basetype == "enclosure-fru" {print ("MSHW;"basetype";"bladeSN";"componentSN";"componentID";")
	                                                basetype="";bladeSN="";componentSN="";componentID=""
	                                               }
