BEGIN { NR = 40; 
        nicStatus = ""; 
        receiveErrors = "";  
        transmitErrors = "";  
        receivePackets = "";  
        transmitPackets = ""; 
        linkStatus = "";
        linkSpeed = ""; 
        duplexMode = "";
        EtherT = ""; 
        mac = "" ;
        nicdevice = "";}

/^ETHERNET STATISTICS/ { nicdevice = $3; gsub (/[\050\051]/,"",nicdevice)}

/^Device Type:/ {
                 if ($3 ~ /EtherChannel/ || /Virtual/) { EtherT = "VIRT"; }
                }

/^Driver Flags:/ {
                  if ($0 ~ /Running/) { nicStatus = "OK";}
                  else {
                        nicStatus = "ALARM";
                        getline
                        if ($0 ~ /Running/) { nicStatus = "OK";}
                       } 
                 }

/Physical Port Link State/ { if ($NF == "up" || $NF == "Up" || $NF == "UP") { linkStatus = "OK"; }
                                else { linkStatus = "WARN"; nicStatus = "OK"; }
                           }

$1 == "Link" && $2 == "Status" { if ($4 == "up" || $4 == "Up" || $4 == "UP") { linkStatus = "OK"; } 
                                      else {linkStatus = "WARN"; nicStatus = "OK";}
                                 }

$1 == "Link" && $2 == "Status:" { if ($3 == "up" || $3 == "Up" || $3 == "UP") { linkStatus = "OK";}
                                       else { linkStatus = "WARN"; nicStatus = "OK"; } 
                                  }
$1 == "Hardware" && $2 == "Address:" { mac = $3 ; gsub (/:/,"",mac)}

$1 == "Packets:" && $3 == "Packets:" { transmitPackets = $2; receivePackets = $4; }

$1 == "Bytes:" && $3 == "Bytes:" {transmitBytes = $2;receiveBytes = $4; }

$1 == "Transmit" && $2 == "Errors:" && $4 == "Receive" && $5 == "Errors:" { transmitErrors = $3; receiveErrors = $6;}

EtherT == "VIRT" && $0 ~ /Number of adapter/ || /^Virtual/  { totalErrors = transmitErrors + receiveErrors; 
                                                              printf("MSHW;%s;%s;%s;%.0f;%.0f;%.0f;%.0f;%.0f;;;%s;;\n", nicdevice,nicStatus, linkStatus, totalErrors, transmitPackets, receivePackets, transmitBytes, receiveBytes, "Logical"); 
                                                                nicStatus = ""; 
                                                                receiveErrors = "";
                                                                transmitErrors = "";  
                                                                receivePackets = "";  
                                                                transmitPackets = ""; 
                                                                linkStatus = "";
                                                                linkSpeed = ""; 
                                                                duplexMode = "";
                                                                EtherT = ""; 
                                                                mac = "";
                                                                nicdevice = "";
                                                            }
# As media status doesn't exist on AIX 4.3 servers, added a trigger to say if you haven't printed by the time you reach then end.. print
($0 ~ /^Media Speed Running:/) || ( nicdevice != "" && $0 ~ /^MS_HW_entstat_End:/ ) {
                                 if ($0 ~ /Media Speed Running:/) {linkSpeed = $4;}
                                 if ($0 ~ /[Hh]alf [Dd]uplex/) { duplexMode = "half"; }
                                    else { duplexMode = "full"; }
                                 totalErrors = transmitErrors + receiveErrors;
                                 printf("MSHW;%s;%s;%s;%.0f;%.0f;%.0f;%.0f;%.0f;%s;%s;%s;%s;\n", nicdevice,nicStatus, linkStatus, totalErrors, transmitPackets, receivePackets, transmitBytes, receiveBytes, linkSpeed, duplexMode, "Physical", mac);
                                 nicStatus = ""; 
                                 receiveErrors = "";
                                 transmitErrors = "";  
                                 receivePackets = "";  
                                 transmitPackets = ""; 
                                 linkStatus = "";
                                 linkSpeed = ""; 
                                 duplexMode = "";
                                 EtherT = "";  
                                 nicdevice = "";                                
                                }