## Diagnostics in the User Program with S7-1200

TIA Portal, S7-1200

https://support.industry.siemens.com/cs/ww/en/view/109483251

SIEMENS
Ingenuity for life

Industry Online Support
Home

---

All rights reserved
2018
Siemens AG 
ã

## Warranty and Liability

##### Note

The Application Examples are not binding and do not claim to be complete 
regarding the circuits shown, equipping and any eventuality. The Application 
Examples do not represent customer-specific solutions. They are only intended 
to provide support for typical applications. You are responsible for ensuring that 
the described products are used correctly. These Application Examples do not 
relieve you of the responsibility to use safe practices in application, installation, 
operation and maintenance. When using these Application Examples, you 
recognize that we cannot be made liable for any damage/claims beyond the 
liability clause described. We reserve the right to make changes to these 
Application Examples at any time without prior notice.

If there are any deviations between the recommendations provided in these 
Application Examples and other Siemens publications – e.g. Catalogs – the 
contents of the other documents have priority.

We do not accept any liability for the information contained in this document.
Any claims against us – based on whatever legal reason – resulting from the use of 
the examples, information, programs, engineering and performance data etc., 
described in this Application Example shall be excluded. Such an exclusion shall 
not apply in the case of mandatory liability, e.g. under the German Product Liability 
Act (“Produkthaftungsgesetz”), in case of intent, gross negligence, or injury of life, 
body or health, guarantee for the quality of a product, fraudulent concealment of a 
deficiency or breach of a condition which goes to the root of the contract 
(“wesentliche Vertragspflichten”). The damages for a breach of a substantial 
contractual obligation are, however, limited to the foreseeable damage, typical for 
the type of contract, except in the event of intent or gross negligence or injury to 
life, body or health. The above provisions do not imply a change of the burden of 
proof to your detriment.

Any form of duplication or distribution of these Application Examples or excerpts 
hereof is prohibited without the expressed consent of the Siemens AG.

##### Security informa- tion

Siemens provides products and solutions with industrial security functions that 
support the secure operation of plants, systems, machines and networks.
In order to protect plants, systems, machines and networks against cyber 
threats, it is necessary to implement – and continuously maintain – a holistic,
state-of-the-art industrial security concept. Siemens’ products and solutions only 
form one element of such a concept.

Customer is responsible to prevent unauthorized access to its plants, systems, 
machines and networks. Systems, machines and components should only be 
connected to the enterprise network or the internet if and to the extent necessary 
and with appropriate security measures (e.g. use of firewalls and network 
segmentation) in place.

Additionally, Siemens’ guidance on appropriate security measures should be 
taken into account. For more information about industrial security, please visit
http://www.siemens.com/industrialsecurity.

Siemens’ products and solutions undergo continuous development to make them 
more secure. Siemens strongly recommends to apply product updates as soon 
as available and to always use the latest product versions. Use of product 
versions that are no longer supported, and failure to apply latest updates may 
increase customer’s exposure to cyber threats.

To stay informed about product updates, subscribe to the Siemens Industrial 
Security RSS Feed under http://www.siemens.com/industrialsecurity.

---

All rights reserved
2018
Siemens AG 
ã

## Table of Contents

**Warranty and Liability ................................................................................................. 2**
**1 Introduction........................................................................................................ 4**
1.1 Overview............................................................................................... 4
1.2 Components used................................................................................ 6
**2 Test Project Engineering.................................................................................. 8**
2.1 Hardware configuration of the test project ........................................... 8
2.2 Configuring the diagnostic settings...................................................... 9
2.3 IP addresses, PROFIBUS addresses and device names.................... 9
2.4 Assigning the PROFINET device names........................................... 10
2.5 Integration into the user project.......................................................... 12
2.5.1 Integrating PLC objects...................................................................... 12
2.5.2 Integrating HMI objects (TP 700)....................................................... 16
2.6 Operation............................................................................................ 17
2.6.1 Overview............................................................................................. 17
2.6.2 Devices and module icon legend....................................................... 19
2.6.3 Diagnostics on the operator panel ..................................................... 20
2.6.4 Evaluating the diagnostic information ................................................ 22
**3 Valuable Information....................................................................................... 23**
3.1 Basics of system diagnostics for S7-1200 PLCs ............................... 23
3.2 Diagnostic instructions ....................................................................... 23
3.3 Details about how the program blocks work ...................................... 24
3.3.1 Complete overview............................................................................. 24
3.3.2 “UserDiag_Constants” tag table......................................................... 25
3.3.3 “UserDiag_DiagnosticsData” DB........................................................ 25
3.3.4 “UserDiag_HmiData” DB.................................................................... 28
3.3.5 “UserDiag_PLCCheck” FB................................................................. 30
3.3.6 “UserDiag_IOSystemCheck” FB........................................................ 33
3.3.7 "UserDiag_Diagnostics" FB................................................................ 36
3.3.8 FB "UserDiag_GetIPAddress"............................................................ 38
3.3.9 “UserDiag_DiagnosticsHmi” FB......................................................... 39
3.3.10 "UserDiag_Main" FB .......................................................................... 41
**4 Appendix .......................................................................................................... 42**
4.1 Service and Support........................................................................... 42
4.2 Links and literature............................................................................. 43
4.3 Change documentation...................................................................... 43

---

All rights reserved
2018
Siemens AG 
ã

## 1 Introduction

In the SIMATIC environment, diagnostics of devices and modules are grouped 
under the term ‘system diagnostics’. The monitoring functions are automatically 
derived from the hardware configuration.

All SIMATIC products have integrated diagnostic functions that allow you to detect 
and clear faults. The components automatically report a fault during operation and 
provide additional detailed information. Plant-wide diagnostics can minimize 
unscheduled downtimes.

Diagnostics with the user program enables you to identify faulty devices and 
modules. This allows you to program responses to diagnostic alarms in the user 
program.

##### Note

If diagnostic information is only displayed on different visual display devices and 
not used in the user program, it is recommended to use the S7-1200 controllers’ 
integrated system diagnostics. For more information about integrated system 
diagnostics, please refer to the following application example:
“System Diagnostics with S7-1500 and TIA Portal”

https://support.industry.siemens.com/cs/ww/en/view/68011497

### 1.1 Overview

This application example describes how to monitor a PROFINET IO system and a 
PROFIBUS DP master system with different devices in the user program. The 
devices are connected to an S7-1200 controller via PROFINET IO. The devices 
detect faults on their modules and send the diagnostic data to the assigned 
controller. The controller evaluates this diagnostic information with the diagnostic 
instructions in the user program and saves it to a global data block. The operator 
panel graphically displays the evaluated diagnostic information in one view per IO 
system or in a device view.

It considers the following states:

• State OK

• State FAULTY

• State LOST CONNECTION

• State DEACTIVATED

• State PROBLEM / MAINTENANCE

• State WAS FAULTY

• State HAD LOST CONNECTION

• State WAS DEACTIVATED

• State WAS PROBLEM / MAINTENANCE

---

All rights reserved
2018
Siemens AG 
ã

The following figure shows the hardware that was used for testing.

Figure 1-1: Hardware overview

STEP 7
TIA POR
S7-1200
Diagnoseinformationen
TP700
PROFINET IE
Störung
Störung
Störung
ET 200SP PN
ET 200MP PN
ET 200SP PN

##### Note

This application example influences the cycle time of your program. The cycle 
time extension size depends on the plant configuration to be monitored and the 
CPU used. Therefore, no approximate values can be given at this point.

You can copy the PLC objects of the application example directly to your project. 
All you have to do is customize the constants to define the size of the diagnostic 
structure (highest device number or slave address in the IO system). In addition, 
customize the hardware ID of your CPU and IO system.

---

All rights reserved
2018
Siemens AG 
ã

### 1.2 Components used

This application example was created with the following hardware and software 
components:

Table 1-1: Hardware and software components

| Component | Qty. | Article number | Note |
| --- | --- | --- | --- |
| CPU 1214C DC/DC/DC | 1 | 6ES7214-1AG40-0XB0 | Alternatively, any other CPU S7-1200 can be used. |
| Memory Card, 12 Mbyte | 1 | 6ES7954-8LE02-0AA0 | optional |
| SCALANCE X208 | 1 | 6GK5208-0BA10 | Alternatively, any other Switch can be used. |
| IM 155-5 PN ST | 1 | 6ES7155-5AA00-0AB0 | ET 200MP |
| Digital input module, DI 32x24VDC HF | 1 | 6ES7521-1BL00-0AB0 | Diagnostics can be configured |
| Digital output module, DQ 32x24VDC/0.5A ST | 1 | 6ES7522-1BL00-0AB0 | Diagnostics can be configured |
| Analog input module, AI 8xU/I/RTD/TC ST | 1 | 6ES7531-7KF00-0AB0 | Diagnostics can be configured |
| Front connector, screw-type, 40-pin | 2 | 6ES7592-1AM00-0XB0 | - |
| Front connector, push-in type, 40-pin | 1 | 6ES7592-1BM00-0XB0 | - |
| IM 155-6 PN ST, incl. server module, incl. bus adapter 2xRJ45 | 1 | 6ES7155-6AA00-0BN0 | ET 200SP |
| DI 8x24VDC HF | 1 | 6ES7131-6BF00-0CA0 | Diagnostics can be configured |
| DQ 8x24VDC/0.5A HF | 1 | 6ES7132-6BF00-0CA0 | Diagnostics can be configured |
| AQ 4xU/I ST | 1 | 6ES7135-6HD00-0BA1 | Diagnostics can be configured |
| BU type A0, 16 push-in, 2 infeed terminals separated (digital/analog, max. 24VDC/10A) | 1 | 6ES7193-6BP00-0DA0 | - |
| BU type A0, 16 push-in, 2 infeed terminals bridged (digital/analog, 24VDC/10A) | 2 | 6ES7193-6BP00-0BA0 | - |
| IM 155-6 PN ST, incl. server module, incl. bus adapter 2xRJ45 | 1 | 6ES7155-6AA00-0BN0 | ET 200SP |
| DI 16x24VDC ST | 1 | 6ES7131-6BH00-0BA0 | Diagnostics can be configured |
| BU-Typ A0, 16 Push-In, 2 Einspeiseklemmen getrennt (Digital-/Analog, max.24VDC/10A) | 1 | 6ES7193-6BP00-0DA0 | - |
| TP700 Comfort | 1 | 6AV2124-0GC01-0AX0 | - |

---

All rights reserved
2018
Siemens AG 
ã

| Component | Qty. | Article number | Note |
| --- | --- | --- | --- |
| STEP 7 Professional V14 SP1 | 1 | 6ES7822-1...04-.. | - |
| WinCC Comfort V14 SP1 | 1 | 6AV210-...4-0 | - |
| STEP 7 Professional V15 | 1 | 6ES7822-1...05-.. | - |
| WinCC Comfort V15 | 1 | 6AV210-...5-0 | - |

This application example consists of the following components:

Table 1-2: Components of the application example

| Component | File name |
| --- | --- |
| Documentation | 109483251_User_defined_diagnostics_S7-1200_DOC_V21_en.pdf |
| STEP 7 project TIA V14 SP1 | 109483251_Diag1200_TIAV14SP1_PROJ_V21.zip |
| STEP 7 project TIA V15 | 109483251_Diag1200_TIAV15_PROJ_V21.zip |

---

All rights reserved
2018
Siemens AG 
ã

## 2 Test Project Engineering

### 2.1 Hardware configuration of the test project

Figure 2-1: Hardware configuration of the test project

L+ M L+ M L+ M
SIMATIC HMI 
SIMATIC SCALANCE
TP700 
CPU 1214C XF208
Comfort
PN PN PN
L+ M L+ M L+ M L+ M
SIMATIC DI DQ AI
IM 155-5 PN 32x24VDC 32x24VDC 8xU/I/RTD/TC
PN PN
L+ M L+ M L+ M L+ M
SIMATIC DI DQ AQ
IM 155-6 PN 8x24VDC 8x24VDC 4xU/I
PN PN
L+ M L+ M L+ M
SIMATIC DI AI
IM 155-6 PN 8x24VDC 4xRTD/TC
PN PN

---

All rights reserved
2018
Siemens AG 
ã

### 2.2 Configuring the diagnostic settings

You can enable the module-specific diagnostic settings separately for each module 
of your project. You can configure the diagnostic settings in the device view in the 
module properties.

The following diagnostic settings can be made:

• No supply voltage L+

• Wire break

• Short circuit to ground

• etc.

##### Note

For examples of how to configure the diagnostic settings, please refer to the 
following application example:

“System Diagnostics with S7-1500 and TIA Portal”

https://support.industry.siemens.com/cs/ww/en/view/68011497

### 2.3 IP addresses, PROFIBUS addresses and device names

The application example uses the following device numbers, device names, IP 
addresses and PROFIBUS addresses:

Table 2-1: Overview of IP addresses, PROFIBUS addresses and device names

| Component | Device number | IP address | Device name |
| --- | --- | --- | --- |
| SIMATIC CPU 1214C | 0 | 192.168.0.1 | PLC_1 |
| SIMATIC IM 155-6 PN | 1 | 192.168.0.2 | ET200SP_1 |
| SIMATIC IM 155-5 PN | 2 | 192.168.0.3 | ET200MP_2 |
| SIMATIC IM 155-6 PN | 3 | 192.168.0.10 | ET200SP_10 |
| SIMATIC HMI TP700 | - | 192.168.0.4 | HMI_1 |

---

All rights reserved
2018
Siemens AG 
ã

### 2.4 Assigning the PROFINET device names

For all PROFINET devices to communicate with each other, assign a PROFINET 
device name to the devices.

To do this, proceed as follows:

1. Open the network view.

2. Right-click the PROFINET connection line and select “Assign device name”.
The “Assign PROFINET device name” dialog is displayed.

Figure 2-2: Network view

DiagnoseAWP_S7-1200_V20 Devices & networks

Network Connections HMI connection Relations IO system:

PLC_1
CPU 1214C

HMI_1
TPZ00 Comfort
Highlight IO system:
PLC_1.PROFINET IO-System (100)
Highlight sync domain:
SyncDomain_1

Cut Ctrl+X
Copy Ctrl+C
Paste Ctrl+V

Delete Del
Rename F2
Assign to new DP master / IO controller

Compile
Download to device

Go online Ctrl+K
Go offline Ctrl+M
Online & diagnostics Ctrl+D

Assign device name

Assign PROFisale address
Receive alarms
Update and display forced operands

Show catalog Ctrl+Shift+C

Properties Alt+Enter

---

All rights reserved
2018
Siemens AG 
ã

3. In “PROFINET device name”, select the device name.

4. From the list of accessible devices, select the device to which you want to 
assign the PROFINET device name. You can update the list by clicking
“Update list”.

5. Click “Assign name”. 
This assigns the device name.

Figure 2-3: “IO Systems” screen

Assign PROFINET device name.

Configured PROFINET device
PROFINET device name: et200sp_1
Device type: IM 155-6 PN ST

Online access
Type of the PG/PC interface: PN/IE
PG/PC interface: Intel(R) 82574L Gigabit Network Connection

Device filter
Only show devices of the same type
Only show devices with bad parameter settings
Only show devices without names

Accessible devices in the network:
IP address MAC address Device type PROFINET device name Status
0.0.0.0 28-63-36-02-91-90 ET200SP — No device name assigned
0.0.0.0 00-1B-1B-5F-E5-59 ET200SP — No device name assigned

Flash LED

Update list Assign name

6. Repeat steps 3 through 5 for all devices.

---

All rights reserved
2018
Siemens AG 
ã

### 2.5 Integration into the user project

The application example described here can be fully integrated into your project. 
The necessary steps are described below.

Chapter <u>3.3</u> describes how the program blocks work.

#### 2.5.1 Integrating PLC objects

##### Copying PLC objects

You can integrate the PLC objects of the application example directly into your 
project.

To do this, proceed as follows:

1. Use TIA Portal to open your project.

2. Open the application example in a second instance of TIA Portal.
Alternatively, you can open the application example as a reference project for 
your project.

3. In the application example, “PLC_1 > PLC tags”, copy the “UserDiag” folder 
and paste the folder into the same location in your project.

4. In the application example, “PLC_1 > PLC data types”, copy the “UserDiag” 
folder and paste the folder into the same location in your project.

5. In the application example, “PLC_1 > Program blocks”, copy the “UserDiag” 
folder and paste the folder into the same location in your project.

6. Call the FB "UserDiag_Main" in the first network of the OB "Main".

Abbildung 2-4: Call in OB "Main"

%DB2000
"UserDiag_
InstMain"

%FB2000
"UserDiag_Main"
EN ENO

7. Close the second instance of TIA Portal with the application example.

---

All rights reserved
2018
Siemens AG 
ã

##### Customizing constants to the project

The user constants allow you to customize the size of the diagnostic structures in 
the “UserDiag_DiagnosticsData” DB to your project.

To do this, proceed as follows:

1. In “PLC_1 > PLC tags > UserDiag > UserDiag_Constants”, open the “User 
constants”.

2. Optional: If the highest device number/slave address in your project is greater 
than 128, modify the “USERDIAG_DEVICES_PER_
IO_SYSTEM_UPPER_LIM” constant to the highest device number/slave 
address.

Figure 2-5: “User constants”

|  | Name | Data type | Value | Comment |
| --- | --- | --- | --- | --- |
| 1 | USERDIAG_DEVICES_PER_IO_SYSTEM_UPPER_LIM | Int | 16 | Upper limit of devices/slaves per IO-System |
| 2 | USERDIAG_DEVICES_PER_SCREEN_UPPER_LIM | Int | 16 | Upper limit of devices per HMI screen |
| 3 | USERDIAG_IO_SYSTEMS_UPPER_LIM | Int | 1 | Upper limit of IO-Systems/DP-Mastersystem... |
| 4 | USERDIAG_IOSYSTEM_1 | Int | 1 | Index for first IO-System/DP-Mastersystem |
| 5 | USERDIAG_SLOTS_PER_DEVICE_UPPER_LIM | Int | 31 | max number of modules per device |
| 6 | USERDIAG_SLOTS_PER_PLC_UPPER_LIM | Int | 9 | max number of local modules |
| 7 | USERDIAG_SLOTS_PER_SCREEN_UPPER_LIM | Int | 31 | Upper limit of slots per HMI screen |

##### Customizing the call of the diagnostic blocks to the project

The hardware IDs of the controller and the IO systems in your project may differ 
from the hardware IDs in the application example. The hardware IDs are 
customized when calling the diagnostic blocks in the "UserDiag_Diagnostics" FB.

##### Note

If you upgrade TIA Portal V13 SP1 projects to TIA Portal V14, you will not find a 
hardware ID for the PLC named “Local~Device” in the system constants. In this 
case, assign the value “32” to the “plcHwId” parameter of the 
“UserDiag_PLCCheck” FB (see step 4).

---

All rights reserved
2018
Siemens AG 
ã

To do this, proceed as follows:

1. Open the "UserDiag_Diagnostics" FB.

2. In “PLC_1 > PLC tags > Default tag table”, open the system constants.

3. Vertically split the editor area.

4. Use drag and drop to assign the PLC hardware ID to the “plcHwId” parameter 
of the “UserDiag_PLCCheck” FB.

5. The PLC’s diagnostic structure has already been assigned to the “plcData” 
parameter of the “UserDiag_PLCCheck” FB.

6. Use drag and drop to assign the IO system hardware ID to the “ioSystemHwId” 
parameter of the “UserDiag_IOSystemCheck” FB.

7. The diagnostic structure of the appropriate IO system (PROFINET IO system 
or DP master system) has already been assigned to the “ioSystemData” 
parameter of the “UserDiag_IOSystemCheck” FB. 
Please note that each IO system gets its own index, for example 
“IO_SYSTEM1”.

Figure 2-6: Customizing the call to the project

PLC tags

Name Data type Value

28 ET2005P_10-Head Hw_SubModule 294
29 ET2005P_10-PROFINETSchnittstelle Hw_Interface 295
30 ET2005P_10-Servermodul_1_1 Hw_SubModule 296
31 ET2005P_10-PROFINETSchnittstelle~Port... Hw_Interface 298
32 ET2005P_10-PROFINETSchnittstelle~Port... Hw_Interface 299
33 Local-PROFINET_IO-System Hw_IoSystem 269
34 ET2005P_1-Proxy Hw_SubModule 274
35 ET2005P_1-IODevice Hw_Device 272
36 ET2005P_1-Head Hw_SubModule 275
37 ET2005P_1-PROFINETSchnittstelle Hw_Interface 276
38 ET2005P_1-Servermodul_1 Hw_SubModule 271
39 ET2005P_1-AQ_4XU_LST_1 Hw_SubModule 270
40 ET2005P_1-DQ_8x24VDC_0_5A_HF_1 Hw_SubModule 277
41 ET2005P_1-PROFINETSchnittstelle~Port_2 Hw_Interface 277
42 ET2005P_1-PROFINETSchnittstelle~Port_1 Hw_Interface 280
43 ET200MP_2-Proxy Hw_SUBMODULE 284
44 ET200MP_2-IODevice Hw_DEVICE 282
45 ET2005P_10-Proxy Hw_SubModule 293
46 ET2005P_10-IODevice Hw_Device 291
47 ET2005P_1-DI_8x24VDC_HF_1 Hw_SubModule 300
48 ET2005P_10-DI_8x24VDC_HF_1 Hw_SubModule 297
49 Local-MC Hw_SubModule 51
50 Local-Common Hw_SubModule 50
51 Local-Device Hw_Device 32
52 Local-Configuration Hw_SubModule 33
53 Local-Exec Hw_SubModule 52
54 Local Hw_SubModule 49
55 Local-PROFINETSchnittstelle_1 Hw_Interface 64
56 Local-PROFINETSchnittstelle_1~Port_1 Hw_Interface 65
57 OB_Cyclic interrupt OB_Cyclic 30
58 OB_Diagnostic error interrupt OB_DIAG 82
59 OB_PullPlug OB_Any 83

Block title:

Network 1: Call startup and diagnostics of PLC

Comment

#instiPLCCheck

EN ENO
error status statusID

Network 2: Call startup and diagnostics of Devices from IO system

Comment

#instiOSystemChck

EN ENO
error status statusID

#B2001 "UserDiagIOSystemCheck"

EN ENO
error status statusID

#ioSystemHwld ioSystemData

---

All rights reserved
2018
Siemens AG 
ã

##### Calling "UserDiag_GetIPAddress"

With the FB "UserDiag_GetIPAddress" you can read the IP addresses of the 
devices of a PROFINET IO system.

Insert additional instances of the FB "UserDiag_GetIPAddress" in the FB 
"UserDiag_Main" as needed. For each PROFINET IO system a separate instance 
of the FB "UserDiag_GetIPAddress" is necessary.

Notice that each IO system requires a unique index, e.g. 
"USERDIAG_IOSYSTEM_1".

Figure 2-7: Call of "UserDiag_GetIPAddress"

#instUserDiagGet
IPAddress

%FB2005
"UserDiag_GetIPAddress"

EN ENO
error ...
status ...
statusID ...
"UserDiag_
DiagnosticsData".
ioSystemData["U
SERDIAG_
IOSYSTEM_1"] 
ioSystemData

##### Enable System memory

The system memory bit "DiagStatusUpdate" is used in the application example to
evaluate the diagnosis at change of the diagnostic state. Therefore you have to 
enable the system memory of the PLC.

To do this, proceed as follows:

1. Open the properties of the PLC.

2. Navigate to "System and clock memory".

3. Enable "Enable the use of system memory byte".

Abbildung 2-8: Enable System memory

PLC_1 [CPU 1214C DC/DC/DC]
Properties Info Diagnostics

General IO tags System constants Texts

Startup
Cycle
Communication load
System and clock memory
Web server
Multilingual support
Time of day
Protection & Security
Configuration control
Connection resources
Overview of addresses

System and clock memory

System memory bits

3 Enable the use of system memory byte
Address of system memory byte (MBx): 1
First cycle: %M1.0 (FirstScan)
Diagnostic status changed: %M1.1 (DiagStatusUpdate)
Always 1 (high): %M1.2 (AlwaysTRUE)
Always 0 (low): %M1.3 (AlwaysFALSE)

---

All rights reserved
2018
Siemens AG 
ã

#### 2.5.2 Integrating HMI objects (TP 700)

##### Note

Alternatively, you can copy the entire HMI from the application example to your 
project.

You can integrate the HMI objects of the application example directly into your 
project.

To do this, proceed as follows:

1. Use TIA Portal to open your project.

2. Open the application example in a second instance of TIA Portal.
Alternatively, you can open the application example as a reference project for 
your project.

3. In the application example, “HMI_1 > HMI tags”, copy the “UserDiag” folder 
and paste the folder into the same location in your project.

4. Open the “UserDiag_TagTable” HMI tag table and in the “Connection” column, 
set the HMI connection of your HMI.

Figure 2-9: “UserDiag_TagTable” HMI tag table

| Name | Data type | Connection | PLC name | PLC tag |
| --- | --- | --- | --- | --- |
| UserDiag_HmiData_actIOSystem | Int | HM_Connection_1 | PLC_1 | UserDiag_HmiData.actIOSystem |
| UserDiag_HmiData_actDeviceOfScreen | Int | HM_Connection_1 | PLC_1 | UserDiag_HmiData.actDeviceOfScreen |
| UserDiag_HmiData_ioSlotDiagnosticsLoaded | Bool | HM_Connection_1 | PLC_1 | UserDiag_HmiData.ioSlotDiagnosticsLoaded |
| UserDiag_HmiData_actScreenDevices | Int | HM_Connection_1 | PLC_1 | UserDiag_HmiData.actScreenDevices |
| UserDiag_HmiData_maxScreensDevices | Int | HM_Connection_1 | PLC_1 | UserDiag_HmiData.maxScreensDevices |
| UserDiag_HmiData_ackWaitTime | Int | HM_Connection_1 | PLC_1 | UserDiag_HmiData.ackWaitTime |

[Image: Image186]
5. In the application example, “HMI_1 > Screen management > Pop-up screens”, 
copy the “UserDiag” folder and paste the folder into the same location in your 
project.

6. In the application example, “HMI_1 > Screens > 001_Application”, copy the 
“Topic_001.0_PLC”, “Topic_002.0_IO_System” and “Topic_003.0_Messages” 
screens and paste the screens into your project.

7. In the screens’ properties, select your template or no template. 
You can also copy the templates in “HMI_1 > Screen management > 
Templates” to your project.

8. To be able to open the screens on the operator panel, integrate the screens 
into your screen navigation.

---

All rights reserved
2018
Siemens AG 
ã

### 2.6 Operation

Operation is shown using the TP700 Comfort operator panel.

#### 2.6.1 Overview

The following figure shows the user interface of the TP700 Comfort.

Figure 2-10: HMI screens overview

Topic
Siemens Industry Online Support
**Slide-in screen navigation**

PLC overview

IO system device overview

System diagnostics

Device detail view pop-up

Reset saved state
Show legend

Enable “faulty devices” filter

Close pop-up

##### Start screen

The “Topic” screen shows the start screen of the application example.

##### Screen navigation

The slide-in screen is used for screen navigation.

---

All rights reserved
2018
Siemens AG 
ã

##### PLC overview

The “PLC” screen shows the diagnostic information of the controller and its 
modules.

• Clicking the “Legend” button opens the pop-up screen with the different 
appearances of the icons for the devices and modules and their meaning.

• The “Reset overview” button allows you to delete the saved state of the 
controller.

• In the “Wait time Acknowledge” input field, you can enter a wait time after 
startup for automatic reset of the saved error state.

##### Overview of the devices in the IO system

The “IO-Systems” screen shows the configured devices of the selected IO system.

• The “Filter” button allows you to view only faulty devices.

• Clicking the “Legend” button opens the pop-up screen with the different 
appearances of the icons for the devices and modules and their meaning.

• The “Reset overview” button allows you to delete the saved state of the current 
IO system.

• Clicking the button of a device opens the pop-up screen with the detail view of 
the device.

##### Detail view of a device

The pop-up screen displays:

• Diagnostic information of the modules of a device

• Index ft he IO system

• Device number

• IP address

• Device name

Clicking the “close” button closes the pop-up screen.

##### System diagnostics

The “System diagnostics” screen shows the System diagnostics view.

##### Legend

The pop-up screen shows the different appearances of the icons for the devices 
and modules and their meaning.

---

All rights reserved
2018
Siemens AG 
ã

#### 2.6.2 Devices and module icon legend

The devices and modules can each have different states. The following table 
shows the appearance of the icons and their meaning.

Table 2-2: Icon legend

| State | <u>Appearance</u> | Meaning |
| --- | --- | --- |
| 1 |  | State OK |
| 2 |  | State FAULTY |
| 3 |  | State LOST CONNECTION |
| 4 |  | State DEACTIVATED |
| 5 |  | State PROBLEM / MAINTENANCE |
| 12 |  | State WAS FAULTY |
| 13 |  | State HAD LOST CONNECTION |
| 14 |  | State WAS DEACTIVATED |
| 15 |  | State WAS PROBLEM / MAINTENANCE |

---

All rights reserved
2018
Siemens AG 
ã

#### 2.6.3 Diagnostics on the operator panel

**Diagnostics of “missing module on ET200SP_1”**

$$
\text{"}
$$

To diagnose the fault, proceed as follows:

1. Remove the DQ8 module from slot 2 of the ET200SP_1.

2. On the operator panel, open the “IO System” screen.

3. For detailed information about the fault, click the button of device 1.

Figure 2-11: “IO Systems” screen

IO System 2 SIEMENS
7/25/2017 6:08 PM
3
1 2 3
Filter Legend Reset overview

4. The pop-up screen with the detail view of device 1 opens. The view displays an 
error on slot 2, the index of the IO system, the device number, the IP address 
and the device name.

Clicking “close” closes the pop-up screen.

Figure 2-12: Pop-up screen with the detail view of the device

IO Systeme SIEMENS
25.07.2017 13:40
00 01 02 03 04
IO System 100 Device 1 IP Adresse 192 168 0 2
Name ET2005P_1 schließen

---

All rights reserved
2018
Siemens AG 
ã

5. Open the “System diagnostics” screen if you want to view the System 
diagnostics information for the fault.

Figure 2-13: “System diagnostics” screen

System diagnostics
SIEMENS
7/25/2017 5:53 PM

S7-1200-Station_1 \ PLC_1 \ PROFINET-Schnittstelle_1 \ PROFINET IO-Sys.../0.5A HF_1

> Status
> Name DQ 8x24VDC/0.5A HF_1
> Operating state
> Rack
> Slot 2
> Type IOSubmodule
> Order number (6ES7 132-6BF00-0CA0)
> Address 277*
> Plant designation
> Location identifier
> Manufacturer ID (SIEMENS AG)
> Hardware version (0)
> Profile ID
> Specific profile data
> I&M data version
> Error text Hardware component removed or missing

##### 6. Reinsert the module into slot 2.

The icon of device 1 turns green with a yellow border. This means that the 
device was faulty and the fault has been cleared.

Clicking “Reset overview” deletes the saved state and the border color returns 
to black.

Figure 2-14: “IO Systems” screen

IO System
SIEMENS
7/26/2017 10:04 AM
1
2
3
Filter
Legend
Reset overview

---

All rights reserved
2018
Siemens AG 
ã

#### 2.6.4 Evaluating the diagnostic information

This chapter uses an example to show you where to find the diagnostic information 
in the event of an error. Retrieving this information in your program allows you to 
program specific responses to certain faults in the plant.

##### Evaluating the “missing module on ET200SP_1” diagnostic information

Remove the DQ8 module from slot 2 of the ET200SP_1 to cause a fault. The fault 
is saved in the “UserDiag_DiagnosticsData” DB. Clicking the “Monitor all” button 
allows you to view the actual values of the tags.

The data in the “UserDiag_DiagnosticsData” DB have to be interpreted as follows:

1. The “ioSystemData > ioSystemData[1]” structure displays the saved diagnostic 
information of the entire IO system. The “error = TRUE” tag indicates a fault in 
the IO system.

2. The “devices > devices[1]” structure displays the saved diagnostic information 
of the device with device number 1. The “error = TRUE” tag indicates a fault in 
the device.

3. The “slot > slot[2]” structure displays the saved diagnostic information of the 
module on slot 2. The “error = TRUE” tag indicates the fault in the module. The 
“exists = FALSE” tag indicates that the module is missing.

Figure 2-15: Diagnostic information about the fault in the “UserDiag_DiagnosticsData” DB
[Image: Image226]

|  | Name | Data type | Start value | Monitor value | Comment |
| --- | --- | --- | --- | --- | --- |
| 1 | Static |  |  |  |  |
| 2 | plcData | *typeUserDiagPLC |  |  | PLC diagnostics data |
| 3 | ioSystemData | Array[1..*USERDIAG_... |  |  | IO-System diagnostics data |
| 1 | ioSystemData[1] | *typeUserDiagIOSyst.. |  |  | IO-System diagnostics data |
| 5 | hardwareId | HW_IOSYSTEM | 16#0000 | 269 | Hardware identifier of the IO-System/DP-Master |
| 6 | number | UInt | 0 | 100 | IO-System/DP-Mastersystem number |
| 7 | error | Bool | FALSE | TRUE | TRUE: Error in IO-System/DP-Mastersystem |
| 8 | firstDevice | Int | 0 | 1 | device number (index) of first device in IO System |
| 9 | lastDevice | Int | 0 | 3 | device number (index) of last device IO System |
| 10 | actualConfiguredDe... | Int | 0 | 3 | Number of configured devices/slaves in IO-System |
| 11 | devices | Array[1..*USERDIAG_... |  |  | Device/Slave information |
| 2 | devices[1] | *typeUserDiagDevice* |  |  | Device/Slave information |
| 13 | hardwareId | HW_DEVICE | 16#0000 | 272 | Hardware identifier of the device/slave |
| 14 | number | UInt | 0 | 1 | Profinet device number/Profibus address of the |
| 15 | name | String[50] | " | 'ET200SP_1' | Name of the device/slave |
| 16 | ipAddress | IP_V4 |  |  | IP address of the device |
| 17 | state | USInt | 0 | 2 | Status of the device/slave. 1:ok; 2:faulty, 3:lost |
| 18 | exists | Bool | FALSE | TRUE | TRUE: Device/slave is existing |
| 19 | configured | Bool | FALSE | TRUE | TRUE: Device/slave is configured |
| 20 | faulty | Bool | FALSE | TRUE | TRUE: Device/slave is faulty |
| 21 | problem | Bool | FALSE | TRUE | TRUE: Device/slave has a problem |
| 22 | disabled | Bool | FALSE | FALSE | TRUE: Device/slave is disabled |
| 23 | error | Bool | FALSE | TRUE | TRUE: Error in device/slave |
| 24 | nextDevice | Int | 0 | 2 | device number of next device |
| 25 | actualConfig... | Int | 0 | 5 | Number of configured slots in the device/slave |
| 26 | slots | Array[0..*USERDIAG_... |  |  | Slot information of a device/slave |
| 27 | slots[0] | *typeUserDiagSlot* |  |  | Slot information of a device/slave |
| 28 | slots[1] | *typeUserDiagSlot* |  |  | Slot information of a device/slave |
| 3 | slots[2] | *typeUserDiagSlot* |  |  | Slot information of a device/slave |
| 30 | hardware... | HW_IO | 16#0000 | 277 | Hardware identifier of the module which is in use |
| 31 | state | USInt | 0 | 2 | Status of the device/slave. 1:ok; 2:faulty, 3:lost |
| 32 | exists | Bool | FALSE | FALSE | TRUE: Modul is existing |
| 33 | config... | Bool | FALSE | TRUE | TRUE: Modul is configured |
| 34 | faulty | Bool | FALSE | TRUE | TRUE: Modul is faulty |
| 35 | problem | Bool | FALSE | TRUE | TRUE: Modul has a problem |
| 36 | disabled | Bool | FALSE | FALSE | TRUE: Modul is disabled |
| 37 | error | Bool | FALSE | TRUE | TRUE: Error in module which is inserted in the s |

---

All rights reserved
2018
Siemens AG 
ã

## 3 Valuable Information

### 3.1 Basics of system diagnostics for S7-1200 PLCs

System diagnostics allow you to analyze errors in the system and generate alarms 
with a textual error description and the error location.

System diagnostics are integrated in the S7-1200 PLC’s firmware and operate 
independently of the cyclic user program. Therefore, system diagnostics are also 
available in STOP mode of the PLC. Faults are detected immediately and reported 
to the higher-level HMI devices, the web server, the LED displays on the relevant 
module and TIA Portal even in STOP mode. This ensures that system diagnostics 
are always synchronized with the actual plant status.

All connected diagnostic display media are supplied with the same system 
diagnostic information by a uniform mechanism.

##### Note

For more information about integrated system diagnostics, please refer to the 
following application example:

“System Diagnostics with S7-1500 and TIA Portal”

https://support.industry.siemens.com/cs/ww/en/view/68011497

### 3.2 Diagnostic instructions

If you want to retrieve the diagnostic information of a device in the user program, 
STEP 7 provides the following instructions:

Table 3-1: Diagnostic instructions overview

| Instruction | Description |
| --- | --- |
| RD_SINFO | Read start information of current OB |
| LED | Read LED status |
| Get_IM_Data | Read identification and maintenance data |
| GET_NAME | Read name of a module |
| GetStationInfo | Read information of an IO device |
| DeviceStates | Read module status information in an IO system |
| ModuleStates | Read module status information of a module |
| GET_DIAG | Read diagnostic information (not part of this application example) |

##### Note

For more information about the diagnostic instructions, please refer to the 
STEP 7 Professional V14 SP1 system manual:

---

All rights reserved
2018
Siemens AG 
ã

### 3.3 Details about how the program blocks work

This chapter describes the program blocks of the application example.

#### 3.3.1 Complete overview

The following figure shows the program structure of the entire STEP 7 project.

Figure 3-1: Program structure

**UserDiag_**
**HmiData**

**Main**

**UserDiag_**
Main

UserDiag_
**Diagnostics** Data

**Startup**

UserDiag_
Diagnostics

UserDiag_
**PLCCheck**

UserDiag_
**IOSystem Check**

**GetStation Info**

**GEO2LOG**

**LOG2GEO**

**Device States**

**Get_Name**

**Module** States

**Data blocks**

**User program**

**System blocks**

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.2 “UserDiag_Constants” tag table

The “UserDiag_Constants” table defines the user constants. With the user 
constants, you define the size of the diagnostic structures in the 
“UserDiag_DiagnosticsData” DB.

<u>Table 3-2: “UserDiag&#95;Constants” tag table</u>

| Constant | Type | Value | Meaning |
| --- | --- | --- | --- |
| USERDIAG_SLOTS_PER_DEVICE_UPPER_LIM | Int | 31 | Maximum number of modules per device. |
| USERDIAG_SLOTS_PER_PLC_UPPER_LIM | Int | 9 | Maximum number of local modules. |
| USERDIAG_DEVICES_PER_IO_SYSTEM_UPPER_LIM | Int | 16 | Upper array limit for devices per IO system. |
| USERDIAG_IO_SYSTEMS_UPPER_LIM | Int | 1 | Upper array limit for IO systems. |
| USERDIAG_DEVICES_PER_SCREEN_UPPER_LIM | Int | 16 | Upper array limit for number of devices per HMI screen. |
| USERDIAG_SLOTS_PER_SCREEN_UPPER_LIM | Int | 31 | Upper array limit for number of slots per HMI screen. |
| USERDIAG_IO_SYSTEM_1 | Int | 1 | Index for first IO system. |

#### 3.3.3 “UserDiag_DiagnosticsData” DB

The “UserDiag_DiagnosticsData” DB saves the hardware IDs and the diagnostic 
information of the controller and the IO systems.

The following table shows the structure of the “UserDiag_DiagnosticsData” DB.

Table 3-3: “UserDiag_DiagnosticsData” DB

| Tag name | Data type | Meaning |
| --- | --- | --- |
| plcData | UserDiag_typePLC | Diagnostic data of the PLC. (See plcData [UserDiag typePlc]) |
| ioSystemData | Array [1.. USERDIAG_IO_SYSTEMS_UPPER_LIM] of UserDiag_typeIoSystem | Diagnostic data of the IO systems. (See ioSystemData [UserDiag typeIoSystem]) |
| startDiag | Bool | Start Diagnostics. |

For greater clarity, the individual tags of the components were grouped into the 
following PLC data types.

---

All rights reserved
2018
Siemens AG 
ã

**plcData [UserDiag_typePlc]**

The PLC data type contains the information of the controller and the modules in the 
local slots.

Table 3-4: “UserDiag_typePlc” PLC data type

| Tag name | Data type | Meaning |
| --- | --- | --- |
| hardwareId | HW_DEVICE | Hardware ID of the PLC. |
| Error | Bool | Error pending. |
| actualConfiguredSlots | Int | Number of configured local slots. |
| Slots | Array [0..USERDIAG_SLOTS_PER_PLC_UPPER_LIM] of "UserDiag_typeSlot" | See Slots [UserDiag_typeSlot]. |

##### Slots [UserDiag_typeSlot]

The PLC data type contains the information of a module (slot).

Table 3-5: “UserDiag_typeSlot” PLC data type

| Tag name | Data type | Meaning |
| --- | --- | --- |
| hardwareId | HW_IO | Hardware ID of the module in the slot |
| state | USInt | Module state (see 2.6.2) |
| exists | Bool | Module exists |
| configured | Bool | Module configured |
| faulty | Bool | Module faulty |
| problem | Bool | Module has a problem |
| disabled | Bool | Module disabled |
| error | Bool | Error in the module |

**ioSystemData [UserDiag_typeIoSystem]**

The PLC data type contains the information of an IO system.

Table 3-6: “UserDiag_typeIoSystem” PLC data type

| Tag name | Data type | Meaning |
| --- | --- | --- |
| hardwareId | HW_IOSSYSTEM | Hardware ID of the IO system. |
| Number | UInt | Number of the IO system / DP master system. |
| Error | Bool | Error in the IO system / DP master system. |
| firstDevice | Int | Number of the first device in the IO system / DP master system. |
| lastDevice | Int | Number of the last device in the IO system / DP master system. |
| actualConfiguredDevices | Int | Number of configured devices in the IO system / DP master system. |
| Devices | Array [0..USERDIAG_DEVICES_PER_IO_SYSTEM_UPPER_LIM] of "UserDiag_typeDevice" | Diagnostic structure of the devices. See Devices [UserDiag_typeDevice]. |

---

All rights reserved
2018
Siemens AG 
ã

**Note**

The array index of the “device” tag corresponds to the device number.

##### Devices [UserDiag_typeDevice]

The PLC data type contains the information of a device (device or slave).

Table 3-7: “UserDiag_typePlc” PLC data type

| Tag name | Data type | Meaning |
| --- | --- | --- |
| hardwareId | HW_DEVICE | Hardware ID of the device. |
| Number | UInt | Device number / PROFIBUS address. |
| Name | String[50] | Device name. |
| ipAddress | IP_V4 | IP address of the device (for IO system only). |
| State | USInt | Device state. (See 2.6.2) |
| exists | Bool | Device exists. |
| Configured | Bool | Device configured. |
| Faulty | Bool | Device faulty. |
| Problem | Bool | Device has a problem. |
| Disabled | Bool | Device disabled. |
| Error | Bool | Error in the device. |
| nextDevice | Int | Number of the next device. |
| actualConfiguredSlots | Int | Number of configured slots of the device. |
| Slots | Array [0..USERDIAG_SLOTS_PER_DEVICE_UPPER_LIM] of "UserDiag_typeSlot" | Diagnostic structure of the modules. See Slots [UserDiag_typeSlot]. |

##### Note

The array index of the “slots” tag corresponds to the slot number on the device.

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.4 “UserDiag_HmiData” DB

The “UserDiag_HmiData” DB serves as an interface to the visualization. The DB is 
of the PLC data type "UserDiag_typeHmiData".

##### [UserDiag_typeHmiData]

Table 3-8: “UserDiag_typeHmiData” PLC data type

| Tag name | Data type | Meaning |
| --- | --- | --- |
| actIOSystem | Int | Index of the selected IO system. |
| actDeviceOf Screen | Int | Index of the selected device in the screen. |
| ioSlotDiagnostics Loaded | Bool | "Device detail view" screen open. |
| actScreenDevices | Int | Current device overview screen of the selected IO system. |
| maxScreens Devices | Int | Maximum number of device overview screens of the selected IO system. |
| ackWaitTime | Int | Wait time after startup for automatic reset of the saved error state in [s]. |
| ackStates | Bool | Reset saved error state. |
| actIOSystemData | UserDiag_typeActIOSystem | Diagnostic data of the selected IO system. (See actIOSystemData [UserDiag_typeActIOSystem]) |
| actDeviceNumber | UInt | Device number of the selected device. |
| actDeviceName | String[50] | Device name of the selected device. |
| actIPAddress | IP_V4 | IP address of the selected device. |
| errorAtLower Screen | Bool | Error on the previous device overview screen of the selected IO system. |
| errorAtUpper Screen | Bool | Error on the next device overview screen of the selected IO system. |
| filterErrorDevices | Bool | Filter, only faulty devices are displayed. |
| ioSystemError | Array [0..USERDIAG_IO_SYSTEMS_UPPER_LIM] of Bool | Error in the IO system. |

---

All rights reserved
2018
Siemens AG 
ã

**actIOSystemData [UserDiag_typeActIOSystem]**

The data type contains the information of the selected IO system and the selected 
device or the information of the controller.

Table 3-9: “UserDiag_typeActIOSystem” PLC data type

| Tag name | Data type | Meaning |
| --- | --- | --- |
| actIoSystem Number | Int | Number of the selected IO system. |
| actIODevice Numbers | Array [1..USERDIAG_DEVICES_PER_SCREEN_UPPER_LIM] of UInt | Device numbers / PROFIBUS addresses of the selected IO system. |
| actIODeviceState | Array [1..USERDIAG_DEVICES_PER_SCREEN_UPPER_LIM] of USInt | State of the devices of the selected IO system. |
| actDeviceSlots Used | Array [0..USERDIAG_SLOTS_PER_SCREEN_UPPER_LIM] of Bool | Slots used of the selected device. (Determined during startup.) |
| actDeviceSlots State | Array [0..USERDIAG_SLOTS_PER_SCREEN_UPPER_LIM] of USInt | State of the slots of the selected device. |

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.5 “UserDiag_PLCCheck” FB

During the first call, the “UserDiag_PLCCheck” FB determines the hardware IDs of 
the local components. The FB reads the status information of the local modules 
and evaluates it.

##### Interfaces

Figure 3-2: Call in "UserDiag_Diagnostics"

Table 3-10: Parameters of the block interface

| Type | Parameter | Data type | Description |
| --- | --- | --- | --- |
| Input | plcHwId | HW_DEVICE | Hardware ID of the controller. |
| InOut | plcData | UserDiag_typePlc | Diagnostic data of the controller and its local modules. |
| Output | error | Bool | An error occurred while processing the FB. The "status" and "statusID" outputs provide related information about locating the error cause. |
| Output | Status | Word | Error code of the FB or of a diagnostic instruction that was called internally. The error location within the FB can be additionally read via the "statusID" output. |
| Output | statusID | Word | Specifies an additional error code for locating the error cause within the FB. |

---

All rights reserved
2018
Siemens AG 
ã

##### “status” error code

The following table describes the possible error code:

Table 3-11: Error code at the “status” output

| Error code | Description |
| --- | --- |
| 16#0000 | No error. |
| 16#8xxx | Error (RET_VAL) of the diagnostic instructions. |
| 16#8400 | Internal error: The number of configured modules exceeds the array limit, “USERDIAG_SLOTS_PER_PLC_UPPER_LIM”. |

##### “statusID” error sources

The following table describes the error sources:

Table 3-12: Error source at the “statusID” output

| statusID | Meaning | Note |
| --- | --- | --- |
| 0 | No error. | - |
| 1 | Internal error of the FB. | - |
| 2 | Error while reading the status information of the modules with “ModuleStates” in mode “1: Modules are configured”. | Reserve |
| 3 | Error while reading the status information of the modules with “ModuleStates” in mode “2: Modules are faulty”. | - |
| 4 | Error while reading the status information of the modules with “ModuleStates” in mode “5: A problem has occurred in the modules”. | - |
| 5 | Error while reading the status information of the modules with “ModuleStates” in mode “4: Modules exist”. | - |

---

All rights reserved
2018
Siemens AG 
ã

##### Functional description

The FB is used to automatically determine the hardware IDs of the local 
components using the “GEO2LOG” instruction based on the slot information. The 
slot information is rewritten with a tag of the “GEOADDR” system data type before 
each call of the instruction.

The “ModuleStates” instruction reads the status information of the local modules for 
different modes. The FB evaluates this status information.

The hardware IDs and the evaluated diagnostic information are saved in the 
appropriate structure in the “UserDiag_DiagnosticsData” global data block.

The following figure shows the basic program flow of the FB.

Figure 3-3: Program flow of the FB

Start/stop
No
Read HW ID
Yes
Set slot
information
of local module x
GEO2LOG
Read HW ID
of local module
No
Last module
Yes
ModuleStates
Read states of modules
(mode 2, 4, 5)
Evaluate and save 
states of
modules
Start/stop

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.6 “UserDiag_IOSystemCheck” FB

During the first call, the “UserDiag_IOSystemCheck” FB determines the hardware 
IDs of the components of an IO system. The FB reads the status information of the 
distributed devices and their modules and evaluates it.

##### Interfaces

Figure 3-4: Call in the "UserDiag_Diagnostics" FB for IO system 1 and IO system 2

Table 3-13: Parameters of the block interface

| Type | Parameter | Data type | Description |
| --- | --- | --- | --- |
| Input | ioSystemHwId | HW_IOSSYSTEM | Hardware ID of the IO system. |
| InOut | ioSystemData | UserDiag_typeIOSystem | Diagnostic data of the IO system. |
| Output | error | Bool | An error occurred while processing the FB. The "status" and "statusID" outputs provide related information about locating the error cause. |
| Output | Status | Word | Error code of the FB or of a diagnostic instruction that was called internally. The error location within the FB can be additionally read via the "statusID" output. |
| Output | statusID | Word | Specifies an additional error code for locating the error cause within the FB. |

---

All rights reserved
2018
Siemens AG 
ã

##### “status” error code

The following table describes the possible error code:

Table 3-14: Error code at the “status” output

| Error code | Description |
| --- | --- |
| 16#0000 | No error. |
| 16#8xxx | Error (RET_VAL) of the diagnostic instructions. |
| 16#8400 | Internal error: The number of configured modules exceeds the array limit, “USERDIAG_SLOTS_PER_DEVICE_UPPER_LIM”. |
| 16#8401 | Internal error: The number of configured devices exceeds the array limit, “USERDIAG_DEVICES_PER_IO_SYSTEM_UPPER_LIM”. |

##### “statusID” error sources

The following table describes the error sources:

Table 3-15: Error source at the “statusID” output

| statusID | Meaning | Note |
| --- | --- | --- |
| 0 | No error. | - |
| 1 | Internal error of the FB. | - |
| 2 | Error while reading the status information of the modules with "ModuleStates" in mode "1: Modules are configured". | Reserve |
| 3 | Error while reading the status information of the modules with "ModuleStates" in mode "2: Modules are faulty". | - |
| 4 | Error while reading the status information of the modules with "ModuleStates" in mode "5: A problem has occurred in the modules". | - |
| 5 | Error while reading the status information of the modules with "ModuleStates" in mode "4: Modules exist". | - |
| 6 | Error while reading the status information of the devices with "DeviceStates" in mode "1: Devices are configured". | - |
| 7 | Error while reading the status information of the devices with "DeviceStates" in mode "2: Devices are faulty". | - |
| 8 | Error while reading the status information of the devices with "DeviceStates" in mode "5: A problem has occurred in the devices". | - |
| 9 | Error while reading the status information of the devices with "DeviceStates" in mode "4: Devices exist". | - |
| 10 | Error while reading the status information of the devices with "DeviceStates" in mode "3: Devices disabled". | - |
| 11 | Error while reading the device name with "GET_NAME". | - |

##### Functional description

The FB is used to automatically determine the hardware IDs of the devices and 
modules of an IO system using the “GEO2LOG” instruction based on the slot 
information. The slot information is rewritten with a tag of the “GEOADDR” system 
data type before each call of the instruction.

The “GET_NAME” instruction reads the names of the PROFIBUS DP slaves / 
PROFINET IO devices.

---

All rights reserved
2018
Siemens AG 
ã

The “DeviceStates” and “ModuleStates” instructions read the status information of 
the devices and their modules for different modes. The FB evaluates the status 
information.

The hardware IDs and the evaluated status information are saved in the 
appropriate structure in the “UserDiag_DiagnosticsData” global data block.
The following figure shows the basic program flow of the FB.

Figure 3-5: Program flow of the FB

Start/stop
No
Read HW ID
Yes
LOG2GEO Set slot DeviceStates
Read number of information Read states of
IO system of device y module x existing devices
PROFIBUS DP or GEO2LOG DeviceStates
PROFINET IO Read HW ID Read states of
evaluation of module faulty devices
DeviceStates No DeviceStates
Read states of Last module Read states of
configured devices deactivated devices
Yes
Set slot DeviceStates
Determine no. of
information Read states of
of device x devices and modules
“problem” devices
GEO2LOG No Evaluate and save 
Read HW ID Last device states
of device of devices
Yes
GET_NAME ModuleStates
Read Read states of
device name “problem” modules
ModuleStates
Read states of
faulty modules
ModuleStates
Read states of
existing modules
Evaluate and save 
states
of modules
Start/stop

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.7 "UserDiag_Diagnostics" FB

The "UserDiag_Diagnostics" FB calls all diagnostic blocks of the controller and the 
IO systems.

##### Interfaces

Figure 3-6: Call in "UserDiag_Main" FB

#instUserDiagMai
nDiagnostics

%FB2003
"UserDiag_Diagnostics"

EN ENO

#statStartDiag startDiag

"UserDiag_
DiagnosticsData".
plcData plcData

"UserDiag_
DiagnosticsData".
ioSystemData ioSystemData

Table 3-16: Parameters of the block interface

| Type | Parameter | Data type | Description |
| --- | --- | --- | --- |
| InOut | startDiag | Bool | Start Diagnostics. |
| InOut | plcData | UserDiag_typePlc | Diagnostic data of the controller and its local modules. |
| InOut | ioSystemData | Array [1.. USERDIAG_IO_SYSTEMS_UPPER_LIM] of UserDiag_typeIoSystem | Diagnostic data of the IO systems. (See ioSystemData [UserDiag_typeIoSystem]) |

The “UserDiag_Diagnostics” FB is called by the following program blocks:

• "UserDiag_Main"

• "Startup"

---

All rights reserved
2018
Siemens AG 
ã

##### Functional description

The diagnostics in the FB "UserDiag_Diagnostics" are only executed, if 
"statStartDiag" is set to "TRUE" by the system memory bit "DiagStatusUpdate" or 
by "UserDiag_DiagnosticsData".startDiag.

The “UserDiag_Diagnostics” FB calls the “UserDiag_PLCCheck” FB to evaluate 
the diagnostic information of the controller and the “UserDiag_IOSystemCheck” FB 
for each IO system to evaluate the diagnostic information of the IO systems.

##### NOTE

If you activate a device which is not connected to the bus system with the 
instruction “D_ACT_DP”, it is possible that the system memory bit 
“DiagStatusUpdate” is not be properly set upon activation. In this case you must 
start the evaluation of the diagnosis by setting the variable 
“UserDiag_DiagnosticsData”.startDiag in the user program.

The following figure shows the basic program flow of the FB.

Figure 3-7: Program flow of the FB

Start/Stop
No
Start Diagnostics
Yes
Reset
Start Diagnostics
UserDiag_
PLCCheck
UserDiag_
IOSystemCheck
IO-System 1
Start/Stop

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.8 FB "UserDiag_GetIPAddress"

The FB "UserDiag_GetIPAddress" reads the IP addresses of the devices of a 
PROFINET IO system.

##### Interfaces

Figure 3-8: Call in "UserDiag_Main" FB

#instUserDiagGet
IPAddress

%FB2005
"UserDiag_GetIPAddress"

EN ENO
error ...
"UserDiag_
DiagnosticsData".
ioSystemData["U
SERDIAG_
IOSYSTEM_1"]
ioSystemData

Table 3-17: Parameters of the block interface

| Type | Parameter | Data type | Description |
| --- | --- | --- | --- |
| InOut | ioSystemData | UserDiag_typeIOSystem | Diagnostic data of the IO system. |
| Output | error | Bool | An error occurred while processing the FB. The "status" and "statusID" outputs provide related information about locating the error cause. |
| Output | Status | Word | Error code of the instruction "GetStationInfo". |
| Output | statusID | Word | ID of the instruction "GetStationInfo". |

##### Functional description

The instruction "GetStationInfo" reads the IP addresses of the PROFINET IO 
devices of a PROFINET IO system.

The following figure shows the basic program flow of the FB.

Figure 3-9: Program flow of the FB

Start/Stop
GetStationInfo
Read IP address
Start/Stop

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.9 “UserDiag_DiagnosticsHmi” FB

The “UserDiag_DiagnosticsHmi” FB responds to the inputs made on the HMI and 
provides the required diagnostic information to the visualization.

##### Interfaces

Figure 3-10: Call in the “UserDiag_Main” FB

#instUserDiagDia
gnosticsHmi

%FB2006
"UserDiag_DiagnosticsHmi"

EN ENO

"UserDiag_
DiagnosticsData".
plcData plcData

"UserDiag_
DiagnosticsData".
ioSystemData ioSystemData

%DB2002
"UserDiag_
HmiData" hmiData

Table 3-18: Parameters of the block interface

| Type | Parameter | Data type | Description |
| --- | --- | --- | --- |
| InOut | plcData | UserDiag_typePlc | Diagnostic data of the controller and its local modules. |
| InOut | ioSystemData | Array [1..USERDIAG_IO_SYSTEMS_UPPER_LIM] of UserDiag_typeIoSystem | Diagnostic data of the IO systems. (See ioSystemData [UserDiag_typeIoSystem]) |
| InOut | actIOSystemData | UserDiag_typeHmiData | Informations for the visualization. (See The DB is of the PLC data type "UserDiag_typeHmiData". [UserDiag_typeHmiData]) |

---

All rights reserved
2018
Siemens AG 
ã

##### Functional description

After startup and a wait time, the “UserDiag_DiagnosticsHmi” FB resets the saved 
error state.

The FB responds to the inputs made on the HMI. Following the request, the FB 
resets the saved error state and supplies the visualization with the requested data, 
for example:

• Diagnostic information of the controller and its local modules

• Diagnostic information of the IO system

• Diagnostic information of the devices and their modules

The following figure shows the basic program flow of the FB.

Figure 3-11: Program flow of the FB

Start/stop
Reset saved error state
after startup and wait time
Check which IO
system has been selected
Yes
Filter
No
Provide states of devices Provide states of faulty devices
of selected IO system of selected IO system
to HMI to HMI
Provide states of modules
of selected device / PLC
to HMI
When requested, reset
error state of selected
IO system / PLC
Start/stop

---

All rights reserved
2018
Siemens AG 
ã

#### 3.3.10 "UserDiag_Main" FB

The FB "UserDiag_Main" calls all program blocks that are required for diagnostics.

##### Interfaces

Figure 3-12: Call in "Main" OB

%DB2000
"UserDiag_
InstMain"

%FB2000
"UserDiag_Main"
EN ENO

##### Functional description

The FB calls the FBs "UserDiag_Diagnostics", "UserDiag_DiagnosticsHMI" and 
"UserDiag_GetIPAddresses" for evaluating and visualizing the diagnostics 
information of the controller.

The following figure shows the basic program flow of the FB.

Figure 3-13: Program flow of the FB

Start/Stop
UserDiag_
Diagnostics
UserDiag_
GetIPAddress
UserDiag_
DiagnosticsHmi
Start/Stop

##### Note

Call the FB "UserDiag_Main" before the processing of your user program, e.g. in 
the first network of the OB "Main". Thus, the evaluated diagnostics information is 
consistent for the duration of the cycle.

---

All rights reserved
2018
Siemens AG 
ã

## 4 Appendix

### 4.1 Service and Support

##### Industry Online Support

Do you have any questions or do you need support?

With Industry Online Support, our complete service and support know-how and 
services are available to you 24/7.

Industry Online Support is the place to go to for information about our products, 
solutions and services.

Product Information, Manuals, Downloads, FAQs and Application Examples – all 
the information can be accessed with just a few clicks: 
https://support.industry.siemens.com

##### Technical Support

Siemens Industry’s Technical Support offers you fast and competent support for 
any technical queries you may have, including numerous tailor-made offerings 
ranging from basic support to custom support contracts.

You can use the web form below to send queries to Technical Support: 
www.siemens.com/industry/supportrequest.

##### Service offer

Our service offer includes the following services:

• Product Training

• Plant Data Services

• Spare Part Services

• Repair Services

• Field & Maintenance Services

• Retrofit & Modernization Services

• Service Programs & Agreements

For detailed information about our service offer, please refer to the Service 
Catalog:

https://support.industry.siemens.com/cs/sc

##### Industry Online Support app

The “Siemens Industry Online Support” app provides you with optimum support 
while on the go. The app is available for Apple iOS, Android and Windows Phone:
https://support.industry.siemens.com/cs/ww/en/sc/2067

---

All rights reserved
2018
Siemens AG 
ã

### 4.2 Links and literature

Table 4-1

| No. | Topic |
| --- | --- |
| \1 | Siemens Industry Online Support https://support.industry.siemens.com |
| \2 | Link to the entry page of the application example https://support.industry.siemens.com/cs/ww/en/view/109483251 |
| \3 | System Diagnostics with S7-1500 and TIA Portal https://support.industry.siemens.com/cs/ww/en/view/68011497 |
| \4 | STEP 7 Professional V14 SP1 System Manual https://support.industry.siemens.com/cs/ww/en/view/109747136 |
| \5 | S7-1200 Systemhandbuch https://support.industry.siemens.com/cs/ww/de/view/109478121 |

### 4.3 Change documentation

Table 4-2

| Version | Date | Modifications |
| --- | --- | --- |
| V1.0 | 09/2014 | First version |
| V2.0 | 01/2018 | Evaluation of the diagnostics in OB "Main". The evaluation is triggered by the system memory bit "DiagStatusUpdate". |
| V2.1 | 02/2018 | Minor error correction |