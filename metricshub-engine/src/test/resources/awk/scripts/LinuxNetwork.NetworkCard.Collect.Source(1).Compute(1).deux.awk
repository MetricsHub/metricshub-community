BEGIN {
	transmitPackets = ""
	transmitErrors = ""
	receivePackets = ""
	receiveErrors = ""
	transmitBytes = ""
	receiveBytes = ""
}
/^eth[0-9][0-9]* |^vmnic[0-9][0-9]* / {
	deviceID = $1
}
/^ +RX packets:/ {
	receivePackets = substr($2, 9, length($2) - 8)
	receiveErrors = substr($3, 8, length($3) - 7)
}
/^ +TX packets:/ {
	transmitPackets = substr($2, 9, length($2) - 8)
	transmitErrors = substr($3, 8, length($3) - 7)
}
/^ +RX bytes:.*TX bytes:/ {
	receiveBytes = substr($2, 7, length($2) - 6)
	transmitBytes = substr($6, 7, length($6) - 6)
}
END {
		print "MSHW;" deviceID ";" receivePackets ";" transmitPackets ";" receiveErrors + transmitErrors ";" receiveBytes ";" transmitBytes
}