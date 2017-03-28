# LDAP-RTC-Synchronizer

A project to produce LDAP2RTCSync, a utility to synchronize user information in LDAP RACF with RTC.

## Synopsis

The utility LDAP2RTCSync allows administrators to create groups in LDAP that can then be used to configure RTC:

* Client Access License allocation
* Project and Team Area membership
* Project and Team Area process role assignments

LDAP2RTCSync complements capabilities already provided by `repotools -userSync` which synchronizes LDAP group members with the Jazz Team Server users, including updating user names and email addresses. 

 
## Example

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

A short description of the motivation behind the creation and maintenance of the project. This should explain **why** the project exists.

## Installation

Provide code examples and explanations of how to get the project.

## API Reference

Depending on the size of the project, if it is small and simple enough the reference docs can be added to the README. For medium size to larger projects it is important to at least provide a link to where the API reference docs live.

## Tests

Describe and show how to run the tests with code examples.

## Contributors

Let people know how they can dive into the project, include important links to things like issue trackers, irc, twitter accounts if applicable.

## License

A short snippet describing the license (MIT, Apache, etc.)
