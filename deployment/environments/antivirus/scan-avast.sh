#!/bin/bash

##########################################################################
# Role:                                                                  #
# Scan a single file using Avast Business Antivirus for Linux            #
##########################################################################
# Args:                                                                  #
# - file to scan                                                         #
##########################################################################
# Expected return values:                                                #
# - 0: scan OK - no virus                                                #
RET_OK=0
# - 1: virus found and corrected                                         #
RET_VIRUS_FOUND_FIXED=1
# - 2: virus found but not corrected                                     #
RET_VIRUS_FOUND_NOTFIXED=2
# - 3: Fatal scan not performed                                          #
RET_FAILURE=3
# Default return code : scan NOK                                         #
RET=$RET_FAILURE
##########################################################################
# For verbose mode, you can add -a parameter to the scan command         #
SCAN_PARAMS='-aifup'
# For verbose mode, you can remove the -q parameter to the unzip command #
UNZIP_PARAMS=''
##########################################################################

custom_scan () {
  local FILE_TO_SCAN="$1"

  scan $SCAN_PARAMS "$FILE_TO_SCAN" &>> ${WORKING_DIR}/scan.log
  local RET=$? # return code of scan
  if [ $RET != $RET_OK ]; then
    RET=$(($RET+1)) # Increase the return code to fit the expected Vitam's scan code
  fi

  # Specific case for scan not performed properly (probably because of big files, decompression bombs or ZIP archive corrupted)
  if [ $RET == $RET_FAILURE ]; then

    # Specific case for decompression bomb
    # Examples output:
    # avast: /vitam/data/ingest-external/upload/SIP_Test_decompression.zip|>content/ID6999.docx|>word/media/image8.emf: The file is a decompression bomb
    # avast: /vitam/data/ingest-external/aeeaaaaabshmriqsaafowal4jqrjleyaaaaq/aeeaaaaabshmriqsaafowal4jqrjleyaaaaq|>content/ID15.docx|>word/embeddings/Microsoft_PowerPoint_Presentation1.pptx|>ppt/media/image1.emf: The file is a decompression bomb
    local DECOMPRESSION_BOMBS=($(cat ${WORKING_DIR}/scan.log | grep -e 'The file is a decompression bomb' | rev | cut -d':' -f2 | cut -d'>' -f1 | rev))
    if [ -n "$DECOMPRESSION_BOMBS" ]; then
      # Whitelist => emf,wmf
      local REAL_BOMBS=($(echo $DECOMPRESSION_BOMBS | grep -vF -e '.emf' -e '.wmf' -e '.log'))

      # If we don't have real decompression bomb (maybe we have too big files)
      if [ -n "$REAL_BOMBS" ]; then
        echo "${#REAL_BOMBS[@]} real decompression bombs found !" |& tee -a ${WORKING_DIR}/scan.log
        RET=$RET_VIRUS_FOUND_NOTFIXED
      else
        echo "${#DECOMPRESSION_BOMBS[@]} decompression bombs ignored..." |& tee -a ${WORKING_DIR}/scan.log
        RET=$RET_VIRUS_FOUND_FIXED
      fi
    fi

    if [ $RET != $RET_VIRUS_FOUND_NOTFIXED ]; then
      # Specific case for corrupted or big files
      # Example output for zip: avast: /vitam/data/ingest-external/upload/SIP-Perf-1-1x500M.zip|>content/ID13.tmp: Compressed file is too big to be processed
      # Example output for tar.gz: avast: /vitam/data/ingest-external/upload/SIP-Perf-1-1x500M.tar.gz|>SIP-Perf-1-1x500M.tar: Compressed file is too big to be processed
      # Example output for tar.bz2: avast: /vitam/data/ingest-external/upload/SIP-Perf-1-1x500M.tar.bz2|>{bzip}: Compressed file is too big to be processed
      # Example output for corrupted: avast: /vitam/data/ingest-external/upload/SIP-Perf-1-18x400M.zip|>content/ID58.tmp: ZIP archive is corrupted
      local FILES_TO_INDIVIDUALLY_SCAN=($(cat ${WORKING_DIR}/scan.log | grep -e 'Compressed file is too big to be processed' -e 'ZIP archive is corrupted' | rev | cut -d':' -f2 | cut -d'>' -f1 | rev))
      if [ -n "$FILES_TO_INDIVIDUALLY_SCAN" ]; then
        # echo "Starting individual scan for ${#FILES_TO_INDIVIDUALLY_SCAN[@]} big files..." |& tee -a ${WORKING_DIR}/scan.log
        unzip_and_scan "$FILE_TO_SCAN" "${FILES_TO_INDIVIDUALLY_SCAN[@]}"
      fi
    fi

  fi

  return $RET

}

unzip_and_scan () {
  local ARCHIVE="$1"
  shift #Remove first element after attribute it
  local FILES="$@"

  echo "INFO: Starting individual scan for $# files..." |& tee -a ${WORKING_DIR}/scan.log

  for FILE in $FILES
  do

    local TMP_FILE="${WORKING_DIR}/${FILE}"

    if [ "$TYPE_SIP" == 'application/zip' ]; then
      unzip $UNZIP_PARAMS "$ARCHIVE" "${FILE}" -d "$WORKING_DIR" |& tee -a ${WORKING_DIR}/scan.log
    elif [ "$TYPE_SIP" == 'application/x-tar' ]; then
      tar xvf "$ARCHIVE" --directory "$WORKING_DIR" "${FILE}" |& tee -a ${WORKING_DIR}/scan.log
    elif [ "$TYPE_SIP" == 'application/gzip' ] || [ "$TYPE_SIP" == 'application/x-gzip' ]; then
      mkdir -p "$TMP_FILE"
      tar xvzf "$ARCHIVE" --directory "$TMP_FILE" |& tee -a ${WORKING_DIR}/scan.log #uncompress the entire archive
      # gunzip -c $ARCHIVE > $TMP_FILE |& tee -a ${WORKING_DIR}/scan.log
    elif [ "$TYPE_SIP" == 'application/x-bzip2' ]; then
      mkdir -p "$TMP_FILE"
      tar xvjf "$ARCHIVE" --directory "$TMP_FILE" |& tee -a ${WORKING_DIR}/scan.log #uncompress the entire archive
    else
      echo "ERROR: $ARCHIVE: mime-type $TYPE_SIP is not supported" |& tee -a ${WORKING_DIR}/scan.log
      break # exit loop
    fi

    # Normal scan...
    custom_scan "$TMP_FILE"
    RET=$? # return code of scan

    rm -f "$TMP_FILE"

    if [ $RET -ge 2 ]; then
      break # exit loop
    fi

  done
  return $RET

}

################################################################################

if [ $# -ne 1 ]; then # Argument number must be one
  echo "ERROR : $# parameter(s) provided, only one parameter is needed"

else # one argument, let's go
  SIP=$1
  if [ ! -f "$SIP" ];then # if the file wich will be scan is existing, keep going
    echo "ERROR : \"$SIP\" doesn't exit"

  else
    START_TIME=$(date +%s)
    WORKING_DIR=$(mktemp -d -p /vitam/tmp/ingest-external/)
    chmod -R 770 $WORKING_DIR
    chown -R vitam:vitam $WORKING_DIR

    echo "$(date +"%Y-%m-%d %T") - scanning $SIP" |& tee -a ${WORKING_DIR}/scan.log

    FILE_SIZE=$(stat -c '%s' "$SIP")
    TYPE_SIP=$(file -b --mime-type "$SIP")
    FILE_SUM=$(sha256sum $SIP | cut -d' ' -f1)

    echo "DEBUG: SIP_size: $FILE_SIZE; SIP_format: $TYPE_SIP; sha256sum: $FILE_SUM" |& tee -a ${WORKING_DIR}/scan.log

    if grep -Fxq "$FILE_SUM" /etc/avast/whitelist; then
      echo "File whitelisted, escape scanning..." |& tee -a ${WORKING_DIR}/scan.log
      RET=0
    else
      custom_scan "$SIP"
      RET=$? # return code of scan
    fi

    # Catch global output reason
    if [ $RET == $RET_OK ]; then
      RET_MSG='[OK]'
    elif [ $RET == $RET_VIRUS_FOUND_FIXED ]; then
      RET_MSG='[OK: Virus found but ignored]'
      RET=$RET_OK # FORCE O UNTIL RET=1 WELL HANDLED BY VITAM
    elif [ $RET == $RET_VIRUS_FOUND_NOTFIXED ]; then
      REASON=$(cat ${WORKING_DIR}/scan.log | tail --lines=1 | cut -f2-)
      RET_MSG="[KO: Virus found - $REASON]"
    elif [ $RET == $RET_FAILURE ]; then
      REASON=$(cat ${WORKING_DIR}/scan.log | tail --lines=1 | cut -d":" -f3- | xargs)
      RET_MSG="[ERROR: Scan not performed - $REASON]"
    fi

    END_TIME=$(date +%s)
    EXECUTION_TIME=$(($END_TIME - $START_TIME))
    echo -e "$SIP\t$RET_MSG - execution time: ${EXECUTION_TIME}s" |& tee -a ${WORKING_DIR}/scan.log

    cat ${WORKING_DIR}/scan.log >> /vitam/log/avast/scan.log # Stores output in global log file for avast; helpful for debugging purpose
    rm -rf ${WORKING_DIR} # cleaning temporary working dir
  fi
fi
exit $RET
