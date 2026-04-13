BEGIN {FS=";";size=""}
$6 ~ /[GgTt].?[Bb]/ && $1 != "" {model=$3;gsub (/\042/,"",model);
                                 firmware=$4;gsub (/\042/,"",firmware);
                                 serial=$5;gsub (/\042/,"",serial);
                                 size=$6;gsub (/[Gg].?[Bb]/,"",size); gsub (/\042/,"",size) ; 
                                 status=$7
                                 if ($6 ~ /[Tt].?[Bb]/) {size=size*1024}
                                 print ("MSHW;"$1";"$2";"model";"firmware";"serial";"size";"status";")
                                 }

$7 ~ /[GgTt].?[Bb]/ && $1 != ""  {model=$4;gsub (/\042/,"",model);
                                 firmware=$5;gsub (/\042/,"",firmware);
                                 serial=$6;gsub (/\042/,"",serial);
                                 size=$7;gsub (/[Gg].?[Bb]/,"",size); gsub (/\042/,"",size) ; 
                                 if ($7 ~ /[Tt].?[Bb]/) {size=size*1024}
                                 status=$8
                                 print ("MSHW;"$1";"$2";"model";"firmware";"serial";"size";"status";")
                                 }       