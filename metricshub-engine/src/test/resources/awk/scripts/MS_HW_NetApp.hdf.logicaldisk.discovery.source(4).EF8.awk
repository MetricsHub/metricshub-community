BEGIN { FS = ";"; totalSizeMB = 0; }
{
	sizeMB = $1;

	totalSizeMB = totalSizeMB + sizeMB;
}
END {
	printf("MSHW;PrimordialStoragePool;;;%d;Primordial Storage Pool;0\n", totalSizeMB);
}
