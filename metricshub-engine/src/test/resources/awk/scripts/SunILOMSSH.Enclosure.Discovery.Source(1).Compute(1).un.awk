BEGIN  {ID="";fruName="";fruVendor="";type="";sensorValue="";upperAlarm="";upperWarn="";lowerAlarm="";lowerWarn=""}

                     
$1 ~ /\057SYS/        {print("MSHW;"ID";"fruName";"fruVendor";"type";"sensorValue";"upperAlarm";"upperWarn";"lowerAlarm";"lowerWarn";"numericalSensorValue";")
                       ID="";fruName="";fruVendor="";type="";sensorValue="";upperAlarm="";upperWarn="";lowerAlarm="";lowerWarn="";numericalSensorValue="";
                       ID=$1 ; gsub(/\057SYS\057/,"",ID);}

$1 ~ /type/ && ID != ""                                             {eqIndex=index($0,"="); type = substr($0,eqIndex+2);}

$1 ~ /product_name/ && ID != ""                                     {eqIndex=index($0,"="); fruName = substr($0,eqIndex+2);}
$1 ~ /fru_name/ && ID != "" && fruName == ""                        {eqIndex=index($0,"="); fruName = substr($0,eqIndex+2);}

$1 ~ /product_manufacturer/ && ID != ""                             {eqIndex=index($0,"="); fruVendor = substr($0,eqIndex+2);}
$1 ~ /fru_manufacturer/ && ID != "" && fruVendor==""                {eqIndex=index($0,"="); fruVendor = substr($0,eqIndex+2);}

$1 ~ /value/ && ID != ""                                            {eqIndex=index($0,"="); sensorValue = substr($0,eqIndex+2);
                                                                     if (sensorValue=="Not Readable") {sensorValue = ""} ;
                                                                     if (sensorValue ~ /^ *[0-9]/) {numericalSensorValue = sensorValue ; sensorValue = ""}
                                                                    }

$1 ~ /upper_nonrecov_threshold/ && $NF !~ /N.A/                     {upperAlarm=$3}
$1 ~ /upper_critical_threshold/ && $NF !~ /N.A/                     {upperWarn=$3}
$1 ~ /upper_noncritical_threshold/ && $NF !~ /N.A/                  {upperAlarm=upperWarn ; upperWarn=$3}
$1 ~ /lower_noncritical_threshold/ && $NF !~ /N.A/                  {lowerWarn=$3}
$1 ~ /lower_critical_threshold/ && $NF !~ /N.A/ && lowerWarn == ""  {lowerWarn=$3}
$1 ~ /lower_critical_threshold/ && $NF !~ /N.A/ && lowerWarn != ""  {lowerAlarm=$3}
$1 ~ /lower_nonrecov_threshold/ && $NF !~ /N.A/ && lowerAlarm == "" {lowerAlarm=$3}

END {print("MSHW;"ID";"fruName";"fruVendor";"type";"sensorValue";"upperAlarm";"upperWarn";"lowerAlarm";"lowerWarn";"numericalSensorValue";");}