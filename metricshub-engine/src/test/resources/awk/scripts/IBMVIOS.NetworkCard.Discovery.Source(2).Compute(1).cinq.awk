BEGIN {nicID="";TxPackets="";RxPackets="";TxBytes="";RxBytes="";ErrorCount="";linkStatus="";speed="";duplex="";portType="";MAC=""}

$1 ~ /ETHERNET/ && $2 ~ /STATISTICS/ {nicID=$3; gsub (/[()]/,"",nicID);}

$1 ~ /Device/ && $2 ~ /Type/ {portType="Ethernet " $NF}

$1 ~ /Hardware/ && $2 ~ /Address/ {MAC=$3; gsub (/[:]/,"",MAC);}

$1 ~ /Packets/ && $2 ~/[0-9]+/ && $3 ~ /Packets/ && $4 ~/[0-9]+/ {TxPackets = $2 ; RxPackets = $4}

$1 ~ /Bytes/ && $2 ~/[0-9]+/ && $3 ~ /Bytes/ && $4 ~/[0-9]+/ {TxBytes = $2 ; RxBytes = $4}

$1 ~ /Transmit/ && $2 ~ /Errors/ && $3 ~ /[0-9]+/ && $4 ~ /Receive/ && $5 ~ /Errors/ && $6 ~ /[0-9]+/ {ErrorCount = $3 + $6}

$1 ~ /Physical/ && $2 ~/Port/ && $3 ~ /Link/ && $4 ~/State/ {if ($NF ~ /[Uu][Pp]/) {linkStatus="OK"}
	                                                              else if ($NF ~ /[Dd][Oo][Ww][Nn]/) {linkStatus="WARN"}
	                                                          }
$1 ~ /Media/ && $2 ~/Speed/ && $3 ~ /Running/ {speed=$4 ; if ($5 ~ /[Gg]bps/) {speed = speed * 1000} 
	                                             if ($6 ~ /[Ff]ull/) {duplex = "Full"}
	                                             	  else if ($6 ~ /[Hh]alf/) {duplex = "Half"}
	                                             print ("MSHW;"nicID";"MAC";"linkStatus";"TxPackets";"RxPackets";"TxBytes";"RxBytes";"ErrorCount";" portType ";"speed";"duplex";MAC;")
	                                             nicID="";TxPackets="";RxPackets="";TxBytes="";RxBytes="";ErrorCount="";linkStatus="";speed="";duplex="";portType="";MAC=""
	                                            }