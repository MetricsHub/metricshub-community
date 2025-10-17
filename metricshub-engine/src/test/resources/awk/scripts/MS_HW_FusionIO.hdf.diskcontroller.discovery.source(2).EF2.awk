$1 ~ /Adapter:/ {adapterModel = $0 ; gsub(/^Adapter: */,"",adapterModel)
	              status = "OK" ; statusInformation = ""
	             }

# Adapter STATUS Collection
# Unfortunately the Status of the adapter is not always in the same place so we are going to have to search every line for known error messages.
# At least the documentation was nice enough to provide those.
# We have to assume that if no error, status is OK
tolower($0) ~ /external power. not connected/ {status = "ALARM"; statusInformation = statusInformation " " $0}

# Generic Catch All:
tolower($0) ~ /warning/ {if (status != "ALARM") {status = "WARN"};
	                        if (statusInformation == "") {statusInformation = statusInformation " " $0}
	                       }


$0 ~ /Connected.*[Mm]odules:/ {adapterID="";
	                             moduleFound="1"
	                             getline
	                             while (moduleFound=="1") {adapterID=adapterID $1;getline;if ($1 !~ /fct[0-9]+/) {moduleFound="0"}}
	                             gsub (/^ */,"",statusInformation);gsub (/  /,"",statusInformation)
                               print "MSHW_ADAPTER;" adapterID ";" adapterModel ";" status ";" statusInformation ";"
                              }

$1 ~ /fct[0-9]+/ && $2 ~ /Attached/ && $3 ~ /(as)|(to)/ {ioDriveID=$1 ;
	                                                       if (statusInformation == "") {statusInformation = "No Warnings Detected"}
	                                                       print "MSHW_DISK_TO_ADAPTER;" ioDriveID ";" adapterID ";"
	                                                      }
