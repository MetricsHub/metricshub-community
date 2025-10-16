function printMSHW()
  {
   print("MSHW;"pseudoName";"manufacturerID";"logicalDeviceID";"deviceWWN";"state";"policy";"alivePathCount";")
	 pseudoName=""
	 manufacturerID=""
	 logicalDeviceID=""
	 pseudoName=""
	 deviceWWN=""
	 state=""
	 policy=""
	 alivePathCount=""
	 pathCountFlag=0
  }

$1~/Pseudo/ && $2~/name=/ {pseudoName = $2; gsub (/.*=/,"",pseudoName) }
$2~/ID=/ {manufacturerID = $2 ; gsub (/.*=/,"",manufacturerID) ; manufacturerID = $1 " " manufacturerID}
$1~/Logical/ && $2~/device/ && $3~/ID=/ {
	                                       logicalDeviceID=$0  ; gsub(/.?Logical device ID=/,"",logicalDeviceID);
	                                       gsub (/.*=/,"",logicalDeviceID)
	                                       if (pseudoName == "") {pseudoName = logicalDeviceID}
	                                      }
$1~/Device/ && $2~/WWN=/ {deviceWWN = $2; gsub (/.*=/,"",deviceWWN); deviceWWN = "naa.ID=" deviceWWN}
$1~/state=/ {state = $1 ; gsub (/state=/,"",state) ; gsub (/;/,"",state);}
$2~/policy=/ {policy = $2 ; gsub (/policy=/,"",policy) ; gsub (/;/,"",policy);}

pathCountFlag==1 && logicalDeviceID !="" {
	                if ($1 ~ /^[0-9]+$/) {
	                     if ($(NF-3)~/active/ && $(NF-2)~/alive/) {alivePathCount = alivePathCount + 1};
	                     if ($(NF-3)~/unlic/ && state~/alive/) {state="unlic"} ;
	                    }
	                    else {printMSHW()}
	                }

$0 ~ /### +HW +Path +I.O +Paths +Interf. +Mode +State/ {
	                                                     pathCountFlag=1;
	                                                     alivePathCount=0;
	                                                     getline
	                                                    }

END { if (logicalDeviceID !="") {printMSHW()} }
