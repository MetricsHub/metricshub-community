BEGIN {enclosureID="";slotID="";status="";firmwareVersion="";model="";}
$1 ~ /[Cc]ontroller/  && $2 ~ /[Ii]n/ && $3 ~ /[Ee]nclosure/ {enclosureID=$4 ;
                                                           gsub (/,/,"",enclosureID) ;
                                                           slotID = $6 ;
                                                          }
$1 ~ /[Ss]tatus/ {status=$2 ;
                  if (NF > 2)  {status = (status " " $3)};
                 }   
$1 ~ /[Ff]irmware/ && $2 ~ /[Vv]ersion/ && firmwareVersion == "" {firmwareVersion = $NF}
$1 ~ /[Rr]eplacement/ && $2 ~ /[Pp]art/ && $3 ~ /[Nn]umber/ && enclosureID !="" && slotID != "" {
                                                            model=$NF ;
                                                            print ("MSHW;"enclosureID";"slotID";"status";"firmwareVersion";"model";"status";");
                                                            enclosureID="";slotID="";status="";firmwareVersion="";model="";
                                                            }