BEGIN {FS="[:]";WWN="";}

$1 ~ /Port Statistics for/ {WWNNF=split($0,WWNArray,/ /);WWN=WWNArray[WWNNF];gsub(/:/,"",WWN)}
	
$1 ~ /^Tx Frame Count +$/ {TxFrame=$2;gsub (/^ +/,"",TxFrame);}

$1 ~ /^Rx Frame Count +$/ {RxFrame=$2;gsub (/^ +/,"",RxFrame);}

$1 ~ /^Tx KB Count +$/ {TxB=$2;gsub (/^ +/,"",TxKB);TxB=TxB*1024;}

$1 ~ /^Rx KB Count +$/ {RxB=$2;gsub (/^ +/,"",RxKB);RxB=RxB*1024;}
	
$1 ~ /^Error Frame Count +$/ && WWN != "" {ErrorCount=$2;gsub (/^ +/,"",ErrorCount);
	                                         print ("MSHW;"TxFrame";"RxFrame";"TxB";"RxB";"ErrorCount";"WWN";")
	                                         WWN="";}