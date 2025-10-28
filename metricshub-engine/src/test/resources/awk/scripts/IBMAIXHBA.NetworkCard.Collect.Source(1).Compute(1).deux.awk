BEGIN {
		nicStatus = "";
		errorFrames = 0;
		dumpFrames = 0;
		receivedFrames = 0;
		transmittedFrames = 0;
		linkStatus = "WARN";
		inputBytes = 0;
		outputBytes = 0;
		speed = "";
}
($1 == "Frames:") {
	transmittedFrames = $2
	receivedFrames = $3
}
($1 == "Error" && $2 == "Frames:") {
	errorFrames = $3
}
($1 == "Dumped" && $2 == "Frames:") {
	dumpFrames = $3
}
/^ *Port Speed \(running\): +[0-9]+ GBIT/ {
	linkStatus = "OK"
	speed = $4 * 1000
}
/^ +Input Bytes: +[0-9]+ *$/ {
	inputBytes = inputBytes + $3
}
/^ +Output Bytes: +[0-9]+ *$/ {
	outputBytes = outputBytes + $3
}
END {
		totalErrors = errorFrames + dumpFrames;
		
		printf("MSHW;%s;%.0f;%.0f;%.0f;%.0f;%.0f;%.0f\n", linkStatus, speed, totalErrors, receivedFrames, transmittedFrames, inputBytes, outputBytes);
}