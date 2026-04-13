BEGIN {controllerID="";deviceID="";hardDriveID="";status="";location="";vendor="";model="";firmware="";serialNumber="";size="";channel="";}
 $1 ~ /MSHWController/ {controllerID=$2};

$1 ~/[Dd]evice/ && $2 ~ /#[0-9]/ {deviceID=(controllerID"-"$2);gsub(/#/,"",deviceID);
	                                getline;
	                                if ( $(NF-1) ~ /[Hh]ard/ && $NF ~ /[Dd]rive/ ) {hardDriveID = deviceID};
	                               }

$1 ~ /[Ss]tate/ && hardDriveID != "" {status=tolower($NF)}

$1 ~ /[Ss]upported/ && hardDriveID != "" && $NF ~ /[Nn]o/ {status="Unsupported"}

$1 ~ /[Rr]eported/ && $2 ~ /[Ll]ocation/ {colonIndex = index($0, ":") ;
	                                        location = ("Controller "controllerID","substr($0, colonIndex + 1)) ;
	                                       }

$1 ~ /[Rr]eported/ && $2 ~ /[Ee]nclosure/ {colonIndex = index($0, ":") ;
	                                         enclosureSlot = substr($0, colonIndex + 2) ; 
	                                         commaIndex = index(enclosureSlot, ",") ;
	                                         enclosure = substr(channelDevice,1,commaIndex-1) ; 
	                                         slot = substr(channelDevice,commaIndex + 1) ; 
	                                         location = ("Enclosure " enclosure ", Slot " slot) ;
	                                        }

$1 ~ /[Rr]eported/ && $2 ~ /[Cc]hannel/ {colonIndex = index($0, ":") ;
	                                        channelDevice = substr($0, colonIndex + 2) ; 
	                                        commaIndex = index(channelDevice, ",") ;
	                                        channel = substr(channelDevice,1,commaIndex-1) ; 
	                                        device = substr(channelDevice,commaIndex + 1) ; 
	                                        channel = ("Channel " channel ", Device " device) ;
	                                       }

$1 ~ /[Vv]endor/ {colonIndex = index($0, ":") ;
	                vendor = substr($0, colonIndex + 2) ;
	               }	                                       

$1 ~ /[Mm]odel/  {colonIndex = index($0, ":") ;
	                model = substr($0, colonIndex + 2) ;
	               }	 

$1 ~ /[Ff]irmware/  {colonIndex = index($0, ":") ;
	                   firmware = substr($0, colonIndex + 2) ;
	                  }	

$1 ~ /[Ss]erial/ && $2 ~ /[Nn]umber/ {colonIndex = index($0, ":") ; 
	                                    serialNumber = substr($0, colonIndex + 2) ;
	                                   }		               

$1 ~ /^[Ss]ize/ && $NF ~ /[TtGgMm][Bb]/ && hardDriveID != "" {size=$(NF-1);
	                                                            if ($NF ~ /[Mm][Bb]/) {size = size / 1024};
                                                              if ($NF ~ /[Tt][Bb]/) {size = size * 1024};
	                                                            print("MSHW;"controllerID";"hardDriveID";"status";"location";"vendor";"model";"firmware";"serialNumber";"size";"channel";");
	                                                            deviceID="";hardDriveID="";status="";location="";vendor="";model="";firmware="";serialNumber="";size="";channel="";
	                                                           }              