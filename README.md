# LDAP-RTC-Synchronizer

A project to produce LDAP2RTCSync, a utility to synchronize user information in LDAP RACF with RTC.

## Synopsis

The utility LDAP2RTCSync allows administrators to create groups in LDAP that can then be used to configure RTC:

* Client Access License allocation
* Project and Team Area membership
* Project and Team Area process role assignments

LDAP2RTCSync complements capabilities already provided by `repotools -userSync` which synchronizes LDAP group members with the Jazz Team Server users, including updating user names and email addresses. 

 
## Example

./syncUsers.sh --config Sample-config.json

Sample-config.json:

```
{
    "LDAPConnection": {
        "URI": "ldap://mvs255.rtp.raleigh.ibm.com",
        "userId": "racfid=BGREEN,profiletype=USER,CN=RACF255,O=IBM,C=RTC",
        "password": "ARfvvd0+8A4="
    },
    "RTCServers": [
        {"serverURI": "https://rtceerb.rtp.raleigh.ibm.com:9443/ccm",
         "admin": "JAZZ",
         "password": "FdjJiGnSwpU=",
         "Licenses": [
            {"Rational Team Concert - Developer": "racfid=RTCDLI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC"},
            {"Rational Team Concert - Contributor": "racfid=RTCCLI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC"}
         ],
         "Project Areas": [
            {"name": "Pet Store",
             "Administrators": "racfid=APETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC",
             "Members": "racfid=APETBI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC",
             "Process Roles": [
                {"Developer": "racfid=OPETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC"},
                {"Tester": "racfid=TPETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC"}],
             "Team Areas": [
                {"name": "Pet Management",
                "Administrators": "racfid=APETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC",
                "Members": "racfid=APETBI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC",
                "Process Roles": [
                    {"Developer": "racfid=OPETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC"},
                    {"Tester": "racfid=TPETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC"}]
                }]
            }            
           ]
        }
    ]
}
```

## Motivation

RTC currently provides a utility, `repotools -syncusers`, to allow customers configure RTC with users pulled from LDAP for authentication as well as define which users are Jazz Admin or Jazz User (repository permissions).  However, it does not support any external group definitions or the ability for LDAP to determine the team membership, roles for individuals and allocated licenses.  

The purpose of LDAP-RTC-Sync is to provide a solution to allow group membership to be defined in LDAP along with the appropriate roles.  This utility was created to initiate the process of taking the information out of the LDAP groups and configuring the RTC Team Area’s automatically.

## Installation

Unzip syncUsers.zip to a convenient folder. The contents contain:

* **license** - a folder containing the International License Agreement for Non-Warranted Programs
* **log4j.properties** - so that clients can change the logging preferences
* **Sample-config.json** - just to provide a sample for documentation
* **syncUsers.bat** - Windows command for invoking syncUsers
* **syncUsers.sh** - UNIX shell command for invoking syncUsers
* **syncUsers.jar** - the JAR file containing LDAP2LDPSync and all its dependencies
* **syncUsers.pdf** - the README.md file documentation of the program exported as a PDF

## Password Obfuscation

Passwords must be supplied for a user with read permissions on the LDAP repository, and an administrator for the RTC servers to configure. These passwords are stored in the JSON config file as encrypted strings. To encrypt a password, use the --encrypt command line argument:

`./syncUsers --encrypt`

The program will prompt for a password and then print the encrypted string to standard output. Copy this string into the proper password value in the JSON config file.


## JSON Configuration File format

The JSON configuration file defines the LDAP server that provides the groups and group members, and a number of RTC Server objects that specify the project and team area administrators and members, the members' process roles, and the client access licenses that should be allocated for the users. Each entry maps an object in RTC to an LDAP group. The members of that group specify the users that are used by that entry. The LDAP groups can also contain subgroups, and the members of the subgroups are recursively applied to the entry.

For example, in the Sample-config.json file above, the Administrators for the *Pet Store* project area are defined by the RACF group with DN racfid=APETKI,profiletype=GROUP,CN=RACF255,O=IBM,C=RTC.

The RTCServers admin is the administrator of the server accessible through serverURI. The JTS server admin can administer any project or team area, they do not need to be a member or administrator of the project area.

## Tests

JUnit tests in test/TestLDAP2RTCSync.java are used to test LDP2RTCSync.java. The test cases read an initial config file to configure RTC in a known way, and then another exectution of LDAP2RTCSync reads a final config file to change the server's project and team areas, and licenses in a known way. The results can then be manually verified by viewing the project area configurations using the RTC web client.

## Dependencies

This distribution has the following dependencies:

 * Rational Team Concert Plain Java Client APIs version 6.0.2
 * junit 4.12
 * Maven artifact com.googlecode.json-simple:json-simple version 1.1.1
 * Maven artifact org.slf4j:slf4j-log4j12 version 1.7.25
 * Maven artifact commons-cli:commons-cli 1.2

## Contributors

Contributors:

* Jim Amsden (IBM)
* David Bellagio (IBM)
* Ralph Schoon (IBM)

## License

Licensed under the [Eclipse Public License](./LDAP-RTC-Sync/license.txt).

