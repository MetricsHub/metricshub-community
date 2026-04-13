BEGIN { lastLine = "" }
{ skip=1 }
/show interconnect status all/,/show server status all/ { skip = 0 }
(skip == 1) { next }
($1 ~ /^Interconnect/) { otherDeviceID = $3 ; gsub(/#/, "", otherDeviceID) ; }
($1 ~ /tatus/ && lastLine ~ /Interconnect/ ) {
  status = $NF
  print "MSHW;" otherDeviceID ";" status ";" status ";"
  otherDeviceID = ""
}
{ lastLine = $0 }
