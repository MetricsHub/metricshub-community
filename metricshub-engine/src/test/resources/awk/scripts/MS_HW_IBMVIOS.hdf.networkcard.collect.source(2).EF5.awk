/^ETHERNET STATISTICS/ { nicdevice = $3; gsub (/[\050\051]/,"",nicdevice); nicdevices[nicdevice]=nicdevice }

/^Device Type:/ {
                 if ( ($3 ~ /EtherChannel/ || $3 ~ /Virtual/) || ($0 ~ /Shared Ethernet Adapter/) || ($0 ~ /Link Aggregation/) || ($0 ~ /VLAN/)) { EtherT[nicdevice] = "Logical"; lastLogical = nicdevice }
                    else { EtherT[nicdevice] = "Physical" }
                }

/^Driver Flags:/ {
                  if ($0 ~ /Running/) { nicStatus[nicdevice] = "OK";}
                  else {
                        nicStatus[nicdevice] = "ALARM";
                        getline
                        if ($0 ~ /Running/) { nicStatus[nicdevice] = "OK";}
                       }
                 }

$1 == "Hardware" && $2 == "Address:" { mac[nicdevice] = $3 ; gsub (/:/,"",mac[nicdevice])}

# Link
/Physical Port Link Stat/ {
                           if ($NF == "up" || $NF == "Up" || $NF == "UP") {
                                                                           linkStatus[nicdevice] = "OK";
                                                                           if ( linkStatus[lastLogical] == "WARN" ) { linkStatus[lastLogical] = "OK" ; nicStatus[lastLogical] = "WARN" ; }
                                                                          }
                              else {
                                    linkStatus[nicdevice] = "WARN"; nicStatus[nicdevice] = "OK";
                                    if (linkStatus[lastLogical] == "OK") { nicStatus[lastLogical] = "WARN" }
                                    if (linkStatus[lastLogical] == "") { linkStatus[lastLogical] = "WARN" ; nicStatus[lastLogical] = "ALARM" }
                                   }
                          }

$1 == "Link" && $2 == "Status" { if ($4 == "up" || $4 == "Up" || $4 == "UP") { linkStatus[nicdevice] = "OK"; }
                                      else {linkStatus[nicdevice] = "WARN"; nicStatus[nicdevice] = "OK";}
                                 }

$1 == "Link" && $2 == "Status:" { if ($3 == "up" || $3 == "Up" || $3 == "UP") { linkStatus[nicdevice] = "OK";}
                                       else { linkStatus[nicdevice] = "WARN"; nicStatus[nicdevice] = "OK"; }
                                  }

# Speed
$0 ~ /Media Speed Running:/ || $0 ~ /Physical Port Speed:/ {
                                 linkSpeed[nicdevice] = $4; gsub(/Gbps/,"000",linkSpeed[nicdevice]);gsub(/Mbps/,"",linkSpeed[nicdevice]);
                                 if ($0 ~ /[Hh]alf [Dd]uplex/) { duplexMode[nicdevice] = "half"; }
                                    else { duplexMode[nicdevice] = "full"; }
                                 }


# Statistics
$1 == "Packets:" && $3 == "Packets:" { transmitPackets[nicdevice] = $2; receivePackets[nicdevice] = $4; }

$1 == "Bytes:" && $3 == "Bytes:" {transmitBytes[nicdevice] = $2;receiveBytes[nicdevice] = $4; }

$1 == "Transmit" && $2 == "Errors:" && $4 == "Receive" && $5 == "Errors:" { transmitErrors[nicdevice] = $3; receiveErrors[nicdevice] = $6; totalErrors[nicdevice] = transmitErrors[nicdevice] + receiveErrors[nicdevice] ; }

END { for (nic in nicdevices) {
          if (EtherT[nic] == "Logical") {
             printf("MSHW;%s;%s;%s;%.0f;%.0f;%.0f;%.0f;%.0f;;;%s;;\n", nicdevices[nic],nicStatus[nic],linkStatus[nic],totalErrors[nic],transmitPackets[nic],receivePackets[nic],transmitBytes[nic],receiveBytes[nic],"Logical");
             }
             else {printf("MSHW;%s;%s;%s;%.0f;%.0f;%.0f;%.0f;%.0f;%s;%s;%s;%s;\n",nicdevices[nic],nicStatus[nic],linkStatus[nic],totalErrors[nic],transmitPackets[nic],receivePackets[nic],transmitBytes[nic],receiveBytes[nic],linkSpeed[nic],duplexMode[nic],"Physical", mac[nic]);
                  }

          }
    }
