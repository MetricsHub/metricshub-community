$1 ~ /[Mm]emory/ && $2 ~ /[Ss]ize/ {size = $3;
                                    if ($4 ~ /[Gg]igabytes/) {size=size*1024}
                                    getline
                                    uname = $1
                                    gsub(/^[Ss].*,/,"",uname)
                                    if ((uname == "Ultra-250") || (uname == "Ultra-4") || (uname == "Ultra-Enterprise") || (uname == "Netra-T12")) {size = ""}
                                    print ("MSHW;Overall;"size";")
                                    }
