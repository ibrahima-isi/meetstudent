*** Settings ***
Library    RequestsLibrary
Library    Collections
Library    String
Library    OperatingSystem
Library    DateTime

*** Variables ***
${BASE_URL}    http://localhost:8080/api/v1
${ADMIN_EMAIL}    admin@meetstudent.com
${PASSWORD}    password

*** Test Cases ***
Seed And Test MeetStudent API
    [Documentation]    End to end test of the MeetStudent API using Robot Framework
    Create Session    api    ${BASE_URL}
    
    Log To Console    \n1. Logging in as Admin...
    ${admin_creds}=    Create Dictionary    username=${ADMIN_EMAIL}    password=${PASSWORD}
    ${resp}=    POST On Session    api    /auth    json=${admin_creds}
    ${ADMIN_TOKEN}=    Set Variable    ${resp.json()}[accessToken]
    ${admin_headers}=    Create Dictionary    Authorization=Bearer ${ADMIN_TOKEN}

    Log To Console    2. Fetching Roles...
    ${resp}=    GET On Session    api    /roles    headers=${admin_headers}
    ${roles}=    Set Variable    ${resp.json()}
    ${student_role_id}=    Set Variable    4
    ${expert_role_id}=     Set Variable    3
    FOR    ${role}    IN    @{roles}
        IF    "${role}[name]" == "ROLE_STUDENT"
            ${student_role_id}=    Set Variable    ${role}[id]
        END
        IF    "${role}[name]" == "ROLE_EXPERT"
            ${expert_role_id}=    Set Variable    ${role}[id]
        END
    END

    Log To Console    3. Creating 5 Tags...
    ${ts}=    Get Current Date    result_format=%H%M%S
    @{tag_names}=    Create List    T1_${ts}    T2_${ts}    T3_${ts}    T4_${ts}    T5_${ts}
    ${tags}=    Create List
    FOR    ${name}    IN    @{tag_names}
        ${payload}=    Create Dictionary    name=${name}
        ${resp}=    POST On Session    api    /tags    json=${payload}    headers=${admin_headers}
        Append To List    ${tags}    ${resp.json()}
    END

    Log To Console    4. Creating 10 Schools...
    ${schools}=    Create List
    FOR    ${i}    IN RANGE    1    11
        ${school_tags}=    Create List    ${tags}[0]    ${tags}[1]
        ${address}=    Create Dictionary    city=City${i}    country=Country${i}
        ${code_str}=    Convert To String    ${i}
        ${short_ts}=    Get Substring    ${ts}    -2
        ${code}=    Set Variable    S${short_ts}${code_str}
        ${payload}=    Create Dictionary    name=School ${ts} ${i}    code=${code}    address=${address}    tags=${school_tags}
        ${resp}=    POST On Session    api    /schools    json=${payload}    headers=${admin_headers}
        Append To List    ${schools}    ${resp.json()}
    END

    Log To Console    5. Creating 100 Programs (10 per school)...
    ${programs}=    Create List
    ${p_count}=    Set Variable    1
    FOR    ${school}    IN    @{schools}
        FOR    ${i}    IN RANGE    1    11
            ${code_str}=    Convert To String    ${p_count}
            ${code}=    Set Variable    P${code_str}
            ${payload}=    Create Dictionary    name=Prog ${p_count}    code=${code}    duration=3    schoolId=${school}[id]
            ${resp}=    POST On Session    api    /programs    json=${payload}    headers=${admin_headers}
            Append To List    ${programs}    ${resp.json()}
            ${p_count}=    Evaluate    ${p_count} + 1
        END
    END

    Log To Console    6. Creating 500 Courses (5 per program)...
    ${courses}=    Create List
    ${c_count}=    Set Variable    1
    FOR    ${prog}    IN    @{programs}
        FOR    ${i}    IN RANGE    1    6
            ${code_str}=    Convert To String    ${c_count}
            ${code}=    Set Variable    C${code_str}
            ${payload}=    Create Dictionary    name=Course ${c_count}    code=${code}    programId=${prog}[id]
            ${resp}=    POST On Session    api    /courses    json=${payload}    headers=${admin_headers}
            Append To List    ${courses}    ${resp.json()}
            ${c_count}=    Evaluate    ${c_count} + 1
        END
    END

    Log To Console    7. Creating Student and Expert Users...
    ${role_obj_student}=    Create Dictionary    id=${student_role_id}
    ${payload}=    Create Dictionary    firstname=Sam    lastname=Stud    email=sam${ts}@test.com    password=password    confirmedPassword=password    role=${role_obj_student}
    ${resp}=    POST On Session    api    /users    json=${payload}
    ${student}=    Set Variable    ${resp.json()}

    ${role_obj_expert}=    Create Dictionary    id=${expert_role_id}
    ${payload}=    Create Dictionary    firstname=Ev    lastname=Exp    email=ev${ts}@test.com    password=password    confirmedPassword=password    role=${role_obj_expert}
    ${resp}=    POST On Session    api    /users    json=${payload}
    ${expert}=    Set Variable    ${resp.json()}

    Log To Console    8. Logging in as Student and Expert...
    ${creds}=    Create Dictionary    username=${student}[email]    password=password
    ${resp}=    POST On Session    api    /auth    json=${creds}
    ${STUDENT_TOKEN}=    Set Variable    ${resp.json()}[accessToken]
    ${student_headers}=    Create Dictionary    Authorization=Bearer ${STUDENT_TOKEN}

    ${creds}=    Create Dictionary    username=${expert}[email]    password=password
    ${resp}=    POST On Session    api    /auth    json=${creds}
    ${EXPERT_TOKEN}=    Set Variable    ${resp.json()}[accessToken]
    ${expert_headers}=    Create Dictionary    Authorization=Bearer ${EXPERT_TOKEN}

    Log To Console    9. Uploading Media for Student...
    Create File    dummy.txt    Dummy content
    ${file_obj}=    Evaluate    builtins.open('dummy.txt', 'rb')    sys, builtins
    ${file_tuple}=    Evaluate    ('dummy.txt', $file_obj, 'text/plain')
    ${files}=    Create Dictionary    file=${file_tuple}
    ${resp}=    POST On Session    api    /media/users/upload    files=${files}    headers=${student_headers}
    ${diploma_url}=    Set Variable    ${resp.json()}[url]

    Log To Console    10. Updating Profile and Wishlist for Student...
    ${diplomas}=    Create List    ${diploma_url}
    ${payload}=    Create Dictionary    diplomas=${diplomas}
    ${resp}=    PATCH On Session    api    /users/${student}[id]    json=${payload}    headers=${student_headers}
    
    ${school_id}=    Set Variable    ${schools}[0][id]
    ${resp}=    POST On Session    api    /users/${student}[id]/wishlist/${school_id}    headers=${student_headers}

    Log To Console    11. Uploading Media and Updating Expert Profile...
    ${file_obj_cert}=    Evaluate    builtins.open('dummy.txt', 'rb')    sys, builtins
    ${file_tuple_cert}=    Evaluate    ('dummy.txt', $file_obj_cert, 'text/plain')
    ${files_cert}=    Create Dictionary    file=${file_tuple_cert}
    ${resp}=    POST On Session    api    /media/users/upload    files=${files_cert}    headers=${expert_headers}
    ${cert_url}=    Set Variable    ${resp.json()}[url]
    
    ${certs}=    Create List    ${cert_url}
    ${payload}=    Create Dictionary    certificates=${certs}
    ${resp}=    PATCH On Session    api    /users/${expert}[id]    json=${payload}    headers=${expert_headers}

    Log To Console    12. Rating Entities...
    # Student rates school
    ${payload}=    Create Dictionary    note=4.5    comment=Great school    schoolId=${school_id}    userId=${student}[id]
    ${resp}=    POST On Session    api    /school-rates    json=${payload}    headers=${student_headers}

    # Expert rates school, program, course
    ${school_id_2}=    Set Variable    ${schools}[1][id]
    ${payload}=    Create Dictionary    note=5.0    comment=Top facilities    schoolId=${school_id_2}    userId=${expert}[id]
    ${resp}=    POST On Session    api    /school-rates    json=${payload}    headers=${expert_headers}

    ${prog_id}=    Set Variable    ${programs}[0][id]
    ${payload}=    Create Dictionary    note=4.0    comment=Good curriculum    programId=${prog_id}    userId=${expert}[id]
    ${resp}=    POST On Session    api    /program-rates    json=${payload}    headers=${expert_headers}

    ${course_id}=    Set Variable    ${courses}[0][id]
    ${payload}=    Create Dictionary    note=3.0    comment=Ok basics    courseId=${course_id}    userId=${expert}[id]
    ${resp}=    POST On Session    api    /course-rates    json=${payload}    headers=${expert_headers}

    Log To Console    13. Validating Data...
    ${resp}=    GET On Session    api    /school-rates/school/${school_id}
    Should Be Equal As Integers    ${resp.status_code}    200

    Log To Console    ALL TESTS PASSED SUCCESSFULLY!

