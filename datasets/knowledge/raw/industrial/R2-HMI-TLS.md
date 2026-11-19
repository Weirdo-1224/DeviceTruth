Configuration of TLS-based PG/HMI Communication And The Protection of Confidential PLC Configuration Data

TIA Portal V17 / S7-1500 PLC / TP1200 Comfort Panel

https://support.industry.siemens.com/cs/ww/en/view/109798583

---

All rights reserved
2021
Siemens AG 
©

## Legal information

###### Use of application examples

Application examples illustrate the solution of automation tasks through an interaction of several components in 
the form of text, graphics and/or software modules. The application examples are a free service by Siemens AG 
and/or a subsidiary of Siemens AG ("Siemens"). They are non-binding and make no claim to completeness or 
functionality regarding configuration and equipment. The application examples merely offer help with typical 
tasks; they do not constitute customer-specific solutions. You yourself are responsible for the proper and safe 
operation of the products in accordance with applicable regulations and must also check the function of the 
respective application example and customize it for your system.

Siemens grants you the non-exclusive, non-sublicensable and non-transferable right to have the application 
examples used by technically trained personnel. Any change to the application examples is your responsibility. 
Sharing the application examples with third parties or copying the application examples or excerpts thereof is 
permitted only in combination with your own products. The application examples are not required to undergo the 
customary tests and quality inspections of a chargeable product; they may have functional and performance 
defects as well as errors. It is your responsibility to use them in such a manner that any malfunctions that may 
occur do not result in property damage or injury to persons.

###### Disclaimer of liability

Siemens shall not assume any liability, for any legal reason whatsoever, including, without limitation, liability for
the usability, availability, completeness and freedom from defects of the application examples as well as for 
related information, configuration and performance data and any damage caused thereby. This shall not apply in 
cases of mandatory liability, for example under the German Product Liability Act, or in cases of intent, gross 
negligence, or culpable loss of life, bodily injury or damage to health, non-compliance with a guarantee, 
fraudulent non-disclosure of a defect, or culpable breach of material contractual obligations. Claims for damages 
arising from a breach of material contractual obligations shall however be limited to the foreseeable damage 
typical of the type of agreement, unless liability arises from intent or gross negligence or is based on loss of life, 
bodily injury or damage to health. The foregoing provisions do not imply any change in the burden of proof to 
your detriment. You shall indemnify Siemens against existing or future claims of third parties in this connection 
except where Siemens is mandatorily liable.

By using the application examples you acknowledge that Siemens cannot be held liable for any damage beyond 
the liability provisions described.

###### Other information

Siemens reserves the right to make changes to the application examples at any time without notice. In case of 
discrepancies between the suggestions in the application examples and other Siemens publications such as 
catalogs, the content of the other documentation shall have precedence.

The Siemens terms of use (https://support.industry.siemens.com) shall also apply.

###### Security information

Siemens provides products and solutions with Industrial Security functions that support the secure operation of 
plants, systems, machines and networks.

In order to protect plants, systems, machines and networks against cyber threats, it is necessary to implement –
and continuously maintain – a holistic, state-of-the-art industrial security concept. Siemens’ products and 
solutions constitute one element of such a concept.

Customers are responsible for preventing unauthorized access to their plants, systems, machines and networks. 
Such systems, machines and components should only be connected to an enterprise network or the Internet if 
and to the extent such a connection is necessary and only when appropriate security measures (e.g. firewalls 
and/or network segmentation) are in place.

For additional information on industrial security measures that may be implemented, please visit 
https://www.siemens.com/industrialsecurity.

Siemens’ products and solutions undergo continuous development to make them more secure. Siemens strongly 
recommends that product updates are applied as soon as they are available and that the latest product versions 
are used. Use of product versions that are no longer supported, and failure to apply the latest updates may 
increase customer’s exposure to cyber threats.

To stay informed about product updates, subscribe to the Siemens Industrial Security RSS Feed at: 
https://www.siemens.com/industrialsecurity.

---

All rights reserved
2021
Siemens AG 
©

## Table of contents

**Legal information..............................................................................................................................2**
**1 Introduction.............................................................................................................................5**
1.1 Overview....................................................................................................................5
1.2 Mode of Operation.....................................................................................................5
1.3 Components Used.....................................................................................................7
**2 New Security Features for PLC and HMI panel in TIA Portal V17......................................8**
2.1 "Security-By-Default" Concept...................................................................................8
2.2 Secure PG/PC and HMI Communication..................................................................9
2.2.1 Transport Layer Security - TLS.................................................................................9
2.2.2 Secure Communication Mechanism........................................................................10
2.3 Protection of the PLC Confidential Configuration Data...........................................13
2.4 Security Wizard........................................................................................................15
2.5 Communication Compatibility..................................................................................16
**3 Engineering ...........................................................................................................................17**
3.1 Project Preparation..................................................................................................17
3.2 SIMATIC Controller Configuration...........................................................................19
3.2.1 PLC security settings...............................................................................................19
3.2.2 Set the IP address...................................................................................................23
3.2.3 Activate global security settings for certificate manager .........................................24
3.3 HMI panel Configuration..........................................................................................27
3.3.1 PLC and HMI Panel are in the same TIA Portal Project..........................................28
3.3.2 PLC and HMI Panel are in two different TIA Portal Projects...................................30
3.3.3 HMI Panel is not configured in TIA Portal - Connection with WinCC SCADA 
V7 Systems..............................................................................................................35
**4 Installation and Commissioning..........................................................................................36**
4.1 Hardware Setup.......................................................................................................36
4.2 Installing Hardware and Software Components......................................................36
4.3 Load hardware components....................................................................................37
4.3.1 Load the S7-1500 PLC............................................................................................37
4.3.2 Load the SIMATIC TP1200 Comfort Panel.............................................................40
4.4 Operation.................................................................................................................42
**5 Device Exchange Scenarios................................................................................................45**
5.1 The Replacement PLC has no password for confidential Configuration Data........46
5.2 The Replacement PLC has the same password for confidential Configuration Data46
5.3 The Replacement PLC has another password for confidential Configuration Data 47
5.3.1 Setting the password in TIA Portal..........................................................................47
5.3.2 Setting the password using an additional SIMATIC Memory Card.........................49
**6 Firmware Update & Device Backup ....................................................................................51**
6.1 Firmware Update of the S7-1500 PLC....................................................................51
6.2 Backing up and Restoring a PLC (S7-1200 PLC, S7-1500 PLC) ...........................52
6.3 Firmware Update and Device Backup of the HMI panel.........................................53
**7 Useful Information................................................................................................................54**
7.1 A List of Components that Support Secure PG/PC and HMI Communication........54

---

All rights reserved
2021
Siemens AG 
©

7.2 Changing the password to protect confidential PLC Configuration Data (S7-1200 
PLC, S7-1500 PLC).................................................................................................54
7.2.1 Change Password - Configuration is not yet loaded...............................................54
7.2.2 Change Password - Configuration is already loaded..............................................57
7.3 Resetting the password to protect confidential PLC Configuration Data (S7-1200 
PLC, S7-1500 PLC).................................................................................................60
7.3.1 Resetting the password - Configuration is not yet loaded.......................................60
7.3.2 Resetting the password - Configuration is already loaded......................................61
7.3.3 Resetting the password using SIMATIC Memory Card...........................................62
7.4 Tips for Error Avoidance and Error Handling ..........................................................63
7.5 Using Legacy PG/PC Communication in TIA Portal................................................64
7.6 TIA Portal V17 Project Description..........................................................................66
7.6.1 Overview..................................................................................................................66
7.6.2 The "SimulatedDrive" function block .......................................................................67
7.6.3 The "SimulatedDriveData" global data block...........................................................68
**Appendix................................................................................................................................69**
8.1 Service and support.................................................................................................69
8.2 Industry Mall ............................................................................................................70
8.3 Links and literature..................................................................................................70
8.4 Change documentation ...........................................................................................71

---

All rights reserved
2021
Siemens AG 
©

## 1 Introduction

### 1.1 Overview

##### Task

Digitalization and the growing networking of machines and industrial systems also mean an 
increase in the risk of cyberattacks. Appropriate protective measures are imperative, especially 
for critical infrastructure facilities.

##### Approach

As a pioneer in industrial security world, Siemens has always aimed to provide holistic, state-ofthe-art solutions to ensure maximum protections of machines and plants. For this reason, 
Siemens has introduced new security features in TIA Portal V17. These features ensure that 
communication data is not subject to manipulation by means of encryption. They also provide 
protection against unauthorized access to machines and software.

##### Solution Description

This application example describes the new security functions that have been introduced with 
TIA Portal V17 and how to apply them on S7-1500 PLCs in connection with HMI panels. The 
user will learn how to use the newly introduced security wizard that helps with the security 
configuration of the PLC. The security configuration includes the following configuration steps:

• Protect the confidential configuration data of the PLC.

• Connect HMI panels to the CPU via secure communication connections.

• Configure the PLC access protection.

In addition to that, the following use cases are described:

• Replace an older device with a new one

• Compatibility with older devices

• Configurations that have been deployed with TIA Portal < V17

• Updating the firmware version of the PLC and the HMI panel

### 1.2 Mode of Operation

In the production an S7-1500 PLC is used to monitor and control a conveyor system and 
conveyor belts. The S7-1500 PLC checks the actual speed of the conveyor belt, "actualSpeed", 
at regular intervals and compares it with a predefined value, "setPointSpeed".

• If the actual speed is greater than the predefined value, the speed "actualSpeed" is reduced 
to the value "setPointSpeed".

• If the actual speed is less than the predefined value, the speed "actualSpeed" is increased 
to the value "setPointSpeed".

The S7-1500 is connected to an HMI panel via Ethernet and communicates with it over TCP/IP. 
The communication is secured by means of Transport Layer Security protocol (TLS) which uses 
digital certificates for encryption and authentication. The "setPointSpeed" value can be 
configured on the HMI panel which also shows the "actualSpeed"" value and the current state of 
the conveyor belt "isActive".

---

All rights reserved
2021
Siemens AG 
©

##### Diagram

The following figure shows the most important components of the solution:

Figure 1-1

**S7-1500 PLC**
**Industrial Ethernet**
**TP Comfort 1200**
**Tags**

##### Implemented Functions

The following functions are implemented in the application example:

• Configuration of the following S7-1500 PLC security features using the security wizard:

– The protection of the confidential configuration data of the PLC.

– The secure connection to the HMI panel using digital certificates.

– The PLC access protection.

• Configuration of the HMI panel for secure connection with the S7-1500 PLC. This includes 
the following possibilities:

– The PLC and the HMI panel are in the same TIA Portal project.

– The PLC and the HMI panel are in different TIA Portal projects.

– The HMI panel is not configured with TIA Portal

– The PLC is connected to WinCC SCADA V7.

• The steps required to replace an older CPU with a new one can be performed using one of 
the following methods:

– Download the TIA Portal project directly to the new PLC.

– Go online to the new CPU to set the password and use the SIMATIC Memory Card of 
the old CPU.

– Use an additional SIMATIC Memory Card and a special JOB file.
It is then possible to use the SIMATIC Memory Card of the old CPU in the new CPU.

• Update of the firmware version of the PLC and the HMI panel.

---

All rights reserved
2021
Siemens AG 
©

### 1.3 Components Used

This application example has been created with the following hardware and software 
components:

Table 1-1

| Component | Number | Article number | Note |
| --- | --- | --- | --- |
| CPU 1515-2 PN from firmware V2.9 | 1 | 6ES7 515-2AM01-0AB0 | A different S7-1500/S7-1200 PLC from the list mentioned in chapter 7.1 can also be used as an alternative. |
| SIMATIC TP1200 Comfort Panel from firmware V17.0.0.0 | 1 | 6AV2 124-0MC01-0AX0 | A different HMI panel from the list mentioned in chapter 7.1 can also be used as an alternative. |
| Power supply PM1207 | 1 | 6EP1332-1SH71 | Alternatively, a different power supply can be used. |
| TIA Portal V17 | 1 | 6ES7822-0AA07-0YA5 | TIA Portal V17 |

You can purchase these components from the <u>Siemens Industry Mall</u>.

##### NOTE

This application example can also be used as a basis for securely connecting other types of 
SIMATIC controllers and HMI panels. See chapter 7.1 for a complete list of devices that 
support the secure PG/PC and HMI communication feature.

This application example consists of the following components:

Table 1-2

| Component | File name | Note |
| --- | --- | --- |
| Project | "109798583_PLC_HMI_Security_PROJ_V17.zip" | This zipped file contains the TIA Portal V17 project. |
| Documentation | <u>"109798583_PLC_HMI_Security_DOCU_V10_en.pdf"</u> | This document. |
|  |  |  |

---

All rights reserved
2021
Siemens AG 
©

**2**

## New Security Features for PLC and HMI panel in TIA Portal V17

This chapter provides an overview of the latest security features that were introduced in TIA 
Portal V17.

###### 2.1 "Security-By-Default" Concept

With TIA Portal Version 17, several options have been preconfigured and are set by default to 
ensure a higher security level for machines and plants.

This includes:

• The preactivated PLC access protection, that prevents any type of access to the controller 
unless the client is verified with the correct password.

| General | IO tags | System constants | Texts |
| --- | --- | --- | --- |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | Access level |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | More information about 'PLC access level' |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | Select the access level for the PLC. |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | Access level | Access |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | HMI | Read |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | ✓ | ✓ |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | ✓ | ✓ |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | ✗ | ✓ |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | ✗ | ✗ |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | No access (complete protection) |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | No access (complete protection): |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | TIA Portal users and HMI applications will not have access to any functions. |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | Mandatory password: |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | For full access, TIA Portal users need to enter the "full access" password. |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | Optional password: |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | A "read access" password can be defined for read access to all functions. |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | For access by HMI applications, an "HMI access" password can be defined. |  |  |
| Port [X1 P1 R] Port [X1 P2 R] Web server access PROFINET interface [X2] Startup Cycle Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event OPC UA System power supply Advanced configuration Connection resources | Some HMI devices do not support all possible characters. If you want to access the PLC from an HMI device, use only the standard characters. Please refer to the documentation of the device. |  |  |

• The predefined secure PG/PC and HMI communication, which prevents legacy PG/PC 
communication with other partners. See chapter 2.2.

| General | IO tags | System constants | Texts |
| --- | --- | --- | --- |
| Communication load System and clock memory SIMATIC Memory Card System diagnostics PLC alarms Web server Display Multilingual support Time of day Protection &amp; Security Protection of the PLC configuration data Access level Connection mechanisms Certificate manager Security event |  | Connection mechanisms |  |

---

All rights reserved
2021
Siemens AG 
©

• The pre-activated requirement to setup the password to protect confidential PLC 
configuration data, that ensures all confidential PLC configuration data are protected by 
default. See chapter <u>2.3</u>.

PLC_1 [CPU 1515-2 PN]
Properties

General IO tags System constants Texts

PROFINET interface [X2]
Startup
Cycle
Communication load
System and clock memory
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC configuration data
Access level
Connection mechanisms
Certificate manager
Security event
OPC UA

Protection & Security
Security settings wizard
Start security wizard

Protection of the PLC configuration data
Protection of confidential PLC configuration data
Confidential PLC configuration data (e.g. OPC UA certificates) should be encrypted using a password. Because the project is not protected, the
password is not saved in the project and must be entered during download. Save the password in a secure location, for example, in a Password ...
More information about 'Protection of confidential PLC configuration data'
Protect confidential PLC configuration data
Password: Press "Setup" to set the password
Reset Setup Change

###### 2.2 Secure PG/PC and HMI Communication

One characteristic of PG communication and HMI communication above all is their simplicity.
Establishing a TIA Portal online connection from a programming device to a PLC, for example, 
to load a program, requires little effort. This online connection also meets certain criteria such as 
confidentiality and integrity - based on a proven SIMATIC communication standard.

However, integrating machines and systems into an open IT environment requires that the 
communication between the programming device or HMI panel and the PLC to be secured in 
the sense of maintaining integrity and confidentiality for sensitive data. It also requires that this 
security meets generally accepted standards and is thus ready for the challenges of the future.

As of TIA Portal V17, PG/PC and HMI communication has been improved. Here, the Transport 
Layer Security protocol (TLS) is used to secure PG/PC and HMI communication using 
standardized security mechanisms.

###### 2.2.1 Transport Layer Security - TLS

TLS is designed to provide communication security over a computer network. This security is 
realized by the following elements:

– Confidentiality: the data is encrypted or unreadable to unauthorized eavesdroppers.

– Integrity: the message leaving the sender arrives to the recipient is unchanged. In other 
words, the message has not been manipulated in transit.

– Endpoint authentication: the communication partner as the end point is exactly the 
person he pretends to be. The identity of the partner is verified.

TLS uses digital certificate to encrypt and authenticate the partners and the data. In TIA Portal 
V17, the user has the option of using induvial, user-specific certificate for the communication 
partners which provides and extra layer of security to the system. If one device is compromised, 
other devices remain safe as they use different certificates. The certificates can be imported or 
created in TIA portal with the certificate manager. For more information on using certificates in 
TIA Portal, refer to the following link: \<u>3</u>\.

---

All rights reserved
2021
Siemens AG 
©

###### 2.2.2 Secure Communication Mechanism

The basis of secure PG/PC and HMI communication is that the PG and HMI panel can verify the 
authenticity of the PLC using the PLC communication certificate that the PLC sends when 
establishing communication and considers this PLC to be "trustworthy". Secure PG/HMI 
communication is only possible when the PG and HMI panel trusts the PLC. 
During connection setup, the PLC transfers the PLC communication certificate to the 
communication partner (PG or HMI panel).

To ensure that communication between the PLC and a programming device or HMI is secured, 
the PLC must first have a certificate. However, this certificate is only issued when the project is 
loaded into the PLC. In the following, the secure communication between PG or HMI panel and 
the PLC is explained.

##### Provisioning Phase

The following figure explains the process of the initial connection establishment from the PG or 
HMI panel to the PLC also known as "provisioning phases".

Figure 2-1

**1. Connection** establishment request

**Manual confirmation (trust self-signed certificate) since no automatic authenticity check is possible**

**Project data**

**PLC is not configured**
PLC

**Generating self-signed certificate sent in response to connection establishment**

**Implicitly configured PLC certificate for PG/PC and HMI communication is loaded**

---

All rights reserved
2021
Siemens AG 
©

The first connection establishment for initial loading to the PLC is secured by the TLS procedure 
in terms of Secure PG/P and HMI communication. The PG sends a connection establishment 
request to the PLC.

The PLC uses its manufacturer device certificate (if available) or a self-signed certificate to 
establish this connection. The PLC can only be used to a limited extent in this phase. In this 
point, the PLC waits for the provision of the password-based key information, i. e. the PLC is 
expecting the password for PLC confidential configuration data (see chapter 2.3). This phase is 
also called the provisioning phase. A message in the diagnostic buffer indicates that the PLC is 
in the provisioning phase.

The PLC sends its certificates to the PG which must be manually trusted by the user for the 
initial download process to continue. This needs to be done only once during the initial 
download.

When a project is loaded into the PLC, the PLC receives the project data:

• Hardware configuration including configured certificates for secure communication (OPC 
UA, HTTPS, Secure OUC, Secure PG/P and HMI communication)

• User program

##### Ending of Provisioning Phase

TIA Portal does not store the password for confidential PLC configuration data itself or the key 
information generated from the password in the project.

Therefore, the password is requested in a dialog when loading the project for the first time or 
when loading a new project and transferred to the PLC as key information. Only after this step 
the PLC can use the protected PLC configuration data. This completes the provisioning phase, 
and the CPU can start operating.

##### NOTE

If you do not protect the confidential PLC configuration data with a password, there is no 
need to enter the password when loading the CPU for the first time. This has no influence on 
the flow of the PG/PC and HMI communication. If the confidential PLC configuration data 
password is not configured, the PLC will leave the provisioning phase and continue to be 
operational. In this case the confidential PLC configuration data (e. g. private keys) are not 
protected against unauthorized access (see chapter 2.3).

---

All rights reserved
2021
Siemens AG 
©

##### Startup of PG/PC and HMI communication

When the PLC is loaded and has received the PLC certificate for secure PG/PC and HMI 
communication, the programming device connects again. This time based on the loaded 
certificate.

Figure 2-2

**2. Connection establishment request**

**Certificate is automatically accepted because it is known from configuration**

Data exchange (secure)

**PLC is configured**

PLC

**Loaded PLC certificate for PG/PC and HMI communication is sent in response**

**Data exchange (secure)**

---

All rights reserved
2021
Siemens AG 
©

###### 2.3 Protection of the PLC Confidential Configuration Data

The proper functioning of certificate-based communication mechanisms that are used for 
secured communication such as Secure PG/PC and HMI communication, Secure Open User 
Communication, HTTPS, Secure SMTP over TLS or OPC UA), requires that the private keys 
used by these certificates are protected as best as possible. As of TIA Portal V17, you can set a 
user-defined password to protect these keys and other data worth protecting: The password to 
protect PLC confidential configuration data.

To protect the PLC confidential configuration data, the user has the option to enter a password 
in TIA portal. Typical configuration data considered as confidential are certificates and private 
keys.

The following figure shows in a simplified way how confidential PLC configuration data, for 
example of a standard S7-1500 PLC, is protected: The project and key information are placed in 
different memory areas when loaded for the first time.

• The project is placed in the load memory (SIMATIC Memory Card).

• The key information is placed in a memory area in the PLC. This key is used to read the 
confidential configuration data on the SIMATIC Memory Card.

For other target systems, such as S7-1200 PLCs and Software Controller), with other memory 
concepts, the implementation is adapted to the corresponding memory concepts. However, the 
principle is the same.

Figure 2-3

$$
\textcircled{1}
$$
$$
\textcircled{2}
$$

1. Project with password-protected confidential configuration data (here: in load
memory = SIMATIC Memory Card).

2. Key information (generated from password) to use the protected confidential configuration 
data (here: in the memory area in the PLC).

---

All rights reserved
2021
Siemens AG 
©

##### Two Memory Areas for More Security

The project and the key are related to each other like two matching puzzle pieces: The project is 
bound to the loaded key information and the loaded key information is bound to the password 
that was assigned during configuration. Project and key information must match, otherwise the 
PLC will not start.

The principle of two separate memory areas also applies to the S7-1200 PLCs and S7-1500 
PLC versions without a SIMATIC Memory Card, e. g. for the Software Controller, PLCSIM and
PLCSIM Advanced. In the versions without SIMATIC Memory Card, two separate partitions are 
used so that the two items of information can be managed independently of one another.

###### Figure 2-4

&

---

All rights reserved
2021
Siemens AG 
©

###### 2.4 Security Wizard

For the security configuration in TIA Portal Version 17, the user is guided through a security
wizard which assists them with the security settings. This includes, the protection of confidential 
configuration data, the access level of the SIMATIC controller, and the secure PG/PC and HMI 
communication.

Figure 2-5

PLC security settings

Protection of confidential PLC data
Select whether your confidential PLC configuration data is to be protected.

Protection of confidential PLC configuration data activated:
- Confidential PLC configuration data (e.g. OPC UA certificates) are protected in the TIA Portal project and during runtime.
- If the PLC must be replaced with a replacement PLC, the password for the protection of confidential PLC configuration data must also be assigned for the new PLC.

Protection of confidential PLC configuration data deactivated:
- Confidential PLC configuration data (e.g. OPC UA certificates) are not protected in the TIA Portal project and during runtime.
- No special password treatment when replacing the substitute PLC

Protects the PLC configuration data from the TIA Portal project and the PLC.

Password: Press "Setup" to set the password
Reset Setup Change

Password TIA project PLC Configuration data

Do not show this dialog again

<<Back Next>> Finish Cancel

##### NOTE

The security wizard opens automatically when a new PLC is added to the TIA Portal project.

Alternatively, it is possible to start the security wizard manually in the properties menu of the 
PLC. In the properties menu of the PLC, navigate to "Protection & Security" and click the 
"Start security wizard" button to start the security wizard.

---

All rights reserved
2021
Siemens AG 
©

Figure 2-6

PLC_1 [CPU 1515-2 PN]
Properties

General IO tags System constants Texts

PROFINET interface [X2]
Startup
Cycle
Communication load
System and clock memory
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC configuration data
Access level
Connection mechanisms
Certificate manager
Security event
OPC UA

Protection & Security
Security settings wizard
Start security wizard

Protection of the PLC configuration data
Protection of confidential PLC configuration data
Confidential PLC configuration data (e.g. OPC UA certificates) should be encrypted using a password. Because the project is not protected, the
password is not saved in the project and must be entered during download. Save the password in a secure location, for example, in a Password ...
More information about 'Protection of confidential PLC configuration data'
Protect confidential PLC configuration data
Password: Press "Setup" to set the password
Reset Setup Change

###### 2.5 Communication Compatibility

Secure PG/PC and HMI communication is activated by default for PLCs configured with 
TIA Portal V17. This mode is called "Secure Mode".

However, to communicate with devices that are configured with previous versions of TIA Portal, 
communication compatibility has been introduced.

You have the option of connecting an S7-1500 PLC V2.9 or S7-1200 PLC V4.5 to a current 
programming device with TIA Portal V17 or higher and additionally, for example, to an HMI 
panel with a runtime from the previous version.

The devices automatically adjust their connection mechanisms accordingly. To be able to better 
differentiate between the two connection mechanisms, we call the procedure of connecting to 
previous versions "Legacy Mode" which is based on a variant of S7 communication.

There are two modes of operation for the SIMATIC controllers in TIA Portal V17:

• Only via secure TLS-based PG/PC and HMI communication ("Secure Mode").

• Both via secure PG/PC and HMI communication and via the previously used PG/PC and 
HMI communication ("Secure Mode" and "Legacy Mode"). This mode is also called "Mixed 
Mode".

Taken above into consideration, we can summarize how the communication compatibility 
behaves in different scenarios:

• PG/HMI panel and PLC are configured in TIA Portal V17 or higher version: The secure TLSbased PG/PC and HMI communication ("Secure Mode").

• PG/HMI panel is configured in a previous version (TIA Portal< V17): "Legacy Mode" is used 
given that you have deactivated the option "Only allow secure PG/PC and HMI 
communication" in the PLC properties.

• PLC is configured in TIA Portal V17 or higher version and several PGs and HMI panels are 
connected which are configured in TIA Portal V17 or higher as in previous versions
(TIA Portal < V17): "Mixed Mode" is used given that you have deactivated the option "Only 
allow secure PG/PC and HMI communication" in the PLC properties.

##### NOTE

By default, only secure TLS-based PG/PC and HMI communication is allowed in TIA Portal 
V17. However, this option can be deactivated if needed, in cases such as when the 
performance is affected, due to higher security standard.

---

All rights reserved
2021
Siemens AG 
©

## 3 Engineering

##### NOTE

The engineering of the S7-1500 PLC and the HMI panel are completely implemented in the 
project.

This section shows how to create a project with a S7-1500 PLC and an HMI panel.

### 3.1 Project Preparation

1. Create a TIA Portal project.

2. In the "Project tree" menu, navigate to "Security settings > Settings".

3. Click the "Protect this project" button to define credentials for the project administrator.
The "Protect project" dialog opens.

Siemens - D:\Projects\Security\Security
Project Edit View Insert Online Options Tools Window Help
Save project Go online Go offline Search in project

Start
Project tree
Devices Plant objects
Seurity Add new device Devices & networks PLC_1 [CPU 1515-2 PN] Ungrouped devices Security settings Settings Users and Cross-device

Security Security settings Settings

Project protection Password policies

Project protection Your project will be protected as soon as you specify a project administrator. Then you must log on to the project. The project protection cannot be removed again.
This setting cannot be undone.
Protect this project

4. Enter the username and password for the project administrator.
The login information for the project administrator can be found in Table 3-1.

5. Click the "OK" button to assign the username and password to the project administrator.

Protect project

Define credentials for the project administrator

User name: administrator
Password: **********
Confirm password: **********
Comment:

5 OK Cancel

---

All rights reserved
2021
Siemens AG 
©

A list of the used passwords can be found in the following table:

Table 3-1

| Description | Password | Note |
| --- | --- | --- |
| TIA Portal project administrator | Siemens1! | Username: administrator |
| Password for protection of confidential PLC configuration data | Siemens00# | - |
| Password for full access | Siemens11# | - |
| Password for read access | Siemens22# | - |
| Password for HMI access | Siemens33# | - |

---

All rights reserved
2021
Siemens AG 
©

### 3.2 SIMATIC Controller Configuration

#### 3.2.1 PLC security settings

1. Add a new S7-1500 PLC, e. g. CPU 1515-2 PN V2.9.

The security wizard opens automatically to support the configuration.

##### NOTE

Other models of SIMATIC controllers can be used as well. See chapter 7.1 for a complete 
list of devices that support the secure PG/PC and HMI communication feature.

2. In the first window, the checkbox "Protects the PLC configuration data from the TIA Portal 
project and the PLC" is activated by default according to the Security-By-Default concept. 
Click the "Setup" button to configure a password to protect the confidential configuration 
data of the PLC.

PLC security settings

Protection of confidential PLC data
Select whether your confidential PLC configuration data is to be protected.

Protection of confidential PLC configuration data activated:
- Confidential PLC configuration data (e.g. OPC UA certificates) are protected in the TIA Portal project and during runtime.
- If the PLC must be replaced with a replacement PLC, the password for the protection of confidential PLC configuration data must also be assigned for the new PLC.

Protection of confidential PLC configuration data deactivated:
- Confidential PLC configuration data (e.g. OPC UA certificates) are not protected in the TIA Portal project and during runtime.
- No special password treatment when replacing the substitute PLC

Protects the PLC configuration data from the TIA Portal project and the PLC.

Password: Press "Setup" to set the password
Reset Setup Change

Password TIA project
PLC Configuration data

Do not show this dialog again

<<Back Next>> Finish Cancel

3. Enter the password and the password confirmation according to Table 3-1.

4. Click the "Ok" button.

---

All rights reserved
2021
Siemens AG 
©

Set password

Define password for protection of confidential PLC configuration data:

New password: **********

Confirm password: **********

OK Cancel

##### 5. Click the "Next" button to go to the next page.

PLC security settings

Protection of confidential PLC data
Select whether your confidential PLC configuration data is to be protected.

Protection of confidential PLC configuration data activated:
- Confidential PLC configuration data (e.g. OPC UA certificates) are protected in the TIA Portal project and during runtime.
- If the PLC must be replaced with a replacement PLC, the password for the protection of confidential PLC configuration data must also be assigned for the new PLC.

Protection of confidential PLC configuration data deactivated:
- Confidential PLC configuration data (e.g. OPC UA certificates) are not protected in the TIA Portal project and during runtime.
- No special password treatment when replacing the substitute PLC

Protects the PLC configuration data from the TIA Portal project and the PLC.

Password: *******
Reset Setup Change

Password TIA project

PLC Configuration data

Do not show this dialog again

Back Next Finish Cancel

---

All rights reserved
2021
Siemens AG 
©

6. In the second window, the PG/PC and HMI communication mode can be configured. The 
option "Only allow secure PG/PC and HMI communication" is activated by default according 
to the Security-By-Default concept. Click the "Next" button to go to the next page.

PLC security settings

Mode for PG/PC and HMI communication
Select how the PLC may communicate with PG/PC and HMIs.

Only secure PG/PC communication allowed:
- Communication to PG/PC and HMI with higher security standard
- Communication to TIA Portal < V17 and HMI systems < V17 is not possible
- Communication performance might be reduced

Legacy and secure PG/PC communication permitted:
- Communication security to PG/PC and HMI according to the options of both communication partners
- Communication to TiA Portal < V17 and HMI systems < V17 is possible. Higher TiA Portal versions require a separate activation of the Legacy communication.
- Communication performance might be higher when only legacy PG/PC communication is used
Only allow secure PG/PC and HMI communication

PG/PC and HMI
Secure
Communication
PLC

Do not show this dialog again

<<Back Next>> Finish Cancel

7. In the third window, the access level to the PLC can be configured. By default, no access to 
the PLC is allowed according to the Security-By-Default concept. Later, you will configure 
the PLC access password on the HMI panel to allow the communication between the two
devices. Keep the default "No access (complete protection)" option.

8. Enter the full access password in the password field according to <u>Table 3-1</u> and confirm this 
password.

9. Click the following symbol [Image: Image315].

10. Click the "Next" button to go to the last page of the security wizard.

---

All rights reserved
2021
Siemens AG 
©

PLC security settings

Protection of confidential PLC data
Mode for PG/PC and HMI communication
PLC access protection
Overview

PLC access protection
Select how the PLC is to be accessed without password protection.

Full access (no protection):
All users have full access to operations that modify standard project data. No password is required.

Read access:
Users have read-only access to PLC configuration data (e.g. hardware configuration, blocks).
Users can also change the PLC operating state (RUNISTOP) and system time.

HMI access:
Only HMI access and access to diagnostics data is possible. This includes read and write access to tags.

No access (complete protection):
TIA Portal users and HMI applications will not have access to any functions.

Access level without password: No access (complete protection)
Password for full access:

Enter password: **************
Confirm password: **************

Do not show this dialog again

Back Next> Finish Cancel

---

All rights reserved
2021
Siemens AG 
©

11. In the last window of the security wizard, an overview of the previously configured security 
settings can be found. Click the "Finish" button to end the security wizard.

PLC security settings

Protection of confidential PLC data
Mode for PG/PC and HMI communication
PLC access protection
Overview

Overview
Overview of the selected PLC security settings.

Protection of confidential PLC data: Enabled

Mode for PG/PC and HMI communication: Only secure PG/PC communication allowed

PLC access protection: No access (complete protection)

Do not show this dialog again

<<Back Next>> Finish Cancel

#### 3.2.2 Set the IP address

1. In the PLC properties menu, navigate to "PROFINET interface [X1] > Ethernet addresses".

2. Set the IP address and the subnet mask of the PLC. The IP address of the PLC can be 
found in Table 4-1.

PLC_1 [CPU 1515-2 PN]

General IO tags System constants Texts

General
PROFINET interface [X1]
General
Ethernet addresses
Operating mode
Advanced options
Interface options
Media redundancy
Real time settings
Port [X1 P1 R]
Port [X1 P2 R]
Web server access
PROFINET interface [X2]
Startup

Ethernet addresses

Interface networked with
Subnet: PN/IE_1
Add new subnet

Internet protocol version 4 (IPv4)

Set IP address in the project
IP address: 192 . 168 . 20 . 120
Subnet mask: 255 . 255 . 255 . 0
Use router

---

All rights reserved
2021
Siemens AG 
©

#### 3.2.3 Activate global security settings for certificate manager

1. In the PLC properties menu, navigate to "Protection & Security > Certificate manager".

2. Activate the checkbox "Use global security settings for certificate manager".
In TIA Portal, there are two methods for managing the PLC certificate:

– locally device-specific in the PLC properties menu

– globally for the whole TIA Portal project in the in the project tree under "Security settings 
> Settings".

The "Use global security settings for certificate manager" option activates the use of the 
global certificate manager to assign global certificate to the PLC.

PLC_1 [CPU 1515-2 PN]
Properties

General IO tags System constants Texts

Communication load
System and clock memory
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC confi...
Access level
Connection mechanisms
Certificate manager
Security event
OPC UA

Certificate manager ___

Global security settings
The global security settings for the certificate manager are not enabled.
Only limited functionality is available.
Use global security settings for certificate manager

Device certificates

ID Common name of subject Issuer Valid until
4 PLC-1/Communication-4 O=Siemens, C=DE, CN=Si.. 6/15/2037
<Add new>

3. When activating the global certificate manager, the current certificate configuration will be 
lost. Therefore, a warning message about the loss of current keys and certificate in the local 
certificate manager appears. Click the "OK" button.

Certificate manager

The global security certificate manager is enabled, your current certificate configuration will be lost:

- The private keys cannot be restored.
- The certificates can be exported and used in the global security certificate manager.
- You must update your configuration or your program, since your IDs may have changed.

OK Cancel

4. The certificate used for the secure PG/PC and HMI communication needs to be created as 
the certificate created locally for the PLC was deleted after performing the previous step.
Navigate to "Protection & Security > Connection mechanism" in the PLC properties menu.

5. Click the following button [Image: Image348] to open the certificate creation menu.

---

All rights reserved
2021
Siemens AG 
©

PLC_1 [CPU 1515-2 PN]
Properties Info

General IO tags System constants Texts

Startup
Cycle
Communication load
System and clock memory
SIMATIC Memory Card
▶ System diagnostics
PLC alarms
▶ Web server
▶ Display
Multilingual support
Time of day
▶ Protection & Security
Protection of the PLC configuration data
Access level
▶ Connection mechanisms
Connection mechanisms
Certificate manager

Connection mechanisms ___
Permit access with PUT/GET communication from remote partner

Connection mechanisms ___

Communication mode to TIA Portal and HMI
More information about 'secure PG/PC and HMI Communication'
Only allow secure PG/PC and HMI communication
PLC communication certificate: ___

##### 6. Click the "Add new" button.

The "Create certificate" dialog opens.

| ID | Common name of subject | Issuer | Valid until |
| --- | --- | --- | --- |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
| < | Ⅷ |  | > |

7. Configure the certificate parameters. Select "EC" as encryption method.

8. Check the certificate validity and adjust it if needed.

9. Select "TLS Server" for the "Usage" option.

10. Click the "OK" button to end the certificate creation process.

---

All rights reserved
2021
Siemens AG 
©

Create certificate

Certificate authority (CA)
Select how the new certificate will be signed:
○ Selfsigned
● Signed by certification authority

CA name: Siemens TIA Project-Security-(EC)-3 Select

Certificate parameter
Enter the parameters for the new certificate:

Common name of subject: PLC-1/Communication-4
Encryption method: EC
Encryption parameter: prime256v1
Hash algorithm: sha256
Valid from: October 05, 2021 02 : 09 : 47 PM
Valid until: October 05, 2037 02 : 00 : 00 AM
Usage: TLS Server

Subject Alternative Name (SAN): Type Value
IP 192.168.0.1
IP 192.168.1.1
<Add new>

OK Cancel

---

All rights reserved
2021
Siemens AG 
©

### 3.3 HMI panel Configuration

##### NOTE

The following section explains the relevant steps to configure the HMI panel to securely 
communicate with the S7-1500 CPU. All further steps for configuring the HMI panel are not 
described here as they are irrelevant for the purpose of this application example. Further 
information on the configuration of the HMI panel can be found at the following link \<u>4</u>\.

##### NOTE

Other models of HMI panels can be used as well. See chapter <u>7.1</u> for a complete list of 
devices that support the secure PG/PC and HMI communication feature.

The secure HMI communication works in a similar way as described in chapter <u>2.2.2</u>. However, 
there are two scenarios to be considered:

• When the PLC communication certificate is already available on the HMI panel with the 
"trustworthy" status, a secure HMI communication is automatically set up between the PLC
and the HMI panel. This applies to the following cases:

– PLC and HMI panel are configured with the same TIA Portal project

– PLC and HMI panel are configured in two different projects, but a device proxy is used. 
This is explained in chapter <u>3.3.1</u> and chapter <u>3.3.2</u> respectively.

• When the PLC communication certificate is not available in the "trustworthy" status on the 
HMI panel, you will see a message in the alarm view of the HMI panel informing you that 
the PLC is not trusted along with an error code. In this case, you must label the PLC 
communication certificate on the HMI panel as "trustworthy". This is explained in
chapter <u>3.3.3</u>.

To configure an HMI panel to communicate with the PLC, we distinguish between three different 
scenarios:

1. The PLC and the HMI panel are in the same TIA Portal project.

2. The PLC and the HMI panel are in different TIA Portal projects.

3. The HMI panel is not configured with TIA Portal (connection with WinCC SCADA V7 
systems).

The configuration for each of these cases are shown in this section.

---

All rights reserved
2021
Siemens AG 
©

#### 3.3.1 PLC and HMI Panel are in the same TIA Portal Project

Figure 3-1

**TIA Portal Project 1**

**TIA Portal**

1. Add a new HMI panel to the project, e. g. SIMATIC TP1200 Comfort Panel, V17.0.

2. In the HMI panel properties menu, navigate to "PROFINET Interface [X1] > Ethernet 
addresses".

3. Set the IP address and the subnet mask of the HMI panel. The IP address must be in the 
same subnet as the IP address of the PLC.

HMI_1.1 [PROFINET Interface]

General IO tags System constants Texts

General
PROFINET Interface [X1]
General
Ethernet addresses
Operating mode
Advanced options

Ethernet addresses

Interface networked with

Subnet: PN/IE_1

Add new subnet

Internet protocol version 4 (IPv4)

Set IP address in the project

IP address: 192 . 168 . 20 . 121

Subnet mask: 255 . 255 . 255 . 0

Use router

---

All rights reserved
2021
Siemens AG 
©

4. In the "Network View" in the project, click on "Connections".

5. Select "HMI connection" from the dropdown menu.

PLC_HMI_Security > Devices & networks

Network Connections HMI connection

4
5

PLC_1
CPU 1515-2 PN

HMI_1
TP1200 Comfort

PN/IE_1

6. Drag and drop a connection from the HMI panel to the PLC to establish an HMI connection 
between both devices.

PLC_HMI_Security ▶ Devices & networks

Network Connections HMI connection NAME

PLC_1
CPU 1515-2 PN

HMI_1
TP1200 Comfort

PN/IE_1

7. Double-click "Connections" in the project tree in the device folder of the HMI panel.

8. Enter the password for the HMI access of the S7-1500 CPU, as specified in <u>Table 3-1</u>.

---

All rights reserved
2021
Siemens AG 
©

Project tree

Devices Plant objects

PLC_HMI_Security
Add new device
Devices & networks
PLC_1 [CPU 1515-2 PN]
HMI_1 [TP1200 Comfort]
Device configuration
Online & diagnostics
Runtime settings
Screens
Screen management
HMI tags
Connections
HMI alarms
Recipes
Historical data
Scripts
Scheduled tasks
Cycles
Reports
Text and graphic lists
User administration

PLC_HMI_Security HMI_1 [TP1200 Comfort] Connections

Connections to S7 PLCs in Devices & networks

Connections
Name Communication driver HMI time synchro... Station Partner Node
HMI_Connection_1 SIMATIC S7 1500 None S7-1500/ET200MP ... PLC_1 CPU 15...
<Add new>

Parameter Area pointer

TP1200 Comfort
Interface:
ETHERNET

Station

HMI device
Address: 192.168.20.121
Access point: S7ONLINE

PLC
Address: 192.168.20.120
Access password: *****

A1

The secure connection between the PLC and the HMI panel has now been configured.

#### 3.3.2 PLC and HMI Panel are in two different TIA Portal Projects

Figure 3-2

**TIA Portal Project 1**
**Proxy**
**TIA Portal Project 2**
**TIA Portal**
TIA Portal

If the PLC and the HMI panel are in two different projects, a device proxy must be exported from 
the PLC project and imported in the HMI panel project. This proxy device contains the PLC 
configuration as well as the certificate needed for the secure connection. It can also include the
program blocks, tags and other options of the PLC.

---

All rights reserved
2021
Siemens AG 
©

1. In the PLC project, in the project tree, navigate to "Device proxy data" and double-click on 
"Add new device proxy data". A new device proxy is created.

Project tree

Devices Plant objects

PLC_HMI_Security
Add new device
Devices & networks

PLC_1 [CPU 1515-2 PN]
Device configuration
Online & diagnostics
Software units
Program blocks
Technology objects
External source files
PLC tags
PLC data types
Watch and force tables
Online backups
Traces
OPC UA communication
Web applications
Device proxy data
Add new device proxy data

2. Double-click on the newly created device proxy to open the configuration menu.

3. In the configuration menu, enter a name to the device proxy.

4. Check the "Program blocks" checkbox to export the PLC program blocks with the device
proxy. If other PLC parameters are needed in the device proxy, they can be chosen here as 
well.

5. Click the "Export device proxy data" button to save the device proxy file on your PG.

---

All rights reserved
2021
Siemens AG 
©

PLC_HMI_Security ▶ PLC_1 [CPU 1515-2 PN] ▶ Device proxy data ▶ PLC_1_Proxy

General
Name: PLC_1_Proxy
Comment:
Export device proxy data

Definition of the content
Software units
Program blocks
SimulatedDriveData [DB2]
Simulation
InstSimulatedDrive [DB4]
Technology objects
PLC tags
PLC supervisions & alarms

6. In the HMI panel project, the device proxy file must be imported. Navigate to the "Hardware 
catalog" and add a device proxy to the project.

Siemens - D:\Projects\109798583_PLC_HMI_Security_PROJ_V17\Project1\Project1

Project Edit View Insert Online Options Tools Window Help
Save project Go online Go offline Search in project

Project tree
Devices Plant objects

Project1 Devices & networks
Topology view Network view Device view
Network Connections HMI connection

Options

Catalog
Search>
Filter Profile:
All>
Controllers
SIMATIC S7-1200
SIMATIC S7-1500
SIMATIC Drive Controller
SIMATIC S7-300
SIMATIC S7-400
SIMATIC ET200 CPU
Device proxy
6ES7 XXX XXXX XXXX
HMI
PC systems
Drives & starters
Network components

Devices & networks
Project1
Add new device
Devices & networks
HMI_1 [TP1200 Comfort]
Ungrouped devices
Security settings
Cross-device functions
Common data
Documentation settings
Languages & resources
Version control interface
Online access
Card Reader/USB memory
HMI_1
TP1200 Comfort

---

All rights reserved
2021
Siemens AG 
©

7. Right-click on the device proxy.
The context menu opens.

8. Choose "Initialize device proxy" menu.

Project1 ▶ Devices & networks
Topology view
Network Connections HMI connection
HMI_1
TP1200 Comfort
PLC_proxy_1
Device proxy
Change device
Initialize device proxy
Update data of the device
Cut
Copy Ctrl+C
Paste Ctrl+V
Delete Del
Rename F2
Show catalog Ctrl+Shift+C

9. Choose the device proxy file saved in step 5.

Open device proxy data source

This PC > DATA (D:) > Search DATA (D:)

Organize New folder

Siemens
Temp
Users
.NET v4.5
.NET v4.5 Classic
Admin
3D Objects
AppData
Contacts
Desktop
Documents
Automation

Name Date modified
GDSOld 13.09.2021 13:47
KMT Macro V6.8.4 23.09.2021 13:51
openssl-1.0.2u-x64_86-win64 08.09.2021 09:34
openssl-1.1.1k.tar 08.09.2021 09:34
Projects 05.10.2021 14:02
Reports 27.09.2021 10:48
ShareX-portable 08.07.2021 10:49
SINEMA_RC_Client_V3_0 14.07.2021 18:59
STEP7_PROF_Safety_WinCC_Adv_Unified_... 27.09.2021 10:08
uaexpert-bin-win32-x86-vs2008sp1-v1.5.... 14.07.2021 19:44
UnifiedArchive 27.09.2021 10:48
Device proxy data PLC.ipe 06.10.2021 11:22

File name: Device proxy data PLC.ipe supported files (*.ap17;*.ipe;

Open Cancel

---

All rights reserved
2021
Siemens AG 
©

10. The PLC information from the PLC TIA Project can be seen here. Click the "OK" button.

Initialize device proxy - PLC_proxy_1

Initialize device proxy from file:

Defined device proxy data:
Device proxy data PLC

Source device:
PLC_1 [CPU 1513-1 PN]

Device proxy data content:
Program blocks

Comment:

OK
Cancel

##### NOTE

The PLC project certificates used to secure the connection are included in the device proxy 
file and will be automatically available to the HMI panel when this file is imported into the 
HMI project.

11. Repeat the steps 3 to 8 from chapter 3.3.1 to establish an HMI connection between the HMI 
panel and the device proxy.

##### NOTE

Depending on your project, other HMI configuration regarding access to the device proxy 
tags might be needed. This configuration is behind the scope of this application example.

---

All rights reserved
2021
Siemens AG 
©

#### 3.3.3 HMI Panel is not configured in TIA Portal - Connection with WinCC SCADA V7 Systems

##### NOTE

The following instructions are valid starting from WinCC SCADA V7.5 SP2 Update 4.

Connection to WinCC SCADA V7 works in a similar way to the device proxy method shown 
above. A file must be exported from the PLC project and then imported in the WinCC SCADA 
project. To export the file from the PLC project, a specific export tool is needed. See this link for 
more information \5\.

See link \6\ for detailed explanation of how to securely connect with WinCC SCADA V7.5.

---

All rights reserved
2021
Siemens AG 
©

## 4 Installation and Commissioning

### 4.1 Hardware Setup

Chapter 1.3 lists the required hardware components.

| CAUTION | Observe the setup guidelines for the S7-1500 PLC and the SIMATIC TP1200 Comfort Panel. Read the corresponding device manual 7 and 8. |
| --- | --- |

| CAUTION | Only switch on the power supply after you have completed and checked the assembly! |
| --- | --- |

Figure 4-1

S7-1500 PLC
TP1200 Comfort Panel
**PROFINET IE**

The following table provides an overview of all IP addresses used in this example. Assignment 
of static IP addresses is assumed.

Table 4-1

| Component | IP address |
| --- | --- |
| CPU 1515-2 PN | 192.168.20.120 |
| SIMATIC TP1200 Comfort Panel | 192.168.20.121 |

The subnet mask in all network components is 255.255.255.0.

### 4.2 Installing Hardware and Software Components

To load the hardware and software components, proceed as follows:

1. Install the hardware and software components shown in Table 1-1 and Table 1-2 according 
to the description of the operating manuals of the respective components.

2. Connect the hardware components as shown in Figure 4-1.

3. Unzip the file “109798583_PLC_HMI_Security_PROJ_V17.zip”.

---

All rights reserved
2021
Siemens AG 
©

### 4.3 Load hardware components

#### 4.3.1 Load the S7-1500 PLC

##### Prerequisite

• You have assigned the S7-1500 CPU the IP address that you have set in the project
(see <u>Table 4</u>-1).

• The Engineering PC and the S7-1500 PLC are in the same IP subnet.

##### Guideline

Proceed as follows to load the configuration to the S7-1500 PLC:

1. Start TIA Portal V17.

2. Open the project "PLC_HMI_Security.ap17".

3. Connect the Ethernet cable of the Engineering PC with the S7-1500 PLC.

4. Right-click the device folder of the S7-1500 PLC in the project tree. The context menu
opens.

5. Select the "Download to device > Hardware and software (only changes)" menu.

Figure 4-2

Siemens - D:\Projects\109798583_PLC_HMI_Security_PROJ_V17\109798583_PLC_HMI_Security_PROJ_V17\PLC_HMI_Security

Project Edit View Insert Online Options Tools Window Help

Save project Go online Go offline

Project tree

Devices Plant objects

Start

PLC_HMI_Security
Add new device
Devices & networks
PLC_1 [CPU 1515-2 PN]
HML_1 [IP1200 Comfort]
Ungrouped devices
Security settings
Cross-device functions
Common data
Documentation settings
Languages & resources
Version control interface
Online access
Card Reader/USB memory

Change device
Open
Open in new editor
Open block/PLC data type... F7
Cut Ctrl+X
Copy Ctrl+C
Paste Ctrl+V
Delete Del
Rename F2
Go to topology view
Go to network view

Compile
Download to device
Backup from online device
Go online
Go offline
Online & diagnostics Ctrl+D
Receive alarms

Hardware and software (only changes)
Hardware configuration
Software (only changes)
Software (all)

6. In the "Extended download to device" dialog, configure the interface parameters.

7. Click the "Start search" button.
The S7-1500 PLC is shown in the "Select target device" list.

8. Select the S7-1500 PLC in the "Select target device" list".

9. Click the "Load" button.

---

All rights reserved
2021
Siemens AG 
©

Extended download to device

Configured access nodes of *PLC_1*
Device Device type Slot Interface type Address Subnet
PLC_1 CPU 1513-1 PN 1 X1 PN/IE 192.168.20.120 PN/IE_1

Type of the PG/PC interface: PN/IE
PG/PC interface: vmxnet3 Ethernet Adapter
Connection to interface/subnet: PN/IE_1
1st gateway:

Select target device:
Show devices with the same addresses
Device Device type Interface type Address Target device
CPUcommon CPU 1513-1 PN PN/IE 192.168.20.120 CPUcommon
PN/IE Access address
Start search

Online status information:
Display only error messages
Scan completed. 1 compatible devices of 1 accessible devices found.
Retrieving device information...
Scan and information retrieval completed.

Load Cancel

10. A warning message appears stating that the CPU certificate is not trustworthy. Consider the 
CPU certificate to be trustworthy to continue loading.

Establish connection to device

"PLC_1" might not be a trustworthy device.

The device uses an unknown, self-signed certificate.

If this device is the one you want, it is trusted and you can connect. If this device is not the one you want, you should abort the connection.

Display certificates
Consider as trusted and m...
Abort connection

##### NOTE

This message appears during the first download to the PLC to notify the user that the PLC 
self-signed certificate is not yet trusted by the PG. This is important as the basis of the 
secure PG/PC and HMI communication is the verification of the PLC certificate by the PG or 
HMI panel. Refer to chapter 2.2.2 for more information about this warning message.

---

All rights reserved
2021
Siemens AG 
©

11. Enter the confidential PLC configuration data password in the "Load preview" dialog. The 
password can be found in Table 3-1.

12. Click the "Load" button.

Check before loading

Status ! Target Message Action
PLC_1 Ready for loading. Load 'PLC_1'
Protection Protection from unauthorized access
Devices connected to an enterprise network or directly to the internet must be appropriately protected against unauthorized access, e.g. by use of firewalls and network segmentation. For more information about industrial security, please visit http://www.siemens.com/industrialsecurity
Software Download software to device Consistent download
Password Download password for confidential PLC configuration data
The PLC has no password for confidential PLC configuration data. Validate the password again.
Text libraries Download all alarm texts and text list texts to device Consistent download

Refresh
Finish Load Cancel

##### NOTE

Refer to chapter 2.3 for more information about the PLC confidential configuration data 
password.

13. Select the "Start module" action in the "Load results” dialog.

14. Click the "Finish" button.

| Status | ! | Target | Message | Action |  |
| --- | --- | --- | --- | --- | --- |
|  |  | PLC_1 | Downloading to device completed without error. | Load 'PLC_1' |  |
|  |  | Start modules | Start modules after downloading to device. | Start module |  |
|  |  |  |  | 13 |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |
| < | Ⅷ |  |  |  | > |

---

All rights reserved
2021
Siemens AG 
©

#### 4.3.2 Load the SIMATIC TP1200 Comfort Panel

##### Prerequisite

• You have assigned the SIMATIC TP1200 Comfort Panel the IP address that you have set in 
the project (see <u>Table 4</u>-1).

• The Engineering PC and the SIMATIC TP1200 Comfort Panel are in the same IP subnet.

##### Guideline

Proceed as follows to load the configuration to the SIMATIC TP1200 Comfort Panel:

1. Select the device folder of the SIMATIC TP1200 Comfort Panel in the project tree.

2. Click the "Download to device" button.

| Project | Edit | View | Insert | Online | Options | Tools | Window | Help |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  | Save project |  |  |  |  |  |  |  |
| Project tree |  |  |  |  |  |  |  |  |
| Start | Devices | Plant objects |  |  |  |  |  |  |
| Start | Security |  |  |  |  |  |  |  |
| Start | Add new device |  |  |  |  |  |  |  |
| Start | Devices &amp; networks |  |  |  |  |  |  |  |
| Start | PLC_1 [CPU 1513-1 PN] |  |  |  |  |  |  |  |
| Start | HMI_1 [TP1200 Comfort] |  |  |  |  |  |  |  |
| Start | Ungrouped devices |  |  |  |  |  |  |  |
| Start | Security settings |  |  |  |  |  |  |  |
| Start | Cross-device functions |  |  |  |  |  |  |  |

3. In the "Extended download to device" menu, configure the interface parameters.

4. Click the "Start search" button.
The HMI Panel is shown in the "Select target device" list.

5. Select the HMI Panel in the "Select target device" list".

6. Click the "Load" button.

---

All rights reserved
2021
Siemens AG 
©

Extended download to device

Configured access nodes of "HMI_1"
Device Device type Slot Interface type Address Subnet
HMI_1 TP1200 Comfort Ethernet 192.168.20.121
HMI_1.IE_CP_1 PROFINET Interface 5 X1 PN/IE 192.168.20.121 PN/IE_1
HMI_RT_1 S7USB Not configured
HMI_1.MPI/DP_CP_1 MPI/DP Interface 7 X2 MPI 1

Type of the PG/PC interface: PN/IE
PG/PC interface: vmxnet3 Ethernet Adapter
Connection to interface/subnet: PN/IE_1

1st gateway:

Select target device: Show devices with the same addresses
Device Device type Interface type Address Target device
tp1500 SIMATIC-HMI PN/IE 192.168.20.121 tp1500
PN/IE Access address

Flash LED

Start search

Online status information:
Display only error messages
Scan completed. 1 compatible devices of 1 accessible devices found.
Retrieving device information...
Scan and information retrieval completed.

Load Cancel

7. Activate the "Overwrite all" checkbox in the "Load preview" dialog.

8. Click the "Load" button.

| Status | Target | Message | Action |
| --- | --- | --- | --- |
| 4 | HMI_1 | Ready for loading. | Load 'HMI_RT_1' |
| ✓ | Overwrite | Overwrite if object exists online? | Overwrite all |
| ✓ | HMI Runtime | Informations |  |
| < | Ⅷ | Refresh |  |

---

All rights reserved
2021
Siemens AG 
©

### 4.4 Operation

##### Introduction

This section will show you how to use the functions of the application example described above.

##### Procedure

1. In the start page of the HMI panel, click on "Start Application".

Start Application
SITRAIN & Online Support

Configuration of TLS-based PG/HMI
Communication And The Protection of
Confidential PLC Configuration Data

Entry-ID 109798583

Industry Online Support

A project of Siemens Industry Online Support

##### 2. Click the "Navigation" button on the top right corner to open the navigation menu.

| Category | Target Speed (m/min) | Actual Speed (m/min) | State | Direction |
| --- | --- | --- | --- | --- |
| Conveyor Belt | 0.0 | 0.0 | Off | Stopped |

+0.0 m/min

---

All rights reserved
2021
Siemens AG 
©

3. Click on "Messages".

PLC HMI Security

Date and Time
10/12/2021 1:52:27 PM

Conveyor Belt

Target Speed
0.0

Actual Speed
+0.0 m/min

State
Off

Direction
Stopped

+0.0 m/min

Startscreen
Application
Messages
Diagnosis
Settings

Overview

4. In the table, you see that the connection between the PLC and the HMI panel has been 
established successfully. This connection is secured as only secured PG/P and HMI 
communication was allowed in the PLC configuration (see chapter <u>3.2</u>).

PLC HMI Security

Date and Time
10/12/2021 1:52:49 PM

Acknowledge group
$ 140000 1:48:09 PM 10/12/2021 | Connection established: HMI_Connection_1, Station 192.168.20.120, Rack 0, Slot 1.
$ 0
$ 0
$ 380100 1:48:06 PM 10/12/2021 | Assign a password for the Sm@rtServer in the WinCC Internet Settings applet, because the connection cannot be established o... 0
$ 70018 1:48:06 PM 10/12/2021 | User administration imported successfully.
$ 0
$ 70022 1:48:06 PM 10/12/2021 | User administration import started.
$ 0

##### 5. Click on the navigation button again and select "Application".

PLC HMI Security

Date and Time
10/12/2021 1:53:42 PM

No. Time Date Status Text
$ 140000 1:48:09 PM 10/12/2021 I Connection established: HMI_Connection_1, Station 192.168.20.120, Rack 0, Slot 1.
$ 110001 1:48:06 PM 10/12/2021 I Change to operating mode 'online'.
$ 380100 1:48:06 PM 10/12/2021 I Assign a password for the Sm@rtServer in the WinCC Internet Settings applet, because the connection cannot be establi
$ 70018 1:48:06 PM 10/12/2021 I User administration imported successfully.
$ 70022 1:48:06 PM 10/12/2021 I User administration import started.

Startscreen
Application
Messages
Diagnostics
Settings

---

All rights reserved
2021
Siemens AG 
©

6. In this page, the conveyor belt can be controlled by increasing its speed or decreasing it. 
The "Actual Speed" will change to match the "Target Speed". The current state of the 
conveyor belt and its direction are also displayed.

PLC HMI Security

Date and Time
10/12/2021 1:54:20 PM

Conveyor Belt

Target Speed
15.0

Actual Speed
+15.0 m/min

State
On

Direction
Direct

+15.0 m/min

---

All rights reserved
2021
Siemens AG 
©

## 5 Device Exchange Scenarios

As described in chapter <u>2.3</u>, the TIA Portal project and the key generated from the confidential 
PLC configuration data password are related to each other like two matching puzzle pieces. The 
project is bound to the loaded key information. The loaded key information is bound to the 
password that was assigned during configuration. Project and key information must match, 
otherwise the PLC will not start.

The project and key information are placed in different memory areas when loaded for the first 
time. The project in the load memory (SIMATIC Memory Card), the key information in a memory 
area in the PLC. This key is used to read the confidential configuration data on the SIMATIC
Memory Card.

Figure 5-1

$$
\textcircled{1}
$$
$$
\textcircled{2}
$$

1. Project with password-protected confidential configuration data (here: in load memory = 
SIMATIC Memory Card).

2. Key information (generated from password) to use the protected confidential configuration 
data (here: in the memory area in the PLC).

Figure 5-2

&

Therefore, the password assignment to protect the PLC confidential configuration data has an 
impact on the replacement parts scenario.

##### NOTE

If you have not assigned a password to the PLC in your project to protect confidential PLC 
configuration data, you can insert the SIMATIC Memory Card of the PLC to be replaced into 
a new PLC without any further action needed.

If you have assigned a password to protect confidential PLC configuration data in your 
TIA Portal project, observe the following rules when replacing a PLC.

---

All rights reserved
2021
Siemens AG 
©

### 5.1 The Replacement PLC has no password for confidential Configuration Data

When the replacement PLC does not have a configuration or a configured password to protect 
confidential PLC configuration data, you can load the project into the replacement PLC without 
any further preparation regardless of whether a password is configured or not in the TIA Portal 
project.

Figure 5-3

1x
**TIA Portal**
Initial Download
**PLC**

### 5.2 The Replacement PLC has the same password for confidential Configuration Data

If the replacement PLC has the same password as in the PLC to be replaced, the SIMATIC 
Memory Card from the PLC to be replaced can be inserted directly to the replacement PLC 
without further configuration.

Figure 5-4

**PLC (old)**
**PLC (new)**

---

All rights reserved
2021
Siemens AG 
©

### 5.3 The Replacement PLC has another password for confidential Configuration Data

If the replacement PLC has already been configured with a different password, you must reset 
the PLC to the factory settings with the following options set:

– "Delete password for protection of confidential PLC configuration data".

– "Format memory card"

Then the correct password must be set on the replacement PLC. This can be achieved using 
one of the following methods.

#### 5.3.1 Setting the password in TIA Portal

Figure 5-5

**TIA Portal (Online Access)**

1

**PLC (old)**

1. In TIA Portal, go online on the replacement PLC.

2. In the online window, navigate to "Functions > Reset to factory settings".

3. Activate the following functions:

– "Delete password for protection of confidential configuration data"

– "Format memory card".

4. Click the "Reset PLC" button.

Online access ▶ vmxnet3 Ethernet Adapter ▶ plc_1.profinet interface_1 [192.168.20.120] ▶ PLC_1 [192.168.20.120] [192.168.20.120]

Diagnostics
General
Diagnostic status
Diagnostics buffer
Cycle time
Memory
Display
OPC UA
PROFINET interface[X1]
Virtual communication interface

Functions
Assign IP address
Set time
Firmware update
Assign PROFINET device name
Specify password to protect
Reset to factory settings
Format memory card
Save service data

Reset to factory settings

IP address: 192 . 168 . 20 . 120
PROFINET device name: plc_1.profinet interface_1
Delete IP address
Delete password for protection of confidential PLC configuration data
Format memory card
Reset PLC

---

All rights reserved
2021
Siemens AG 
©

5. After the reset process ends, go online to the replacement again.

6. Navigate to "Functions > Specify password to protect the PLC configuration data".

7. Click the "Setup" button.

Online access ▶ vmxnet3 Ethernet Adapter ▶ plc_1.profinet interface_1 [192.168.20.120] ▶ PLC_1 [192.168.20.120] [192.168.20.120]

Diagnostics
General
Diagnostic status
Diagnostics buffer
Cycle time
Memory
Display
OPC UA
PROFINET interface[X1]
Virtual communication interface

Functions
Assign IP address
Set time
Firmware update
Assign PROFINET device name
Specify password to protect the PLC configuration data
Reset to factory settings
Format memory card
Save service data

Specify password to protect the PLC configuration data

Protection of confidential PLC configuration data

The passwords for confidential PLC configuration data must be identical in the PLC and in the PLC project.
The new password is evaluated on loading into the device and during startup of the PLC.
If the passwords in the project and in the PLC are different, the PLC does not switch to RUN mode.

Password:

Setup Delete

8. Configure the same password as in the PLC to be replaced.

9. Click the "OK" button.

Set password

Define password for protection of confidential PLC configuration data:

New password: **********

Confirm password: **********

OK
Cancel

10. Insert the SIMATIC Memory Card from the PLC to be replaced in the replacement PLC.

---

All rights reserved
2021
Siemens AG 
©

#### 5.3.2 Setting the password using an additional SIMATIC Memory Card

Figure 5-6

**Memory Card**
1
**PLC (old)**
2
**PLC (new)**

If TIA Portal is not available during the device replacement, this method can be used. It requires 
an additional SIMATIC Memory Card used to configure the password for confidential PLC 
configuration data on the replacement PLC.

##### Procedure

1. Configure a SIMATIC Memory Card with "SET PASSWORD" JOB file.

With this action, a folder and file structure following a special pattern is created. A password 
to protect the PLC confidential configuration data is written as plain text to a special file on 
the SIMATIC Memory Card. To see the description of the steps necessary to create the 
“SET PASSWORD” JOB file, refer to these instructions.

2. Insert the prepared SIMATIC Memory Card into the replacement PLC and switch it on.
The PLC reads the password, processes it, and stores the result in the internal memory. A 
possibly existing entry is overwritten.

3. Pull the SIMATIC Memory Card and restart the PLC.

##### Result (S7-1500 PLC)

While the PLC reads the SIMATIC Memory Card, the LED shows the same behavior as during a 
firmware update.

While the PLC is setting the password, the "RUN/STOP" LED blinks.

After the process has been completed successfully, the "RUN/STOP" LED is yellow and the 
"MAINT" LED blinks yellow.

The result of the operation is displayed in the diagnostic buffer as a success or error message. 
If the password could not be set, the error LED flashes together with the other LEDs.

---

All rights reserved
2021
Siemens AG 
©

##### Creating a SIMATIC Memory Card with "SET PASSWORD" JOB file

1. Create a folder in the root directory and give it the name "SET_PWD.S7S".

2. Create a text file with the name "PWD.TXT" with the password as plain text in the folder you 
have just created in the SIMATIC Memory Card.

3. Create a text file with the name "S7_JOB.S7S" with the content **"SET_PWD"** in the root 
directory of the SIMATIC Memory Card.
This file is the "JOB file". It is used to assign a password to the PLC to protect the 
confidential configuration data.

The following figure shows the file structure on the SIMATIC Memory Card.
Figure 5-7
[Image: Image588]

$$
\equiv\mathrm{SIMATIC}_{-}\mathrm{MC}
$$

$$
\mathrm{E}\mathrm{ET}_{-}\mathrm{PWD}.\mathrm{S7S}
$$

$$
\mathrm{PWD}.\mathrm{TXT}
$$

$$
\mathrm{S7_{-}JOB.S7S}
$$

CAUTION

| CAUTION | Safe storage of the SIMATIC Memory CardStore the SIMATIC memory card in a safe place to which only authorized persons have access. |
| --- | --- |

##### Rules and Recommendations

• The password must be set in a secure environment.

• The content of the text file "PWD.TXT" defines the password to protect the PLC confidential 
configuration data. It must correspond to the password that you have also set in the PLC
configuration.

• To reset an existing password of a PLC, the text file "PWD.TXT" must be empty. That 
means, the file size must be 0 bytes.

• Use any text editor to create the text file. The recommended text format is "UTF-8".

• Folder and file names are case-insensitive. However, the password is case sensitive.

• Do not add "CR" or "LF" character at the end of the text files ("PWD.TXT" or 
"S7_JOB.S7S").

---

All rights reserved
2021
Siemens AG 
©

## 6 Firmware Update & Device Backup

### 6.1 Firmware Update of the S7-1500 PLC

When updating the PLC firmware, always update to the latest version available for the 
respective article number.

An overview of article numbers and firmware versions of the S7-1500 PLCs included displays 
and ET 200 PLCs can be found here: \9\.

The respective latest version of a firmware is valid for all versions of that article number.
When updating the PLC firmware an update of the display is not mandatory but recommended.

An overview of how to update the PLC can be found here: \10\.

The already configured PLCs that have been updated to the latest firmware version in TIA 
Portal V17 will not have the newest security capabilities by default. The user must explicitly 
configure the PLC to have the new security features. The newly updated PLC will operate in the 
so-called "Legacy Mode" according to the configuration data in the PLC.

The "Mixed Mode" allows the PLC to communicate securely with other devices, e. g. PG or HMI 
panel which are configured with TIA portal V17 and have the latest security features ("Secure 
Mode"). In addition to that, the PLC is also able to communicate with devices that have been 
configured with previous versions of TIA Portal and do not have the latest security features on a 
non-secure basis ("Legacy Mode"). More information about the communication compatibility can 
be found in chapter 2.5.

##### NOTE

The PLCs in TIA Portal V17 can work in two modes:

• Secure Mode

In the secure mode, only secure TLS-based communication is allowed between the PLC 
and PG or HMI panel.

• Mixed Mode

In the Mixed mode, the PLC can communicate securely using TLS to PG or HMI panel 
that use TIA Portal V17 as well as to PG or HMI panel that use previous version of 
TIA Portal.

##### Projects Created with TIA Portal < V17

If you have created a project with TIA Portal version V16 for an S7-1500 PLC (e. g. version 
V2.8), then the corresponding configuration with TIA Portal V17 can also be loaded into an 
S7-1500 PLC V2.9. The behavior of the S7-1500 PLC V2.9 will be the same to that of V2.8 i. e. 
the PLC doesn't support the new security functions of TIA Portal Version 17.

This also applies to projects created with TIA Portal < V17 and transferred to a SIMATIC 
Memory Card. They work without problems in an S7-1500 PLC V2.9.

However, the concept of protecting the PLC confidential configuration data applies as soon as 
you open the project with TIA Portal ≥ V17, update the firmware version of the PLC via a device 
change and thus save it as a PLC with a firmware version ≥ V2.9. The project can no longer be 
edited with previous versions of TIA Portal V17.

---

All rights reserved
2021
Siemens AG 
©

### 6.2 Backing up and Restoring a PLC (S7-1200 PLC, S7-1500 PLC)

You can back up a functional configuration of a PLC in the TIA Portal and access it later. This 
makes it possible to restore the originally backed-up configuration. In this way you can load a 
modified configuration to perform the following actions, e. g.:

• test product improvements

• change programs for troubleshooting in the system

• replace components on a test basis

##### Backing up the configuration

You can then restore the originally backed-up configuration of the PLC.

If a CPU is backed up in the TIA Portal using the "Online> Backup from online device" menu, 
the password to protect the confidential PLC configuration data is also saved.

##### Restoring the backup

When restoring the backup of a PLC using the "Online > Download to device" menu with 
marked backup in TIA Portal the PLC can only communicate with a PG/PC or HMI panel if the 
following condition is fulfilled:

• After the restoration of a configuration protected with a password to protect confidential PLC 
configuration data, exactly this password must be present in the PLC.

Otherwise, the PLC cannot access the configuration data and does not start.

Figure 6-1

| Project tree |  |
| --- | --- |
| Devices | Plant objects |
| PLC_HMI_Security |  |
| * Add new device |  |
| Devices &amp; networks |  |
| PLC_1 [CPU 1513-1 PN] |  |
| IT Device configuration |  |
| Online &amp; diagnostics |  |
| Software units |  |
| Program blocks |  |
| Technology objects |  |
| External source files |  |
| PLC tags |  |
| PLC data types |  |
| Watch and force tables |  |
| Online backups |  |
| 2021-10-09_16:00:27 |  |
| 2021-10-13_15:58:39 |  |
| Traces |  |

---

All rights reserved
2021
Siemens AG 
©

##### Remedy

If the above error occurs, that is, the password to protect confidential PLC configuration data 
does not match the backup, you must delete the password and then set the correct password. 
See chapter 7.2. After restarting the PLC, the backup is functional.

### 6.3 Firmware Update and Device Backup of the HMI panel

An overview of the steps necessary to perform the following actions on operator panels can be 
found here: \11\ and \12\.

• Update

• Backup

• Restore

##### NOTE

When performing a system backup on the HMI panel, the certificates are backed up 
automatically as well. This means, if there is a functioning secure connection to the PLC 
before the backup, the connection to that PLC will be established successfully after restoring 
the backup.

---

All rights reserved
2021
Siemens AG 
©

## 7 Useful Information

### 7.1 A List of Components that Support Secure PG/PC and HMI Communication

##### Servers

• S7-1500 PLCs V2.9

• S7-1200 PLCs V4.5

• S7-PLCSIM Advanced

• Drive Controller V2.9

##### Clients

• STEP 7 V17 (TIA Portal V17)

• HMI Basic Panels 2nd Generation, V17

• HMI Mobile Panels 2nd Generation, V17

• HMI Comfort Panels, V17

• WinCC Runtime Advanced V17

• WinCC Runtime Professional V17

• WinCC Unified PC V17

• HMI Unified Comfort Panels V17

• SIMATIC NET V17 (OPC UA server)

• WinCC V7 from V7.5 SP2 Update 4

• WinCC OA from 3.18-P003

### 7.2 Changing the password to protect confidential PLC Configuration Data (S7-1200 PLC, S7-1500 PLC)

## Changing the password to protect confidential PLC ã

A distinction must be made between the following states:

• The PLC is loaded with a configuration.

• The PLC is in the delivery state (factory setting), i. e. the PLC is not yet loaded with a
configuration.

If the PLC is loaded with a configuration, it has the key information with which the passwordprotected PLC configuration data can be used.

#### 7.2.1 Change Password - Configuration is not yet loaded

If the CPU has not yet been loaded with a configuration, it is possible to change an entered 
password or to revoke the activation of the password protection

##### Precondition

• The PLC is not yet loaded with a configuration.

---

All rights reserved
2021
Siemens AG 
©

##### Change the password

1. In the TIA Portal project, open the PLC properties menu.

2. Navigate to "Protection & Security > Protection of PLC configuration data".

3. Click on the "Change" button.

The "Change password" dialog opens.

PLC_1 [CPU 1513-1 PN]
Properties Info Diagnostics

General IO tags System constants Texts

Web server access
Startup
Cycle
Communication load
System and clock memory
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC configuration data
Access level

Protection of the PLC configuration data

Protection of confidential PLC configuration data

Confidential PLC configuration data (e.g. OPC UA certificates) should be encrypted using a password. Because the project is not protected, the
password is not saved in the project and must be entered during download. Save the password in a secure location, for example, in a Password ...
More information about 'Protection of confidential PLC configuration data'
Protect confidential PLC configuration data
Password: ******
Reset Setup Change

2

4. Enter the previously valid password.

5. Enter the new password and confirm the new password.

6. Click the "OK" button to apply the changes.

Change password

Change password for the protection of confidential configuration data:

Old password: **********

New password: **********

Confirm password: **********

OK Cancel

---

All rights reserved
2021
Siemens AG 
©

##### Deactivate the "Protect confidential PLC configuration data" function

1. In the TIA Portal project, open the PLC properties menu.

2. Navigate to "Protection & Security > Protection of PLC configuration data".

3. Deactivate the "Protect confidential PLC configuration data" checkbox.

The "Deactivate protection of confidential PLC configuration data" dialog opens.

PLC_1 [CPU 1513-1 PN]
Properties Info Diagnostics

General IO tags System constants Texts

Web server access
Startup
Cycle
Communication load
System and clock memory
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC configuration data
Access level

Protection of the PLC configuration data

Protection of confidential PLC configuration data

Confident
password
Information data (e.g. OPC UA certificates) should be encrypted using a password. Because the project is not protected, the
project and must be entered during download. Save the password in a secure location, for example, in a Password ...
More info
Protect confidential PLC configuration data
Password:
Reset Setup Change

4. Enter the previously valid password.

5. Click the "OK" button to apply the changes.

Deactivate protection of confidential PLC configuration data

Enter password to deactivate the protection of confidential configuration data:

Password: **********

AI

OK Cancel

If you have not yet loaded any configuration into the PLC, the PLC is in the provisioning phase,
and you may load any valid configuration with your configured password. More on the 
provisioning phase and its meaning can be found in chapter <u>2.2.2</u>.

---

All rights reserved
2021
Siemens AG 
©

#### 7.2.2 Change Password - Configuration is already loaded

If the PLC has already been loaded with a configuration and the configuration is protected with 
a password for confidential PLC configuration data, this password must be deleted. There are 
the following methods to delete the password.

• Reset the PLC to factory settings

• Go online to the PLC to delete the password for protection of confidential PLC configuration 
data directly and to define it again.

##### Precondition

• You have write access to the PLC.

• The PLC is in "STOP" mode.

##### Procedure

Depending on the task to be performed, either one of the following steps must be performed:
If you want to also change the project on the SIMATIC Memory Card, i. e. you want to re-load 
the configuration you have to perform the following actions:

1. In the TIA Portal project, go online to the PLC.

2. In the online window, navigate to "Functions > Reset to factory settings".

3. Activate the "Delete password to protect confidential PLC configuration data" checkbox.

4. Select the "Format memory card" checkbox to avoid a repeated start-up of the PLC.

5. Click the "Reset PLC" button.

Online access ▶ vmxnet3 Ethernet Adapter ▶ plc_1.profinet interface_1 [192.168.20.120] ▶ PLC_1 [192.168.20.120] [192.168.20.120]

Diagnostics
General
Diagnostic status
Diagnostics buffer
Cycle time
Memory
Display
OPC UA
PROFINET interface[X1]
Virtual communication interface

Functions
Assign IP address
Set time
Firmware update
Assign PROFINET device name
Specify password to protect the PLC configuration data
Reset to factory settings
Format memory card
Save service data

Reset to factory settings
192 . 168 . 20 . 120
PROFINET interface_1
Delete IP address
Delete password for protection of confidential PLC configuration data
Format memory card
Reset PLC

6. Load the project with the changed configuration and the desired password.

---

All rights reserved
2021
Siemens AG 
©

If you do not have to change the project on the SIMATIC Memory Card, i. e. only the wrong 
password is set:

1. In the TIA Portal project, go online to the PLC.

2. In the online window, navigate to "Specify password to protect the PLC configuration data".

3. Click the "Delete" button. If the "Delete" button is not available, no password has been set in 
the PLC yet.

PLC_HMI_Security ▶ PLC_1 [CPU 1513-1 PN]

Online access
Diagnostics
Functions
Assign IP address
Set time
Firmware update
Assign PROFINET device name
Specify password to protect the PLC configuration data
Reset to factory settings
Format memory card
Save service data

Specify password to protect the PLC configuration data

Protection of confidential PLC configuration data

The passwords for confidential PLC configuration data must be identical in the PLC and in the PLC project.
The new password is evaluated on loading into the device and during startup of the PLC.
If the passwords in the project and in the PLC are different, the PLC does not switch to RUN mode.

Password:

Setup Delete

##### 4. Click the "Setup" button.

##### The "Set password" dialog opens.

Specify password to protect the PLC configuration data

Protection of confidential PLC configuration data

The passwords for confidential PLC configuration data must be identical in the PLC and in the PLC project.
The new password is evaluated on loading into the device and during startup of the PLC.
If the passwords in the project and in the PLC are different, the PLC does not switch to RUN mode.

Password:

Setup Delete

5. Enter the required password and confirm the password.

6. Click the "OK" button.

Set password

Define password for protection of confidential PLC configuration data:

New password: **************

Confirm password: ************** AI

OK Cancel

If the correct password has been entered, the PLC can use the protected PLC configuration 
data.

---

All rights reserved
2021
Siemens AG 
©

##### No write access to the PLC

If you do not have write access to the load memory but only read access, do one of the 
following actions:

• Remove the SIMATIC Memory card from the PLC before you reset the PLC to factory 
settings with the option "Delete password to protect confidential PLC configuration data".

• Delete the SIMATIC Memory Card externally, e. g. in your PC, before you reset the PLC to 
factory settings with the option "Delete password to protect confidential PLC configuration 
data".

##### NOTE

Restoring the factory settings of the PLC via the mode selector also deletes the IP address 
of the PLC, but not the password for protecting confidential PLC configuration data.

---

All rights reserved
2021
Siemens AG 
©

### 7.3 Resetting the password to protect confidential PLC Configuration Data (S7-1200 PLC, S7-1500 PLC)

The password to protect the confidential PLC configuration data can be reset. This is e. g. 
necessary if the password is to be changed, but the current password is no longer known.

#### 7.3.1 Resetting the password - Configuration is not yet loaded

Since you must enter the password when loading the PLC via TIA Portal for the first time, the 
PLC configuration for this PLC can no longer be used. To change the password in the PLC
properties, you must also enter the previously valid password. If you forget your password, do 
the following.

##### Precondition

• The PLC is not yet loaded.

##### Procedure

1. In the PLC properties menu in TIA Portal, navigate to "Protection & Security > Protection of 
the PLC configuration data".

2. Click the "Reset" button.

PLC_1 [CPU 1513-1 PN]
Properties Info Diagnostics

General IO tags System constants Texts

Web server access
Startup
Cycle
Communication load
System and clock memory
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC configuration data
Access level

Protection of the PLC configuration data

Protection of confidential PLC configuration data

Confidential PLC configuration data (e.g. OPC UA certificates) should be encrypted using a password. Because the project is not protected, the
password is not saved in the project and must be entered during download. Save the password in a secure location, for example, in a Password ...
More information about 'Protection of confidential PLC configuration data'
Protect confidential PLC configuration data
Password:
*************
Reset Setup Change

##### NOTE

The certificates of the CPU (e. g. certificates for web server, for OPC UA server, for PG/PC 
and HMI communication) can no longer be used after the reset. It is necessary to recreate 
and reassign the certificates of the CPU.

• If you use the global security settings for the certificate manager, you must reassign the 
certificates from the certificate manager.

• If you do not use the global security settings for the certificate manager, you must 
recreate and reassign the certificates.

---

All rights reserved
2021
Siemens AG 
©

3. Confirm the reset of the password with "Yes".

Reset password (0268:005003)

Do you really want to reset the password to protect confidential PLC configuration data?

By resetting the password, the certificates used by the PLC can no longer be used and must be reassigned.

Yes No

The option for the protection of confidential PLC configuration data is still activated.

#### 7.3.2 Resetting the password - Configuration is already loaded

If the PLC has already been loaded with a configuration and the configuration is protected with 
a password for confidential PLC configuration data, you can, for loading a new project, delete 
the password for confidential PLC configuration data online and then specify a new password.

##### Precondition

• You have write access to the PLC.

• The PLC must be in STOP mode.

##### Procedure

1. In TIA Portal, go online to the PLC.

2. In the online window, navigate to "Functions > Specify password to protect the PLC 
configuration data".

3. Click the "Delete" button.

If the "Delete" button is not available, no password has been set in the PLC yet.

PLC_HMI_Security ▶ PLC_1 [CPU 1513-1 PN]

Online access
Diagnostics
Functions
Assign IP address
Set time
Firmware update
Assign PROFINET device name
Specify password to protect the PLC configuration data
Reset to factory settings
Format memory card
Save service data

Specify password to protect the PLC configuration data

Protection of confidential PLC configuration data

The passwords for confidential PLC configuration data must be identical in the PLC and in the PLC project.
The new password is evaluated on loading into the device and during startup of the PLC.
If the passwords in the project and in the PLC are different, the PLC does not switch to RUN mode.

Password:

Setup Delete

##### NOTE

If the password is deleted and a loaded project requires a corresponding password, this 
project may no longer work without entering the password.

---

All rights reserved
2021
Siemens AG 
©

##### 4. If required, click the "Setup" button to set a new password. The "Set password" dialog opens.

Online access ▶ vmxnet3 Ethernet Adapter ▶ plc_1.profinet interface_1 [192.168.20.120] ▶ PLC_1 [192.168.20.120] [192.168.20.120]

Diagnostics
General
Diagnostic status
Diagnostics buffer
Cycle time
Memory
Display
OPC UA
PROFINET interface[X1]
Virtual communication interface

Functions
Assign IP address
Set time
Firmware update
Assign PROFINET device name
Specify password to protect the PLC configuration data
Reset to factory settings
Format memory card
Save service data

Specify password to protect the PLC configuration data

Protection of confidential PLC configuration data

The passwords for confidential PLC configuration data must be identical in the PLC and in the PLC project.
The new password is evaluated on loading into the device and during startup of the PLC.
If the passwords in the project and in the PLC are different, the PLC does not switch to RUN mode.

Password:

Setup Delete

5. Enter the new password.

6. Click the "OK" button.

Set password

Define password for protection of confidential PLC configuration data:

New password: **************

Confirm password: **************

AI

OK
Cancel

#### 7.3.3 Resetting the password using SIMATIC Memory Card

It is also possible to reset the password using an additional SIMATIC Memory Card. This 
method can be used for scenarios where TIA Portal is not available. To read more about how to 
perform this procedure, refer to chapter <u>5.1</u>.

##### NOTE

To reset the password, the “PWD.TXT” file must be empty. That means, the file size must be 
0 bytes.

---

All rights reserved
2021
Siemens AG 
©

### 7.4 Tips for Error Avoidance and Error Handling

The following description lists some use cases that may result in PLC error messages.

##### Diagnostic buffer provides information

The PLC detects when the password to protect the confidential PLC configuration data and the 
loaded configuration do not match. A message in the diagnostic buffer indicates possible 
causes and remedies and usually leads to a solution of the problem.

##### Typical "Pitfalls"

You should pay attention to the following circumstances to avoid or correct errors:

Configuration loaded?

Regardless of whether you protect your confidential PLC configuration data with a password 
or not the following circumstance must be observed:

Without a loaded configuration, the PLC does not leave the provisioning phase (see chapter 
<u>2.2.2</u>).

• You are trying to load a configured password into a CPU that has already received an other 
password, e. g.:

PLC is exchanged for another PLC from the stock. The replacement PLC was not 
completely reset (reset to factory settings with option "Delete password for protection of 
confidential PLC configuration data").

##### Remedy:

– For the configuration to be loaded, use the same password that was already used for 
the configuration already loaded.

– It is also possible that the wrong project or PLC configuration was loaded. Check 
whether the correct PLC configuration is available.

– Use the online function "Specify password to protect confidential PLC configuration 
data" to delete the password or to set the same password as in the PLC configuration.

• The same error occurs if your PLC configuration does not use a password and the already 
loaded configuration requires a user-defined password.

##### Remedy:

– Use the online function "Set password to protect confidential PLC configuration data" to 
delete the password or to set the same password as in the PLC configuration.

---

All rights reserved
2021
Siemens AG 
©

### 7.5 Using Legacy PG/PC Communication in TIA Portal

From TIA Portal version V17, the TIA Portal and the S7-1200 PLCs from firmware V4.5 as well 
as S7-1500 PLCs from firmware V2.9 automatically communicate "securely", i. e. the 
connection partners set their connection mechanisms automatically to the highest possible 
security method.

Only special circumstances cause a fallback to the legacy PG/PC communication. See <u>Section</u> 
<u>2.5</u>.

There may be some cases in which the higher security is not desirable because it can impact 
the transmission rate of PLCs with weak communication performance. For the cases where this 
is true, legacy PG/PC communication can be activated.

##### Requirement

• Online connections to the CPUs must not be established.

• For PLCs that are to be reached online, the option "Only allow secure PG/PC and HMI 
communication" must be disabled.

• The communication partners are in a protected environment, for example, during the 
commissioning phase.

##### Setting the Legacy PG/PC communication

1. In the "Online" menu, activate the "Use only legacy PG/PC communication" checkbox.

Siemens - D:\Projects\109798583_PLC_HMI_Security_PROJ_V17\109798583_PLC_H

Project Edit View Insert Online Options Tools Window Help
Save project

Project tree
Devices Plant obj
Start
PLC_HMI_Security
Add new device
Devices & networks
PLC_1 [CPU 1513-1
HMI_1 [TP1200 Con
Ungrouped devices
Security settings
Cross-device function
Common data
Documentation set
Languages & resou
Version control inte
Online access
Card Reader/USB mem

Go online Ctrl+K
Extended go online...
Go offline Ctrl+M

Use only legacy PG/PC communication

Simulation
Stop runtime/simulation

Download to device Ctrl+L
Extended download to device...
Download and reset PLC program
Download user program to Memory Card

Snapshot of the actual values
Load snapshots as actual values
Load start values as actual values

Upload from device (software)
Upload device as new station (hardware and software)...
Backup from online device
Hardware detection

HMI Device maintenance

Accessible devices... Ctrl+U

---

All rights reserved
2021
Siemens AG 
©

2. Click the "Yes" button when the warning message appears.

Change communication mode (0135:000022)

Do you really want to change from secure PG/PC communication to legacy PG/PC communication?

Only the legacy security standard is used for the communication between TIA Portal and PLC. This mode should only be used in a secure environment.

In this mode, communication is only possible with devices whose configuration allows legacy communication.

Yes No

All online connections are set up as for TIA Portal versions < V17.

The setting remains active for the duration of the session. When you open a project, the option 
"Use only legacy PG/PC communication" is not set.

##### Behavior with enabled option "Use only legacy PG/PC communication"

• In TIA Portal, a password to protect confidential PLC configuration data cannot be specified, 
modified, or deleted online for PLCs. These functions require disabling the "Use only legacy 
PG/PC communication" function.

• A PLC that is set to only allow secure PG/PC and HMI communication can no longer be 
reached online.

---

All rights reserved
2021
Siemens AG 
©

### 7.6 TIA Portal V17 Project Description

#### 7.6.1 Overview

##### Introduction

The TIA Portal V17 project contains:

• The user program for the S7 PLC with the "SimulatedDrive" function block.

• The configuration of the new security functions of the S7-1500 PLC.

• The configuration of the SIMATIC TP1200 Comfort Panel.

##### Diagram

The following graphic shows the program structure of the whole TIA Portal V17 project.
Figure 7-1

SimulatedDriveData
IDB 
(DB2)
(DB4)

##### Program blocks

The user program for the S7-1500 PLC consists of the following elements:

Table 7-1

| Element | Symbolic name | Description |
| --- | --- | --- |
| OB1 | Main | In OB1, the function block "SimulatedDrive" including the corresponding instance data block is called cyclically. |
| FB1 | SimulatedDrive | The function block "SimulatedDrive" contains the functions implemented in this example. |
| DB2 | SimulatedDriveData | Global data block storing the data. |
| DB4 | InstSimulatedDrive | Instance data block from the "SimulatedDrive" function block. |

---

All rights reserved
2021
Siemens AG 
©

#### 7.6.2 The "SimulatedDrive" function block

##### Function

The "SimulatedDrive" function block checks the current speed of the conveyor belt 
"actualSpeed" at regular intervals and compares it with a predefined value "setpointSpeed".

• If the actual speed is greater than the predefined value, the speed "actualSpeed" is reduced 
to the value "setpointSpeed".

• If the actual speed is less than the predefined value, the speed "actualSpeed" is increased 
to the value "setpointSpeed".

##### Parameter

The Figure and table below show the call interface of the "SimulatedDrive" function block.
Figure 7-2

"InstSimulated Drive"
"SimulatedDrive"
EN ENO
"SimulatedDrive Data".drive1. setpointSpeed
setPoint Speed isActive "SimulatedDrive Data".drive1.is Active
actualSpeed "SimulatedDrive Data".drive1. actualSpeed

Table 7-2

| Parameter |  | Data type | Description |
| --- | --- | --- | --- |
| INPUT | EN | BOOL | Enable input. Only in FDP and LAD. |
| INPUT | setpointSpeed | LREAL | Predefined value with which the speed of the conveyor belt is compared at regular intervals. |
| OUTPUT | ENO | BOOL | Enable output. Only in FDP and LAD. |
| OUTPUT | isActive | BOOL | State of the conveyor belt. |
| OUTPUT | actualSpeed | LREAL | Indicates the current speed of the conveyor belt: • If the actual speed is greater than the predefined value, the speed "actualSpeed" is reduced to the value "setpointSpeed". • If the actual speed is less than the predefined value, the speed "actualSpeed" is increased to the value "setpointSpeed". |

---

All rights reserved
2021
Siemens AG 
©

#### 7.6.3 The "SimulatedDriveData" global data block

The "SimulatedDriveData" data block contains the data for communication between the 
S7-1500 PLC and the HMI panel:

• isActive.

• actualSpeed

• setpointSpeed

Figure 7-3

| Name | Data type | Start value | Retain | Accessible f... | Write... | Visible in ... | Setpoint | Supervision | Comment |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Static |  |  |  |  |  |  |  |  |  |
| drive1 | *typeDriveInterface* |  |  |  |  |  |  |  |  |
| isActive | Bool | false |  |  |  |  |  |  | Drive state |
| actualSpeed | LReal | 0.0 |  |  |  |  |  |  | Drive actual speed |
| setpointSpeed | LReal | 0.0 |  |  |  |  |  |  | Drive target speed |

##### NOTE

The PLC tags used for communication between the S7-1500 PLC and the HMI panel must 
be declared as accessible for HMI ("Accessible from HMI/OPC UA/Web API").

---

All rights reserved
2021
Siemens AG 
©

## 8 Appendix

### 8.1 Service and support

##### Industry Online Support

Do you have any questions or need assistance?

Siemens Industry Online Support offers round the clock access to our entire service and support 
know-how and portfolio.

The Industry Online Support is the central address for information about our products, solutions 
and services.

Product information, manuals, downloads, FAQs, application examples and videos – all 
information is accessible with just a few mouse clicks:

<u>support.industry.siemens.com</u>

##### Technical Support

The Technical Support of Siemens Industry provides you fast and competent support regarding 
all technical queries with numerous tailor-made offers

– ranging from basic support to individual support contracts.

Please send queries to Technical Support via Web form:

siemens.com/SupportRequest

##### SITRAIN – Digital Industry Academy

We support you with our globally available training courses for industry with practical 
experience, innovative learning methods and a concept that’s tailored to the customer’s specific 
needs.

For more information on our offered trainings and courses, as well as their locations and dates, 
refer to our web page:

siemens.com/sitrain

##### Service offer

Our range of services includes the following:

• Plant data services

• Spare parts services

• Repair services

• On-site and maintenance services

• Retrofitting and modernization services

• Service programs and contracts

You can find detailed information on our range of services in the service catalog web page:

support.industry.siemens.com/cs/sc

##### Industry Online Support app

You will receive optimum support wherever you are with the "Siemens Industry Online Support" 
app. The app is available for iOS and Android:

support.industry.siemens.com/cs/ww/en/sc/2067

---

All rights reserved
2021
Siemens AG 
©

### 8.2 Industry Mall

SIEMENS INDUSTRY MALL INDUSTRY MALL

The Siemens Industry Mall is the platform on which the entire siemens Industry product portfolio 
is accessible. From the selection of products to the order and the delivery tracking, the Industry 
Mall enables the complete purchasing processing – directly and independently of time and 
location:

mall.industry.siemens.com

### 8.3 Links and literature

Table 8-1

| No. | Topic |
| --- | --- |
| \1\ | Siemens Industry Online Support https://support.industry.siemens.com |
| \2\ | Link to this entry page of this application example https://support.industry.siemens.com/cs/ww/en/view/109798583 |
| \3\ | Using Certificate with TIA Portal https://support.industry.siemens.com/cs/ww/en/view/109769068 |
| \4\ | HMI Design with HMI Template Suite https://support.industry.siemens.com/cs/ww/en/view/91174767 |
| \5\ | SIMATIC SCADA Export for TIA Portal https://support.industry.siemens.com/cs/ww/en/view/109748955 |
| \6\ | FAQ WinCC SCADA https://support.industry.siemens.com/cs/ww/en/view/109798498 |
| \7\ | SIMATIC S7-1500/ET 200MP Manual Collection https://support.industry.siemens.com/cs/ww/en/view/86140384 |
| \8\ | SIMATIC HMI - HMI Devices Comfort Panels https://support.industry.siemens.com/cs/ww/en/view/49313233 |
| \9\ | Firmware update S7-1500 CPUs incl. Displays and ET 200 CPUs (ET 200SP, ET 200pro) https://support.industry.siemens.com/cs/ww/en/view/109478459 |
| \10\ | Description of Firmware Update for S7-1500 CPU, Displays, ET 200SP CPUs und ET 200pro CPUs https://support.industry.siemens.com/cs/ww/en/view/77492231 |
| \11\ | How do you do an operating system update on operator panels or perform a "Reset to factory settings"? https://support.industry.siemens.com/cs/ww/en/view/19701610 |
| \12\ | How do you backup/restore with a Comfort Panel? https://support.industry.siemens.com/cs/ww/en/view/58876345 |

---

All rights reserved
2021
Siemens AG 
©

### 8.4 Change documentation

Table 8-2

| Version | Date | Modifications |
| --- | --- | --- |
| V1.0 | 11/2021 | First version |
|  |  |  |
|  |  |  |