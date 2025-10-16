BEGIN {controllerID="";status="";model="";serial="";memory="";firmware="";driver="";batteryStatus="";}
$1 ~ /MSHWController/ {if (controllerID != "") {print ("MSHW;"controllerID";"status";"model";"serial";"slot";"memory";"firmware";"driver";"batteryStatus";");
	                                              controllerID="";status="";model="";serial="";memory="";firmware="";driver="";batteryStatus="";
	                                             }
	                     controllerID=$2};

$1 ~ /[Cc]ontroller/ && $2 ~ /[Ss]tatus/ {status=tolower($4);if (NF > 4) {status=status " " tolower($5) }}

$1 ~ /[Cc]ontroller/ && $2 ~ /[Mm]odel/  {colonIndex = index($0, ":") ;
	                                        model = substr($0, colonIndex + 2) ;
	                                       }

$1 ~ /[Cc]ontroller/ && $2 ~ /[Ss]erial/ {colonIndex = index($0, ":") ;
	                                        serial = substr($0, colonIndex + 2) ;
	                                       }

$1 ~ /[Pp]hysical/ && $2 ~ /[Ss]lot/     {slot = "Slot " $NF}

$1 ~ /[Ii]nstalled/ && $2 ~ /[Mm]emory/  {colonIndex = index($0, ":") ;
	                                        memory = "Memory " substr($0, colonIndex + 2) ;
	                                       }
	                                       
$1 ~ /[Ff]irmware/ {colonIndex = index($0, ":") ;
	                  firmware = substr($0, colonIndex + 2) ;
	                 }
	                 
$1 ~ /[Dd]river/   {colonIndex = index($0, ":") ;
	                  driver = substr($0, colonIndex + 2) ;
	                 }

$1 ~ /[Ss]tatus/   {batteryStatus=tolower($3);
	                  if (NF > 3) {batteryStatus=batteryStatus " " tolower($4) };
	                  if (batteryStatus ~ /not installed/) {batteryStatus=""};
	                  getline;
	                  if ($1 ~ /[Oo]ver/ && $2 ~ /[Tt]emperature/ && $NF ~ /[Yy]es/ && batteryStatus!="") {batteryStatus="over temperature";}
	                 }

END {print ("MSHW;"controllerID";"status";"model";"serial";"slot";"memory";"firmware";"driver";"batteryStatus";");}