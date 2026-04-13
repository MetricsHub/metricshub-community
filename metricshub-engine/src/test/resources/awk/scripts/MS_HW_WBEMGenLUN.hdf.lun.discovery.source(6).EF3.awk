BEGIN {FS="[;]"}
$1 ~ /.Device.MPIO/ { print ( "MSHW;" $1 ";" toupper($2) ";") }
