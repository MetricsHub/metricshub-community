{skip=1}
/show environment/,/#/ {skip = 0}
skip == 1 {next}
$0 ~ /Total Power Capacity/ { print ("MSHW;"$(NF-1)) }
