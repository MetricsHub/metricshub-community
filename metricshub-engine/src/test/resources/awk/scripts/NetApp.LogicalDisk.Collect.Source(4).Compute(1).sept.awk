BEGIN { FS = ";"; totalUsedMB = 0; totalSizeMB = 0; }
{
	usedMB = $1;
	sizeMB = $2;
	
	totalUsedMB = totalUsedMB + usedMB;
	totalSizeMB = totalSizeMB + sizeMB;
}
END {
	unallocatedSpace = totalSizeMB - totalUsedMB;
	if (unallocatedSpace < 0) { unallocatedSpace = 0; }
	printf("MSHW;PrimordialStoragePool;OK;;;%d\n", unallocatedSpace);
}