BEGIN {
	cpuID = 0;
}
/clock [0-9]+ MHz\)/ {
	printf("MSHW;%d;%s;%d\n", cpuID, $1, $(NF-1));
	cpuID++;
}
