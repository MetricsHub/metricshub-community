BEGIN {NumberPaths=0; LunName=""; LunInfo="" ; LunStatus=""; LunStatusInfo="";AvailablePaths=""}

# RHEL 5 uses the format:
#	0HP_OPEN-E_500060e802c43216001e0f00000000000000c600dm-2 HP,OPEN-E
#	[size=14G][features=0][hwhandler=0]
#	\_ round-robin 0 [prio=0][active]
#	\_ 1:0:0:30 sdc 8:32 [active][undef]

# RHEL 6 uses the format:
#	mpathc (360000970000294901120533030303644) dm-0 EMC,SYMMETRIX
#	size=5.0G features='1 queue_if_no_path' hwhandler='0' wp=rw
#	`-+- policy='round-robin 0' prio=0 status=active
#	  |- 2:0:0:3 sdc 8:32  active undef running
#   `- 2:0:1:3 sde 8:64  active undef running
#
# OR
#
# mpath2 (36001b97004106bf304106bf26b52a9fc) dm-10 VIOLIN,SAN ARRAY
# size=128G features='0' hwhandler='0' wp=rw
# |-+- policy='round-robin 0' prio=0 status=enabled
# | `- 2:0:0:3  sdau 66:224 active undef running
# |-+- policy='round-robin 0' prio=0 status=enabled
# | `- 1:0:2:3  sdw  65:96  active undef running
# `-+- policy='round-robin 0' prio=0 status=enabled
#   `- 1:0:3:3  sdag 66:0   active undef running

$1 ~ /^[0-9A-Za-z][0-9A-Za-z]/ && $1 !~ /[=\[]/ {
                          if (LunName != "") {print ("MSHW;"LunName";"LunInfo";"NumberPaths";"LunStatus";"LunStatusInfo";"AvailablePaths";")}
	                        NumberPaths=0; LunName=""; LunInfo="" ; LunStatus=""; LunStatusInfo="";AvailablePaths="";
	                        LunName=$1 ; LunInfo = $0 ;
	                       }

($1 ~ /\\_/ || $1 ~ /[\|\`]\-/) && $2 !~ /[0-9]+:[0-9]+:[0-9]+:[0-9]+/  {
	                                                    if ($0 ~ /(active)|(ready)|(enabled)/) {LunStatus="OK" ;}
	                                                   	else if ($NF ~ /(faulty)|(failed)/) {LunStatus="ALARM" ; LunStatusInfo = $NF}
                                                      }
(($1 ~ /\\_/ || $1 ~ /[\|\`]\-/) && $2 ~ /[0-9]+:[0-9]+:[0-9]+:[0-9]+/ ) {
	                                                    if ($0 ~ /(active)|(ready)|(enabled)/) {NumberPaths=NumberPaths+1
                                                      if (AvailablePaths=="") {AvailablePaths="Path: " $2 " " $3}
                                                   	      else {AvailablePaths=AvailablePaths " - Path: " $2 " " $3}}
	                                                    }

($1 ~ /[|]/ && $2 ~ /[\|\`]\-/ && $3 ~ /[0-9]+:[0-9]+:[0-9]+:[0-9]+/ ) {
	                                                    if ($0 ~ /(active)|(ready)|(enabled)/) {NumberPaths=NumberPaths+1
                                                      if (AvailablePaths=="") {AvailablePaths="Path: " $3 " " $4}
                                                   	      else {AvailablePaths=AvailablePaths " - Path: " $3 " " $4}}
	                                                    }

END {if (LunName != "" ) {print ("MSHW;"LunName";"LunInfo";"NumberPaths";"LunStatus";"LunStatusInfo";"AvailablePaths";")}}
