# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

*** Settings ***
Documentation       Keyword definitions for Ozone Debug CLI tests
Library             Collections
Resource            ../lib/os.robot

*** Variables ***
${OM_SERVICE_ID}                    %{OM_SERVICE_ID}

*** Keywords ***
Execute replicas verify checksums CLI tool
    Execute                         ozone debug -Dozone.network.topology.aware.read=true replicas verify --checksums o3://${OM_SERVICE_ID}/${VOLUME}/${BUCKET}/${TESTFILE}
    ${directory} =                  Execute     ls -d ${TEMP_DIR}/${VOLUME}_${BUCKET}_${TESTFILE}_*/ | tail -n 1
    Directory Should Exist          ${directory}
    File Should Exist               ${directory}/${TESTFILE}_manifest
    [Return]                        ${directory}

Read Replicas Manifest
    ${manifest} =        Get File        ${DIR}/${TESTFILE}_manifest
    ${json} =            Evaluate        json.loads('''${manifest}''')        json
    Validate JSON                        ${json}
    [return]    ${json}

Validate JSON
    [arguments]                     ${json}
    Should Be Equal                 ${json}[filename]                   ${VOLUME}/${BUCKET}/${TESTFILE}
    ${file_size} =                  Get File Size                       ${TEMP_DIR}/${TESTFILE}
    Should Be Equal                 ${json}[datasize]                   ${file_size}
    Should Be Equal As Integers     ${json}[blocks][0][blockIndex]      1
    Should Not Be Empty             Convert To String       ${json}[blocks][0][containerId]
    Should Not Be Empty             Convert To String       ${json}[blocks][0][localId]
    Should Be Equal As Integers     ${json}[blocks][0][length]          1048576
    Should Not Be Empty             Convert To String       ${json}[blocks][0][offset]
    Should Be Equal As Integers     ${json}[blocks][1][blockIndex]      2
    Should Not Be Empty             Convert To String       ${json}[blocks][1][containerId]
    Should Not Be Empty             Convert To String       ${json}[blocks][1][localId]
    Should Be Equal As Integers     ${json}[blocks][1][length]          451424
    Should Not Be Empty             Convert To String       ${json}[blocks][1][offset]

Get Replica Filenames
    [arguments]                     ${json}    ${replica}

    ${list} =     Create List

    FOR    ${block}    IN RANGE    2
        ${datanode} =    Set Variable    ${json}[blocks][${block}][replicas][${replica}][hostname]
        ${n} =           Evaluate    ${block} + 1
        Append To List   ${list}    ${DIR}/${TESTFILE}_block${n}_${datanode}
    END

    ${filenames} =   Catenate    @{list}

    [return]    ${filenames}

Verify Healthy Replica
    [arguments]              ${json}    ${replica}    ${expected_md5sum}

    ${block_filenames} =     Get Replica Filenames    ${json}    ${replica}
    ${md5sum} =              Execute     cat ${block_filenames} | md5sum | awk '{print $1}'
    Should Be Equal          ${md5sum}   ${expected_md5sum}

Verify Healthy EC Replica
    [arguments]              ${directory}    ${block}    ${expected_block_size}

    ${block_size} =          Execute     ls -l ${directory} | grep "testfile_block${block}_ozone-datanode-.*\.ozone_default" | awk '{sum += $5} END {print sum}'
    Should Be Equal As Integers      ${block_size}     ${expected_block_size}

Verify Corrupt Replica
    [arguments]              ${json}    ${replica}    ${valid_md5sum}

    ${block_filenames} =     Get Replica Filenames    ${json}    ${replica}
    ${md5sum} =              Execute     cat ${block_filenames} | md5sum | awk '{print $1}'
    Should Not Be Equal      ${md5sum}   ${valid_md5sum}

Verify Stale Replica
    [arguments]              ${json}    ${replica}

    FOR    ${block}    IN RANGE    2
        ${datanode} =    Set Variable    ${json}[blocks][${block}][replicas][${replica}][hostname]

        IF    '${datanode}' == '${STALE_DATANODE}'
            Should Contain          ${json}[blocks][${block}][replicas][${replica}][exception]    UNAVAILABLE
        END
    END
