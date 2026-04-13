BEGIN {controllerID="";logicaldriveID="";logicaldriveName="";status="";size="";raidLevel="";statusInfo="";
	    }
$1 ~ /MSHWController/ {controllerID=$2};

$1 ~ /[Ll]ogical/ && $2 ~ /([Dd]evice)|([Dd]rive)/ && $3 ~ /[Nn]umber/ {logicaldriveID=controllerID "-" $NF}

$1 ~ /[Ll]ogical/ && $2 ~ /[Dd]evice|([Dd]rive)/ && $3 ~ /[Nn]ame/   {logicaldriveName=$NF}

tolower($1) ~ /raid/ && $2 ~ /[Ll]evel/ {raidLevel=$NF}

$1 ~/[Ss]tatus/ && $3 ~ /[Ll]ogical/ && $4 ~ /[Dd]evice|([Dd]rive)/ {status=tolower($6); gsub(/,/,"",status);
	                                                                   colonIndex = index($0, ":") ;
	                                                                   statusInfo = substr($0, colonIndex + 2) ;
	                                                                  }

$1 ~ /[Ss]ize/ && $NF ~ /[TtGgMm][Bb]/ {size=$(NF-1);
	                                      if ($NF ~ /[Mm][Bb]/) {size = size / 1024};
                                        if ($NF ~ /[Tt][Bb]/) {size = size * 1024};
                                        print ("MSHW;"controllerID";"logicaldriveID";"logicaldriveName";"status";"size";"raidLevel";"statusInfo";");
                                        logicaldriveID="";logicaldriveName="";status="";size="";raidLevel="";statusInfo="";
                                       }