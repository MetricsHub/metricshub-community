BEGIN  {attachToType=""; module="" ;port="" ;portDescription=""; wwn="" ;portDesc=""; portMode="";speed="" ; duplexMode="";transmitPackets="";receivePackets="";transmitBytes=""; receiveBytes="";transmitErrors="";receiveErrors="";totalErrors="";section="";}
($1 ~ /^fc[0-9]/) || ($1 ~ /^[Ee]thernet[0-9]/) || ($1 ~ /^[Ee]xt[0-9]/) || ($1 ~ /^[Bb]ay[0-9]/) { 
	                  port = $1 ; gsub (/fc/,"",port) ; gsub (/[Ee]thernet/,"",port) ;
                    if (port ~ /\057/) {attachToType = "blade" ; module = substr(port,1,2) ; gsub (/\057/,"",module); }
	                     else {attachToType = "enclosure" ; module = "MDS9000Bay" ; }
                    
                    offset = (index($0,"is") + 3)  ; statusInformation = substr ($0,offset) ; status=tolower(statusInformation);linkStatus=statusInformation;
                    gsub (/(.*\050)|(\051)/,"",status); gsub (/[^a-z]/,"",status);
                    gsub (/up /,"",statusInformation); gsub (/trunking /,"",statusInformation) 
                    gsub (/ \050.+\051/,"",linkStatus);
                    if (($0 ~ /SFP not present/) || ($0 ~ /SFP not inserted/)){linkStatus = "missing"} ;
                    if ($1 ~ /^fc[0-9]/) {portDesc = "FC Port "}
                    if ($1 ~ /^[Ee]thernet[0-9]/) {portDesc = "Ethernet Port "}
                    }

$1 ~ /Port/ && $2 ~ /description/ && $3 ~ /is/ {offset = (index($0,"is") + 3) ;portDescription = substr ($0,offset)}
$0 ~ /WWN/          { wwn = $NF }
$0 ~ /Port mode is/ { portMode = $4 ; gsub (",","",portMode) ; }
                            
($1 ~ /^mgmt[0-9]/) || ($1 ~ /sup-fc[0-9]/)   { module = "MDS9000Bay" ;
	                  attachToType = "enclosure" ;
	                  port = $1 ;
                    offset = (index($0,"is") + 3)  ; statusInformation = substr ($0,offset) ; status=tolower(statusInformation);linkStatus=statusInformation;
                    gsub (/(.*\050)|(\051)/,"",status); gsub (/[^a-z]/,"",status);
                    gsub (/up /,"",statusInformation); gsub (/trunking /,"",statusInformation) 
                    gsub (/ \050.+\051/,"",linkStatus);
                    portDesc = "Ethernet Port" ;
                    }
$0 ~ /Internet address is/ { wwn = $NF }
$1 == "MTU"         { speed = $5 ; duplexMode = tolower($7) ;
                      if ($6 ~ /[Gg]bps/) {speed=speed*1000} ;
                      }
($0 ~ /Speed is/ && $NF ~ /[GgMm]bps/) {speed = $(NF-1) ;  duplexMode="full" ;
                                        if ($NF ~ /[Gg]bps/) {speed=speed*1000} ;
                                        }
# FC Received                                        
($2 ~ /packets|frames/) && ($3 ~ /input/) && ($5 ~ /bytes/)  {receivePackets=$1 ; receiveBytes = $4 ; section="input"; }
($2 ~ /input/) && ($3 ~ /errors/) && ($5 ~ /frame/) && ($7 ~ /overrun/) && ($9 ~ /fifo/) && (section=="input") {receiveErrors=($1 + $4 + $ 6 + $8);}
($2 ~ /discards/) && ($4 ~ /errors/) && (section=="input") {receiveErrors=($1 + $3);}
($2 ~ /CRC/) && ($4 ~ /unknown/) && (section=="input") {receiveErrors=(receiveErrors + $1 + $3);}
($2 ~ /too/) && ($3 ~ /long/) && ($5 ~ /too/) && ($6 ~ /short/) && (section=="input") {receiveErrors=(receiveErrors + $1 + $4);}

# FC Transmitted
($2 ~ /packets|frames/) && ($3 ~ /output/) && ($5 ~ /bytes/) {transmitPackets=$1 ; transmitBytes = $4 ; section="output"; }
($2 ~ /output/) && ($3 ~ /errors/) && ($5 ~ /collisions/) && ($7 ~ /fifo/)  && (section=="output") {transmitErrors=($1 + $4 + $ 6);}
($2 ~ /carrier/) && ($3 ~ /errors/) && (section=="output") {transmitErrors=(transmitErrors + $1)}
($2 ~ /discards/) && ($4 ~ /errors/) && (section=="output") {transmitErrors=($1 + $3);}

# Ethernet Received
($1 ~ /^RX$/) && (NF == 1) {section="RX"; }
($2 ~ /input/ && $3 ~ /packets/ && $5 ~ /bytes/ && section=="RX") {receivePackets=$1; receiveBytes = $4}
($2 ~ /input/) && ($3 ~ /error/) && ($5 ~ /short/) && ($6 ~ /frame/) && ($8 ~ /overrun/) && ($10 ~ /underrun/) && ($12 ~ /ignored/)&& (section=="RX") {receiveErrors=$1 + $4 + $7 + $9 + $11}
($2 ~ /watchdog/) && ($4 ~ /bad/) && ($5 ~ /etype/) && ($6 ~ /drop/) && ($8 ~ /bad/) && ($9 ~ /proto/) && ($10 ~ /drop/) && ($12 ~ /if/) && ($13 ~ /down/) && ($14 ~ /drop/) && (section=="RX") {receiveErrors=receiveErrors + $1 + $3 + $7 + $11}
($2 ~ /input/) && ($3 ~ /with/) && ($4 ~ /dribble/) && ($6 ~ /input/) && ($7 ~ /discard/) && (section=="RX") {receiveErrors=receiveErrors + $1 + $5}

# Ethernet Transmitted
($1 ~ /^TX$/) && (NF == 1) {section="TX"; }
($2 ~ /output/ && $3 ~ /packets/ && $5 ~ /bytes/ && section=="TX") {transmitPackets=$1; transmitBytes = $4}
($2 ~ /output/) && ($3 ~ /errors/) && ($5 ~ /collision/) && ($7 ~ /deferred/) && ($9 ~ /late/) && ($10 ~ /collision/) && (section=="RX") {transmitErrors=$1 + $4 + $6 + $8}
($2 ~ /lost/) && ($3 ~ /carrier/) && ($5 ~ /no/) && ($6 ~ /carrier/) && ($8 ~ /babble/) && ($10 ~ /proto/) && ($11 ~ /drop/) && (section=="RX") {transmitErrors=transmitErrors + $1 + $4 + $7 + $9}

((length($0) < 1) &&(module != ""))  {totalErrors = (transmitErrors + receiveErrors);
                                      print ("MSHW;" attachToType ";" module ";" port ";" statusInformation ";" wwn  ";" portDesc portMode ";" speed ";" linkStatus ";" duplexMode ";" transmitPackets ";" receivePackets ";" transmitBytes ";" receiveBytes ";" totalErrors ";" status ";" portDescription ";") ;
                                      attachToType=""; module="" ;port="" ;portDescription="";wwn="" ;portDesc=""; portMode="";speed="" ; duplexMode="";transmitPackets="";receivePackets="";transmitBytes=""; receiveBytes="";transmitErrors="";receiveErrors="";totalErrors="";section="";status="";statusInformation="";
                                      }