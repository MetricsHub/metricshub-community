BEGIN {FS=";";}
NF > 4 && $1 != "" {if ($3 ~ /^[0-9]+$/) {warnThreshold = ""
                                          if ($4 ~ /[Cc][Pp][Uu]/ && $5 ~ /^[-0-9.]*$/) {if ($5 > 0) {warnThreshold = 50};
                                                                                        if ($5 <= 0) {warnThreshold = -10};
                                                                                       }
                                          if ($4 ~ /[Aa]mbient/ && $5 ~ /^[-0-9.]*$/) {warnThreshold = 50}
                                          print "MSHW;" $1 ";" $3 ";" $4 ";" $5 ";" $6 ";" warnThreshold ";"
                                         }
                      else {warnThreshold = ""
                            if ($3 ~ /[Cc][Pp][Uu]/ && $4 ~ /^[-0-9.]*$/) {if ($4 > 0) {warnThreshold = 70};
                                                                          if ($4 <= 0) {warnThreshold = -10};
                                                                         }
                            if ($3 ~ /[Aa]mbient/ && $4 ~ /^[-0-9.]*$/) {warnThreshold = 50}
                            print "MSHW;" $1 ";" $2 ";" $3 ";" $4 ";" $5 ";" warnThreshold ";"
                           }
                   }
