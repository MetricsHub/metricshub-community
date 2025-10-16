$1 ~ /<HBA>/ {PortID="";WWPN="";Model="";ActualDataRate="";PortType="";SerialNumber="";}

$1 ~ /HBAID/ {PortID=$0;gsub(/^.*="/,"",PortID);gsub(/"$/,"",PortID);}

$1 ~ /HBAModel/ {Model=$0;gsub(/^.*="/,"",Model);gsub(/"$/,"",Model);}

$1 ~ /WWPN/ {WWPN=$0;gsub(/^.*="/,"",WWPN);gsub(/"$/,"",WWPN);gsub(/-/,"",WWPN);}

$1 ~ /ActualDataRate/ {ActualDataRate=$0;gsub(/^.*="/,"",ActualDataRate);gsub(/ Gbps"$/,"",ActualDataRate);}

$1 ~ /PortType/ {PortType=$0;gsub(/^.*="/,"",PortType);gsub(/"$/,"",PortType);gsub(/-/,"",PortType);}

$1 ~ /SerialNumber/ {SerialNumber=$0;gsub(/^.*="/,"",SerialNumber);gsub(/"$/,"",SerialNumber);gsub(/-/,"",SerialNumber);}

$1 ~ /Status/ {Status=$0;gsub(/^.*="/,"",Status);gsub(/" .>$/,"",Status);gsub(/-/,"",Status);}

$1 ~ /<\057HBA>/ {print ("MSHW;"PortID";"Model";"WWPN";"ActualDataRate";"PortType";"SerialNumber";"Status";")
	             PortID="";WWPN="";Model="";ActualDataRate="";PortType="";SerialNumber="";}