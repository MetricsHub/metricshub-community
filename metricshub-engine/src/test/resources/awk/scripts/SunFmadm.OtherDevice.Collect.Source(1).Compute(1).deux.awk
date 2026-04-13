BEGIN {status="OK";statusInformation="No CPU Faults Reported by fmadm";exitFlag="1";}

nextLineTrigger == "1" {nextLineTrigger = "0";
	                     if ($NF ~ /:\057\057:/) {nextLineTrigger="1"};
                       if ($1 ~ /[Aa]ffects/) {nextLineTrigger="1"};
	                     if (status == "ALARM") {status = "ALARM"}
	                     else if (tolower($1) ~ /faulted/) {status = "ALARM"}
	                     else {status = "WARN"}
	                     }
($1 ~ /[Ff]ault [Cclass]/) && ($NF ~ /\056cpu\056/)  {nextLineTrigger="1";
                                                      statusInformation="Fault(s) Detected - Run fmadm faulty" ;
	                                                   }

($1 ~ /[Aa]ffects/) && ($NF ~ /cpu:/)  {nextLineTrigger="1";
                             statusInformation="Fault(s) Detected - Run fmadm faulty" ;
	                          }



(tolower($1) ~ /degraded/ || tolower($1) ~ /faulted/) && ($NF ~ /cpu:/)  {statusInformation="Fault(s) Detected - Run fmadm faulty"
	                                                                      if (status == "ALARM") {status = "ALARM"}
	                                                                          else if (tolower($1) ~ /faulted/) {status = "ALARM"}
	                                                                          else if (tolower($1) ~ /degraded/) {status = "WARN"}
	                                                                      }

$1 ~ /^errorlevel$/ {if ($2 == 0) {exitFlag="0"} }

END { if (exitFlag=="0") {print ("MSHW;Overall;"status";"statusInformation";")};}