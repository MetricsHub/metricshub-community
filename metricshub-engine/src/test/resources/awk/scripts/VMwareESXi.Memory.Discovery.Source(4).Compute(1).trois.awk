BEGIN {FS="[;]";size=0;}
$3 ~ /^[0-9]+$/ {size = size + $3}
END {print ("MSHW;;Global;" size ";")}