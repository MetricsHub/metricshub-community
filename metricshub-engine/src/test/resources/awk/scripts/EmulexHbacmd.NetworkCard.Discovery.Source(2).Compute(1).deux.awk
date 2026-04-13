BEGIN {FS="[:]";WWN="";}

$1 ~ /Port Attributes for/ {WWNNF=split($0,WWNArray,/ /);WWN=WWNArray[WWNNF];gsub(/:/,"",WWN)}

$1 ~ /^Port State +$/ {Status=$2;gsub (/^ +/,"",Status);}

$1 ~ /^Port Speed +$/ {Speed=$2;gsub (/^ +/,"",Speed);Speed=$2;gsub (/[^0-9]/,"",Speed);
	                                  print ("MSHW;"Speed";"Status";"WWN";")
	                                  WWN="";}