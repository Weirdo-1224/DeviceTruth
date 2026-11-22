**Siemens** 
**Industry** 
**Online** 
**Support**

**APPLICATION EXAMPLE**

# Using Certificates with TIA Portal

SIMATIC / TIA Portal

## SIEMENS

---

## Legal information

###### Use of application examples

Application examples illustrate the solution of automation tasks through an interaction of several components in the form of text, 
graphics and/or software modules. The application examples are a free service by Siemens AG and/or a subsidiary of Siemens AG
("Siemens"). They are non-binding and make no claim to completeness or functionality regarding configuration and equipment. The 
application examples merely offer help with typical tasks; they do not constitute customer-specific solutions. **The application examples 
are not subject to standard tests and quality inspections of a chargeable product and may contain functional and performance 
defects or other faults and security vulnerabilities. You are responsible for the proper and safe operation of the products in 
accordance with all applicable regulations, including checking and customizing the application example for your system, and 
ensuring that only trained personnel use it in a way that prevents property damage or injury to persons. You are solely 
responsible for any productive use.**

Siemens grants you the non-exclusive, non-sublicensable and non-transferable right to have the application examples used by technically 
trained personnel. Any change to the application examples is your responsibility. Sharing the application examples with third parties or 
copying the application examples or excerpts thereof is permitted only in combination with your own products. Any further use of the 
application examples is explicitly not permitted and further rights are not granted. You are not allowed to use application examples in 
any other way, including, without limitation, for any direct or indirect training or enhancements of AI models.

###### Disclaimer of liability

Siemens shall not assume any liability, for any legal reason whatsoever, including, without limitation, liability for the usability, 
availability, completeness and freedom from defects of the application examples as well as for related information, configuration and 
performance data and any damage caused thereby. This shall not apply in cases of mandatory liability, for example under the German 
Product Liability Act, or in cases of intent, gross negligence, or culpable loss of life, bodily injury or damage to health, non-compliance 
with a guarantee, fraudulent non-disclosure of a defect, or culpable breach of material contractual obligations. Claims for damages 
arising from a breach of material contractual obligations shall however be limited to the foreseeable damage typical of the type of 
agreement, unless liability arises from intent or gross negligence or is based on loss of life, bodily injury or damage to health. The 
foregoing provisions do not imply any change in the burden of proof to your detriment. You shall indemnify Siemens against existing or 
future claims of third parties in this connection except where Siemens is mandatorily liable.

By using the application examples you acknowledge that Siemens cannot be held liable for any damage beyond the liability provisions 
described.

###### Other information

Siemens reserves the right to make changes to the application examples at any time without notice and to terminate your use of the 
application examples at any time. In case of discrepancies between the suggestions in the application examples and other Siemens 
publications such as catalogs, the content of the other documentation shall have precedence.

The Siemens terms of use (https://www.siemens.com/global/en/general/terms-<u>of-use.html</u>) shall also apply.

###### Cybersecurity information

Siemens provides products and solutions with industrial cybersecurity functions that support the secure operation of plants, systems, 
machines and networks.

In order to protect plants, systems, machines and networks against cyber threats, it is necessary to implement – and continuously 
maintain – a holistic, state-of-the-art industrial cybersecurity concept. Siemens’ products and solutions constitute one element of such a 
concept.

Customers are responsible for preventing unauthorized access to their plants, systems, machines and networks. Such systems, machines 
and components should only be connected to an enterprise network or the internet if and to the extent such a connection is necessary 
and only when appropriate security measures (e.g. firewalls and/or network segmentation) are in place.

For additional information on industrial cybersecurity measures that may be implemented, please visit www.siemens.com/cybersecurity-
<u>industry</u>.

Siemens’ products and solutions undergo continuous development to make them more secure. Siemens strongly recommends that 
product updates are applied as soon as they are available and that the latest product versions are used. Use of product versions that are 
no longer supported, and failure to apply the latest updates may increase customer’s exposure to cyber threats.

To stay informed about product updates, subscribe to the Siemens Industrial Cybersecurity RSS Feed under 
https://www.siemens.com/cert.

---

## Table of contents

**1. Introduction...................................................................................................................................5**
1.1. Overview......................................................................................................................................................5
1.2. Public Key Infrastructure ...............................................................................................................................5
1.3. Digital certificates.........................................................................................................................................7
1.4. Certificate Management................................................................................................................................8
1.5. Components used.........................................................................................................................................9
**2. Engineering .................................................................................................................................10**
2.1. Hardware setup..........................................................................................................................................10
2.2. Planning.....................................................................................................................................................10
2.3. TIA Portal for certificate management..........................................................................................................11
2.3.1. Overview....................................................................................................................................................11
2.3.2. Using the local certificate manager..............................................................................................................12
2.3.3. Using the global certificate manager............................................................................................................15
2.3.4. Loading certificates to the CPU during runtime.............................................................................................22
2.4. Certificates in the scope of OPC UA communication......................................................................................23
2.4.1. Overview on OPC UA communication ..........................................................................................................23
2.4.2. Security settings for the OPC UA server........................................................................................................23
2.4.3. Setting up the OPC UA server.......................................................................................................................24
2.4.4. Setting up the OPC UA client .......................................................................................................................31
2.4.5. Testing a secure OPC UA connection............................................................................................................36
2.4.6. OPC UA certificate handling scenarios–where does each certificate belong?..................................................38
2.5. Certificates in the scope of HTTPS web server communication.......................................................................41
2.5.1. Overview on HTTPS communication.............................................................................................................41
2.5.2. Setting up the web server............................................................................................................................41
2.5.3. Setting up the web browser.........................................................................................................................44
2.5.4. Testing a secure HTTPS connection..............................................................................................................46
2.5.5. HTTPS certificate handling scenarios–where does each certificate belong? ....................................................48
2.6. Certificates in the scope of Secure OUC communication ...............................................................................51
2.6.1. Overview on Open User Communication......................................................................................................51
2.6.2. Setting up the TLS server.............................................................................................................................51
2.6.3. Setting up the TLS client..............................................................................................................................56
2.6.4. Testing a Secure OUC communication..........................................................................................................59
2.7. Certificates in the scope of PG/PC and HMI communication...........................................................................61
2.7.1. Overview on Secure PG/PC and HMI communication.....................................................................................61
2.7.2. PLC communication certificate.....................................................................................................................61
2.7.3. Security settings for PG/PC and HMI communication.....................................................................................61
2.7.4. Testing a secure connection with a Field PG.................................................................................................62
2.7.5. Testing a secure connection with an HMI device...........................................................................................62
Entry ID: 109769068 V2.1 06/2025

---

**3. Additional information – Certificate management via OPC UA.......................................................63**
3.1. GDS Push for dynamic certificate management ............................................................................................63
3.1.1. Overview....................................................................................................................................................63
3.1.2. GDS Push certificate handling scenarios.......................................................................................................64
**4. FAQ / Error Handling.....................................................................................................................67**
4.1. Certificates.................................................................................................................................................67
4.1.1. Is it possible to work with certificates without protecting the TIA Portal project? ............................................67
4.1.2. How long should a certificate be valid? ........................................................................................................67
4.1.3. Which changes on the device configuration will require a new certificate?.....................................................67
4.1.4. Which SAN fields are mandatory?.................................................................................................................67
4.1.5. What is the difference between exporting the certificate chain vs certificate? ................................................67
4.1.6. What happens if an incomplete chain of trust is loaded into the PLC’s "trusted certificate store"?.....................68
4.1.7. Which tools can be used to create certificates without TIA Portal? .................................................................69
4.1.8. How can certificates be managed without downloading via TIA Portal? .........................................................69
4.1.9. Can third-party certificates be integrated in TIA Portal?.................................................................................69
4.1.10. How is certificate management in TIA Portal compared to the Internet PKI?...................................................69
4.1.11. How is certificate management handled for SCALANCE devices?...................................................................69
4.1.12. How is certificate management handled for Unified PCs and Panels?.............................................................70
4.1.13. Are X-509 V1 and V2 certificates supported?................................................................................................70
4.1.14. What happens when a certificate expires while the connection is already established?...................................71
4.2. PLC – HMI communication ..........................................................................................................................72
4.2.1. Disabling the check box "Only allow secure PG/PC and HMI communication" implies that no certificates are 
being used?................................................................................................................................................72
4.2.2. Is the CPU certificate, used for PG/PC and HMI communication, visible in WinCC Unified PCs and Comfort 
Panels?.......................................................................................................................................................72
4.2.3. Why is CA trust not possible for PLC-HMI communication in WinCC Unified PCs and Comfort Panels?..............72
4.2.4. How to renew the "PLC communication certificate" in WinCC Unified PCs and Comfort Panels?.......................72
4.3. OPC UA ......................................................................................................................................................72
4.3.1. Are certificates required to authenticate via username and password?...........................................................72
4.3.2. What happens to the OPC UA connection if one certificate expires?...............................................................73
4.3.3. What is the SecureChannel and how often is it renewed?..............................................................................73
4.3.4. Why is it important to renew the SecureChannel?.........................................................................................74
4.3.5. What limitations exist for GDS Push?............................................................................................................74
**5. Appendix.....................................................................................................................................75**
5.1. Service and support ....................................................................................................................................75
5.2. Links and literature.....................................................................................................................................76
5.3. Change documentation...............................................................................................................................76

---

## 1. Introduction

### 1.1. Overview

This document addresses the key aspects of certificate management in OT environments, a topic gaining special 
significance as industries shift to the digital era. In this new industrial landscape, OT systems face the same challenges 
and risks as the IT domain, underscoring the need to secure communications and ensure proper understanding and 
utilization of certificates.

Therefore, this application example provides guidelines to establish secure communications in the context of PG/PC and 
HMI communication, OPC UA, OUC (Open User Communication) and HTTPS, offering an overview of the tools and 
possibilities available within TIA Portal.

### 1.2. Public Key Infrastructure

###### Integrity, confidentiality, and endpoint authentication

The Public Key Infrastructure (PKI) stands as a robust framework in the field of cybersecurity, safeguarding digital 
communications by ensuring the integrity and confidentiality of messages transmitted over networks, as well as providing 
endpoint authentication.

• Integrity: Data must remain unchanged and unaltered during transmission.

• Confidentiality: Sensitive information must be kept private and inaccessible to unauthorized parties.

• Authentication: The communication partner is who it claims to be and the party who is to be reached.

###### Basic principles

To grasp how the Public Key Infrastructure manages to secure communications, it is necessary to understand signing and 
encryption.

• Encryption is the process of transforming plain text into ciphertext, making it solely understandable to authorized 
users. By employing an "encryption key", a unique cipher is generated, effectively locking the message. Only entities 
possessing the correct key can decrypt and unlock this data, guaranteeing its confidentiality.

There are two types of encryptions: symmetric and asymmetric encryption.

a. Symmetric encryption employs a single key for both encryption and decryption of the cipher, rendering it an 
efficient, simple, and high-performance procedure. The main challenge of this approach lies in securely 
distributing the shared key between the sender and the receiver, without it being intercepted by unauthorized 
parties.

b. On the other hand, asymmetric encryption involves a pair of keys: a private key and a public key. Data encrypted 
with one key can only be decrypted with the other. While the public key is shared openly, the private key must 
always remain secret.

Each communication partner has its own private and public keys, solving the key distribution problem present in 
symmetric cryptography. Nonetheless, it is computationally intensive and therefore not suitable for large data 
transfers.

To tackle the disadvantages associated with each individual encryption method, the PKI adopts a hybrid approach. 
Initially, communication partners employ asymmetric encryption to establish a secure channel, facilitating the 
exchange of a symmetric key between them. Once the shared key has been exchanged, communication transitions to 
symmetric encryption that can handle extensive sets of data.

• Digital signing is used to verify authenticity and integrity. The sender passes the message through a hashing 
algorithm, generating a unique code known as "hash". This code is encrypted with the sender’s private key, forming a 
digital signature. Both the signature and the original message are transmitted.

---

Upon receiving the message, the recipient uses the sender's public key to decrypt the signature and extract the 
original hash. Then, the receiver performs the hashing process with the received message and compares it with the 
original. If both hashes match, the integrity and authenticity of the message can be confirmed.

###### Course of the secure communication

The figure below shows, in simplified terms, how communication is established ("handshake") focusing on the 
negotiation of keys used for data exchange. This process can be generalized to all communication options that are based 
on the usage of TLS, i.e., HTTPS, Secure Open User Communication, etc.

Alice

Alice accesses website with "https..."

Web server accepts the connection and sends its certificate with public key .

Signed public key and private key of the Web server

Alice selects random symm. key

Alice sends the symmetric key encrypted with the public key of the server

Web server decrypts the symmetric key with its private key

Alice and the Web server now use symmetric encryption and decryption

###### Key components of the PKI

The Public Key Infrastructure main components are:

• Public and private keys, to perform the initial asymmetric encryption.

• Digital certificates, which serve as electronic credentials that bind public keys to the identity of the certificate holder.

• Certificate Authorities, CAs; used to validate the identity of certificate holders and issue new certificates.

• Servers, often referred to as subjects, are network entities that need to prove their identity.

• Clients, or relying parties, must trust servers to establish a secure connection with them.

**NOTE** In certain protocols, such as HTTPS, clients validate the server's identity and are typically not required 
to authenticate themselves. In contrast, other protocols, such as OPC UA, usually require mutual 
authentication, establishing a two-way trust relationship between clients and servers.

###### NOTE

Clients and servers can be configured to allow connections from any communication partner without 
requiring authentication. It is essential, however, to restrict this configuration to the testing or 
commissioning stages. Transitioning to a production environment mandates special emphasis on 
security. Thus, access must be limited to authorized entities and communication safeguarded through 
signing and encryption.

---

### 1.3. Digital certificates

###### Certificate basics

A certificate is a digital credential used to assert the online identities of individuals, computers, and other entities on a
network. They are similar to ID cards, and they bind the identity of the owner with a public key.

X-509 V3 is one of the most common standards used to define the format of certificates. To ensure the authenticity of the 
entities and enable signing and encryption, these certificates include the following fields:

• Public key.

• Serial number of the certificate.

• Version number of the certificate.

• Validity period: starting and expiring dates.

• Owner: certificate subject.

• Issuer: CA or self-signed.

• Supported encryption and signing algorithms.

• Extensions:  Key Usage, Extended Key Usage, Subject Alternative Name, etc.

**NOTE** Certificates do not contain the private key of the subject, as it must be kept secret.

###### Certificate types

Different types of certificates can be found depending on the entity of the issuer, as they can be generated by their own
(self-signed) or issued by a Certificate Authority (CA).

• **Self-signed certificates:** Each device creates its own certificate; therefore, they are both certificate holder and issuer. 
To establish trust relations between devices, it is necessary to import all partner certificates.

• **Certificates issued by a Certificate Authority (CA):** Device certificates are signed by a Certificate Authority. 
Therefore, if the CA is trusted, all communication devices whose certificates are issued by the same CA are also trusted 
and thus authorized to establish a connection.

To invalidate certificates that are no longer deemed valid or trusted, Certificate Authorities employ Certificate 
Revocation Lists (CRL).

###### Decision making for different scenarios

• **Self-signed approach:** It can be useful in small and static systems where the number of devices is low. However, 
given that devices have a maximum number of keys that can be stored (64 in the case of SIMATIC PLCs), this approach 
can lead to resource bottlenecks.

Additionally, introducing new devices to the system can be challenging, as new certificates must be distributed to all 
communication partners. Therefore, industrial systems with self-signed certificates can be difficult to maintain (renew 
certificates) and expand.

• **Issued by Certificate Authorities:** This second approach offers more flexibility when managing medium to big 
systems, and it consumes less storage resources as only CA certificates, and the respective CRLs, need to be loaded to 
the CPUs.

The primary drawback of this approach is the need for a centralized administrative instance equipped with security 
measures to protect private keys. However, this concern can be addressed with TIA Portal’s Certificate Manager.

---

### 1.4. Certificate Management

###### Key Handling

Proper handling of certificates is critical to maintain integrity, confidentiality, and availability within industrial systems. As 
highlighted earlier, in the Public Key Infrastructure, entities make use of both a public and a private key to establish secure 
communication. Therefore, understanding the distinctions between these keys and how they must be handled is of 
utmost importance.

**Public Key:** The public key is intentionally created for widespread use, and it can be shared freely to any entity without 
compromising the security of the system. Its responsibilities include:

• Encryption of messages: to ensure that only the owner of the matching private key can decrypt and access the 
information.

• Verification of digital signatures: providing assurance that a message is indeed originated from the rightful owner of 
the private key.

**Private Key:** On the other hand, the private key is strictly confidential and must be kept secret. Its roles include:

• Decryption of messages: that are sent and encrypted by communication partners, using the corresponding public key.

• Signing: so other devices can verify the authenticity and integrity of messages.

Consequently, protection of private keys is imperative to prevent unauthorized access, decryption, and forging of digital 
signatures. Sections <u>2.4.6</u> and <u>2.5.5</u> include diagrams to showcase how certificates and private keys must be handled in 
different scenarios.

###### Certificate renewal

In Operational Technology (OT), the recommended certificate validity is regulated by the specific needs and security 
practices of the organization. There are, however, some general guidelines.

1. **Shorter validity periods:** Due to the criticality of industrial systems and the ever-evolving security threats, certificates 
in OT environments often have shorter validity periods compared to traditional IT systems. This typical validity might 
range from a few months to a few years.

2. **Regular renewal:** Renewing certificates regularly helps to maintain a higher level of security by ensuring that older 
certificates, which might become compromised or less secure over time due to newly emerging technologies, are 
replaced with newer ones.

3. **Balance security and operational impact:** While shorter validity periods enhance security, frequent certificate 
changes could potentially impact operational continuity. Therefore, it is essential to find a balance between security 
needs and operational impact.

Refer to chapter <u>3.1</u>, <u>&#34;GDS Push for dynamic certificate management&#34;</u>, to gain insights on how to handle certificates 
with minimal disruptions to production.

4. **Compliance to industry standards:** Some industries or regulatory frameworks might require specific conditions 
regarding certificate validity periods.

Thus, OT environments tend to follow a more conservative approach than some IT environments when it comes to 
certificate validity. This stems from the critical nature of industrial control systems and the need to mitigate risks 
associated with cyber threats, while considering the continuous operation of these systems.

---

### 1.5. Components used

This application example consists of the following components:

| Component | Article number | Note |
| --- | --- | --- |
| TIA Portal V19 | 6ES7822-1AA23-0YA5 | - |
| CPU 1516F-3 PN/PD | 6ES7516-3FP03-0AB0 | FW 3.1 – With previous firmware versions, step-by-step procedures can differ from those outlined in this document. |
| CPU 1516-3 PN/PD | 6ES7516-3AP03-0AB0 | FW 3.1 |

---

## 2. Engineering

### 2.1. Hardware setup

A PG with TIA Portal V19 is used to create and manage certificates, as well as configuring the S7-1500 CPU. The CPU will 
run an OPC UA server, a web server, and a TLS server for Secure OUC. To test these protocols, UaExpert operates as the 
OPC UA client, Microsoft Edge functions as the web browser for connecting to the web server, and a second PLC runs the 
TLS client.

**TIA Portal** V19
Engineering station
Secure PG/PC and HMI communication
CPU 1516F-3 PN/PD
OPC UA
HTTPS
Secure OUC
OPC UA client
Web browser
**OUC**

### 2.2. Planning

This application example is structured into distinct sections, each focusing on a specific topic. It starts with an assessment 
on how to manage certificates within TIA Portal, offering a comprehensive overview of the possibilities that TIA Portal 
currently offers. Following this point, the document provides guidelines and examples on how to handle certificates for 
each communication protocol. Finally, a brief introduction to dynamic management of certificates via GDS Push is carried 
out, followed by a section dedicated to frequently asked questions.

---

### 2.3. TIA Portal for certificate management

#### 2.3.1. Overview

###### Certificate management options in TIA Portal

Since TIA Portal version V14 and CPU firmware version V2.0, certificate management for S7-1500 CPUs has been 
available. The table below outlines all certificate management options based on the service used, TIA Portal version, and 
firmware version of S7-1500 PLCs.

| Service | Certificate management with TIA Portal (TIA Portal version / S7-1500 CPU FW-version) | Certificate management with OPC UA GDS push methods (TIA Portal version / S7-1500 CPU FW-version) |
| --- | --- | --- |
| Web server | as of V14 / as of V2.0 | as of V18 / as of V3.0 |
| Secure OUC | as of V14 / as of V2.0 | - |
| OPC UA server | as of V14 / as of V2.0 | as of V17 / as of V2.9 |
| OPC UA client | as of V15.1 / as of V2.6 | - |
| Secure PG/HMI communication | as of V17 / as of V2.9 | - |
| Syslog client | as of V19 / as of V3.1 | - |

Additionally, as of firmware version V4.4, S7-1200 CPUs also support secure communication.

###### Local and global certificate managers

TIA Portal offers different options to manage certificates via the local and global certificate managers.

• Local certificate manager: Each device has its own local certificate manager, where certificates are generated and 
managed for each individual device. These certificates can be used for Secure PG/PC and HMI communication, for the 
OPC UA client, the OPC UA server and for the web server running on the device, as well as for additional 
communication methods that require certificates, such as Secure OUC.

• Global certificate manager: Contains an overview of all the certificates used in a project, including Certification 
Authorities, certificates issued by CAs, self-signed certificates, and third-party certificates that have been imported.

**NOTE** Certificates must always be included in the local certificate manager of a device to be part of the HW 
configuration. Referencing the certificate’s ID in the global certificate manager is not sufficient to 
assign the certificate to a device.

###### Global security settings

Devices in TIA Portal can be configured to operate exclusively with the local certificate manager or utilize both the local 
and global certificate managers through the global security settings.

If the global security settings are disabled in a device, it will only have access to the CPU-specific certificate manager. 
Consequently, its functionality will be limited, as it won't have access to CAs or other certificates imported into the 
project.

Activating the global security settings allows the local certificate manager to access the global certificate manager and 
vice versa. Thus, the device is granted access to the certificate store of the project and to additional functionalities 
covered in section <u>2.3.3</u>.

---

#### 2.3.2. Using the local certificate manager

###### Access to the local certificate manager

The local certificate manager is located within each device.

1. To access the local CPU-specific certificate manager, select the CPU in the project tree and navigate to the "Properties"
tab.

Project tree

Application Example Certificate Management ▶ PLC_**1** [CPU 1516F-3 PN/DP]

Devices Plant objects

PLC_1 [CPU 1516F-3 PN/DP]

Application Example Certificate Manag...
Add new device
Devices & networks
PLC_1 [CPU 1516F-3 PN/DP]
Ungrouped devices
Security settings
Cross-device functions
Common data
Documentation settings
Languages & resources
Version control interface
Online access
Card Reader/USB memory

Print... Ctrl+P
Print preview...
Export CAx data...
Export module labeling strips...
Properties... Alt+Enter

Open in new editor
Open block/PLC data type... F7

Cut Ctrl+X
Copy Ctrl+C
Paste Ctrl+V

Delete Del
Rename **F2**

Go to topology view
Go to network view

2. Select "Protection & Security > Certificate Manager > Certificate Management with TIA Portal". If a device supports 
Secure PG/PC and HMI communication, a certificate is automatically generated to allow this type of communication.

PLC_1 [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Entry page
Overview of interfaces
Display
Multilingual support
Time of day
Protection & Security
Protection of the PLC conf...
Access control
Connection mechanisms
Connection mechanisms
Certificate manager
Certificate manager
Certificate management...
Syslog

Certificate management with TIA Portal

Global security settings
The global security settings for the certificate manager are not enabled.
Only limited functionality is available.
Use global security settings for certificate manager

Device certificates

ID Common name of subject Service Issuer Valid until
1 PLC-1/Communication-1 Communication O=Siemens, C=DE, CN=P... 1/10/2037
<Add new>

###### Functions of the local certificate manager

Due to the limited functionality of the local certificate manager, certificates can only be:

1. Created.

2. Exported (without the private key).

3. Or deleted.

Device certificates

ID Common name of subject Service Issuer Valid until
1 PLC-1/Communication-1 Communication O=Siemens, C=DE, CN=P... 1/10/2037
<Add new>
Export certificate
Delete certificate

These limitations create further restrictions, such as preventing the renewal of certificates during runtime, as detailed in 
section <u>2.3.4</u>.

---

###### Creating new certificates

New certificates can be created in various ways within the local certificate manager.

1. To add new certificates, click on "<Add new>" and "Create". Enter all certificate parameters in the corresponding input 
fields. As highlighted in the snapshot, certificates can only be self-signed, as the local certificate manager has no 
access to Certification Authorities.

Device certificates

ID Common name of subject Service Issuer Valid until
**1** PLC-1/Communication-1 Comm O=Siemens, C=DE, CN=P... 1/10/2037
<Add new>

ID Common name of subject Issuer Valid until
1 PLC-1/Communication-1 O=Siemens, C=DE, CN=P... 1/10/2037

Create certificate

Purpose
Usage: OpcUA Server
Key usage: Non Repudiation, Digital Signature, Key Encipherment,
Data Encipherment, Certificate Sign

Certificate authority (CA)
Select how the new certificate will be signed:
Self signed
Signed by certification authority
CA name: Select

Certificate parameter
Enter the parameters for the new certificate:

Common name of subject: PLC-1/OPCUA-1-2
Encryption method: RSA
Key length: 2048
Hash algorithm: sha256
Valid from: January 10, 2024 04 : 02 : 44 PM
Valid until: January 10, 2037 04 : 00 : 00 PM

Subject Alternative Name (SAN):
Type Value
URI um:SIAMATIC.57:1500.OPCUA.Applica...
IP 192.168.0.1
IP 192.168.1.1
<Add new>

OK Cancel

---

2. In addition, certificates are automatically generated during the activation of both the web and OPC UA servers. Full 
detailed instructions on how to configure these servers is provided in sections <u>2.4.3</u> and <u>2.5.2</u>.

PLC_1 [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

System and clock...
SIMATIC Memory Card
System diagnostics
PLC alarms
Web server
General

Web server _____
General _____

Activate web server on this module
Permit access only with HTIPS
Permit data access only via Web API

PLC_1 [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Syslog
Security event
OPC UA
General
Server
Client
System power supply

Server _____
General _____

Accessibility of the server
Activate OPC UA server

## Device certificates

|  | ID | Common name of subject | Service | Issuer | Valid until |
| --- | --- | --- | --- | --- | --- |
|  | 1 | PLC-1/Communication-1 | Communication | O=Siemens, C=DE, CN=P... | 1/10/2037 |
|  | 2 | PLC-1/OPCUA-1-2 | Not specified | CN=PLC-1/OPCUA-1-2 | 1/10/2037 |
|  | 3 | PLC-1/Webserver-3 | Web server | O=Siemens, C=DE, CN=P... | 1/10/2037 |
|  | 4 | PLC-1/OPCUA-1-4 | OPC UA | O=Siemens, C=DE, CN=P... | 1/10/2037 |
|  |  |  |  |  |  |

---

#### 2.3.3. Using the global certificate manager

###### Description

To access the global certificate manager the project must be protected from unauthorized access, which is done from the 
project’s "Security settings".

In TIA Portal version V14, these settings become visible only after activating the global security settings on at least one 
device. Since TIA Portal version V15, such activation is no longer required, and the project's security settings are always
visible.

###### Access to the security settings of the project (only for TIA Portal version V14)

To access the security settings of a project in TIA Portal V14, these steps must be followed:

1. Select a CPU in the project tree and navigate to the "Properties" tab.

Devices & networks

Application Example Certificate Manag...
Add new device
Devices & networks
PLC_**1** [CPU 1516F-3 PN/DP]
Ungrouped devices
Security settings
Cross-device functions
Common data
Documentation settings
Languages & resources
Version control interface

Open in new editor
Open block/PLC data type... F7

Cut Ctrl+X
Copy Ctrl+C
Paste Ctrl+V

Print... Ctrl+P
Print preview...
Export CAx data...
Export module labeling strips...
Properties... Alt+Enter

2. Select "Protection & Security > Certificate Manager > Certificate management with TIA Portal". Activate the function 
"Use global security settings for certificate manager".

PLC_**1** [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Protection & Security
Protection of the PLC configuration data
Access control
Connection mechanisms
Certificate manager
Certificate manager
Certificate management with TIA Portal
Syslog

Certificate management with TIA Portal

Global security settings
The global security settings for the certificate manager are not enabled.
Only limited functionality is available.
Use global security settings for certificate manager

###### 3. Confirm the following message with the "OK" button.

Certificate manager

The global security certificate manager is enabled, your current certificate configuration will be lost:

- The private keys cannot be restored.
- The certificates can be exported and used in the global security certificate manager.
- You must update your configuration or your program, since your IDs may have changed.

OK Cancel

---

As a result, the security settings of the project become visible.

Project tree

Devices Plant objects

Application Example Certificate Manag...
Add new device
Devices & networks
PLC_1 [CPU 1516F-3 PN/DP]
Ungrouped devices
Security settings
Settings
Users and roles
Cross-device functions
Common data
Documentation settings
Languages & resources
Version control interface
Online access

Application Example Certificate Management > PLC_1 [CPU 1516F-3 PN/DP]

PLC_1 [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Certificate management with TIA Portal ___

Global security settings
The global security settings for the certificate manager are enabled.
Sufficient function rights or project protection are required to work with the certificate manager.
Use global security settings for certificate manager

###### NOTE

Enabling the global security settings deletes the contents of the local certificate manager in the device. 
Therefore, if any type of secure communication is being used, such as Secure PG/PC and HMI 
communication, OPC UA, HTTPS or Secure OUC, new certificates need to be created.

If the global security settings are needed in a device, it is recommended to activate them from the 
beginning to avoid losing previously configured certificates.

###### Access to the global certificate manager

To access the global certificate manager, the project must be protected from unauthorized access.

To create a project administrator:

1. Double-click on the entry "Settings" in the project tree under "Security settings". Click the "Protect this project" button.

Project tree

Devices Plant objects

Application Example Certificate Manag..
Add new device
Devices & networks
PLC_1 [CPU 1516F-3 PN/DP]
Ungrouped devices
Security settings
Settings
Users and roles
Cross-device functions

Management > Security settings > Settings

Project protection

Your project will be protected as soon as you specify a project administrator. Then you must log on to the project. The project protection cannot be removed again.

This setting cannot be undone.
Protect this project

2. Define a username and password. Confirm the password and click on "OK".

Protect project

Define credentials for the project administrator

User name: admin
Password: **********
Confirm password: **********
Comment: Administrator account

OK Cancel

---

**NOTE**

Following this step, the TIA Portal project can only be opened by the project administrator or users 
with the appropriate engineering function rights: "Open the project read-only" or "Open and edit the 
project".

The "Certificate manager" will appear under the project’s "Security settings > Security features".

Security settings
Settings
Users and roles
Security features

Certificate manager
Firewall
VPN groups
NTP
Syslog
RADIUS
Log files (offline view)

Application Example Certificate Management ▶ Security features ▶ Certificate manager

Certificate authority (CA)
Device certificates
Trusted certificates and root cert...

Certificate authority (CA)

ID Common name of subject Serial nu... Issuer Valid to Used as Pri. Signat... Key length
1 Siemens TIA Project - Appli... 2A020E7 ... CN = Siemens TIA Project - App... Thursday, January 8, ... Certification authorit... Yes RSA-S... 2048 Bit
2 Siemens TIA Project - Appli... 379D3EF ... CN = Siemens TIA Project - App... Thursday, January 8, ... Certification authorit... Yes RSA-S... 2048 Bit

**NOTE**

To access the global certificate manager, the project must be protected, and the user must be logged 
in as administrator or have the appropriate engineering function rights: "View security device 
configuration" or "Edit security device configuration".

The engineering function right "Edit security device configuration" is necessary to manage certificates 
in the global certificate manager.

###### Default Certification Authorities

By default, two root certificates (Certification Authorities) are provided in each project:

• The first root certificate with ID=1 employs SHA1 as the hashing algorithm.

• The second Certification Authority with ID=2 is based on SHA-256.

| ID | Common name of subject | Serial nu... | Issuer | Valid to | Used as | Pri... | Signat... | Key length |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Siemens TIA Project - Appli... | 2A020E7... | CN = Siemens TIA Project - App... | Thursday, January 8, ... | Certification authorit... | Yes | RSA-S... | 2048 Bit |
| 2 | Siemens TIA Project - Appli... | 379D3EF... | CN = Siemens TIA Project - App... | Thursday, January 8, ... | Certification authorit... | Yes | RSA-S... | 2048 Bit |

**NOTE**

SHA1 is deprecated and it is no longer considered secure, therefore the first CA should only be used in 
legacy systems that do not support SHA-256.

###### Certificate fields in the global certificate manager

Some of the most relevant fields shown in the global certificate manager are:

| Field | Description |
| --- | --- |
| ID | Unique ID assigned by the global certificate manager when a certificate is created or imported. |
| Common name of subject | Device or certificate authority for which the certificate is valid. |
| Serial number | Unique serial number of the certificate. |
| Issuer | Shows the name, organization, and country of the certificate issuer. |
| Valid to | Indicates when the certificate expires. |
| Used as | Application or service the certificate is used for. |
| Private key | Indicates whether the private key exists in the project. |
| Signature algorithm | Signature algorithm with which the certificate was signed, i.e. the signature algorithm of the issuer. |
| Key length | Shows the key length of the certificate. |

---

###### Functions of the global certificate manager

The global certificate manager contains an overview of all the certificates used in the project, and, in contrast to the local 
certificate manager, incorporates a wide variety of options to manage certificates:

1. Create certificates and Certification Authorities.

2. Import third-party certificates and Certification Authorities.

3. Export certificates, private keys and CRLs.

4. Renew expired certificates and Certification Authorities.

5. Replace existing certificates.

6. Delete certificates.

###### Exporting certificates

Certificates and private keys can be exported in various formats. Depending on the format, the following options are 
available during export.

| Available for selection during export | Format .cer | Format .der | Format .crt | Format .pem | Format .crl | Format .p12 | Note |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Private key | x | x | x | x | - | x | For *.cer and *.der, an additional key file is exported for the certificate. The exported key file cannot be imported again. For *.crt and *.pem, the private key is stored together with the certificate in a file. |
| Encrypted private key | - | - | x | x | - | - | Selection of the encryption method and input of password possible. If no password is entered, the project name is used as the password. |
| Certificate chain | - | - | x | x | - | - | Only possible if the certificates of the certificate chain are stored in the certificate manager. |
| Password only | - | - | - | - | - | x | If no password is entered, the project name is used as password. |
| Revocation list | - | - | - | - | x | - | Certain programs, such as UaExpert, require revocation lists to establish trust relationships with root CAs. TIA Portal can only export empty revocation lists. |

---

###### Creating and renewing certificates

Establishing new secure communication channels involves the creation of digital certificates. Once created, long-term 
maintenance is achieved through the renewal of these certificates, allowing users to update the validity period of expired 
certificates or adjust the encryption and hashing algorithms if they become deprecated.

The creation and renewal of certificates is done through the pop-up window depicted below. To configure new 
certificates, the following steps must be carried out.

1. Specify the intended purpose of the certificate by choosing a predefined template i.e. OPC UA Client/Server, TLS 
Client/Server, CA, etc. Depending on the intended use, specific "KeyUsage" and "ExtendedKeyUsage" extensions will be 
included in the certificate.

2. Select the issuer of the new certificate: self-signed or signed by a Certification Authority.

3. If the certificate is issued by a Certification Authority, select it with the "Select" button. Only Certification Authorities 
from the certificate store of the current project, equipped with a private key, can be selected.

4. Depending on the certificate, enter the following parameters in the corresponding input fields:

-Common name of subject: name associated with the certificate holder.

-Encryption method: cipher algorithm used to perform asymmetric encryption.

-Key length: depends on the cipher. For RSA, it represents the key length in bits, while for EC, it corresponds to the 
ECC curve (e.g., prime256v1, secp256r1, secp384r1).

-Hash algorithm: algorithm used for signing the certificate.

-Valid from/until: validity period of the certificate.

-Subject Alternative Name (SAN): additional host names, IP addresses, email addresses, and other identifiers 
associated with the certificate subject beyond the common name.

---

Purpose

Usage: OpcUa Server

Key usage: Non Repudiation, Digital Signature, Key Encipherment,
Data Encipherment

Certificate authority (CA)

Select how the new certificate will be signed:

Self signed
Signed by certification authority

CA name: (ID = 2) Siemens TIA Project - Application Example Certificate M Select

Certificate parameter

Enter the parameters for the new certificate:

Common name of subject: PLC-1/OPCUA-1-4

Encryption method: RSA

Key length: 2048

Hash algorithm: sha256

Valid from: January 15 , 2024 10 : 44 : 47 AM

Valid until: January 14 , 2037 04 : 00 : 00 PM

Subject Alternative Name (SAN):

Type Value
URI um:SIMATIC.S7-1500.OPC-UA.Applica...
IP 192.168.0.1
IP 192.168.1.1
<Add new>

###### NOTE

When certificates are created from the global certificate manager, a blank canvas is provided. In 
contrast, generating certificates directly from the CPU offers templates that streamline the creation 
process.

Creating a certificate from the CPU does not restrict the certificate to the local certificate manager of 
the device. With the global security settings activated, these certificates can also be accessed and 
managed through the global certificate manager.

###### Replacing certificates

The process of replacing certificates enables the substitution of an existing certificate with a new ".p12" certificate while
preserving its unique ID within the global certificate manager. The benefits of replacing certificates instead of importing 
or creating new ones is explained in section <u>2.3.4</u>.

---

Replace certificate

New certificate
C:\Users\Siemens\Desktop\Application Example\PLC-1OPCUA-1-4_X.509 Certificate_3.p12 Browse...

Information
Common name of sub... PLC-1/OPCUA-1-4
Private key: Yes
Issuer: Siemens TIA Project - Application Example Cer
Valid from: 1/15/2024
Valid until: 1/14/2037

Certificates involved
ID Common name of su... Issuer Valid to Used as Private key

###### Assigning certificates

When integrating other devices, such as communication processors (CPs), certificates need to be assigned through the 
global certificate manager. The dialog "Assign certificate with ID x to device" provides the mechanism to transfer
certificates to these processors.

As shown in the manual (https://support.industry.siemens.com/cs/document/103948898) of the CP 1243-1, to assign 
trusted partner certificates to the CP via the global certificate manager:

1. Right-click on the desired certificate.

2. Select "Assign" in the shortcut menu.

3. Mark the device/module in the subsequent dialog.

4. Define how the certificate is to be assigned in the "Used as" field:

-Device certificate (not specified): e.g., for Secure OUC or blocks.

-Device certificate: Web server certificate.

-Trusted certificate: e.g., OPC UA.

Assign certificate with ID 2 to device

Name Used as
CP 1243-1 (PLC_2)
Not assigned
Device certificate (not specified)
Device certificate
Trusted certificate

Only one certificate per device can be used as a "Device certificate". Once assigned, the certificate appears in the 
"Certificates of the partner devices" table in the local certificate manager of the communication processor.

---

#### 2.3.4. Loading certificates to the CPU during runtime

Downloading certificates to the CPU is part of the hardware configuration. Traditionally, applying changes to the 
hardware configuration required the CPU to be in STOP, which, in turn, could disrupt production processes. However, 
with TIA V19 and FW 3.1, new certificates can be loaded to S7-1500 CPUs (incl. R/H) during runtime.

To do so, certificates need to have the same certificate ID in the global certificate manager as the ones already loaded on 
the device. The only available means to generate new certificates with matching IDs are the "Renew" and "Replace"
methods.

Certificate authority (CA)

ID Common name of subject Serial number I... Valid to ... Priv... Signat... Key length

1 Siemens TIA Project - Appli... 7E619EC155BBE058 ... Friday, February 13, 2037 ... Yes RSA-S... 2048 Bit

2 ▼ Siemens TIA Project - Appli... 7EB249AFD22DB7 ... Friday, February 13, 2037 ... Yes RSA-S... 2048 Bit

4 PLC-1/OPCUA-1-4 6B39D9B29514C186 ... Thursday, February 12, 2037 ... Yes RSA-S... 2048 Bit

Certificate authority (CA)

ID Common name of subject Serial number I... Valid to ... Priv... Signat... Key length

1 Siemens TIA Project - Appli... 7E619EC155BBE058 ... Friday, February 13, 2037 ... Yes RSA-S... 2048 Bit

2 ▼ Siemens TIA Project - Appli... 7EB249AFD22DB7 ... Friday, February 13, 2037 ... Yes RSA-S... 2048 Bit

4 PLC-1/OPCUA-1-4-Rene... 449DE59DEB9F47C6 ... Friday, March 20, 2026 ... Yes RSA-S... 2048 Bit

Certificate authority (CA)

ID Common name of subject Serial number I... Valid to ... Priv... Signat... Key length

1 Siemens TIA Project - Appli... 7E619EC155BBE058 ... Friday, February 13, 2037 ... Yes RSA-S... 2048 Bit

2 Siemens TIA Project - Appli... 7EB249AFD22DB7 ... Friday, February 13, 2037 ... Yes RSA-S... 2048 Bit

3 Siemens TIA Project - Applic... 11106E72F5425875 ... Thursday, February 12, 2037 ... Yes ecdsa... 256 Bit

7 ▼ Siemens TIA Project - Proje... 73B7D5B3DD5642CB ... Saturday, January 31, 2037 ... No RSA-S... None

4 New OPC UA cert 1D5E3CAE84B909B6 ... Monday, February 4, 2030 ... Yes RSA-S... 2048 Bit

###### NOTE

To load certificates during runtime with the administrator account created during project protection, 
users need to sign out and log back in again. This step is only necessary the first time this user is 
created.

---

### 2.4. Certificates in the scope of OPC UA communication

#### 2.4.1. Overview on OPC UA communication

###### Description

OPC UA communication follows a client-server approach, where servers provide services, such as read, write, browse or
subscribe, enabling clients to access data stored in the AddressSpace.

For a secure connection to be established, the client must validate and accept the digital certificate presented by the 
server, deeming it trustworthy. Simultaneously, the server is required to verify the certificate provided by the client, 
ensuring mutual authentication.

Once the secure connection is established via the Public Key Infrastructure, a shared key is transmitted, and 
communication transitions to symmetric encryption.

###### OPC UA certificates

OPC UA certificates adhere to the format specified by X-509 V3 of the ITU (International Telecommunication Union) for 
the authentication of client and server.

When a connection is being established between both entities, the devices check all information from the certificate that 
is required to determine its integrity, such as signature, period of validity, application name (URI) and, in case of firmware 
version V2.5 (S7-1500 CPU), also the IP address of the client.

#### 2.4.2. Security settings for the OPC UA server

###### Security policies and security modes

Server endpoints can be configured with different "SecurityModes" and "SecurityPolicies".

| SecurityMode | Description |
| --- | --- |
| None | No security is applied, and communication is in plaintext. |
| Sign | Sign security mode ensures message integrity by adding a digital signature to each message. The signature is generated using the private key of the sender and can be verified using the sender's public key. This ensures that the message has not been tampered with during transmission. |
| Sign &amp; Encrypt | Sign&amp;Encrypt security mode provides both message integrity and confidentiality. In addition to generating a digital signature, this mode encrypts the message to prevent unauthorized access or reading of the message content. The encryption is performed using a shared secret key that is negotiated between the sender and receiver during the communication process. |

| SecurityPolicy | Description |
| --- | --- |
| None | No security is applied, and communication is in plaintext. |
| Basic128Rsa15 (deprecated) | RSA-15 for asymmetric encryption, AES128 for symmetric encryption, and SHA-1 for message signing. |
| Basic256 (deprecated) | RSA-OAEP for asymmetric encryption, AES256 for symmetric encryption, and SHA-1 for message signing. |
| Basic256Sha256 | RSA-OAEP for asymmetric encryption, AES256 for symmetric encryption, and SHA-256 for message signing. |
| Aes128Sha256RsaOaep | RSA-OAEP for asymmetric encryption, AES128 for symmetric encryption, and SHA-256 for message signing. |
| Aes256Sha256RsaPss | RSA-OAEP for asymmetric encryption (with RSA-PSS-SHA2-256 as the asymmetric signature algorithm), AES256 for symmetric encryption, and SHA-256 for message signing. |

###### User authentication

In addition to transport layer security, OPC UA can use application-based security to control access to the server, known as 
user authentication. This mechanism is performed each time a new session is activated.

During this process, the client is given a "userIdentityToken", which allows the server to determine if the token is 
authorized to establish a connection. This introduces an additional layer of security, as not only the client certificate needs 
to be trusted, but also a valid token must be provided for user authentication.

---

There are several types of user authentication methods in OPC UA, including:

| Token | Description |
| --- | --- |
| Anonymous | Clients can connect to the server without providing any user identity. |
| Username and password | Clients provide a username and password, which are authenticated by the server. |
| X-509 Certificate | Clients provide a digital certificate that is validated by the server. |

**NOTE** Since S7-1200 and S7-1500 CPUs do not support user authentication through X-509 certificates, no 
additional information is provided in this document.

#### 2.4.3. Setting up the OPC UA server

###### Commissioning an OPC UA server

For security reasons, the OPC UA server is not enabled by default. To activate the server of the CPU, proceed as follows:

1. Select the CPU in the project tree and navigate to the "Properties" tab.

| Project tree Application Example Certificate Management PLC_1 [CPU 1516F-3 PN/DP] |  |
| --- | --- |
| Devices Plant objects PLC_1 [CPU 1516F-3 PN/DP] |  |
| Application Example Certificate Manag... Add new device Devices &amp; networks PLC_1 [CPU 1516F-3 PN/DP] |  |
| Ungrouped devices Security settings Cross-device functions Common data Documentation settings Languages &amp; resources Version control interface Online access Card Reader/USB memory | Open in new editor Open block/PLC data type... F7 Ctrl+X Copy Paste Delete Rename Go to topology view Go to network view |

2

---

2. Navigate to the entry "OPC UA > Server". Activate the server and confirm the security message.

PLC_**1** [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Certificate management...
▶ Syslog
Security event
▶ OPC UA
General
▶ Server
General
Options

Server _____
General _____

Accessibility of the server
Activate OPC UA server

Activating the OPC UA server reduces the protection against unauthorized internal or external access to functions and data of this CPU.

3. In "OPC UA > Server > Security > Secure channel", select the server endpoints that will be available for OPC UA clients 
(security policies and modes).

| General | IO tags | System constants | Texts |
| --- | --- | --- | --- |
| OPC UA |  | Security policies available on the server for the secure channel: |  |
| General |  | Security policies available on the server for the secure channel: |  |
| Server |  | Security policies available on the server for the secure channel: |  |
| General |  | Security policies available on the server for the secure channel: |  |
| Options |  | Security policies available on the server for the secure channel: |  |
| Security |  | Security policies available on the server for the secure channel: |  |
| Secure channel |  | Security policies available on the server for the secure channel: |  |
| Certificates |  | Security policies available on the server for the secure channel: |  |
| Diagnostics |  | Security policies available on the server for the secure channel: |  |
| Export |  | Security policies available on the server for the secure channel: |  |
| Client |  | Security policies available on the server for the secure channel: |  |
| System power supply |  | Security policies available on the server for the secure channel: |  |
| Advanced configuration |  | Security policies available on the server for the secure channel: |  |
| Connection resources |  | Security policies available on the server for the secure channel: |  |
| Overview of addresses |  | Security policies available on the server for the secure channel: |  |
| Runtime licenses |  | Security policies available on the server for the secure channel: |  |

---

4. By default, a server certificate is automatically generated in the local certificate manager of the device. As it can be 
seen <u>here</u>, the certificate is issued by TIA Portal’s Certification Authority (ID=2).

PLC_1 [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

OPC UA
General
Server
General
Options
Security
Secure channel
Certificates
Diagnostics
Export
Client

Certificates

Use certificates configured and downloaded using TIA Portal

Server certificate

The global security settings for the certificate manager are enabled.
Sufficient function rights or project protection are required to work with the certifi...
The server certificate is used to verify the servers identity when it is accessed and to en...

Server certificate: PLC-1/OPCUA-1-5 ...

5. Beneath the server’s certificate is the store for trusted OPC UA clients. To restrict connections exclusively to trusted 
clients, the "Automatically accept client certificates during runtime" checkbox must be disabled.

| Server |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
| General |  |  | ID | Common name of subject | Issuer | Valid until |
| Options |  |  |  |  |  |  |
| Security |  |  |  |  |  |  |
| Secure channel |  |  |  |  |  |  |
| Certificates |  |  |  |  |  |  |
| Diagnostics |  |  |  |  |  |  |
| Export |  |  |  |  |  |  |
| Client |  |  |  |  |  |  |
| System power supply |  |  |  |  |  |  |
| Advanced configuration |  |  |  |  |  |  |
| Connection resources |  |  |  |  |  |  |
| Overview of addresses |  |  |  |  |  |  |
| Runtime licenses |  |  |  |  |  |  |
| OPC UA |  |  |  |  |  |  |
| ProDiag |  |  |  |  |  |  |
| Energy Suite |  |  |  |  |  |  |
| MAC |  |  |  |  |  |  |

6. Select the area "Runtime licenses" in the CPU properties and set the purchased runtime license for the OPC UA server 
in the selection list "Type of purchased license".

PLC_**1** [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Options
Security
Secure channel
Certificates
Diagnostics
Export
Client
System power supply
Advanced configuration
Connection resources
Overview of addresses
Runtime licenses
OPC UA
ProDiag
Energy Suite
MAC

OPC UA
Runtime licenses
Type of required license: SIMATIC OPC UA S7-1500 medium
Type of purchased license: SIMATIC OPC UA S7-1500 medium
No license
SIMATIC OPC UA S7-1500 small
SIMATIC OPC UA S7-1500 medium
SIMATIC OPC UA S7-1500 large

---

###### User authentication

As of TIA Portal version V19 and firmware version FW 3.1, S7-1500 CPUs use a new and improved approach to manage 
users, roles and PLC function rights known as "User Management & Access Control" (UMAC).

From the versions mentioned above onwards, project users and their respective rights for all PLCs in the project can be
managed from the "Users and roles" tab in the "Security Settings" of the TIA Portal project. To create a new user with "OPC 
UA server access":

1. Double click on "Security settings > Users and roles" and open the "Roles" tab. Click on "<Add new role>".

| Project tree | ...ication Example Certificate Management Security settings Users and roles |  |  |  |
| --- | --- | --- | --- | --- |
| Devices Plant objects | Users User groups Roles |  |  |  |
|  | Roles |  |  |  |
| Application Example Certificate Manage Name Description Runtime timeout Add new device Engineering administrator System-defined role "Engine... 30 Min Engering administrator" Engineering standard System-defined role "Engine... 30 Min Engineering standard role HMI Administrator System-defined role "HMI Ad... 30 Min User management, Monit... HMI Operator System-defined role "HMI Op... 30 Min Web access, Operate, HMI ... HMI Monitor System-defined role "HMI Mo... 30 Min Web access, Monitor, HMI ... HMI Monitor Client System-defined role "HMI Mo... 30 Min WinCC Unified Client Moni... HMI Online Configuration E... System-defined role "HMI On... 30 Min Operate HMI, read and wri... NET Administrator System-defined role "NETAd... 30 Min NetStandard System-defined role "NETSta... 30 Min Firewall NETDiagnose Syste"e "NETDia... 30 Min VPN groups |  |  |  |  |

360

2. Create a new role for an OPC UA user. Select "OPC UA server access" from the "Runtime rights" of the target CPU.

| Engineering rights | Runtime rights | User-specific runtime rights |
| --- | --- | --- |
| Function rights c... | Function rights |  |
| Runtime rights | Name | Group |
| S7-1500 V3.1 (fail... | HMI access | Access level |
| PLC_1 | Read access | Access level |
|  | Full access | Access level |
|  | Full access including fail-safe | Access level |
|  | OPC UA server access | OPC UA |
|  | User authentication of the OPC UA... | OPC UA |
|  | Manage certificates | OPC UA |

---

3. "Add a new local user" and assign it a username and password. Select the role with OPC UA server access.

Project tree
Devices Plant objects

Application Example Certificate Management Security settings Users and roles

Users User groups Roles

Application Example Certificate Manage...
Add new device
Devices & networks
PLC_**1** [CPU 1516F-3 PN/DP]
Ungrouped devices
Security settings
Settings
Users and roles

User name Password Runtime timeout UM domain ID
Anonymous
admin ********* 30 Min
<Add new user>
Add new local user
Add global user

**2**
User **30** Min
<Add new user>
Password:
Confirm password:

Assigned user groups Assigned roles Assigned rights

Assigned roles
Assigned to Name Description Runtime timeout Comment
NET Diagnose System-defined role "NET Diagnos... 30 Min
opcua user User-defined role 30 Min

###### NOTE

While not advisable, it is possible to implement anonymous user authentication by activating the 
default "Anonymous" user and assigning it the recently created "opcua user" role.

Refer to the application example "User Management & Access Control with TIA Portal V19"
(https://support.industry.siemens.com/cs/ww/en/view/109973173) for further details on this topic.

###### View the server certificate

In section <u>2.3.3</u>, the CPU was configured with the global security settings. As a result, the server’s certificate can be 
managed through the global certificate manager, where it can be found under the "Certificate authority (CA)" and "Device 
certificates" tabs.

| ID | Common name of subject | Serial nu... | Issuer | Valid to | Used as | Pri... | Signat... | Key length |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Siemens TIA Project - Appliance | OD51425... | CN = Siemens TIA Project - Appliance | Saturday, January 10... | Certification authorit... | Yes | RSA-S... | 2048 Bit |
| 2 | Siemens TIA Project - Appliance | 73AE492... | CN = Siemens TIA Project - Appliance | Saturday, January 10... | Certification authorit... | Yes | RSA-S... | 2048 Bit |
| 5 | PLC-1/OPCUA-1-5 | 1E4E100... | CN = Siemens TIA Project - Appliance | Thursday, January 15... | Not assigned | Yes | RSA-S... | 2048 Bit |
| 3 | Siemens TIA Project - Applicants | 7CBD92D... | CN = Siemens TIA Project - Applicants | Thursday, January 15... | Certification authorit... | Yes | ecds... | 256 Bit |
| 4 | PLC-1/Communication-4 | 55CE5E4... | CN = Siemens TIA Project - Applicants | Thursday, January 15... | Not assigned | Yes | ecds... | 256 Bit |

| ID | Common name of subject | Serial nu... | Issuer | Serial number issuer | Valid to | Used as | Pri... | ... |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4 | PLC-1/Communication-4 | 55CE5E4... | CN = Siemens TIA Project- Appl... | 7CBD92DA30F69210 | Thursday, Janua... | Not assigned | Yes | ... |
| 5 | PLC-1/OPCUA-1-5 | 1E4E100... | CN = Siemens TIA Project- App... | 73AE492F6B853818 | Thursday, Janua... | Not assigned | Yes | ... |

---

###### Create a new server certificate

If the server certificate was deleted due to the activation of the global security settings, proceed as follows to generate a 
new certificate for the server:

1. Select the entry "OPC UA > Server > Security > Certificates" in the navigation area.

2. To generate a new server certificate or substitute it with an existing one, click on the button integrated in the "Server 
certificate" drop-down list.

PLC_**1** [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

OPC UA
General
Server
General
Options
Security
Secure channel
Certificates
Diagnostics
Export
Client

Certificates

Use certificates configured and downloaded using TIA Portal

Server certificate

The global security settings for the certificate manager are enabled.
Sufficient function rights or project protection are required to work with
certifi...
The server certificate is used to verify the servers identity when it is
to en...

Server certificate: PLC-1/OPCUA-1-5

3. A dialog appears with all available server certificates. To create a new certificate, click on the "Create" button.

| ID | Common name of subject | Issuer | Valid until |
| --- | --- | --- | --- |
| 4 | PLC-1/Communication-4 | O=Siemens, C=DE, CN=S... | 1/15/2037 |
| 5 | PLC-1/OPCUA-1-5 | O=Siemens, C=DE, CN=S... | 1/15/2037 |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
| < | ✗ |  | > |

1

---

4. The dialog "Create certificate" appears. Automatically, the certificate is assigned with the necessary Key Usage and 
Subject Alternative Name (SAN) fields.

Create certificate

Purpose
Usage: OpcUa Server
Key usage: Non Repudiation, Digital Signature, Key Encipherment,
Data Encipherment

Certificate authority (CA)
Select how the new certificate will be signed:
○ Self signed
○ Signed by certification authority
CA name: (ID = 2) Siemens TIA Project - Application Example Certificate M Select

Certificate parameter
Enter the parameters for the new certificate:

Common name of subject: PLC-1/OPCUA Server
Encryption method: RSA
Key length: 2048
Hash algorithm: sha256
Valid from: January 16, 2024 10 : 30 : 37 AM
Valid until: January 15, 2037 04 : 00 : 00 PM

Subject Alternative Name (SAN):
Type Value
URI um:SIMATIC.S7-1500.OPC-UA.Applica...
IP 192.168.0.1
IP 192.168.1.1
<Add new>

OK Cancel

5. If the certificate is issued by a Certificate Authority, the CA’s certificate can be transferred to the "Trusted clients" store 
within the CPU's OPC UA settings. This establishes a trust relationship with the CA, allowing the CPU to trust all OPC UA 
clients whose certificates are signed by the same Certification Authority.

###### NOTE

When a certificate is created via the local certificate manager, the Certificate Authority will be added to 
the "Certificates of the partner devices" during compilation, automatically establishing a trust
relationship with the CA.

If the certificate is created via the global certificate manager, the Certificate Authority will be added to 
the "Certificates of the partner devices" when importing the certificate to the "Device certificates" store 
in the CPU.

PLC_1 [CPU 1516-3 PN/DP]

General IO tags System constants Texts

Protection & Security
Protection of the PLC config...
Access control
Connection mechanisms
Connection mechanisms
Certificate manager
Certificate manager
Certificate management ...
Syslog
Syslog server

Certificates of the partner devices
Note: The certificates of the partners may be needed to prove yo...

ID Common name of subject Issuer Valid until
3 Siemens TIA Project-Project1-(EC)-3 O=Siemens, C=DE, CN=S... 2/12/2037
2 Siemens TIA Project-Project1 O=Siemens, C=DE, CN=S... 2/12/2037

---

#### 2.4.4. Setting up the OPC UA client

###### Description

In this application example, UaExpert is used as an OPC UA client. Developed by Unified Automation, UaExpert is an easyto-use, out-of-the-box software application that acts as a client and allows users to access and test connections with OPC 
UA servers.

###### Creation of the client’s certificate

By default, UaExpert generates its own self-signed certificate. To ensure a secure connection with the OPC UA server, it is 
necessary to import this certificate into the local certificate manager of the CPU, designating it as a trusted device 
certificate.

However, to take advantage of the trust chain associated with Certificate Authorities, a new certificate for UaExpert will 
be generated using the default CA from TIA’s certificate manager (ID=2), thereby granting it direct trust and access.

To create the client’s certificate:

1. Open the global certificate manager. Right-click on an empty row and select "Create" from the context menu.

Project tree

Application Example Certificate Manager ▶ Security features ▶ Certificate manager

Devices Plant objects

Certificate authority (CA) Device certificates Trusted certificates and root cert...

Trusted certificates and root certification authorities

ID Common name of subject Serial nu... Issuer Valid to Used as

Create Import

Application Example Certifica...
Add new device
Devices & networks
PLC_**1** [CPU 1516F-3 PN/...
Ungrouped devices
Security settings
Settings
Users and roles
Security features
Certificate manager
Firewall

2. In the "Create certificate" window, insert all fields needed to generate the certificate. The Subject Alternative Name 
fields for this certificate are:

-URI: urn: [PC_Hostname]:UnifiedAutomation:UaExpert

-IP: [IP Address of the client]

-DNS: [PC_Hostname]

###### NOTE

To establish a secure connection with OPC UA servers running on SIMATIC PLCs, client certificates only 
require the URI field. For S7-1500 PLCs operating on FW version 2.5, the IP field is also mandatory. 
While some fields are optional, it is advisable to include all fields, as other communication partners 
may consider them compulsory.

---

Create certificate

Purpose
Usage: OpcUa Client
Key usage: Non Repudiation, Digital Signature, Key Encipherment,
Data Encipherment

Certificate authority (CA)
Select how the new certificate will be signed:
○ Self signed
● Signed by certification authority
CA name: (ID = 2) Siemens TIA Project - Application Example Certificate M Select

Certificate parameter
Enter the parameters for the new certificate:

Common name of subject: UaExpert/OPCUA Client
Encryption method: RSA
Key length: 2048
Hash algorithm: sha256
Valid from: January 16, 2024 04 : 46 : 13 PM
Valid until: January 15, 2037 04 : 00 : 00 PM

Subject Alternative Name (SAN): Type Value
URI urn:desktop-f0efhml:UnifiedAutomati...
IP 127.0.0.1
DNS desktop-f0efhml
<Add new>

OK Cancel

3. Upon generating the client's certificate, export it in ".der" format with the name "uaexpert.der", excluding the private 
key in the export.
[Image: Image340]

| Certificate authority (CA) |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
| ID | Common name of subject | Serial nu... | Issuer | Valid to | Used as |
| 1 | Siemens TIA Project - Appli... | OD51425... | CN = Siemens TIA Project - App... | Saturday, January 10... | Certification authorit... |
| 2 | Siemens TIA Project - Appli... | 73AE492... | CN = Siemens TIA Project - App... | Saturday, January 10... | Certification authorit... |
| 5 | PLC-1/OPCUA-1-5 | 29586C4... | CN = Siemens TIA Project - App... | Thursday, January 15... | Not assigned |
| 6 | PLC-1/OPCUA Server | 0F13231... | CN = Siemens TIA Project - App... | Friday, January 16, 2... | Not assigned |
| 7 | UaExpert/OPCUA Client | 70000020 | CN = Siemens TIA Project - App... | Thursday, January 15... | Not assigned |
| 3 | Siemens TIA Project - Applic... | Delete Export Assign Show Renew Replace | N = Siemens TIA Project - Appl... | Thursday, January 15... | Certification authorit... |

Save As
Siemens > Desktop >
Search Desktop
Organize New folder
Certificates Name Date modified Type
File name: uaexpert
Save as type: Certificate - DER coded (unencrypted folder)
Hide Folders Save Cancel

---

4. To sign and decrypt messages, the client also requires the matching private key. Repeat the steps to export the private 
key in ".pem" format. Save the key without encryption and rename it as "uaexpert_key.pem" to ensure that UaExpert 
can read it.

Certificate authority (CA)
ID Common name of subject Serial nu... Issuer Valid to Used as
**1** Siemens TIA Project - Appli... 0D51425... CN = Siemens TIA Project - App... Saturday, January 10... Certification authorit...
2 Siemens TIA Project - Appli... 73AE492... CN = Siemens TIA Project - App... Saturday, January 10... Certification authorit...
5 PLC-1/OPCUA-1-5 29586C4... CN = Siemens TIA Project - App... Thursday, January 15... Not assigned
6 PLC-1/OPCUA Server 0F13231... CN = Siemens TIA Project - App... Friday, January 16, 2... Not assigned
7 UaExpert/OPCUA Client 20FEE2D... CN = Siemens TIA Project - App... Thursday, January 15... Not assigned
3 Siemens TIA Project - Applic... N = Siemens TIA Project - Appl... Thursday, January 15... Certification authorit...

1 Delete Export Assign Show Renew Replace

Save As
Siemens > Desktop >
Organize New folder
Certificates Name Date modified Type
File name uaexpert_key
Save as type Certificate - PEM coded (*.pem)

Hide Folders
Save Cancel

Export certificate
Include certificate chain
Include private key
Encryption for private key None
Enter a password for the private key. If you do not enter a password, the project name will be used as the password.
Password: [ ] Enter check
Confirm password: [ ] Export Cancel

5. Navigate to the "Settings" tab in UaExpert and select "Manage certificates…". A pop-up window appears, presenting 
UaExpert's own certificate, along with trusted certificates, issuer certificates, and more.

Unified Automation UaExpert - The OPC Unified Architecture Client - NewProject
File View Server Document Settings Help
Plugins...
Configure UaExpert...
Manage Certificates...
Access View
Server Node Id

Project
Project
Servers
Documents
Data Access View

Address Space

Manage Certificates

Default Certificate Store GDS Server **1**

Default certificate store with self-signed certificate

Trusted Issuers TLS Issuers

Certificates

Status Name Valid From Valid To Organization
Own Certificate UaExpert@OPCUAClient 16/01/2024... 14/01/20... Unified Aut...

Certificate Revocation Lists

Number Valid From Next Update Organization OrganizationUnit Locality State Cour

Copy Application Certificate To... Create new Application Certificate... Open Certificate Location

OK

---

6. Navigate to the "own > certs" folder and copy the newly created certificate. Repeat the process with the private key in 
the folder "own > private".

certs

File Home Share View
Siemens AppData Roaming unifiedautomation uaexpert PKI own certs Search certs
Downloads
Documents
Pictures
Name Date modified Type Size
uaexpert 16/01/2024 17:06 Security Certificate 2 KB

private

File Home Share View
Siemens AppData Roaming unifiedautomation uaexpert PKI own private Search privat
Downloads
Documents
Pictures
Name Date modified Type Size
uaexpert_key 16/01/2024 17:07 Privacy Enhanced ... 4 KB

**NOTE** Private keys must always remain secret. They should only be exported if necessary and protected with 
a password.

###### Import the CA’s certificate to UaExpert

Finally, to establish a trust relationship with the CA, and all certificates issued by it, import the root certificate into 
UaExpert as a trusted partner.

1. Open the global certificate manager. Export the Certification Authority with ID=2 as a .der file. Do not include the 
private key in the export.

Certificate authority (CA)
ID Common name of subject Serial nu... Issuer Valid to Used as
**1** Siemens TIA Project - Appli... 0D51425... CN = Siemens TIA Project - App... Saturday, January 10... Certification authorit...
2 ▶ Siemens TIA Project - Appli... 73A5402... CN = Siemens TIA Project - App... Saturday, January 10... Certification authorit...
3 ▶ Siemens TIA Project - Applic... Export = Siemens TIA Project - Appl... Thursday, January 15... Certification authorit...
Assign Show Renew Replace

Save As
Siemens > Desktop >
Search Desktop
Organize New folder
Certificates Name Date modified Type
File name: Certification Authority ID 2
Save as type: Certificate - DER coded (unencrypted) (*.der)
Hide Folders Save Cancel

---

###### 2. Export the revocation list of the CA.

Certificate authority (CA)

ID Common name of subject Serial nu... Issuer Valid to Used as
**1** Siemens TIA Project - Appli... OD51425... CN = Siemens TIA Project - App... Saturday, January 10... Certification authorit...
2 ▶ Siemens TIA Project - Appli... Z7345492 CN = Siemens TIA Project - App... Saturday, January 10... Certification authorit...
3 ▶ Siemens TIA Project - Applic... Export = Siemens TIA Project-Appl... Thursday, January 15... Certification authorit...

Save As
Siemens > Desktop >
Search Desktop
Organize New folder
Certificates Name Date modified Type
File name: Revocation List ID 2.crl
Save as type: Certificate - Certificate revocation list, DER coded (unencrypted) (*.crl)
Hide Folders Save Cancel

3. Navigate to the "trusted > certs" folder and copy the root certificate. Repeat the process with the revocation list in the 
folder "trusted > crl".

certs

File Home Share View
Siemens AppData Roaming unifiedautomation uaexpert PKI trusted certs Search certs

Quick access
Desktop

Name Certification Authority ID 2.der Type Size Security Certificate 2 KB

crl

File Home Share View
Siemens AppData Roaming unifiedautomation uaexpert PKI trusted crl Search crl

Quick access
Desktop

Name Revocation List ID 2.crl Type X.509 Certificate R... 1 KB

---

#### 2.4.5. Testing a secure OPC UA connection

After configuring the OPC UA server and client, follow these steps to establish a connection between UaExpert and the 
CPU.

1. Click the "Add Server" button and then double-click on "<Double click to Add Server…>". A dialog will appear to input 
the OPC UA server's URL running on the CPU.

File View Server Document Settings Help

Project
Project
Service
Document
Data A
View

Add Server
Configuration Name
PKI Store Default
Discovery Advanced
Endpoint Filter: No Filter
Local
ServersOnNetwork
Global Discovery Server
< Double click to Add GDS Server... >
Custom Discovery
< Double click to Add Server... >
Reverse Discovery
< Double click to Add Reverse Discovery... >
Recently Used

Authentication Settings
Anonymous
Username Store
Password
Certificate
Private Key

Connect Automatically
OK Cancel

Enter URL
Enter the URL of a computer with discovery service running:
opc.tcp://192.168.0.1:4840
OK Cancel

2. The server endpoints will be displayed under the "Custom Discovery" section. Double-click on the endpoint with the 
security modes and policies configured earlier. This will add the server to the project.

Local
ServersOnNetwork
Global Discovery Server
< Double click to Add GDS Server... >
Custom Discovery
< Double click to Add Server... >
opc.tcp://192.168.0.1:4840
SIMATIC.S7-1500.OPC-UA.Application:PLC_1 (opc.tcp://192.168.0.1:4840)
Basic256Sha256 - Sign & Encrypt (uatcp-uasc-uabinary)
Reverse Discovery
< Double click to Add Reverse Discovery... >
Recently Used

Unified Automation UaExpert - The OPC Unified Architecture Client - NewProject*
File View Server Document Settings Help

Project
Project
Servers
SIMATIC.S7-1500.OPC-UA.Application:PLC_1@192.168.0.1
Documents
Data Access View

---

3. Select the OPC UA server and click on the "Connect server" button. Insert the username and password to perform the 
user authentication.

Unified Automation UaExpert - The OPC Unified Architecture Client - NewProject*
File View Server Document Settings Help

Project
Project
Servers
SIMATIC.S7-1500.OPC-UA.Application:PLC_1@192.168.0.1
Documents
Data Access View

Data Access View
# Server Node Id

Enter user credentials
Please enter the user credentials for the connection to the server 'SIMATIC.S7-1500.OPC-UA.Application:PLC_1@192.168.0.1'
Username: User
Password:
OK Cancel

As a result, the OPC UA client establishes a connection with the server, enabling secure access to the information stored 
within its AddressSpace.

Unified Automation UaExpert - The OPC Unified Architecture Client - NewProject*
File View Server Document Settings Help

Project
Project
Servers
SIMATIC.S7-1500.OPC-UA.Application:PLC_1@192.168.0.1
Documents
Data Access View

Address Space
No Highlight
Root
Objects
DeviceSet
PLC_1
Server
Types
Views

---

#### 2.4.6. OPC UA certificate handling scenarios–where does each certificate belong?

###### Description

This chapter concludes with a set of diagrams designed to highlight the correct handling of certificates and private keys in 
diverse OPC UA communication scenarios. These illustrations should be used as a guide to understand when and where 
certificates, private keys, and revocation lists should be exported.

###### Scenario 1

Using the default Certification Authorities in the global certificate manager to issue certificates for servers (CPU) and 
clients (external application).

##### Workflow

TIA Portal Project

* TIA's Certificate Manager can only export empty revocation lists from the Certificate Authorities

1
Compile and Download Configuration

* The certificate and private key of the server need to be imported into the CPU

OPC UA Server

Client

##### Where does each certificate belong?

---

###### Scenario 2

Using an external Certificate Authority managed within TIA Portal’s Certificate Manager.

##### Workflow

CA
Certification authority
* The certificate and private key of the CA must be imported into the project

1
Compile and Download Configuration

OPC UA Server

Client

* TIA's Certificate Manager can not import revocation lists from external CA's. It can only export empty revocation lists (if the private key of the CA is added to the project)

##### Where does each certificate belong?

---

###### Scenario 3

Using a central certificate manager to issue and manage certificates for clients and servers. The Certificate Authority can 
belong to the user/company or to a third-party corporation such as DigiCert or RapidSSL among many others.

##### Workflow

CA Certification authority
OPC UA Server certificates
CRL Revocation list
Compile and Download Configuration 1
OPC UA

##### Where does each certificate belong?

---

### 2.5. Certificates in the scope of HTTPS web server communication

#### 2.5.1. Overview on HTTPS communication

###### Description

The foundation of HTTPS security lies in the TLS protocol, requiring the presence of certificates on those devices hosting 
web servers. In contrast to OPC UA, which mandates mutual authentication and trust, HTTPS places the trust burden on 
the clients, the web browsers, which must verify the validity of the web server’s certificate.

###### HTTPS certificates

In the context of web servers, X-509 V3 certificates are used to secure communication over HTTPS. These certificates are 
issued by Certificate Authorities and provide the trusted means to establish the identity of the server to the client, using 
asymmetric cryptography before transitioning to symmetric encryption.

#### 2.5.2. Setting up the web server

###### Commissioning of the web server

To configure the web server using HTTPS, the next steps must be followed:

1. Select the CPU in the project tree and navigate to the "Properties" tab.

| Project tree Devices | Project tree Plant objects | Application Example Certificate Management > PLC_1 [CPU 1516F-3 PN/DP] PLC_1 [CPU 1516F-3 PN/DP] |
| --- | --- | --- |
|  | Application Example Certificate Manag... |  |
|  | Add new device |  |
|  | Devices &amp; networks |  |
|  | PLC_1 [CPU 1516F-3 PN/DP] | vice Open in new editor Open block/PLC data type... F7 Ctrl+X Cut Copy Paste Delete Rename Del F2 |
|  | Ungrouped devices |  |
|  | Security settings |  |
|  | Cross-device functions |  |
|  | Common data |  |
|  | Documentation settings |  |
|  | Languages &amp; resources |  |
|  | Version control interface |  |
|  | Online access |  |
|  | Card Reader/USB memory |  |

2

---

2. In the navigation area of the "Properties" tab, select the "Web server" entry. Activate the option "Activate web server on 
this module" and the option "Permit access only with HTTPS". Confirm the security message.

| General | IO tags | System constants | Texts |
| --- | --- | --- | --- |
| System diagnostics |  | Web server |  |
| PLC alarms |  | Web server |  |
| Web server |  | General |  |
| General |  | General |  |
| Automatic update |  | General |  |
| Security |  | General |  |
| Watch tables |  | General |  |
| User-defined pages |  | General |  |
| Entry page |  | General |  |

1

Activating the Web server reduces protection from unauthorized access to functions and data on this device from the outside and over the network.

3. By default, a server certificate is automatically generated in the local certificate manager of the device. As can be seen 
<u>here</u>, the certificate is issued by a new Certification Authority with ID=3.

General IO tags System constants Texts
Web server
General
Automatic update
Security
Watch tables
User-defined pages
Entry page
Overview of interfaces

Security
The global security settings for the certificate manager are enabled.
Sufficient function rights or project protection are required to work with the
The server certificate is used to verify the servers identity when it is access
Server certificate: PLC-1/Webserver-8 ...

The web server’s certificate can be found in the global certificate manager, under the "Certificate authority (CA)" and
"Device certificates" tabs. In this case, the root certificate with ID=3 has issued the web server certificate as well as the 
"PLC communication certificate" used for Secure PG/PC and HMI communication.
[Image: Image418]

| ID | Common name of subject | Serial nu... | Issuer | Valid to | Used as | Pri... | Signat... | Key length |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Siemens TIA Project - Appli... | OD51425... | CN = Siemens TIA Project - App... | Saturday, January 10... | Certification authorit... | Yes | RSA-S... | 2048 Bit |
| 2 | Siemens TIA Project - Appli... | 73AE492... | CN = Siemens TIA Project - App... | Saturday, January 10... | Certification authorit... | Yes | RSA-S... | 2048 Bit |
| 3 | Siemens TIA Project - Appli... | 7CBD92D... | CN = Siemens TIA Project - Appl... | Thursday, January 15... | Certification authorit... | Yes | ecds... | 256 Bit |
| 4 | PLC-1/Communication-4 | 7E4BA2C... | CN = Siemens TIA Project - Appl... | Friday, January 16, 2... | Not assigned | Yes | ecds... | 256 Bit |
| 8 | PLC-1/Webserver-8 | 3434F8A... | CN = Siemens TIA Project - Appl... | Friday, January 16, 2... | Not assigned | Yes | ecds... | 256 Bit |

---

###### User authentication

Like OPC UA, web server access can be controlled through user authentication. To create a new user with "Web server"
access rights:

1. Double click on "Security settings > Users and roles" and navigate to the "Roles" tab. Click on "<Add new role>". From 
the "Runtime rights" of the target CPU, assign those web server rights strictly required by the user.
[Image: Image421]

| Roles |  |  |  |  |
| --- | --- | --- | --- | --- |
|  | Name | Description | Runtime timeout | Comment |
|  | HMI Online Configuration Engineer | System-defined role "HMI Online C... 30 | Min | Operate HMI, read |
|  | NET Administrator | System-defined role "NET Adminis... 30 | Min |  |
|  | NET Standard | System-defined role "NET Standard" 30 | Min |  |
|  | NET Diagnose | System-defined role "NET Diagnos... 30 | Min |  |
|  | opcua user | User-defined role | 30 | Min |
|  | webserver user | User-defined role | 30 | Min |
| Engineering rights | Runtime rights | User-specific runtime rights |  |  |
| Function rights c... | Function rights |  |  |  |
| Runtime rights | Name |  | Group |  |
| S7-1500 V3.1 (fail... | User authentication of the OPC UA... |  | OPC UA |  |
| PLC_1 | Manage certificates |  | OPC UA |  |
|  | Change operating mode |  | Web server - General |  |
|  | Change default page of the web s... |  | Web server - General |  |
|  | Change parameter of the F-System... |  | Web server - General |  |
|  | Read out diagnostics |  | Web server - PLC diagnostics |  |
|  | Acknowledge alarms |  | Web server - PLC diagnostics |  |
|  | Read out Syslog buffer of the CPU |  | Web server - PLC diagnostics |  |
|  | Flash LEDs |  | Web server - PLC diagnostics |  |
|  | Update firmware |  | Web server - Maintenance |  |
|  | Change time settings |  | Web server - Maintenance |  |
|  | Create a backup of the CPU |  | Web server - Maintenance |  |
|  | Restore the CPU using a backup file |  | Web server - Maintenance |  |
|  | Download service data |  | Web server - Maintenance |  |
|  | Read process data |  | Web server - Access to process data |  |
|  | Read process data of the watch ta... |  | Web server - Access to process data |  |
|  | Write process data |  | Web server - Access to process data |  |
|  | Write process data of the watch ta... |  | Web server - Access to process data |  |
|  | Open user-defined web pages |  | Web server - User-defined web pages |  |
|  | Manage user-defined web pages |  | Web server - User-defined web pages |  |
|  | Write process data through comm... |  | Web server - User-defined web pages |  |
|  | Read files |  | Web server - Access to file browser |  |
|  | Write/delete files |  | Web server - Access to file browser |  |

###### 2. Assign the new role to a user. Compile and download the project to the CPU.

| Users |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
|  |  | User name | Password | Runtime timeout | UM domain ID |  |
|  | ☐ | Anonymous |  |  |  |  |
|  | ☑ | admin | ******** | 30 | Min |  |
|  | ☑ | User | ******** | 30 | Min |  |
| < | ☐ |  |  |  |  |  |
| Assigned user groups |  |  | Assigned roles | Assigned rights |  |  |
| Assigned roles |  |  |  |  |  |  |
|  | Assigned to | Name | Description | Runtime timeout | Comment |  |
| 1 | ☑ | opcua user | User-defined role | 30 | Min |  |
| 2 | ☑ | webserver user | User-defined role | 30 | Min |  |
|  |  |  |  |  |  |  |

---

#### 2.5.3. Setting up the web browser

###### Description

In this Application Example, Microsoft Edge has been selected as the browser to establish the connection with the web
server.

###### Configuration

For Microsoft Edge to trust the web server's certificate, it needs to establish a trust relationship with the CA. For this, it is 
necessary to export the root certificate from TIA Portal and import it into Windows’ certificate store.

1. To export the root certificate from the global certificate manager, right-click on the CA and select the "Show" option. 
Once the certificate is opened, click on "Install Certificate…".

| Certificate authority (CA) |  |  |  |  |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ID | Common name of subject | Serial nu... | Issuer | Valid to | Used as | Pri... | Signat... | Key length |  |  |
| 1 | Siemens TIA Project - Appli... | 0D51425... | CN = Siemens TIA Project - App... | Saturday, January 10... | Certification authorit... | Yes | RSA-S... | 2048 Bit |  |  |
| 2 | ▶ Siemens TIA Project - Appli... | 73AE492... | CN = Siemens TIA Project - App... | Saturday, January 10... | Certification authorit... | Yes | RSA-S... | 2048 Bit |  |  |
| 3 | ▼ Siemens TIA Project - Applic... | 7CRD02D | CN - Siemens TIA Project - Appl... | Thursday, January 15... | Certification authorit... | Yes | ecdsa... | 256 Bit |  |  |
| 4 | PLC-1/Communication-4 | Export | Siemens | ct-Appl... | Friday, January 16, 2... | Not assigned | Yes | ecdsa... | 256 Bit |  |
| 8 | PLC-1/Webserver-8 | 3 | Siemens | Appl... | Friday, January 16, 2... | Not assigned | Yes | ecdsa... | 256 Bit |  |
|  |  | Show |  |  |  |  |  |  |  |  |
|  |  | Renew |  |  |  |  |  |  |  |  |
|  |  | Replace |  |  |  |  |  |  |  |  |

Certificate authority (CA)

ID Common name of subject Serial nu... Issuer Valid to Used as Pri. Signat.. Key length

**1** Siemens TIA Project - Appli... OD51425... CN = Siemens TIA Project - Appl... Saturday, January 10... Certification authorit... Yes RSA-5... 2048 Bit
2 ▼ Siemens TIA Project - Appli... 73AE492... CN = Siemens TIA Project - Appl... Saturday, January 10... Certification authorit... Yes RSA-5... 2048 Bit
3 ▼ Siemens TIA Project - Applic... 7CBD92D... CN = Siemens TIA Project - Appl... Thursday, January 15... Certification authorit... Yes ecdsa... 256 Bit
4 PLC-1/Communication-4 7 Siemens TIA Project - Appl... Friday, January 16, 2... Not assigned Yes ecdsa... 256 Bit
8 PLC-1/Webserver-8 3 Siemens TIA Project - Appl... Friday, January 16, 2... Not assigned Yes ecdsa... 256 Bit

Certificate

General Details Certification Path

Certificate Information

This certificate is intended for the following purpose(s):
• All issuance policies
• All application policies

Issued to: Siemens TIA Project- Application Example Certificate Manager--3

Issued by: Siemens TIA Project- Application Example Certificate Manager--3

Valid from 16/01/2024 to 16/01/2037

Install Certificate... Issuer State

OK

2. The wizard is started. Follow the instructions of the installation wizard.

-Select the "Local Machine" as the storage location.

-The wizard issues a security warning, which must be acknowledged with "Yes".

-Store the certificate in the "Trusted Root Certification Authorities" folder.

The "Finish" button imports the certificate into the selected folder.

---

Certificate Import Wizard

Welcome to the Certificate Import Wizard
This wizard helps you copy certificates, certificate trust lists, and certificate revocation lists from your disk to a certificate store.

A certificate, which is issued by a certification authority, is a confirmation of your identity and contains information used to protect data or to establish secure network connections. A certificate store is the system area where certificates are kept.

Store Location
○ Current User
○ Local Machine
To continue, click Next.

Certificate Import Wizard

Certificate Store
Certificate stores are system areas where certificates are kept.

Windows can automatically select a certificate store, or you can specify a location for the certificated.
○ Automatically select the certificate store based on the type of certificate
○ Place all certificates in the following store

Certificate store:
Trusted Root Certification Authorities
Browse...

Certificate Import Wizard
Next Cancel

Completing the Certificate Import Wizard
The certificate will be imported after you click Finish.

You have specified the following settings:
Certificate Store Selected by User Trusted Root Certification Authorities
Content Certificate

Finish Cancel

---

#### 2.5.4. Testing a secure HTTPS connection

With the CPU running and the root certificate imported into Windows’ certificate store, establish a connection to the web 
server using the following URL: https://[CPU IP Address].

As shown in the image below, the connection is secured by TLS, and the web server’s certificate is trusted.

About 192.168.0.1

Connection is secure

Permissions for this site

Cookies (0 cookies in use)

Tracking prevention for this site (Balanced)

Trackers (0 blocked)

Connection is secure

This site has a valid certificate, issued by a trusted authority.

This means information (such as passwords or credit cards) will be securely sent to this site and cannot be intercepted.

Always be sure you're on the intended site before entering any information.

Learn more

SIMATIC S7-1500
CPU 1516F-3 PN/DP

To test the user authentication, press the "ENTER" button. As observed, anonymous users have limited access to the 
information displayed on the web server.

S7-1500/ET200MP station_1

https://192.168.0.1/Portal/Portal.mwsl?intro_enter_button=ENTER&PriNav=Start&coming_from_intro=true

SIEMENS S7-1500/ET200MP station_1/PLC_1

User name
Log in

PLC_1

Start page

Introduction

General:
Project name: Application Example Certificate Manager
TIA Portal: V19.0.0.0
Step 7 Safety: V19
Station name: S7-1500/ET200MP station_1
Module name: PLC_1
Module type: CPU 1516F-3 PN/DP

Status:
Operating Mode: RUN
Status: ✓ OK
Mode selector: RUN

Fail-safe:
Safety mode: Enabled
Collective F-signature: 9a0773bd
Last fail-safe modification: 01/01/2012 03:47:37.893 am

---

After logging in with username and password, the web server checks the user’s rights and grants it access to further 
features.

S7-1500/ET200MP station_1

https://192.168.0.1/Portal/Portal.mwsl?PriNav=Online&SecNav=RuntimeInfo

SIEMENS S7-1500/ET200MP station_1/PLC_1

User User
Log out

Diagnostics

Identification Program protection Memory Runtime information Fail-safe

Start page

Diagnostics

Diagnostic Buffer

Motion Control diagnostics

Module information

Alarms

Communication

Topology

Tag status

Watch tables

Online backup

Trace

DataLogs

User Files

User-defined pages

Filebrowser

Introduction

Program/Communication load

Value refresh: Every second

Measurement: Current measurement

*Program load cyclic program OBs: 4%
**Program load high-priority OBs: 0%
***Current communication load: 6%
Maximum permissible communication load: 20%
No-load operation: 90%

Cycle time

Shortest cycle time: 1.014 ms
Current cycle time: 1.027 ms
Longest cycle time: 1.438 ms
Configured min. cycle time: 1 ms
Configured max. cycle time: 150 ms

Measurement of load distribution and cycle time

0% 6% 90% Cycle time: 1.027ms

Prognosis of load distribution and cycle time

0% 20% 96% 4% Predicted cycle time: -1ms (max: 150ms)

The user program can be processed within the maximum cycle time with at least 30% of the maximum cycle time still being available as reserve.

Trend for program/communication load

Number of recorded measuring points: 200

*Runtime of the program cycle OBs including system tasks (e.g., update of the process image), calculated as arithmetic mean of the last second
**Runtime of all OBs with higher priority than the program cycle OBs, calculated as arithmetic mean of the last second
***Communication load calculated as arithmetic mean of the last second
***Measurement with the longest cycle time observed in the Web server (not the longest cycle time recorded by the PLC)

---

#### 2.5.5. HTTPS certificate handling scenarios–where does each certificate belong?

###### Description

The chapter concludes with a set of diagrams designed to highlight the correct handling of certificates and private keys in 
diverse HTTPS communication scenarios. These diagrams do not include any client certificates in the web browser, as 
HTTPS does not often require them for client authentication.

###### Scenario 1

Using TIA Portal’s default CAs in the Certificate Manager to issue certificates for web servers.

##### Workflow

TIA Portal Project

1
Compile and Download Configuration

* The certificate and private key of the server need to be imported into the CPU

HTTPS Server

Browser

Client

##### Where does each certificate belong?

HTTPS Client Trusted root Certification Authorities Webserver certificate HTTPS Server

---

###### Scenario 2

Using an external Certificate Authority managed within TIA’s Certificate Manager.

##### Workflow

CA
Certification authority
* The certificate and private key of the CA must be imported into the project

1
Compile and Download Configuration

HTTPS Server

Browser
Client

##### Where does each certificate belong?

---

###### Scenario 3

Using a central certificate manager to issue and manage certificates for the web server. The Certificate Authority can 
belong to the user/company or to a third-party corporation such as DigiCert or RapidSSL among many others.

##### Workflow

CA Certification authority
Server certificates
Compile and Download Configuration
1
HTTPS Server
Browser
Client

##### Where does each certificate belong?

---

### 2.6. Certificates in the scope of Secure OUC communication

#### 2.6.1. Overview on Open User Communication

###### Description

Open User Communication is an open standard that enables communication between SIMATIC CPUs as well as with 
suitable third-party devices. It supports various communication protocols, some of which can be secured through TLS v1.2 
and v1.3.

| Transport protocol | Via interface |
| --- | --- |
| TCP | PROFINET/IE |
| ISO-on-TCP | PROFINET/IE |
| ISO | Industrial Ethernet (only CP 1543-1) |
| UDP | PROFINET/IE |
| FDL | PROFIBUS |

| Application protocol | Used transport protocol |
| --- | --- |
| Modbus TCP | TCP |
| E-Mail | TCP |
| FTP | TCP |

The application example titled "Basic Examples for Open User Communication (OUC)" provides in-depth details on 
establishing non-secure OUC, focusing on ISO-on-TCP, TCP and UDP communication protocols.

(https://support.industry.siemens.com/cs/document/109747710)

###### OUC certificates

Secure OUC employs X-509 V3 certificates. First device acts as a TLS server, opening a designated port to establish the TCP 
connection, while a second device, the TLS client, initiates and establishes the connection with the server through this 
port.

Like web servers, only the client side is required to trust the server’s certificate. However, users can modify this setting, 
making it mandatory to establish a mutual trust relationship between server and client.

#### 2.6.2. Setting up the TLS server

###### Description

In this application example, the CPU will be used as a TLS server. To communicate with it, a second PLC will be configured 
as a TLS client.

To implement the Secure Open User Communication, the function blocks "TRCV_C" and "TSEND_C" will be utilized in the 
Main Organization Block (OB) of both the server and client. These blocks combine the functionalities of TCON (connect), 
TRCV (receive) / TSEND (send), and TDISCON (disconnect) into a single, integrated block.

---

###### Creation of the server’s certificate

To set up a TLS server in the CPU, the first step is to create a new TLS client/server certificate:

1. Select the CPU in the project tree and navigate to the "Properties" tab.

| Project tree |  | Application Example Certificate Management ▶ PLC_1 [CPU 1516F-3 PN/DP] |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
| Devices | Plant objects | PLC_1 [CPU 1516F-3 PN/DP] |  |  |  |  |
| Devices &amp; networks | Application Example Certificate Manag... |  |  |  |  |  |
| Devices &amp; networks | Add new device |  |  |  |  |  |
| Devices &amp; networks | Devices &amp; networks |  |  |  |  |  |
| Devices &amp; networks | PLC_1 [CPU 1516F-3 PN/DP] |  |  |  |  |  |
| Devices &amp; networks | Ungrouped devices |  | Open in new editor Open block/PLC data type... F7 | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Security settings |  | Open in new editor Open block/PLC data type... F7 | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Cross-device functions |  | Open in new editor Open block/PLC data type... F7 | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Common data |  | Open in new editor Open block/PLC data type... F7 | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Documentation settings |  | Cut Ctrl+X Copy Ctrl+C Paste Ctrl+V | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Languages &amp; resources |  | Cut Ctrl+X Copy Ctrl+C Paste Ctrl+V | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Version control interface |  | Cut Ctrl+X Copy Ctrl+C Paste Ctrl+V | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Online access |  | Delete Delete Rename F2 | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |
| Devices &amp; networks | Card Reader/USB memory |  | Go to topology view Go to network view | Print... Print preview... Export CAx data... Export module labeling strips... Properties... Alt+Enter |  | 5 6 ...14 ...22 |

2. Navigate to the entry "Protection & Security > Certificate manager > Certificate management with TIA Portal" and 
create a new certificate in the "Device certificates" section.
[Image: Image473]

| General | IO tags | System constants | Texts |
| --- | --- | --- | --- |
| Protection &amp; Security Protection of the PLC configuration data Access control Connection mechanisms Certificate manager Certificate manager Certificate management with TIA Portal |  | Certificate management with TIA Portal Global security settings The global security settings for the certificate manager are enabled. Sufficient function rights or project protection are required to work with the certificate manager. Use global security settings for certificate manager |  |
| Syslog Security event OPC UA System power supply Advanced configuration Connection resources Overview of addresses Runtime licenses OPC UA ProDiag |  | Device certificates |  |
|  |  | ID | Common name of subject |
|  |  | 8 | PLC-1/Webserver-8 |
|  |  | 5 | PLC-1/OPCUA-1-5 |
|  |  | 4 | PLC-1/Communication-4 |
|  |  | 6 | PLC-1/OPCUA Server |
|  |  |  |  |

ID Common name of subject Issuer Valid until
4 PLC-1/Communication-4 O=Siemens, C=DE, CN=S... 1/16/2037
5 PLC-1/OPCUA-1-5 O=Siemens, C=DE, CN=S... 1/15/2037
6 PLC-1/OPCUA Server O=Siemens, C=DE, CN=S... 1/16/2037
7 UaExpert/OPCUA Client O=Siemens, C=DE, CN=S... 1/15/2037
8 PLC-1/Webserver-8 O=Siemens, C=DE, CN=S... 1/16/2037

Create

---

3. Select the usage "TLS Client/Server" and choose a Certification Authority to issue the certificate. The Key Usage and 
Subject Alternative Name (SAN) fields are automatically generated.

Create certificate

Purpose
Usage: TLS Client / Server
Key usage: Non Repudiation, Digital Signature, Key Encipherment, Key Agreement

Certificate authority (CA)
Select how the new certificate will be signed:
Self signed
Signed by certification authority
CA name: (ID = 3) Siemens TIA Project-Application Example Certificate Me Select

Certificate parameter
Enter the parameters for the new certificate:
Common name of subject: PLC-1/TLS Server
Encryption method: EC
Key length: prime256v1
Hash algorithm: sha256
Valid from: January 19, 2024 09:56:04 AM
Valid until: January 18, 2037 04:00:00 PM

Subject Alternative Name (SAN): Type Value
IP 192.168.0.1
IP 192.168.1.1
<Add new>

OK Cancel

###### Program the OUC communication block

Once the certificate is created, open the "Main" OB of the CPU.

1. In the "Instructions" tab, open the folder "Communication > Open user communication" and drag-and-drop the 
"TRCV_C" function block to the main program.

Block title: *Main Program Sweep (Cycle)*
Comment

Network **1:**
Comment

%DB2
*TRCV_C_DB*
TRCV_C
EN ENO
EN_R DONE false
CONNECT BUSY false
DATA ERROR false
STATUS 16#7000
RCVD_LEN 0

Name Description
Date and time-of-day
String + Char
Process image
Distributed I/O
PROFlenergy
Module parameter assig...

Technology
Name Description
Counting, measurement...
PID Control
Motion Control

Communication
Name
S7 communication
Open user communication
TSEND_C
TRCV_C
TMAIL_C
CommConfig
Others

---

This function block requires three main input parameters:

-EN_R (Enable receive): indicates if the server is ready to receive data.

-CONNECT: variable with all necessary connection parameters.

-DATA: variables that will be overwritten with the data being sent by the client.

2. To set up the variable with the necessary connection parameters, create a new data block in the project and add a 
"TCON_IP_V4_SEC" variable. Set up this variable as shown in the image below.

Project tree

Application Example Certificate Manager > PLC_**1** [CPU 1516F-3 PN/DP] > Program blocks > Main

Devices Plant objects

Add new block

Name:
Server_Connection_Parameters

OB
Organization block

Type: Global DB
Language: DB
Number: 3
Manual
Automatic

FB
Function block

Fail-safe: Create F-block

Description:
Data blocks (DBs) save program data.

FC
Function

DB
Data block

more...

Additional information

Add new and open

OK Cancel

| Server_Connection_Parameters |  |  |  |  |
| --- | --- | --- | --- | --- |
|  | Name | Data type | Start value |  |
| 1 | Static |  |  |  |
| 2 | Server IP_V4_ConnectionSEC | TCON_IP_V4_SEC |  |  |
| 3 | ConnPara | TCON_IP_v4 |  |  |
| 4 | Interfaceld | HW_ANY | 64 |  |
| 5 | ID | CONN_OUC | 1 |  |
| 6 | ConnectionType | Byte | 11 |  |
| 7 | ActiveEstablished | Bool | false |  |
| 8 | RemoteAddress | IP_V4 |  |  |
| 9 | ADDR | Array[1..4] of Byte |  |  |
| 10 | ADDR[1] | Byte | 192 |  |
| 11 | ADDR[2] | Byte | 168 |  |
| 12 | ADDR[3] | Byte | 0 |  |
| 13 | ADDR[4] | Byte | 2 |  |
| 14 | RemotePort | UInt | 0 |  |
| 15 | LocalPort | UInt | 2000 |  |
| 16 | ActivateSecureConn | Bool | true |  |
| 17 | TLSServerReqClientCert | Bool | true |  |
| 18 | ExtTLSCapabilities | Word | 16#0 |  |
| 19 | TLSServerCertRef | UDInt | 9 |  |
| 20 | TLSClientCertRef | UDInt | 3 |  |

###### NOTE

The "TLSServerCertRef" field must contain the ID, specified within the global certificate manager, of
the server’s certificate.

The "TLSClientCertRef" is configured with the root certificate (ID = 3), as this CA will issue the client’s 
certificate. If the server trusts the CA, it automatically extends that trust to the client.

---

3. Add a second data block to store the incoming information sent by the client. Configure the data block to operate 
without "Optimized block access" and include the variables that are intended for transfer during communication.

PLC_1 [CPU 1516F-3 PN/DP]
Device configuration
Online & diagnostics
Safety Administration
Software units
Program blocks
Add new block
Main [OB1]
Received_Data [DB4]
Server_Connection_Param..
FOB_RTG1 [OB123]
Main_Safety_RTG1 [FB1]
Main_Safety_RTG1_DB [D...
System blocks
Technology objects
Energy objects
External source files
PLC tags
PLC data types
Watch and force tables
Online backups

Received_Data [DB4]

General Texts

General
Information
Time stamps
Compilation
Protection
Attributes
Download without reinitializati...

Attributes
Only store in load memory
Data block write-protected in the device
Optimized block access
Data block accessible from OPC UA
Data block accessible via Web server

|  | Name | Data type | Offset | Start value |
| --- | --- | --- | --- | --- |
| 1 | Static |  |  |  |
| 2 | Var1 | Bool | ... | false |
| 3 | Var2 | String | ... | " |

4. Include the "Server IP_V4_ConnectionSEC" variable into the "CONNECT" field of the function block and drag-and-drop 
the "Received_Data" DB to the "DATA" field.

To indicate when the server is ready to receive data, add a new tag to the "EN_R" field. Set the "CONT" field to true to 
maintain the connection alive.

Comment

%DB2
*TRCV_C_DB*
TRCV_C

EN ENO
DONE false
BUSY false
ERROR false
STATUS 16#7000
RCVD_LEN 0

%M0.0
*Enable receive*

EN_R
CONT

*Server_
Connection_
Parameters*.
Server IP_V4_
ConnectionSEC*
=CONNECT

%DB4
*Received_Data*
=DATA

---

#### 2.6.3. Setting up the TLS client

###### Commissioning of a new device

As previously mentioned, the TLS client will be running on a second CPU. Therefore, add a new CPU to the project and 
commission it:

1. Navigate to the "Network view" and assign new IP Addresses to the second CPU.

Project tree
Devices Plant objects

Application Example Certificate Manager Devices & networks

Network Connections HMI connection Relations

Application Example Certificate Manage
Add new device
Devices & networks
PLC_1 [CPU 1516F-3 PN/DP]
PLC_**2** [CPU 1516-**3** PN/DP]
Ungrouped devices
Security settings
Cross-device functions
Common data

10
PLC_1
U 1516F-3 PN...
192.168.1.1 192.168.0.1
2

PLC_2
CPU 1516-3 PN/...
192.168.1.2 192.168.0.2
2

3

2. Activate the global security settings in the device following the steps covered in section <u>2.3.3</u>. Renew the "PLC 
communication certificate" if it was deleted during this step.

3. Set up a new user and role with the necessary "Access Level" rights to access the CPU.

###### Creation of the client’s certificate

To set up the TLS client, create a new TLS client/server certificate:

1. Select the CPU in the project tree and navigate to the "Properties" tab.

Project tree
Devices Plant objects

Application Example Certificate Manager
Add new device
Devices & networks
PLC_**1** [CPU 1516F-3 PN/DP]
PLC_**2** [CPU 1516-3 PN/DP]
Ungrouped devices
Security settings
Cross-device functions
Common data
Documentation settings
Languages & resources
Version control interface
Online access
Card Reader/USB memory

Application Example Certificate Manager > PLC_2 [CPU 1516-3 PN/DP]

Topology view

PLC_2 [CPU 1516-3 PN/DP]

100 0 1 2 3 4 5 6 14 22 31

ange device
Open
Open in new editor
Open block/PLC data type... F7
Cut Ctrl+X
Copy Ctrl+C
Paste Ctrl+V
Delete Del
Go to topology view
Go to network view

Export CAx data...
Export module labeling strips...
Properties... Alt+Enter

2. Navigate to the entry "Protection and Security > Certificate Manager > Certificate management within TIA Portal" and 
create a new certificate in the "Device certificate" section.

---

3. Select the usage "TLS Client/Server" and choose Certification Authority with ID 3 to issue the certificate. The Key Usage 
and Subject Alternative Name (SAN) fields are automatically generated.

Create certificate

Purpose
Usage: TLS Client / Server
Key usage: Non Repudiation, Digital Signature, Key Encipherment, Key Agreement

Certificate authority (CA)
Select how the new certificate will be signed:
○ Self signed
☑ Signed by certification authority
CA name: (ID = 3) Siemens TIA Project- Application Example Certificate Me Select

Certificate parameter
Enter the parameters for the new certificate:
Common name of subject: PLC-2/TLS Client
Encryption method: EC
Key length: prime256v1
Hash algorithm: sha256
Valid from: January 19, 2024 11 : 31 : 26 AM
Valid until: January 18, 2037 04 : 00 : 00 PM

Subject Alternative Name (SAN): Type Value
IP 192.168.0.2
IP 192.168.1.2
<Add new>

OK Cancel

###### Program the OUC communication block

1. Open the folder "Communication > Open user communication" and drag-and-drop the "TSEND_C" function block to 
the main program.

Block title: "Main Program Sweep (Cycle)"
Comment

Network **1:**
Comment

%DB1
"TSEND_C_DB"
TSEND_C
EN ENO
REQ DONE
CONNECT BUSY
DATA ERROR
STATUS 16#7000

Name Description
Date and time-of-day
String + Char
Process image
Distributed I/O
PROFlenergy
Module parameter assign...
Interrupts

Technology
Name Description
Counting, measurement...
PID Control
Motion Control
SINAMICS Motion Control

Communication
Name
S7 communication
Open user communication
TSEND_C
TRCV_C
TMAIL_C
CommConfig

This function block requires three main input parameters:

-REQ: trigger to send the data.

-CONNECT: variable with all necessary connection parameters.

-DATA: variables that will be sent by the client.

---

2. To set up the variable with the necessary connection parameters, create a new data block in the project and add a 
"TCON_IP_V4_SEC" variable. Set up this variable as shown in the image below.
[Image: Image503]

| Client_Connection_Parameters |  |  |  |  |
| --- | --- | --- | --- | --- |
|  | Name | Data type | Start value |  |
| 1 | Static |  |  |  |
| 2 | Client IP_V4_Connection_SEC | TCON_IP_V4_SEC |  |  |
| 3 | ConnPara | TCON_IP_v4 |  |  |
| 4 | Interfaceld | HW_ANY | 64 |  |
| 5 | ID | CONN_OUC | 1 |  |
| 6 | ConnectionType | Byte | 11 |  |
| 7 | ActiveEstablished | Bool | true |  |
| 8 | RemoteAddress | IP_V4 |  |  |
| 9 | ADDR | Array[1..4] of Byte |  |  |
| 10 | ADDR[1] | Byte | 192 |  |
| 11 | ADDR[2] | Byte | 168 |  |
| 12 | ADDR[3] | Byte | 0 |  |
| 13 | ADDR[4] | Byte | 1 |  |
| 14 | RemotePort | UInt | 2000 |  |
| 15 | LocalPort | UInt | 0 |  |
| 16 | ActivateSecureConn | Bool | true |  |
| 17 | TLSServerReqClientCert | Bool | false |  |
| 18 | ExtTLSCapabilities | Word | 16#0 |  |
| 19 | TLSServerCertRef | UDInt | 3 |  |
| 20 | TLSClientCertRef | UDInt | 11 |  |

###### NOTE

The "ExtTLSCapabilities" field is used to specify whether the SAN fields of the server are checked. If set 
to one, the client will validate the IP address of the TLS server against the Subject Alternative Name of 
the server’s X-509 V3 certificate.

3. Add a second data block to store the information that is going to be sent by the client. Configure the data block to 
operate without "Optimized block access" and include the variables that are intended for transfer during 
communication.
[Image: Image505]

| Send_Data |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
|  | Name | Data type | Offset | Start value |  |
| 1 | Static |  |  |  |  |
| 2 | Var1 | Bool |  | ... | false |
| 3 | Var2 | String |  | ... | " |

4. Include the "Client IP_V4_ConnectionSEC" variable into the "CONNECT" field of the function block and drag-and-drop 
the "Send_Data" DB to the "DATA" field. To trigger the send data instruction, add a new tag to the "REQ" field. Set the 
"CONT" field to true to maintain the connection alive.

Comment

%DB1
"TSEND_C_DB"
TSEND_C

EN ENO
DONE false
BUSY false
ERROR false
TRUE CONT STATUS 16#7000

*Client_
Connection_
Parameters*."
Client IP_V4_
Connection_SEC*
=CONNECT

%DB3
*Send_Data*
=DATA

---

#### 2.6.4. Testing a Secure OUC communication

Once both devices are configured and running, the Secure Open User Communication can be tested. To establish the 
connection between server and client:

1. Activate "Enable receive" to allow data reception on the server’s side. Confirm that the status of the output field "BUSY"
is true.

%DB2
*TRCV_C_DB*
TRCV_C

EN ENO
%M0.0
*Enable receive*
DONE false
EN_R
CONT BUSY
TRUE false
ERROR FALSE
*Server_
Connection_
Parameters*.
Server IP_V4_
ConnectionSEC*
STATUS 16#7006
16#7000
CONNECT RCVD_LEN
0 0

%DB4
*Received_Data*
DATA

2. Apply changes to the variables that the client is set to transmit. As depicted bellow, there are different values in the 
respective data blocks of client and server.
[Image: Image512]

|  | Name | Data type | Offset | Start value | Monitor value |
| --- | --- | --- | --- | --- | --- |
| 1 | Static |  |  |  |  |
| 2 | Var1 | Bool | 0.0 | false | TRUE |
| 3 | Var2 | String | 2.0 | " | 'Hello World!' |

|  | Name | Data type | Offset | Start value | Monitor value |
| --- | --- | --- | --- | --- | --- |
| 1 | Static |  |  |  |  |
| 2 | Var1 | Bool | 0.0 | false | FALSE |
| 3 | Var2 | String | 2.0 | " | " |

3. Activate the client’s trigger to transmit the data.

%DB1
*TSEND_C_DB*
TSEND_C
EN ENO
%M0.0
*Send data*
REQ DONE FALSE
TRUE CONT BUSY FALSE
ERROR FALSE
*Client_
Connection_
Parameters*.
Client IP_V4_
Connection_SEC*
STATUS 16#7004
16#7000
= CONNECT
%DB3
*Send_Data*
= DATA

---

4. Verify that the new updated values are received in the "Received_Data" DB.
[Image: Image519]

|  | Name | Data type | Offset | Start value | Monitor value |
| --- | --- | --- | --- | --- | --- |
| 1 | Static |  |  |  |  |
| 2 | Var1 | Bool | 0.0 | false | TRUE |
| 3 | Var2 | String | 2.0 | " | 'Hello World!' |

###### Secure OUC vs Unsecured OUC

To understand the importance of encrypting and signing data, Wireshark is used to analyze the communication packages 
sent during transmission.

When data is transmitted via secure communication, it is encrypted and safeguarded with TLS v1.3. This ensures 
confidentiality of sensitive data, maintains its integrity, and authenticates the sender.

| 60 | 30.628910 | 192.168.0.2 | 192.168.0.1 | TLSv1.3 | 332 Client Hello |
| --- | --- | --- | --- | --- | --- |
| 61 | 30.639511 | 192.168.0.1 | 192.168.0.2 | TLSv1.3 | 1244 Server Hello, Change Cipher Spec, Application Data, Application Data, Appli |
| 62 | 30.653596 | 192.168.0.2 | 192.168.0.1 | TLSv1.3 | 990 Change Cipher Spec, Application Data, Application Data, Application Data |
| 63 | 30.660089 | 192.168.0.1 | 192.168.0.2 | TLSv1.3 | 212 Application Data, Application Data |
| 64 | 30.816795 | 192.168.0.2 | 192.168.0.1 | TCP | 54 63801 + 2000 [ACK] Seq=1215 Ack=1349 Win=8034 Len=0 |
| 1498 | 61.162622 | 192.168.0.2 | 192.168.0.1 | TLSv1.3 | 334 Application Data |
| 1513 | 61.270204 | 192.168.0.1 | 192.168.0.2 | TCP | 54 2000 + 63801 [ACK] Seq=1349 Ack=1495 Win=8192 Len=0 |

60 30.628910 192.168.0.2 192.168.0.1 TLSv1.3 332 Client Hello
61 30.639511 192.168.0.1 192.168.0.2 TLSv1.3 1244 Server Hello, Change Cipher Spec, Application Data, Application Data, Appli
62 30.653596 192.168.0.2 192.168.0.2 TLSv1.3 990 Change Cipher Spec, Application Data, Application Data, Application Data
63 30.660089 192.168.0.2 192.168.0.2 TLSv1.3 212 Application Data, Application Data
64 30.816795 192.168.0.2 192.168.0.2 TCP 54 63801 → 2000 [ACK] Seq=1215 Ack=1349 Win=8034 Len=0

1498 61.162622 192.168.0.2 192.168.0.1 TLSv1.3 334 Application Data
1513 61.270204 192.168.0.1 192.168.0.2 TCP 54 2000 → 63801 [ACK] Seq=1349 Ack=1495 Win=8192 Len=0

Frame 1498: 334 bytes on wire (2672 bits), 334 bytes captured (2672 bits)
Ethernet II, Src: 02:c0:a8:00:63:10 (02:c0:a8:00:63:10), Dst: 02:c0:a8:00:
Internet Protocol Version 4, Src: 192.168.0.2, Dst: 192.168.0.1
Transmission Control Protocol, Src Port: 63801, Dst Port: 2000, Seq: 1215,
Transport Layer Security

0000 02 c0 a8 00 63 00 02 c0 a8 00 63 10 08 00 45 00
001 01 40 oc dd 40 00 06 ab 87 c0 a8 00 02 c0 a8
000 01 f9 39 07 d0 f6 d7 6b 3e 68 4f f9 60 50 18
1f 62 42 7d 00 00 17 03 03 01 13 59 4e 86 c7 cb
ba ec 49 48 1f 3a b3 3f 1f 42 5f d8 f4 a4 5f 01
b2 40 84 2d 20 d1 d7 c8 ce a0 01 77 93 93 1c ed
16 36 8c 74 de a6 45 af d5 bc 8a 62 00 d2 98 ee
58 8e 3a 73 17 9b a2 8b 4b 40 18 da fd 1c 33 cb
cc dc 58 49 33 98 ec 74 78 8d 71 ab a2 75 d1 a1
1a ee f6 e4 0a a3 ce 21 98 25 dd ec 4f 88 d4 c3
80 db 84 07 c8 c1 78 22 2a cc c1 79 50 d3 34
82 f2 f2 a1 fe 33 8c 12 b0 40 67 92 cf 4b 30 ff
f6 9a b2 6e 55 2b 2c 24 ef e4 62 d2 08 3a 1a1
ec 40 49 0b 50 85 72 1d 26 d3 ff f6 3a 56 63 b7
17 3e ad e2 82 4b 76 d3 31 0e 42 9cfc 12bf 3c
22 66 c1 aa 98 c4 84 e4 c4 89 ba 10 88 1e 5a e4
52 5e 3c e4 02 79 8f ba 54 c4 61 64 e0 e3 20bd
13 bd a6 6e 1a 8b 01 a8 6a d3 dc e6 3a 7f 4e 8e
68 c2 0b 45 ad 44 2f f2 0a b5 96 ac 79 88 c9 e3
fe 5f c6 4a 34 db 9b 72 b5 eb 75 5c a1 41 12 8e
ee ed f9 4b f9 51 84 44 bf 44 80 5b cd 3e

...c ...c ...E
@ @ @
9 ...k>hO `P`
bB} ...YN
IH : ? B _
- ...w ...
6 t E ...b
X : s K@ ...3
XI3 t x q r]
! % O
x "*" yP4
3 ...@ K0
nU+, $ b ..@
@ I P r &

On the other hand, unsecure OUC transmits data in plain text, leaving sensitive information vulnerable to theft and 
manipulation by attackers.

| 659 | 13.691099 | 192.168.0.2 | 192.168.0.1 | TCP | 54 | 61260 → 2000 [ACK] Seq=1 Ack=1 Win=8192 Len=0 |
| --- | --- | --- | --- | --- | --- | --- |
| 1830 | 44.745785 | 192.168.0.2 | 192.168.0.1 | TCP | 54 | [TCP Keep-Alive] 61260 → 2000 [ACK] Seq=0 Ack=1 Win=8192 Len=0 |
| 1831 | 44.745914 | 192.168.0.1 | 192.168.0.2 | TCP | 54 | [TCP Keep-Alive ACK] 2000 → 61260 [ACK] Seq=1 Ack=1 Win=8192 Len=0 |
| 4308 | 75.839051 | 192.168.0.1 | 192.168.0.2 | TCP | 54 | [TCP Keep-Alive] 2000 → 61260 [ACK] Seq=0 Ack=1 Win=8192 Len=0 |
| 4309 | 75.839145 | 192.168.0.2 | 192.168.0.1 | TCP | 54 | [TCP Keep-Alive ACK] 61260 → 2000 [ACK] Seq=1 Ack=1 Win=8192 Len=0 |
| 4361 | 76.065424 | 192.168.0.2 | 192.168.0.1 | TCP | 312 | 61260 → 2000 [PSH, ACK] Seq=1 Ack=1 Win=8192 Len=258 |
| 4408 | 76.258203 | 192.168.0.1 | 192.168.0.2 | TCP | 54 | 2000 → 61260 [ACK] Seq=1 Ack=259 Win=8192 Len=0 |

Hello
World!

02 c0 a8 00 63 00 02 c0 a8 00 63 10 08 00 45 00
01 2a 05 2a 40 00 40 06 b3 50 c0 a8 00 02 c0 a8
00 01 ef 4c 07 d0 4e a1 27 77 62 2d d0 81 50 18
20 00 1c 96 00 01 00 fe 0c 48 65 6c 6c 6f 20
57 6f 72 6c 64 21 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 0

---

### 2.7. Certificates in the scope of PG/PC and HMI communication

#### 2.7.1. Overview on Secure PG/PC and HMI communication

###### Description

Starting with TIA Portal V17, PG/PC and HMI communication is secured with TLS, protecting the data exchanged between
Field PGs and HMIs with SIMATIC CPUs.

The CPU families that support Secure PG/HMI communication are:

• S7-1500 controllers as of firmware version V2.9.

• S7-1200 controllers as of firmware version V4.5.

• Software controllers as of firmware version V21.9.

• SIMATIC Drive controllers as of firmware version V2.9.

• PLCSim and PLCSim Advanced Version V4.0.

HMI components that support Secure PG/HMI communication, as of image version V17, are:

• Panels or PCs configured with WinCC Basic, Comfort and Advanced.

• PCs with WinCC RT Professional.

• WinCC Unified PCs and Comfort Panels.

In addition, SINAMICS RT SW, as of version V6.1, and STARTDRIVE, as of version V17, support secure communication.

#### 2.7.2. PLC communication certificate

Secure PG/HMI communication relies on Field PGs and HMIs being able to verify the CPU's authenticity. This verification is 
done using the "PLC communication certificate" that the CPU sends when establishing a new connection.

PLC_1 [CPU 1516F-3 PN/DP]

General IO tags System constants Texts

Protection & Security
Protection of the PLC con...
Access control
Connection mechanisms
Connection mechanisms
Certificate manager
Syslog
Security event

Connection mechanisms

Communication mode to TIA Portal and HMI

More information about 'secure PG/PC and HMI Communication'
Only allow secure PG/PC and HMI communication

PLC communication certificate: PLC-1/Communication-4

###### NOTE

The "PLC communication certificate" is automatically generated when a CPU is added to the project, 
and it is mandatory for all controllers that support Secure PG/PC and HMI communication. An error 
occurs during compilation if no certificate is configured.

#### 2.7.3. Security settings for PG/PC and HMI communication

###### Operation mode

SIMATIC CPUs offer two operation modes for PG/PC and HMI communication:

1. Secure Mode: Only secure TLS-based PG/HMI communication is allowed.

2. Mixed Mode: Both Legacy and Secure PG/HMI communication are allowed. To enable the mixed mode, deactivate the 
setting "Only allow secure PG/PC and HMI communication" in the CPU properties.

**NOTE**

When a CPU is configured in "Mixed Mode", unsecure connections can be established with Field PGs or 
HMI devices configured with TIA Portal versions prior to V17.

However, if the Field PG or HMI device supports Secure PG/HMI communication, the connection with 
the CPU is always protected.

---

As the topic of Secure PG/PC and HMI communication is covered in detail in other application examples and manuals, no 
additional information is provided in this document.

• Configuration of the security functions in TIA Portal V17

(https://support.industry.siemens.com/cs/ww/en/view/109798583).

• S7-1500/ET 200 Communication (https://support.industry.siemens.com/cs/ww/en/view/59192925).

• Updates for WinCC Runtime Professional V17 (https://support.industry.siemens.com/cs/ww/en/view/109800913).

#### 2.7.4. Testing a secure connection with a Field PG

When establishing a connection between TIA Portal and a CPU, a dialog message appears if the "PLC communication 
certificate" is untrusted, expired, or has an incorrect IP address. To proceed with the connection, the user must manually 
verify the CPU’s authenticity.

Establish connection to device

"PLC_1" might not be a trustworthy device.

The following errors were found when verifying the certificate:

-The IP address of the device does not match the addresses of the associated certificate.
-The device uses an unknown, self-signed certificate.

If this device is the one you want, it is trusted and you can connect. If this device is not the one you want, you should abort the connection.

Display certificates Connect Abort connection

#### 2.7.5. Testing a secure connection with an HMI device

Refer to sections 3 and 4 of the application example "Configuration of the security functions in TIA Portal V17"
(https://support.industry.siemens.com/cs/ww/en/view/109798583), for further information on how to configure a PLC-
HMI connection. Additionally, refer to section <u>4.2</u> in this document for frequently asked questions regarding PLC-HMI 
communication.

---

## 3. Additional information – Certificate management via OPC UA

### 3.1. GDS Push for dynamic certificate management

#### 3.1.1. Overview

###### Supported TIA Portal and firmware versions

With TIA Portal V17 and firmware version V2.9, the GDS Push functionality has been integrated into the SIMATIC S7-1500 
controller for OPC UA server certificates. Additionally, as of TIA Portal version V18 and firmware version V3.0, GDS push 
management for web server certificates is also supported.

###### Description

GDS Push utilizes the certificate management services of an OPC UA server to transfer web and OPC UA server certificates 
during runtime, as well as trust lists and certificate revocation lists for the latter.

After the CPU has been provisioned, certificates can be managed without TIA Portal, offering enhanced flexibility and 
convenience for long-term maintenance. This dynamic certificate management approach eliminates any manual work 
required for reconfiguring the CPU, i.e. after a certificate has expired. Moreover, GDS Push can transfer certificates and 
lists with the CPU in STOP and RUN mode, enabling operators to handle certificates with minimal disruptions to 
production.

###### Certificate management from the CPU’s point of view

The use of dynamic certificate handling on the CPU is divided into three phases:

1. CPU configuration in TIA Portal and downloading of the configuration to the CPU.

2. Initial provisioning of a trust list and a server certificate.

3. Regular updating of certificates according to the security policies of a company.

OEM/OT Engineer OT Engineer IT Engineer Loop based on security guidelines Endcustomer / IT Engineer 1 Configuration 2 Commissioning 3 Management TIA V17 ST OME Push initial certificates OPC UA Server Provisioning mode Update certificates Download configuration

Refer to the application example titled "Dynamic certificate management with OPC UA GDS Push" for detailed instructions 
on how to commission a CPU with GDS Push (https://support.industry.siemens.com/cs/document/109799888).

---

#### 3.1.2. GDS Push certificate handling scenarios

###### Scenario 1 - OPC UA certificates

Certificates need to be managed dynamically with GDS Push while controllers are in running mode. Both the certificate 
and private key of the server are generated using a central certificate management.

##### Workflow

CA Certification authority
Server certificates
Client certificates
CRL Revocation list
Si OME 1
CA CRL OPC UA

##### Where does each certificate belong?

CA Certification authority Server certificate CRL Client certificates CRL Revocation List

OPC UA Server certificate

Trustlist

CA

CRL

---

###### Scenario 2 - OPC UA certificates

Certificates need to be managed dynamically with GDS Push while controllers are in running mode. To enhance security 
and stick to safety recommendations, it has been decided to use a Certificate Signing Request (CSR) to guarantee that the 
private key of the controller never leaves the CPU.

##### Workflow

CA Certification authority
Client certificates
CRL Revocation list
Certificate Sign Request
Server certificate
Si OME
* A new private key for the server is generated in the CPU. With the private key, a CSR is sent to the CA (the private key never leaves the CPU)

##### Where does each certificate belong?

CA Certification authority Server certificate DPC UA Client certificates CRL Revocation List

OPC UA Server certificate

Trustlist

CA

CRL

---

###### Scenario 3 - Web server certificates

Certificates need to be managed dynamically with GDS Push while controllers are in running mode.

##### Workflow

CA Certification authority
Server certificates
Client certificates
CRL Revocation list
* To exit provisioning mode, users must update the Trustlist used for OPC UA communication, as well as the certificates for the Webserver and OPC UA server
Si OME
1
2
Browser
Client

##### Where does each certificate belong?

Central Certificate Management
CA
Certification authority
Server certificate
PC UA
Client certificates
CRL

OPC UA Server certificate Trustlist CA CRL

---

## 4. FAQ / Error Handling

### 4.1. Certificates

#### 4.1.1. Is it possible to work with certificates without protecting the TIA Portal project?

The only available method to work with certificates in TIA Portal, without protecting the project, is by using the local 
certificate manager with the global security settings disabled. Nonetheless, this approach offers limited functionality and
restricts certificates to being self-signed.

#### 4.1.2. How long should a certificate be valid?

In OT environments, the recommended certificate validity is regulated by the specific needs and security practices of the 
organization. There are, however, some general guidelines highlighted in chapter <u>1.4</u>.

#### 4.1.3. Which changes on the device configuration will require a new certificate?

Any information of the device configuration that has been included in the certificate, such as IP addresses, will require a 
certificate renewal.

Certificates generated through the local certificate manager are automatically updated, when relevant configuration 
changes are applied, once the project is compiled. However, certificates created via the global certificate manager do not 
undergo automatic updates, requiring users to "Renew" them manually.

#### 4.1.4. Which SAN fields are mandatory?

Different communication protocols may impose different mandatory fields within the SAN. The table below provides a 
summary of the required fields for each use case.

Table 4-1

|  | OPC UA server and client | TLS server* |
| --- | --- | --- |
| URI | Mandatory | - |
| IP | Optional (Mandatory with FW 2.5) | Mandatory (if no DNS is specified) |
| DNS | Optional | Mandatory (if no IP is specified) |
| RID | - | - |
| Email | - | - |
| Other Name | - | - |

* HTTPS, Secure OUC, Secure PG/PC and HMI communication.

**NOTE** Some devices can be configured to check additional fields or, conversely, to not check any fields in the 
Subject Alternative Name. For example, WinCC Unified PCs and Comfort Panels do not verify the CPU’s 
IP address against the one defined in the "PLC communication certificate".

#### 4.1.5. What is the difference between exporting the certificate chain vs certificate?

Exporting the certificate chain involves the export of not just the certificate itself but also of all intermediary and root 
authorities above it. This results in an export of all public keys from the trust hierarchy.

Check <u>Table 2-3</u> to determine which file formats support the export of certificate chains.

---

#### 4.1.6. What happens if an incomplete chain of trust is loaded into the PLC’s "trusted certificate store"?

###### Trust anchors and certificate chain validation in the Internet X-509 PKI (TLS)

To trust a device certificate, the certificate chain must be built up starting with the target (device) certificate and ending 
in a trust anchor. A certificate, which may be a Certification Authority or an end-entity certificate, is considered a trust 
anchor if all the following conditions hold:

1. It is an element of the trust store.

2. It does not have a negative trust attribute rejecting the given use.

3. It is self-signed.

The chain can be built with certificates stored locally, or by those provided with the application/target certificate. To know 
if a communication partner, such as a web server, is sending only the end-entity certificate or the whole trust chain, the 
following OpenSSL command can be used:

```
openssl s_client -connect [IP_Address]:[Port] -showcerts
```

Depending on the certificates sent by the communication partner, either only the root CA or the whole certificate chain 
may need to be stored in the PLC’s "trusted certificate store".

###### Trust anchors and certificate chain validation in OPC UA

According to part 4 of the OPC UA standard (OPC 10000-4: Services), "When building a chain, each Certificate in the chain 
shall be validated back to a CA with a self-signed Certificate (a.k.a. a root CA) […] Determining trust requires access to all 
Certificates in the chain. These Certificates may be stored locally, or they may be provided with the application Certificate
[…] A Certificate is trusted if the Certificate or at least one of the Certificates in the chain are in the list of trusted 
Certificates for the Application and the chain is valid."

In contrast with the Internet X-509 PKI, trust anchor is no longer required to be a self-signed certificate; it can be any 
certificate stored in the trusted folder.

Depending on the certificates sent by the communication partner, either only the root CA or the whole certificate chain 
may need to be stored in the PLC’s "trusted certificate store".

###### Trusted vs Issuer folder

• OPC UA applications rely on two separate lists to validate trust chains: a list of trusted certificates and a list of issuer 
certificates. The list of trusted certificates contains those certificates trusted by the application, while the list of issuer 
certificates is used to store the remaining CAs (root or intermediate) required to perform the chain validation.

• Similarly, Window’s certificate manager has a "Trusted Root Certification Authorities" folder to store trust anchors, and 
a "Intermediate Certification Authorities" folder. These folders can be used by web browsers, such as Microsoft Edge, 
to validate the trust chain of a web server’s certificate.

###### NOTE

The local certificate manager on the PLC does not distinguish between trusted and issuer/intermediate
certificates. All certificates are placed in the trusted certificate store.

With GDS Push, certificates for OPC UA communication can be placed in the trusted and issuer folders
independently.

---

#### 4.1.7. Which tools can be used to create certificates without TIA Portal?

Some useful tools to create certificates are:

• OpenSSL: is an open-source software used for general-purpose cryptography and to secure communications. To 
configure the necessary extensions needed for OPC UA and TLS certificates, configuration files can be used to 
summarize all certificate requitements.

The main drawback of OpenSSL is its reliance on command-line execution, resulting in a steep learning curve that can 
pose challenges for new users.

• XCA: built on top of OpenSSL, XCA incorporates a graphical interface that offers a user-friendly and intuitive 
experience for working with certificates. It is designed to handle various tasks, including creating, managing, and 
renewing X-509 certificates, securely storing private keys, generating revocation lists, processing certificate sign 
requests and more.

#### 4.1.8. How can certificates be managed without downloading via TIA Portal?

As mentioned in section <u>3.1.1</u>, GDS Push makes use of the certificate management services of an OPC UA server to 
transfer certificates during runtime. Additionally, trust lists and certificate revocation lists can be updated dynamically.

A notable advantage of GDS Push is that, after provisioning the CPU, certificates can be managed without TIA Portal, 
offering enhanced flexibility and convenience for long-term maintenance.

#### 4.1.9. Can third-party certificates be integrated in TIA Portal?

To integrate third-party devices into TIA Portal, the project must be protected to access the global certificate manager. 
Certificates can then be manually imported into the project in any of the supported file formats – check <u>Table 2-3</u>. To 
transfer these certificates to the CPU, either as own or trusted partner certificates, the global security settings must be 
enabled in the device.

#### 4.1.10. How is certificate management in TIA Portal compared to the Internet PKI?

STEP 7, as of version V14, together with the S7‑1500 CPUs, as of firmware version 2.0, support the Internet PKI (RFC 
5280). This enables S7-1500 CPUs to communicate with other devices that also support the Internet PKI.

STEP 7 uses a PKI similar to Internet PKI. Certificate Revocation Lists (CRLs), for example, are not supported. Refer to the 
"SIMATIC S7-1500/ET 200MP Manual Collection" for additional information 
(https://support.industry.siemens.com/cs/ww/en/view/86140384).

#### 4.1.11. How is certificate management handled for SCALANCE devices?

###### Supported certificates

SCALANCE firewalls can use various types of certificates. These include a certificate for the web server interface, and a 
certificate to establish VPN connections when the machine needs to communicate over untrusted networks like the 
Internet.

###### Certificate management

Certificates for SCALANCE devices can be created using TIA Portal’s certificate manager or any other external certificate 
manager. To transfer these certificates to the device, the web server interface, also known as Web Based Management 
(WBM), is used. For detailed step-by-step instructions, refer to the FAQ "How do you create, export and import certificates 
for the SCALANCE SC-600?" (https://support.industry.siemens.com/cs/ww/en/view/109792637).

SCALANCE devices also offer a command line interface, or CLI, with the same functionality as the Web Based 
Management. In addition, certificates can be managed and renewed via Siemens’ Network Management System, known 
as SINEC NMS.

---

#### 4.1.12. How is certificate management handled for Unified PCs and Panels?

###### Supported certificates

WinCC Unified PCs and Comfort Panels support many types of certificates, with the most important one being used for 
secure PLC and HMI communication. During connection establishment, the HMI uses the certificate provided by the 
controller to verify its trustworthiness.

The HMI also supports OPC UA as both a client and server, and secure communication with the UMC ecosystem. To 
authenticate the UMC server, the HMI employs the thumbprint of the UMC’s web server certificate. For remote access, 
users can connect to the HMI through the web server interface, via the Smart Server using VNC, and through Unified 
Collaboration, all protected using digital certificates.

Furthermore, certificates can be used outside the scope of secure communication, to ensure the integrity of audit logs in 
the Audit Trail System.

###### Certificate management

Certificates for Unified PCs and Panels can be managed using the WinCC Unified Certificate Manager, running on an 
engineering station. USB flash drives are used to distribute and import these certificates to the panels, where they can be 
dynamically managed during runtime through the panel’s certificate manager.

**NOTE** From image versions 17 to 20, the "PLC communication certificate", used for PLC and HMI 
communication, cannot be imported via USB flash drive and requires a TIA Portal download.

#### 4.1.13. Are X-509 V1 and V2 certificates supported?

The table below lists all the fields included in a certificate, along with the X-509 version in which they were introduced.

Table 4-2

| Fields | Description | Introduced in version |
| --- | --- | --- |
| Version | An integer that identifies the version number of the certificate. | V1 |
| Serial Number | An integer that represents the unique number for each certificate issued by a Certification Authority. | V1 |
| Signature | The identifier for the cryptographic algorithm used by the CA to sign the certificate. | V1 |
| Issuer | The distinguished name (DN) of the certificate's issuing CA. | V1 |
| Validity | The time-period for which the certificate is valid. | V1 |
| Subject | The distinguished name (DN) of the certificate subject. | V1 |
| Subject Public Key Info | The public key owned by the certificate subject. | V1 |
| Issuer Unique ID | A unique identifier that represents the issuing CA, as defined by the issuing CA. | V2 |
| Subject Unique ID | A unique identifier that represents the certificate subject, as defined by the issuing CA. | V2 |
| Extensions | A collection of standard and Internet-specific certificate extensions. | V3 |

If the communication partner does not require any of the internet-specific extensions introduced with version 3, X-509 V1 
and V2 certificates can be employed,

---

#### 4.1.14. What happens when a certificate expires while the connection is already established?

• PLC - HMI Communication: When the PLC certificate expires, communication continues until a new connection 
attempt takes place (e.g., the panel is rebooted, runtime is restarted, or the ChangeConnection system function is 
used).

• Secure OUC: If the connection between devices has already been established, communication continues until 
connection reestablishment.

• HTTPS: When the PLC's web server certificate expires, connection is interrupted after approximately 30 minutes.

• OPC UA: Refer to question <u>4.3.2</u>.

**NOTE**

When the "PLC communication certificate" expires, connection with the Engineering Station is still 
possible. TIA Portal displays a warning message; "PLC might not be a trustworthy device", to alert the 
user that the certificate has expired. However, it still allows the connection to take if the user chooses 
to proceed.

A similar behavior can be seen in web browsers when the connection is not secure.

---

### 4.2. PLC – HMI communication

#### 4.2.1. Disabling the check box "Only allow secure PG/PC and HMI communication" implies that no certificates are being used?

When disabling the "Only allow secure PG/PC and HMI communication" check box, connections with devices that 
do not support encryption can take place. However, if the PG/PC or HMI device supports secure communication, 
the connection will be secured using certificates. This is known as "Mixed mode".

#### 4.2.2. Is the CPU certificate, used for PG/PC and HMI communication, visible in WinCC Unified PCs and Comfort Panels?

The "PLC communication certificate" for PG/PC and HMI communication is transferred to the PC or Panel during a TIA 
Portal download. However, from image/runtime versions V17 to V20, it is not visible in the certificate manager of Unified 
PCs and Comfort Panels.

#### 4.2.3. Why is CA trust not possible for PLC-HMI communication in WinCC Unified PCs and Comfort Panels?

WinCC Unified devices, from image/runtime versions V17 to V20, use certificate pinning. This technique ensures that only 
authorized, or pinned, certificates are accepted when establishing a secure session. Any attempt to use a different 
certificate, even if issued by a trusted CA, will be rejected.

The only available method to trust a new certificate is by using the "ChangeConnection" system function of the HMI.

#### 4.2.4. How to renew the "PLC communication certificate" in WinCC Unified PCs and Comfort Panels?

TIA Portal download: When loading the HMI device, the PLC certificate is downloaded as part of the current configuration. 
The certificate is stored as "Trusted", and it is managed internally in the system (not visible in SIMATIC Runtime Manager 
or in the Panel).

ChangeConnection: After calling the "ChangeConnection" system function, the following scenarios can occur depending 
on the certificate used by the CPU:

• Self-signed: The certificate is displayed in the HMI as "Untrusted". Manual trust is possible.

• Signed by the same CA as the certificate downloaded via TIA Portal: Connection is established automatically, as the 
Certification Authority is known and trusted by the HMI.

• Signed by a different CA as the certificate downloaded via TIA Portal: The certificate is displayed in the HMI’s certificate 
manager as "Untrusted". From image/runtime versions V17 to V20, manual trust is not possible.

### 4.3. OPC UA

#### 4.3.1. Are certificates required to authenticate via username and password?

When a client session is authenticated via username and password, encryption of the password is essential to protect it 
from being stolen. Nonetheless, only the server’s certificate is required, as the client uses the server's public key to encrypt 
the password.

Therefore, a client without public key (certificate) and private key can connect to an endpoint, with SecurityMode "None", 
employing username and password authentication.

**NOTE** Regardless of the SecurityMode and authentication method used, the OPC UA server always sends its 
certificate to the client before starting the SecureChannel.

---

#### 4.3.2. What happens to the OPC UA connection if one certificate expires?

If the OPC UA connection has already been established, the communication between client and server is maintained until 
the SecureChannel is renewed.

#### 4.3.3. What is the SecureChannel and how often is it renewed?

Before initiating a session, OPC UA communication partners are required to establish a SecureChannel using the Public
Key Infrastructure for asymmetric signing and encryption. The SecureChannel is then used to transfer the symmetric key
between client and server without the risk of it being intercepted.

To initiate a SecureChannel, the OPC UA client sends a request to the server, specifying the desired Lifetime for the 
SecureChannel. The server then processes this request and responds to the client by generating a SecurityToken with a 
designated RevisedLifetime.

```powershell
√ Message : Encodeable Object
  > TypeId : ExpandedNodeId
  √ OpenSecureChannelResponse
    > ResponseHeader: ResponseHeader
      ServerProtocolVersion: 0
    √ SecurityToken: ChannelSecurityToken
      ChannelId: 2931577472
      TokenId: 1
      CreatedAt: Oct 26, 2023 14:19:51.402071500
      RevisedLifetime: 300000
      ServerNonce: 01
```

OPC UA clients are configured to reopen the SecureChannel when 75% of the SecurityToken Lifetime has elapsed, 
ensuring that they will receive a new SecurityToken before the previous one expires.

• The minimum lifetime of a SecureChannel in a SIMATIC controller is 300.000 milliseconds, 5 minutes, while the 
maximum lifetime is set to 3.600.000 milliseconds, 1 hour. Therefore, OPC UA servers running on SIMATIC controllers 
have a maximum SecurityToken Lifetime of 60 minutes. Due to the 75% rule, clients renew the SecureChannel every 
45 minutes.

• Other OPC UA servers have a configuration file where these minimum and maximum values can be modified. SIMATIC 
controllers don’t have an accessible configuration file that users can modify.

```xml
<TransportQuotas>
       <ChannelLifetime>300000</ChannelLifetime>
       <SecurityTokenLifetime>3600000</SecurityTokenLifetime>
</TransportQuotas>
```

---

#### 4.3.4. Why is it important to renew the SecureChannel?

Renewing the SecureChannel is important for two main reasons:

1. Attackers can decipher keys by analyzing encrypted messages. The more messages sent using the same key, the easier 
it becomes to perform "cryptanalytic attacks". Therefore, symmetric keys used in OPC UA communication should 
periodically be renewed to avoid this threat.

2. If a server/client certificate expires or its private key gets compromised, it is important to terminate ongoing 
communications. Applications are only able to verify their partners’ certificates during the process of opening or 
renewing the SecureChannel. Therefore, without a predefined expiration mechanism for the SecurityToken, clients 
and servers would not know if their partner’s certificate has expired or if it has been revoked.

**NOTE**

The OPC Foundation recommends shorter SecurityToken lifetimes for applications where the number 
of exchanged messages is expected to be high. This parameter can easily be modified in certain OPC 
UA client’s like UaExpert.

## Configure UaExpert

| Parameter | Value |
| --- | --- |
| General.PublishTimeout | 60000 |
| General.ResetAddServerDialog | true |
| General.RetrievalCons | false |
| General.SecureChannelLifetime | 3600000 |
| General.SessionTimeout | 1,2e+06 |
| General.ShowUTCTimestamps | false |
| General.SubscribeForModelChangeEvents | true |

#### 4.3.5. What limitations exist for GDS Push?

For the OPC UA Push function, an S7-1500 CPU, regardless of the type, has a configuration limit of 62 trust list entries as 
of firmware version V2.9.

• Each activated certificate-based service, web server and OPC UA server, "consumes" one entry for the certificate and 
an entry for the private key.

• A Certificate Revocation List (CRL) counts as one entry in the list of trusted certificates.

• A certificate that is used by different services counts as a single trust list entry.

Additionally, the push function has a maximum size limit for elements, such as certificates, of 4096 bytes.

To gain deeper understanding of GDS Push, refer to chapter 11.2.7 of the manual titled "SIMATIC S7-1500, ET 200MP, ET 
200SP, ET 200AL, ET 200pro, ET 200eco PN Communication"
(https://support.industry.siemens.com/cs/document/59192925).

---

## 5. Appendix

### 5.1. Service and support

##### SiePortal

The integrated platform for product selection, purchasing and support - and connection of Industry Mall and Online 
support. The SiePortal home page replaces the previous home pages of the Industry Mall and the Online Support Portal 
(SIOS) and combines them.

###### • Products & Services

In Products & Services, you can find all our offerings as previously available in Mall Catalog.

###### • Support

In Support, you can find all information helpful for resolving technical issues with our products.

• mySieportal

mySiePortal collects all your personal data and processes, from your account to current orders, service requests and 
more. You can only see the full range of functions here after you have logged in.

You can access SiePortal via this address: <u>sieportal.siemens.com</u>

##### Technical Support

The Technical Support of Siemens Industry provides you fast and competent support regarding all technical queries with 
numerous tailor-made offers – ranging from basic support to individual support contracts. 
Please send queries to Technical Support via Web form: support.industry.siemens.com/cs/my/src

##### SITRAIN – Digital Industry Academy

We support you with our globally available training courses for industry with practical experience, innovative learning 
methods and a concept that’s tailored to the customer’s specific needs.

For more information on our offered trainings and courses, as well as their locations and dates, refer to our web page:
siemens.com/sitrain

##### Industry Online Support app

You will receive optimum support wherever you are with the "Industry Online Support" app. The app is available for iOS 
and Android:

Available on the
App Store

```qrcode

---

### 5.2. Links and literature

**No. Topic**

| \1 | Siemens Industry Online Support https://support.industry.siemens.com |
| --- | --- |
| \2 | Link to this entry page of this application example https://support.industry.siemens.com/cs/ww/en/view/109769068 |

### 5.3. Change documentation

| Version | Date | Modification |
| --- | --- | --- |
| V1.0 | 09/2019 | First version |
| V2.0 | 03/2024 | Complete rework |
| V2.1 | 06/2025 | Minor rework; Added Information about Secure PG/PC and HMI communication |