SIEMENS

**Application Example**  **03/2016**

# Time Synchronization with an HMI Operator Panel and a SIMATIC PLC

**WinCC V13 SP1, Comfort Panels, S7-1200/S7-1500 and S7-300/S7-400** rved
All rights rese
2016
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
2016
Siemens AG 
ã

## Table of Contents

**Warranty and Liability ................................................................................................. 2**
**1 Task..................................................................................................................... 4**
1.1 Introduction........................................................................................... 4
1.2 Overview of the automation task.......................................................... 4
**2 Solution............................................................................................................... 5**
2.1 Overview of the overall solution........................................................... 5
2.1.1 Selection aid......................................................................................... 5
2.1.2 Supported HMI operator panels:.......................................................... 6
2.1.3 Description of the time synchronization variants.................................. 7
2.2 Description of the core functionality..................................................... 9
2.3 Hardware and software components used......................................... 10
**3 Basics............................................................................................................... 12**
3.1 HMI time synchronization mode......................................................... 12
3.2 Area pointer........................................................................................ 14
3.2.1 DateTimePLC (PLC  →  operator panel) ........................................... 14
3.2.2 DateTime (operator panel  →  PLC) .................................................. 14
3.2.3 Controller job...................................................................................... 15
**4 Functional Mechanisms of this Application................................................. 17**
4.1 Data block “DB10_HMI_AreaPointer” (DB10).................................... 17
4.2 Function block “FB110_TimeSyn_HMI_To_PLC”.............................. 18
4.3 Function block “FB120_TimeSyn_PLC_To_HMI”.............................. 21
4.4 Function block “FB210_TimeSyn_HMI_To_PLC”.............................. 25
4.5 Function block “FB220_TimeSyn_PLC_To_HMI”.............................. 25
**5 Configuration and Project Engineering......................................................... 26**
5.1 Variant 1: PLC (master/slave)  ↔  HMI operator panel ..................... 26
5.2 Variant 2: PLC (master)  →  HMI operator panel (slave)................... 29
5.3 Variant 3: PLC (master)  →  HMI operator panel (slave)................... 33
5.4 Variant 4: HMI operator panel (master)  →  PLC (slave)................... 39
**6 Operation of the Application.......................................................................... 45**
6.1 General............................................................................................... 45
6.1.1 Opening the “plant screens”............................................................... 46
6.1.2 Functionality of the plant screens....................................................... 47
6.2 Adjusting the time zone on the panel and in the CPU ....................... 48
6.3 TimeSyn_Project_01 (variant 1)......................................................... 50
6.4 TimeSyn_Project_02 (variant 2)......................................................... 52
6.5 TimeSyn_Project_03 (variant 3)......................................................... 53
6.6 TimeSyn_Project_04 (variant 4)......................................................... 54
**7 Notes and Tips................................................................................................. 55**
7.1 Replacing the PLC ............................................................................. 55
7.2 Replacing the HMI operator panel ..................................................... 55
**8 Links & Literature............................................................................................ 56**
8.1 References......................................................................................... 56
8.2 Internet links....................................................................................... 56
**9 History............................................................................................................... 57**

---

All rights reserved
016
2
Siemens AG 
ã

### 1.1 Introduction

## 1 Task

### 1.1 Introduction

In order for components such as HMI operator panels and SIMATIC controllers of a 
plant to operate with an identical time, one of the listed components has to be the 
timer for all other components.

• The component acting as the timer is called the time master.

• The time receiving components are the time slaves.

The application shows the respective time synchronization options and their 
implementation in the program.

Use the selection aid to select the time synchronization option that suits your 
requirements. For more information, please see chapter <u>2.1.1</u>.

### 1.2 Overview of the automation task

The figure below provides an overview of the automation task.

Figure 1-1

Panel 1
11.04.2013 10:04:11
TOUCH
Synchronisierung der Uhrzeit
11.04.2013 11:26:20
Synchronisierung der Uhrzeit
Panel 2
11.04.2013 12:13:04
SPS

##### Description of the automation task

In a plant, there are several HMI operator panels that exchange data with a 
SIMATIC PLC.

The following tasks are to be implemented:

1. For the first task, a SIMATIC PLC is to be the timer component 
(SIMATIC PLC  →  HMI operator panel).

2. For the second task, an HMI operator panel is to be the timer component 
(HMI operator panel  →  SIMATIC PLC).

---

All rights reserved
016
2
Siemens AG 
ã

2.1 Overview of the overall solution

## 2 Solution

### 2.1 Overview of the overall solution

WinCC TIA Portal offers various solutions for the tasks described in chapter 1. The 
solutions depend on

• the SIMATIC PLC used.

• the time synchronization required.

• the HMI operator panel used.

#### 2.1.1 Selection aid

The PLC is normally used as the time master (timer component). However, it is 
also possible that the operator panel is the time master. This allows the user to 
specify, for example, the time using the operator panel (start value) and then the 
PLC applies the time synchronization as it has a higher accuracy.

The following figure supports you in selecting the different time synchronization 
variants.

Figure 2-1

SIMATIC S7-1200 / S7-1500 SIMATIC S7-300 / S7-400
SPS Master HMI Master SPS Master HMI Master
Continuous Manual
Synchronization Synchronization
Variant 1 Variant 2 Variant 3 Variant 4
Link Link Link Link
Variant 1 Variant 2 Variant 3 Variant 4

---

All rights reserved
016
2
Siemens AG 
ã

2.1 Overview of the overall solution

#### 2.1.2 Supported HMI operator panels:

– Basic Panels

– Comfort Panels

– Mobile Panels

– RT Advanced

– Multi Panels

The diagrammatic representation below shows the different time synchronization 
variants between the above-listed operator panels and the SIMATIC controllers.

Table 2-1

|  | S7-1200 | S7-1500 | S7-300 | S7-400 |
| --- | --- | --- | --- | --- |
| HMI time synchronization (master) → see sample project for variant 1 | X1 | X2 | -- | -- |
| HMI time synchronization (slave) → see sample project for variant 1 | X3 | X4 | -- | -- |
| Area pointer: DateTimePLC → see sample project for variant 2 | X | X | X | X |
| Area pointer: DateTime → see sample project for variant 3 | X | X | X | X |
| Area pointer: JobMailbox (controller job 14 and 15) → see sample project for variant 4 | X | X | X | X |

The following chapters provide a more detailed description of the individual “time 
synchronization” variants.

<sup>1</sup>Except Basic Panels

<sup>2</sup>Except Basic Panels

<sup>3</sup>WinCC V12 or higher also Basic Panels

4
WinCC V12 or higher also Basic Panels

---

All rights reserved
016
2
Siemens AG 
ã

2.1 Overview of the overall solution

#### 2.1.3 Description of the time synchronization variants

The following sections provide a brief description of the individual time 
synchronization variants and list the functional differences.

##### Variant 1

Variant 1 allows the user to parameterize the operator panel as a master or slave. 
The functionality is only supported when using a SIMATIC S7-1200 / S7-1500 as 
the communications driver.

Table 2-2

If you are using a SIMATIC S7-1200 / S7-1500, the only thing you have to do is select 
whether the operator panel will be the time master or slave. This is done in the “HMI 
configuration”, “HMI time synchronization mode” option. 
**No** other settings are required.

If, for example, the PLC is the time master, the time on the operator panel will be 
automatically synchronized with the PLC time every 10 minutes.

• For more information, please refer to the “Basics” chapter, <u>3.1</u>.

• For a configuration guide, please see chapter <u>5.1</u>

##### Note

Variant 1 is the variant recommended for the current HMI device generation 
(e.g., Comfort Panels) in conjunction with the SIMATIC S7-1200 / S7-1500 
controllers.

##### Variant 2

This variant is recommended when the PLC is the time master and you want 
synchronization to be performed automatically and continuously in a fixed time 
frame.

Table 2-3

Timer component (master): S7-1200 / S7-1500 / S7-300 / S7-400

Transferring date and time together

Date and time synchronization is performed on the operator panel in a fixed cycle. The current PLC system time is read out via the PLC program and the value is transferred to the “DateTimePLC” area pointer. The refresh cycle of the area pointer is permanently stored in the HMI configuration. The time selected should not be less than 1 minute.

• For information on the area pointer, please refer to the “Basics” chapter, 3.2.1.

• For a configuration guide, please see chapter 5.2.

• For a configuration guide, please see chapter <u>5.2.</u>

---

All rights reserved
016
2
Siemens AG 
ã

2.1 Overview of the overall solution

##### Variant 3

This variant is recommended when the PLC is the time master and you want to 
**manually** trigger the time for the synchronization.

Table 2-4

Timer component (master): S7-1200 / S7-1500 / S7-300 / S7-400

Transferring date and time separately The date and time of the operator panel can be separately synchronized with the PLC system time. The time for the synchronization is specified manually, for example, by pressing a button on the operator panel. In terms of the example, a function block was created that facilitates handling time synchronization. The function block evaluates the “JobMailbox” area pointer (controller job 14 and 15) and transfers the data to the operator panel. • Chapter 4.3 provides detailed information on the structure of the function block used, “FB120” or “FB210”. • For a configuration guide, please see chapter 5.3

• For a configuration guide, please see chapter <u>5.3</u>.

##### Variant 4

This variant is recommended when the operator panel is the time master and you 
want to manually specify the time for the synchronization.

Table 2-5

| Timer component (master): HMI operator panel |
| --- |
| Transferring date and time together |
| Synchronization of the PLC system time with the operator panel system time is performed manually, for example, by pressing a button on the operator panel. In terms of the example, a function block was created that facilitates handling time synchronization. The function block evaluates the “DateTime” area pointer and the “JobMailbox” area pointer (controller job 40) and transfers the operator panel data to the PLC. • Chapter 4.2 provides detailed information on the structure of the function block used, “FB110” or “FB210”. |

• For a configuration guide, please see chapter <u>5.4</u>.

---

All rights reserved
016
2
Siemens AG 
ã

2.2 Description of the core functionality

The core functionality is the implementation of the time synchronization variants 
described in chapter <u>2.1.3</u> (variants 1 to 4).

For each of the different time synchronization variants, the application is supplied 
with a sample project and an “FB_TimeSyn” TIA Portal library file.

You can customize the configurations to your tasks as needed.

The following chapters of the application provide a detailed description of the 
individual projects.

##### • Variant 1: Project_01 (includes two projects)

**Note:** For SIMATIC S7-1200 / S7-1500 only.

– HMI time synchronization (master).

In this application, the operator panel is the timer 
component.

– HMI time synchronization (slave).

In this application, the SIMATIC PLC is the timer 
component.

• Variant 2: Project_02

In this application, the SIMATIC PLC is the timer 
component.

• Variant 3: Project_03

In this application, the SIMATIC PLC is the timer 
component and the operator panel’s date and time 
can be synchronized independently of each other.

• Variant 4: Project_04

In this application, the operator panel is the timer 
component.

• TIA Portal library: FB_TimeSyn

Contains the function blocks used in the projects, FB110; 
FB120 (FB210; FB220), and the “DB10” data block with the
data areas of the individual area pointers.

As an alternative, copy the appropriate blocks from the projects.

##### Scope

This application does not include a description of …

• the SIMATIC PLC used.

• the HMI operator panels used.

• the WinCC V12 configuration user interface.

Basic knowledge of these topics is required. If necessary, please refer to the 
appropriate manuals. For a list of manuals, please see chapter <u>8</u> “Links & 
Literature”.

---

All rights reserved
016
2
Siemens AG 
ã

2.3 Hardware and software components used

##### Required knowledge

Implementing time synchronization via area pointers requires basic knowledge of 
the STEP 7 configuration.

### 2.3 Hardware and software components used

The application was created with the following components:

##### Hardware components

Table 2-6

| Component | No. | Article number | Note |
| --- | --- | --- | --- |
| CPU S7-1200 CPU 1212C DC/DC/DC | 1 | 6ES7 212-1AD30-0XB0 | All S7-1200 / S7-1500 controllers can be used. |
| CPU S7-300 CPU 315-2 PN/DP | 1 | 6ES7 315-2EH14-0AB0 | All S7-300 / S7-400 controllers can be used. |
| TP1200 Comfort Panel | 1 | 6AV2124-0MC01-0AX0 | All WinCC (TIA Portal) operator panels can be used. See notes in the documentation. |

##### Standard software components

Table 2-7

| Component | No. | Article number | Note |
| --- | --- | --- | --- |
| WinCC Advanced V13 SP1 | 1 | 6AV2102-0AA03-0AA5 | Depending on the operator panel used, WinCC V13 Basic or higher. |
| STEP 7 Professional V13 SP1 | 1 | 6ES7822-1AA03-0YA5 | A CPU S7-1500 can be used for WinCC V13 or higher. |

##### Sample files and projects

The following list contains all files and projects that are used in this example.

Table 2-8

| Component | Note |
| --- | --- |
| 69864408_WinCC_TimeSyn_CODE_v40.zip The zip file contains four sample projects and a library file with four function blocks. • Project_01 (variant 1) - HMI time synchronization (master) - HMI time synchronization (slave) • Project_02 (variant 2) - The SIMATIC PLC is the timer component. | This zip file contains the STEP 7 project and the HMI project. |

---

All rights reserved
016
2
Siemens AG 
ã

<u>2 Solution</u>

### 2.3 Hardware and software components used

| Component | Note |
| --- | --- |
| Project_03 (variant 3)- The SIMATIC PLC is the timer component. Date and time of the operator panel can be synchronized independently of each other.Project_04 (variant 4)- The HMI operator panel is the timer component.Libraries (FB_TimeSyn)- Contains the function blocks used in Project_03 and Project_04, FB110; FB120 (FB210; FB220), and the DB10 data block with the data areas of the individual area pointers. |  |
| 69864408_WinCC_TimeSyn_DOKU_v41_e.pdf | This document. |

---

All rights reserved
016
2
Siemens AG 
ã

3.1 HMI time synchronization mode

## 3 Basics

##### Introduction

The aim of this chapter is to provide a technical overview of the functions, area 
pointer blocks and system data blocks used in the different time synchronization 
variants.

This chapter is **not** relevant to implementing the tasks.

### 3.1 HMI time synchronization mode

##### Concerns time synchronization variant 1

If an S7-1200 or S7-1500 controller is selected as a communication partner in the 
WinCC engineering system, one of the following options can be selected in the 
connection settings in “HMI time synchronization mode”.

• None: No time synchronization is used.

• Master: The operator panel specifies the time.

If multiple operator panels are used, one operator panel is configured as the 
“master” and all other operator panels are configured as “slaves”.

• Slave: The PLC specifies the time.

For this type of time synchronization, **no** further configuration steps are needed.

Select menu view in the HMI configuration.

Figure 3-1

|  | Name | Communication driver | HMI time synchronization mode | Station | Partner |
| --- | --- | --- | --- | --- | --- |
|  | HMI_connection_1 | SIMATIC S7 1200 | Slave | SIMATIC 1200 stati... | PLC_1 |
|  |  |  | None |  |  |
|  |  |  | Master |  |  |
|  |  |  | Slave |  |  |

##### Time synchronization properties

• The operator panel can specify the time as a master.

– In “Master mode”, time synchronization is performed each time a 
connection is established.

• The operator panel can apply the time from the PLC as a slave.

– In “Slave mode”, time synchronization is performed each time a connection 
is established and then every 10 minutes.

• The first time synchronization is performed directly after starting Runtime on 
the operator panel.

• Time synchronization is only performed while running Runtime on the operator 
panel.

---

All rights reserved
016
2
Siemens AG 
ã

##### 3 Basics

### 3.1 HMI time synchronization mode

##### Time synchronization restrictions

Time synchronization with the “HMI time synchronization mode” function can be 
configured with the following operator panels:

• Basic Panels

• Comfort Panels

• KTP Mobile

• Mobile 277F IWLAN V2

• WinCC Runtime Advanced

• WinCC Runtime Professional

• TP177 4

• Multi Panel 177

• Multi Panel 277

• Multi Panel 377

• Mobile Panel 277

##### Restrictions during the configuration

• If multiple connections to a SIMATIC S7-1200 / SIMATIC S7-1500 are 
configured for an operator panel, you can only configure one connection as a 
“slave”.

• If you have enabled time synchronization for the operator panel as a “slave”, 
you can no longer use the “DateTimePLC” global area pointer.

• Basic Panels can only be configured as a “slave”.

• If you are using Basic Panels for the configuration, it is not possible to 
simultaneously use time synchronization via NTP and the “DateTimePLC” area 
pointer.

• If a PLC is configured with the “Complete protection” protection level, an 
operator panel can poll the time only if the correct access password was 
configured on the operator panel. The access password for communication 
with a PLC with the “Complete protection” protection level is configured in the 
“Connections” editor of the operator panel. The access password must match 
the configured password in the PLC. The password for the PLC is assigned in 
the PLC properties in: “General > Protection”.

---

All rights reserved
016
2
Siemens AG 
ã

### 3.2 Area pointer

An area pointer allows you to access a data area in the controller. During 
communication, alternate read/write processes between the controller and the 
operator panel take place in these data areas. By evaluating the stored data, 
controller and operator panel mutually trigger predefined actions.

This application uses the “DateTimePLC”, “DateTime” and “JobMailbox” area 
pointers.

#### 3.2.1 DateTimePLC (PLC → operator panel)

## DateTimePLC (PLC → operator panel)

##### Transferring date and time from the controller to the operator panel

à Concerns time synchronization variant 2.

The “DateTimePLC” data area has the following structure:

Figure 3-2

| Data word | Most significant byte |  |  | Least significant byte |  |  |
| --- | --- | --- | --- | --- | --- | --- |
| Data word | 7 | ...... | 0 | 7 | ...... | 0 |
| n+0 | Year (80 to 99/0 to 29) |  |  | Month (1 to 12) |  |  |
| n+1 | Day (1 to 31) |  |  | Hour (0 to 23) |  |  |
| n+2 | Minute (0 to 59) |  |  | Second (0 to 59) |  |  |
| n+3 | Reserved |  |  | Reserved |  | Weekday (1 to 7, 1=Sunday) |
| n+4 1) | Reserved |  |  | Reserved |  |  |
| n+5 1) | Reserved |  |  | Reserved |  |  |

In the PLC, “Struct” is used as the data type. For more information, please see the 
structure of the configured DB10 in chapter 4.1.

#### 3.2.2 DateTime (operator panel → PLC)

## DateTime (operator panel → PLC)

##### Transferring date and time from the operator panel to the controller

à Concerns time synchronization variant 4.

The “DateTime” data area has the following structure:

Figure 3-3

| Data word | Most significant byte |  |  |  |  |  |  | Least significant byte |  |  |  |  |  |  |  |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Data word | 7 |  |  |  |  |  | 0 | Least significant byte |  |  |  |  |  |  |  | 7 |  |  |  |  | 0 |
| n+0 | Reserved |  |  |  |  |  |  | Hour (0 to 23) |  |  |  |  |  |  | Time |  |  |  |  |  |  |
| n+1 | Minute (0 to 59) |  |  |  |  |  |  | Second (0 to 59) |  |  |  |  |  |  | Time |  |  |  |  |  |  |
| n+2 | Reserved |  |  |  |  |  |  | Reserved |  |  |  |  |  |  | Time |  |  |  |  |  |  |
| n+3 | Reserved |  |  |  |  |  |  | Weekday (1 to 7, 1=Sunday) |  |  |  |  |  |  | Date |  |  |  |  |  |  |
| n+4 | Day (1 to 31) |  |  |  |  |  |  | Month (1 to 12) |  |  |  |  |  |  | Date |  |  |  |  |  |  |
| n+5 | Year (80 to 99/0 to 29) |  |  |  |  |  |  | Reserved |  |  |  |  |  |  | Date |  |  |  |  |  |  |

In the PLC, “Date_And_Time” is used as the data type. For more information, 
please see the structure of the configured DB10 in chapter 4.1.

---

All rights reserved
016
2
Siemens AG 
ã

3.2 Area pointer

#### 3.2.3 Controller job

##### Triggering functions on the operator panel using the control program

àConcerns time synchronization variants 3 and 4

The “JobMailbox” data area has the following structure:

Figure 3-4

| Word | Most significant byte | Least significant byte |
| --- | --- | --- |
| n+0 | 0 | Job number |
| n+1 | Parameter 1 |  |
| n+2 | Parameter 2 |  |
| n+3 | Parameter 3 |  |

The first word of the controller job mailbox contains the job number. Depending on 
the controller job, up to three parameters can then be transferred.

If the first word of the controller job mailbox is not equal to 0, the operator panel 
evaluates the controller job. For this reason, the parameters first have to be 
entered in the controller job mailbox (parameter 1 to parameter 3), followed by the 
job number.

If the operator panel has accepted the controller job, the first word is reset to 0. At 
this time, the controller job execution is generally **not** yet completed.

In the PLC, “Array [0..3] of Word” is used as the data type. For more information, 
please see the structure of the configured DB10 in chapter 4.1.

##### Concerns only variant 4, controller job 40 (job number)

Controller job 40 transfers the system time from the operator panel to the PLC.

Controller job 40 parameters

Figure 3-5

| No | Function |  |
| --- | --- | --- |
| 40 | Transfer date/time to PLC |  |
|  | (in the S7 format DATE_AND_TIME) An interval of at least 5 seconds must be maintained between two successive jobs to prevent overload of the HMI device. |  |

---

All rights reserved
016
2
Siemens AG 
ã

3.2 Area pointer

##### Concerns only variant 3, controller job 14 (job number)

Controller job 14 transfers the (BCD-coded) time from the PLC to the operator 
panel.

Controller job 14 parameters

Figure 3-6

| No | Function |  |
| --- | --- | --- |
| 14 | Set time (BCD-coded) |  |
|  | Parameter 1 | Left byte: -Right byte: hours (0-23) |
|  | Parameter 2 | Left byte: minutes (0-59)Right byte: seconds (0-59) |
|  | Parameter 3 | - |

##### Concerns only variant 3, controller job 15 (job number)

Controller job 15 transfers the (BCD-coded) date from the PLC to the operator 
panel.

Controller job 15 parameters

Figure 3-7

| No | Function |  |
| --- | --- | --- |
| 15 | Setting the date (BCD coded) |  |
|  | Parameter 1 | Left byte: -Right byte: weekday (1-7: Sunday-Saturday) |
|  | Parameter 2 | Left byte: day (1-31)Right byte: month (1-12) |
|  | Parameter 3 | Left byte: year |

##### Notes:

• The KTP 600 BASIC PN operator panel ignores the weekday.

• If you are using the “DateTimePLC” area pointer, the weekday will be ignored.

---

All rights reserved
016
2
Siemens AG 
ã

## 4 Functional Mechanisms of this Application

##### Introduction

The following sections describe the structure, principle of operation and data 
structure of the created data blocks and program blocks.

You only need this chapter if you need detailed information on the function block 
used, “DB10”, or want to reproduce the method of functioning of the function blocks 
used, “FB110”(FB210) or “FB120” (FB220).

This chapter is **not** relevant to implementing the tasks.

### 4.1 Data block “DB10_HMI_AreaPointer” (DB10)

##### Concerns time synchronization variants 2, 3 and 4

DB10 includes a summary of the possible area pointers and their data areas. The 
time synchronization implementation does **not** require all of the listed area 
pointers.

“DB10” is included in the “FB_TimeSyn” TIA Portal library file.

• “Coordination” area pointer (unused).

• “DateTime” area pointer.

• “JobMailbox” area pointer.

• “DataRecord” area pointer (unused).

• “ProjectID” area pointer (unused).

• “ScreenNumber” area pointer (unused).

• “DateTimePLC” area pointer.

Figure 4-1

DB10_HMI_AreaPointer

|  | Name | Data type | Offset | Start value | Retain | Visible in ... |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Static |  |  |  |  | ☐ |
| 2 | Coordination | Word | 0.0 | 16#0 | ☑ | ☑ |
| 3 | DateTime | Date_And_Time | 2.0 | DT#1990-01-01-00:00:00 | ☑ | ☑ |
| 4 | JobMailbox | Array [0..3] of Word | 10.0 |  | ☑ | ☑ |
| 5 | JobMailbox[0] | Word | 0.0 | 16#0 | ☑ | ☑ |
| 6 | JobMailbox[1] | Word | 2.0 | 16#0 | ☑ | ☑ |
| 7 | JobMailbox[2] | Word | 4.0 | 16#0 | ☑ | ☑ |
| 8 | JobMailbox[3] | Word | 6.0 | 16#0 | ☑ | ☑ |
| 9 | DataRecord | Array [0..4] of Word | 18.0 |  | ☑ | ☑ |
| 10 | ProjectID | Word | 28.0 | 16#0 | ☑ | ☑ |
| 11 | ScreenNumber | Array [0..4] of Word | 30.0 |  | ☑ | ☑ |
| 12 | DateTimePLC | Struct | 40.0 |  | ☑ | ☑ |
| 13 | DateTime | Date_And_Time | 0.0 | DT#1990-01-01-00:00:00 | ☑ | ☑ |
| 14 | Reserve_01 | Word | 8.0 | 16#0 | ☑ | ☑ |
| 15 | Reserve_02 | Word | 10.0 | 16#0 | ☑ | ☑ |
|  |  |  |  |  |  |  |

---

All rights reserved
016
2
Siemens AG 
ã

### 4.2 Function block “FB110_TimeSyn_HMI_To_PLC”

##### Time synchronization “operator panel → PLC”

FB110 is used in the “TimeSyn_Project_04” project.

When the operator panel is the time master, use “FB110”.

“FB110” has “input/output parameters” that are interconnected as specified. You

can edit the FB if needed. By default, it is **not** necessary to edit the FB.

“FB110” is included in the “FB_TimeSyn” TIA Portal library file.

##### “FB110” view

Figure 4-2

%DB110
"DB110_
TimeSyn_HMI_
To_PLC"

%FB110
"FB110_TimeSyn_HMI_To_PLC"
EN ENO

P#DB10.DBX2.0
"DB10_HMI_
AreaPointer".
DateTime

AreaPointer_
DateTime

Current_
PLCTime

P#DB10.DBX10.0
"DB10_HMI_
AreaPointer".
JobMailbox

AreaPointer_
JobMailbox

%DB100.DBX12.0
"DB100_HMI_
DataExchange".
Start_TimeSyn

SetTime

##### Principle of operation

Time synchronization is implemented via the following area pointers:

– “DateTime”.

– “JobMailbox” with job number “40”.

The block includes the following system function blocks:

– WR_SYS_T

(call sets the time and date of the CPU clock).

– RD_SYS_T

(the “RD_SYS_T” instruction reads the current date and time out of the 
CPU clock).

---

All rights reserved
016
2
Siemens AG 
ã

##### FB110 functional sequence

The following table describes the program flow of FB110. The program flow is 
implemented as a sequencer.

Table 4-1

| Network | Comment |
| --- | --- |
| 1. | General: Copy values from the “DateTime” area pointer to the static area. The “AreaPointer_DateTime” input parameter is used to read in the contents of the assigned “DateTime” area pointer. The input parameter has the “Date_And_Time” data type. For later evaluation of the time synchronization, it is necessary to divide the input parameter “byte by byte”. The result is copied to the data area of the static “Edited_AreaPointer_DateTime” tag. “AreaPointer_DateTime” → “Edited_AreaPointer_DateTime”. |
| 2. | Step 1: Start time synchronization. The “SetTime” InOut parameter is used to read in/out the “start signal” for enabling time synchronization. The InOut parameter has the “Bool” data type. The “AreaPointer_JobMailbox” InOut parameter is used to read in/out the contents of the “JobMailbox” area pointer. The input parameter has the “Array [0..3] of Word” data type. With the start of time synchronization, the “JobMailbox” area pointer assigns the default value “0” to parameters 1 to 3. |
| 3. | Step 2: Save the current value from the DateTime area pointer. |

In the network, the “Edited_AreaPointer_DateTime” tag is read out and its contents are divided into the - “SaveData_Previous_HMI_Time_01” “SaveData_Previous_HMI_Time_02” tags and temporarily stored.

Technical background: The “Edited_AreaPointer_DateTime” tag has the “Date_And_Time” data type, which corresponds to a length of 2 double words. The tag will later be needed for a “time comparison”. To be able to compare the contents of the tag to the “==D” instruction, the tag is divided into two separate double words.

$$
``==\mathsf{D}''
$$

---

All rights reserved
016
2
Siemens AG 
ã

4.2 Function block “FB110_TimeSyn_HMI_To_PLC”

$$
\mathsf{[PLC]}
$$

| Network | Comment |
| --- | --- |
| 4. | Step 3: Execute controller job “40”. Job number “40” is transferred to the “AreaPointer_JobMailbox” InOut parameter. Note: The job number must be specified in “hex format”. |
| 5. | Step 4: Evaluation, job “40” done. The “AreaPointer_JobMailbox” InOut parameter is used to evaluate when the previously set controller job “40” was reset to “0” by the operator panel. Note: If the operator panel has accepted the controller job, the controller job will be reset to “0”. At this time, the controller job execution is generally not yet completed. |
| 6. | Step 5: Evaluation, new HMI time applied? |

$$
\mathrm{job}^{\mathrm{~\text{~}}}40
$$

$$
\text{"}40\text{"}
$$

$$
\text{"}40\text{"}
$$

$$
\dot{\mathrm{to}}\mathrm{~"0"~}
$$

|  | The static “Edited_AreaPointer_DateTime” tag is read out via the network and its contents are divided into the - “SaveData_New_HMI_Time_01” - “SaveData_New_HMI_Time_02” tags and temporarily stored. |
| --- | --- |
|  | Technical background: To detect that the controller job has been fully completed, the network compares the time. The controller job is completed when the comparison of the "SaveData_Previous_HMI_Time" and “SaveData_New_HMI_Time” tags is not equal. |
| 7. | Step 6: Transfer new HMI system time to PLC. |

|  | Via the network, the PLC system time is synchronized with the operator panel system time. For this purpose, the “AreaPointer_DateTime” input parameter tag is assigned to the “IN Parameter” of the “WR_SYS_T” system function block. |
| --- | --- |
| 8. | General: Output current PLC time on operator panel. The read out PLC system time is output via the “Current_PLCTime” output parameter tag. For this purpose, the “Current_PLCTime” tag is assigned to the “OUT Parameter” of the “RD_SYS_T” system function block. The output parameter has the “Date_And_Time” data type. |

---

All rights reserved
016
2
Siemens AG 
ã

### 4.3 Function block “FB120_TimeSyn_PLC_To_HMI”

##### Time synchronization “PLC → operator panel”

FB120 is used in the “TimeSyn_Project_03” project.

When the PLC is the time master, use “FB120”.

“FB120” has “input/output parameters” that are interconnected as specified. You 
can edit the FB if needed. By default, it is **not** necessary to edit the FB.

“FB120” is included in the “FB_TimeSyn” TIA Portal library file.

##### “FB120” view

Figure 4-3

%DB120
"DB120_
TimeSyn_PLC_
To_HMI_DB"

%FB120
"FB120_TimeSyn_PLC_To_HMI"
EN ENO

P#DB100.DBX0.0
"DB100_HMI_
DataExchange".
PLC_Time_RD_
SYS_T

Current_PLC_
Time

P#DB10.DBX10.0
"DB10_HMI_
AreaPointer".
JobMailbox

AreaPointer_
JobMailbox

%DB100.DBX10.0
"DB100_HMI_
DataExchange".
Start_TimeSyn

SetTime

%DB100.DBX10.1
"DB100_HMI_
DataExchange".
Start_DateSyn

SetDate

##### Principle of operation

The function block allows you to separately synchronize the **time** and the **date** of 
the HMI operator panel with the time of the PLC.

Time synchronization is implemented via the following area pointer:

– “JobMailbox” with job number “14” (time).

– “JobMailbox” with job number “15” (date).

---

All rights reserved
016
2
Siemens AG 
ã

##### FB120 functional sequence

The following table describes the program flow of FB120. The program flow is 
implemented as a sequencer.

Table 4-2

| Network | Comment |
| --- | --- |
| 1. | General: Copy values from the “JobMailbox” area pointer to the temp area. The contents of the “JobMailbox” area pointer are read in via the “AreaPointer_JobMailbox” InOut parameter. The InOut parameter has the “Array [0..3] of Word” data type. For the later parameter assignment, it is necessary to divide the input parameter “byte by byte”. The result is temporarily stored in the “temp area”. Note: The InOut parameter has to be edited for the implementation. For information on how to program a structured data type in the InOut area of an FB and parameterize a function call with an ARRAY tag, please refer to Entry ID: 19106712. |
| 2. | General: Copy and exclude “ms” from PLC time. The current PLC system time is read out via the “Actual_PLC_Time” In parameter. The In parameter has the “Date_And_Time” data type. For the later parameter assignment, it is necessary to divide the input parameter “byte by byte” and hide the “milliseconds” contained in the time. The result is copied to the data area of the static “Edited_PLC_Time” tag. For details on the structure of the “Date_And_Time” format, please refer to the WinCC Advanced system manual. |
| 3. | Step 1: Start time synchronization via controller job “14” → time. The “SetTime” InOut parameter is used to read in/out the “start signal” for enabling time synchronization. The InOut parameter has the “Bool” data type. With the start of time synchronization, the “JobMailbox” area pointer assigns the default value “0” to parameters 1 to 3. |

---

All rights reserved
016
2
Siemens AG 
ã

4.3 Function block “FB120_TimeSyn_PLC_To_HMI”

| Network | Comment |
| --- | --- |
| 4. | Step 2: Enter parameters in “JobMailbox” area pointer. In the network, the parameters provided for job number “14” are transferred to the “JobMailbox” area pointer. For details on the structure of controller job “14”, please refer to chapter 3.2.3. From the tag area of the “Edited_PLC_Time” tag, the • hour • minute • second are read out and transferred to the parameters of the “JobMailbox” area pointer. |
| 5. | Step 3: Execute controller job “14”. Job number “14” is transferred to the auxiliary tag of the “AreaPointer_JobMailbox” InOut parameter. Note: The job number must be specified in “hex format”. |
| 6. | Step 4: Evaluation, job “14” done. The auxiliary tag of the “AreaPointer_JobMailbox” InOut parameter is used to evaluate when the previously set controller job “14” was reset to “0” by the operator panel. Note: If the operator panel has accepted the controller job, the controller job will be reset to “0”. At this time, the controller job execution is generally not yet completed. |
| 6. | <u>The evaluation of time synchronization via controller job “14” is now complete.</u> |
| 7. | Step 1: Start time synchronization via controller job “15” → date. The “SetDate” InOut parameter is used to read in/out the “start signal” for enabling time synchronization. The InOut parameter has the “Bool” data type. With the start of time synchronization, the “JobMailbox” area pointer assigns the default value “0” to parameters 1 to 3. |

$$
\text{"}14\text{"}
$$

$$
\mathsf{Time}
$$

$$
\text{"}14\text{"}
$$

$$
\text{"}0\text{"}
$$

$$
\text{"}0\text{"}.
$$

$$
"15"\rightarrow data
$$

---

All rights reserved
016
2
Siemens AG 
ã

4.3 Function block “FB120_TimeSyn_PLC_To_HMI”

| Network | Comment |
| --- | --- |
| 8. | Step 2: Enter parameters in “JobMailbox” area pointer. In the network, the parameters provided for job number “15” are transferred to the “JobMailbox” area pointer. For details on the structure of controller job “15”, please refer to chapter 3.2.3. From the tag area of the “Edited_PLC_Time” tag, the • weekday • day • month • year are read out and transferred to the parameters of the “JobMailbox” area pointer. |
| 9. | Step 3: Execute controller job “15”. Job number “15” is transferred to the auxiliary tag of the “AreaPointer_JobMailbox” InOut parameter. Note: The job number must be specified in “hex format”. |
| 10. | Step 4: Evaluation, job “15” done. The auxiliary tag of the “AreaPointer_JobMailbox” InOut parameter is used to evaluate when the previously set controller job “15” was reset to “0” by the operator panel. Note: If the operator panel has accepted the controller job, the controller job will be reset to “0”. At this time, the controller job execution is generally not yet completed. The evaluation of time synchronization via control job “15” is now complete. |
| 11. | General: Write values back to the “AreaPointer_JobMailbox” InOut tag. In network 1 for the evaluation, the parameter values of job numbers “14” and “15” have been written to the associated auxiliary tags (#Tmp_JobMailBox_Byte0, etc.). Now the values of the auxiliary tags are written back to the “AreaPointer_JobMailbox” InOut parameter. The implementation of “time synchronization” via controller jobs “14” and “15” is now complete. |

---

All rights reserved
2016
Siemens AG 
ã

### 4.4 Function block “FB210_TimeSyn_HMI_To_PLC”

##### Time synchronization “operator panel → PLC” (operator panel is the master)

“FB210” is **only** included in the “FB_TimeSyn” TIA Portal library file that can be 
found in the download section of this application example.

If you are using a SIMATIC S7-1200 / S7-1500, you can use the integrated system 
function to synchronize the time (time synchronization for integrated connections). 
For more information, please see chapter <u>5.1 (link)</u>.

If you want to synchronize the time manually, use 
“FB210_TimeSyn_HMI_To_PLC”.

“FB210” was implemented in “SCL” and has the same functionality as 
“FB110_TimeSyn_HMI_To_PLC”. Therefore, the block is not described in greater 
detail at this point.

The block is parameterized with the same tags as “FB110”.

### 4.5 Function block “FB220_TimeSyn_PLC_To_HMI”

##### Time synchronization “PLC → operator panel” (PLC is the master)

“FB220” is **only** included in the “FB_TimeSyn” TIA Portal library file that can be 
found in the download section of this application example.

If you are using a SIMATIC S7-1200 / S7-1500, you can use the integrated system 
function to synchronize the time (time synchronization for integrated connections). 
For more information, please see chapter <u>5.1 (link)</u>.

If you want to synchronize the time manually, you can optionally use 
“FB220_TimeSyn_PLC_To_HMI”.

“FB220” was implemented in “SCL” and has the same functionality as 
“FB120_TimeSyn_HMI_To_PLC”. Therefore, the block is not described in greater 
detail at this point.

The block is parameterized with the same tags as “FB120”.

---

All rights reserved
2016
Siemens AG 
ã

5.1 Variant 1: PLC (master/slave)  ↔ HMI operator panel

## 5 Configuration and Project Engineering

The following sections provide a detailed description of the individual sample 
configurations.

##### Remarks

• Please note the settings in chapter <u>6.2</u>. “Adjusting the time zone on the panel 
and in the CPU”.

• To be able to test the time synchronization functionality, additional I/O fields 
are configured in the bottom part of the HMI screens (1). 
The I/O fields are not relevant to the implementation of the time 
synchronization and are therefore not described in greater detail. 
The I/O fields are used to test the function to be executed.

Figure 5-1

Display the time

HMI system time (Slave)
5/6/2013 19:03:08

PLC system time (Master)
5/6/2013 19:03:08

Manual setting of the system time

HMI system time
5/6/2013 19:03:08

To test the clock synchronization, you can change the HMI system time here.
The HMI system time will be automatically sychronized with the plc system time. The update time are dependet of the acquisition cycle. In this case "1 minute".

### 5.1 Variant 1: PLC (master/slave) ↔ HMI operator panel

##### Time synchronization between an S7-1200 and a TP1200 Comfort Panel

##### Note

The guide can also be used for a SIMATIC S7-1500 controller.

##### Project_01, “TimeSyn_Project_01”.

In the sample project, time synchronization is performed using the “HMI time 
synchronization mode” (Master/Slave) HMI system function.

• CPU: S7-1200

• HMI_1: TP1200 “HMI time synchronization mode” => Slave

• HMI_2: TP1200 “HMI time synchronization mode” => Master

---

All rights reserved
2016
Siemens AG 
ã

5.1 Variant 1: PLC (master/slave)  ↔ HMI operator panel

##### HMI configuration

The sample project includes two HMI configurations. The configurations differ only 
in the type of time synchronization (master/slave).

##### HMI_1 (Slave)

In this configuration, the **PLC** is the timer (master). The operator panel is the time 
receiving component (slave).

Table 5-1

displayed in the working window.

It is required that a connection to an S7-1200 / S7-1500 PLC has already been configured.

| No. | Action |
| --- | --- |
| 1. | Connections |

| Project tree | TimeSyn_Project_01 [V11] ▶ HMI_01_TP1200_Slave [TP1200 Comfort] ▶ Connections |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
| Devices |  |  |  |  |  |
| TimeSyn_Project_01 [V11] | Name | Communication driver | HMI time synchronization mode | Station | Partner Node |
| Add new device | HMI_connection_1 | SIMATIC S7 1200 | Slave | SIMATIC 1200 station_1 | PLC_1 CPU 1212C DC/DC |
| Devices &amp; networks |  |  |  |  |  |
| PLC_1 [CPU 1212C DC/DC/DC] |  |  |  |  |  |
| HMI_01_TP1200_Slave [TP1200 Co... |  |  |  |  |  |
| Device configuration | < |  |  |  |  |
| Online &amp; diagnostics |  |  |  |  |  |
| Runtime settings | Parameter | Area pointer |  |  |  |
| Screens | TP1200 Comfort |  |  |  |  |
| Screen management |  |  |  |  |  |
| HMI tags |  | Interface: |  |  |  |
| Connections |  | ETHERNET |  |  |  |
| HMI alarms |  |  |  |  |  |

| Connections |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
|  | Name | Communication driver | HMI time synchronization mode | Station | Partner | Node |
|  | HMI_connection_1 | SIMATIC S7 1200 | Slave | SIMATIC 1200 station_1 | PLC_1 | CPU 1212C DC/D |
|  |  |  | None Master Slave |  |  |  |
| < | III |  |  |  |  |  |
| Parameter | Area pointer |  |  |  |  |  |
| TP1200 Comfort Interface: ETHERNET Station |  |  |  |  |  |  |
| 3. | Other time synchronization settings are not required. |  |  |  |  |  |

In “HMI time synchronization mode”, open the drop-down list and select the “ Slave ” item. The

---

All rights reserved
2016
Siemens AG 
ã

5.1 Variant 1: PLC (master/slave)  ↔ HMI operator panel

##### HMI_2 (Master)

In this configuration, the **operator panel** is the timer (master). The PLC is the time 
receiving component (slave).

Table 5-2

| No. | Action |
| --- | --- |
| 1. | Connections |

| Project tree Devices |  | TimeSyn_Project_01 [V11] ▶ HMI_02_TP1200_Master [TP1200 Comfort] ▶ Connections |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
| TimeSyn_Project_01 [V11] |  | Connections to S7 PLCs in Devices &amp; Networks |  |  |  |  |
| Add new device Devices &amp; networks |  | Name | Communication driver | HMI time synchronization mode | Station | Partner Node |
| PLC_1 [CPU 1212C DC/DC/DC] |  | HMI_connection | SIMATIC S7 1200 | Master | SIMATIC 1200 station_1 | PLC_1 CPU 1212C DC/DC |
| HMI_01_TP1200_Slave [TP1200 Comfort] |  |  |  |  |  |  |
| HMI_02_TP1200_Master [TP1200 Comfort] |  | Parameter Area pointer |  |  |  |  |
| Device configuration |  | TP1200 Comfort |  |  |  |  |
| Online &amp; diagnostics |  | Interface |  |  |  |  |
| Runtime settings |  | ETHERNET |  |  |  |  |
| Screens |  | Station |  |  |  |  |
| Screen management |  | ETHERNET |  |  |  |  |
| HMI tags |  | Connection |  |  |  |  |
| Connections |  | HMI alarms |  |  |  |  |
| Selecting time synchronization |  |  |  |  |  |  |

| Connections |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
|  | Name | Communication driver | HMI time synchronization mode | Station | Partner | Node |
|  | HMI_connection | SIMATIC S7 1200 | Master | SIMATIC 1200 station_1 | PLC_1 | CPU 1212C DC/DC |
|  |  |  | None |  |  |  |
|  |  |  | Master |  |  |  |
|  |  |  | Slave |  |  |  |
| < | III |  |  |  |  |  |
| Parameter | Area pointer |  |  |  |  |  |
| TP1200 Comfort Interface: ETHERNET |  |  |  |  |  | Station |
| 3. | Other time synchronization settings are not required. |  |  |  |  |  |

---

All rights reserved
2016
Siemens AG 
ã

5.2 Variant 2: PLC (master)  → HMI operator panel (slave)

##### Time synchronization between an S7-300 and a TP1200 Comfort Panel

**Project_02, “TimeSyn_Project_02”.**

Time synchronization via the “DateTimePLC” area pointer

• CPU: S7-300

• HMI: TP1200 Comfort Panel

##### PLC program

<u>Table 5-3</u>

| No. | Action |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- |
| 1. | DB100 (DB100_HMI_DataExchange) For data exchange between the PLC and the operator panel, DB100 was created with the following data structure. |  |  |  |  |  |
|  | DB100_HMI_DataExchange |  |  |  |  |  |
|  | Name | Data type | Offset | Start value | Retain | Visible in ... |
|  | 1 Static |  |  |  |  |  |
|  | 2 PLC_Time_RD_SYS_T | Date_And_Time | 0.0 | DT#1990-01-01-0 |  |  |
|  | 3 PLC_Time_RD_SYS_T_RetVal1 | Int | 8.0 | 0 |  |  |
|  | 4 PLC_Time_RD_SYS_T_RetVal2 | Int | 10.0 | 0 |  |  |
| 2. | OB1 (Main), network 1 In network 1, the “RD_SYS_T” system block is called. The block is used to read out the PLC system time and transfer it to the “DateTimePLC” area pointer. For this purpose, the “DateTime” tag of the “DateTimePLC” area pointer is assigned to the output of the system block. In this example: “DB10_HMI_AreaPointer”.DateTimePLC.DateTime (DB10.DBX40.0) For details on DB10 (DB10_HMI_AreaPointer), please refer to chapter 4.1. The different time functions can be found in the “Instructions” task card. The individual time functions are listed in the “Extended instructions > Date and time-of-day > Time-of-day functions” palette. |  |  |  |  |  |

---

All rights reserved
2016
Siemens AG 
ã

### 5.2 Variant 2: PLC (master) → HMI operator panel (slave)

| EN | ENO |
| --- | --- |
|  | %DB100.DBW8 |
|  | "DB100_HMI_DataExchange".PLC_Time_RD_SYS_T_RetVal1 |
|  | RET_VAL |
|  | P#DB10.DBX40.0 |
|  | "DB10_HMI_AreaPointer".DateTimePLC. |
|  | OUT - DateTime |

RD_SYS_T
Date_And_Time

EN ENO

%DB100.DBW8
"DB100_HMI_
DataExchange".
PLC_Time_RD_
SYS_T_RetVal1

RET_VAL

P#DB10.DBX40.0
"DB10_HMI_
AreaPointer".
DateTimePLC
DateTime

P#DB10.DBX40.0 / Date_And_Time

OUT

3. **OB1, network 2 (optional network)**

In network 2, the **“RD_SYS_T”** system block is called.
The block is used to read out the PLC system time and directly output it in an I/O field on the 
operator panel.
In terms of the example, this allows you to compare the current operator panel system time to 
the PLC system time.
This network is not required to implement the task.

| RD_SYS_T Date_And_Time |  |
| --- | --- |
| EN | ENO |
|  | %DB100.DBWIO *DB100_HMI_ DataExchange*. PLC_Time_RD_ SYS_T_RetVal2 |
| RET_VAL | P#DB100.DBXO.O *DB100_HMI_ DataExchange*. PLC_Time_RD_ SYS_T |
| OUT | OUT |

4. In the PLC, other time synchronization settings are not required.

---

All rights reserved
2016
Siemens AG 
ã

5.2 Variant 2: PLC (master)  → HMI operator panel (slave)

##### HMI configuration

In this configuration, the **PLC** is the timer (master). The HMI operator panel is the 
time receiving component (slave).

<u>Table 5-4</u>

| No. | Action |
| --- | --- |
| 1. | Connections In the project tree, open the “Connections” component. The existing connections are displayed in the working window. Note: It is required that a connection to a SIMATIC S7 controller has already been configured. In this example: SIMATIC S7 300/400 |
| Project tree | TimeSyn_Project_02 [V11] HMI_1 [TP1200 Comfort] Connections |
| Devices |  |
| TimeSyn_Project_02 [V11] | Name Communication driver HMI time synchronization mode Partner Node Station Online |
| Add new device | HMI_connection SIMATIC S7 300/400 PLC_1 CPU 315-2 PN/DP S7300/ET200M station_1 |
| Devices &amp; networks |  |
| PLC_1 [CPU 315-2 PN/DP] | < |
| HMI_1 [TP1200 Comfort] | Parameter Area pointer |
| Device configuration Online &amp; diagnostics Runtime settings Screens Screen management HMI tags Connections HMI alarms Recipes Historical data Scripts Scheduled tasks Cycles Reports Text and graphic lists User administration | TP1200 Comfort Interface ETHERNET |

TimeSyn_Project_02 [V11] > HMI_1 [TP1200 Comfort] > Connections

Connections to S7 PLCs in Devices & Networks

Connections
Name Communication driver HMI time synchronization mode Partner Node Station Online
HMI_connection SIMATIC S7 300/400 PLC_1 CPU 315-2 PH/DP S7300/ET200M station_1
<Add new>

Parameter Area pointer

TP1200 Comfort
Interface:
ETHERNET

Station

HMI device
Address: 172 16 34 200
Access point: S7ONLINE

PLC
Address: 172 16 34 34
Expansion slot: 2
Rack: 0
Cyclic operation: ✓

---

All rights reserved
2016
Siemens AG 
ã

5.2 Variant 2: PLC (master)  → HMI operator panel (slave)

| No. | Action |
| --- | --- |
| 2. | Selecting the area pointer • In the “Connections” editor, select the “Area pointer” tab. • The “Area pointer” tab includes two tables with area pointers. Open the “Global area pointer” table. You may have to use the arrow buttons (1) to open the table. • Assign a connection to be used for time synchronization to the “DateTimePLC” area pointer. (2). |
| Global area pointer of HMI device |  |
| 3. | Parameterizing the “DateTimePLC” area pointer The figure below shows the parameterized “DateTimePLC” area pointer. |
| Parameter description Connection: In the window, select the PLC connection. Selecting the connection (controller) activates the area pointer. In this example: HMI_connection Name of the area pointer. Set by WinCC. This is where you select the PLC tag you have configured as the data area for the area pointer. In this example: DB10_HMI_AreaPointer.DateTimePLC.DateTime When you have symbolically selected the tag in the “PLC tag” field, this field displays the tag address. Alternatively, enter the tag address manually. In this example: %DB10.DBX40.0 Length of the area pointer. Set by WinCC. In this field, define the acquisition cycle. Please keep in mind that a very short acquisition time can influence the operator panel performance. In this example: 1 min |  |
| 4. | Other time synchronization settings are not required. |

---

All rights reserved
2016
Siemens AG 
ã

5.3 Variant 3: PLC (master)  → HMI operator panel (slave)

##### Time synchronization between an S7-300 and a TP1200 Comfort Panel

**Project_03, “TimeSyn_Project_03”.**

Time synchronization via the “JobMailbox” area pointer with controller job numbers 
14 (time) and 15 (date).

For this time synchronization, the time and the date of the operator panel can be 
synchronized with the PLC system time/date independently of each other.

• CPU: S7-300

• HMI: TP1200 Comfort Panel

• PLC program block:

FB120 (S7-300/400) or

FB220 (S7-1200 / S7-1500)

**Note** If you are using an S7-1200 / S7-1500, please follow the information provided in 
chapter <u>4.5</u>.

##### PLC program

<u>Table 5-5</u>

| DB10_HMI_AreaPointer |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
|  | Name | Data type | Offset | Start value |  |
| 1 | Static |  |  |  |  |
| 2 | Coordination | Word | 0.0 | 16#0 |  |
| 3 | DateTime | Date_And_Time | 2.0 | DT#1990-01-01-0 |  |
| 4 | JobMailbox | Array [0..3] of Word | 10.0 |  |  |
| 5 | DataRecord | Array [0..4] of Word | 18.0 |  |  |
| 6 | ProjectID | Word | 28.0 | 16#0 |  |
| 7 | ScreenNumber | Array [0..4] of Word | 30.0 |  |  |
| 8 | DateTimePLC | Struct | 40.0 |  |  |

---

All rights reserved
2016
Siemens AG 
ã

5.3 Variant 3: PLC (master)  → HMI operator panel (slave)

| No. | Action |  |  |  |
| --- | --- | --- | --- | --- |
| 2. | DB100 (DB100_HMI_DataExchange) For data exchange between the PLC and the operator panel, DB100 was created with the following data structure. DB100_HMI_DataExchange |  |  |  |
|  | Name | Data type | Offset | Start value |
|  | Static |  |  |  |
| 3. | OB1 (Main), network 1 In network 1, the “RD_SYS_T” system block is called. The block is used to read out the PLC system time and transfer it to FB120 “FB120_TimeSyn_PLC_To_HMI” via a tag. Furthermore, the PLC system time is output on the operator panel via the used tag. In this example: “DB100_HMI_DataExchange”.PLC_Time_RD_SYS_T (DB100.DBX0.0) Network 1: Read current PLC system time Comment EN RD_SYS_T Date_And_Time ENO %DB100.DBWB *DB100_HMI_ DataExchange PLC_Time_RD_ SYS_T_RetVal RET_VAL PDB100.DBX0.0 *DB100_HMI_ DataExchange PLC_Time_RD_ SYS_T OUT |  |  |  |
| 4. | OB1 (Main), network 2 In network 2, the “FB120” program block is called. (FB120_TimeSyn_PLC_To_HMI). FB120 evaluates the data of the “JobMailbox” area pointer and, based on this information, provides the time and date of the operator panel. FB120 was created specifically for this time synchronization. It is not necessary to edit the program block. The block includes all the necessary functions. To implement time synchronization, it is only necessary to parameterize FB120 according to the specifications. For details on FB120, please refer to chapter 4.3. |  |  |  |

---

All rights reserved
2016
Siemens AG 
ã

5.3 Variant 3: PLC (master)  → HMI operator panel (slave)

| No. | Action |
| --- | --- |
|  | Network 2: Time synchronization PLC -> HMI via area pointer "Job mailbox" and job number "14" or "15". Comment %DB120 *DB120_TimeSyn_PLC_To_HMI_DB %FB120 FB120_TimeSyn_PLC_To_HMI" EN ENO PIDB100_DBX0.0 *DB100_HMI_DataExchange". PLC_Time_RD_SYS_T Current_PLC_Time PIDB10_DBX10.0 *DB10_HMI_AreaPointer_JobMailbox AreaPointer_JobMailbox %DB100_DBX10.0 *DB100_HMI_DataExchange". Start_TimeSyn — SetTime %DB100_DBX10.1 *DB100_HMI_DataExchange". Start_DateSyn — SetDate |
| 5. | FB120, parameterization Name/no. of FB: Any FB name/number can be selected. In this example: Name: FB120_TimeSyn_PLC_To_HMI FB no.: 120 Instance DB: Any instance DB name/number can be selected. In this example: Name: DB120_TimeSyn_PLC_To_HMI_DB Current_PLCTime: IN parameter of the "Date_And_Time" type The parameter is used to read in the current PLC system time. Link the parameter to the OUT parameter of the "RD_SYS_T" system block (network 1). In this example: "DB100_HMI_DataExchange".PLC_Time_RD_SYS_T DB100.DBX0.0 AreaPointer_Job_Mailbox: INOUT parameter of the "Array [0..3] of Word" type Link the parameter to the address of the "JobMailbox" area pointer. In this example: "DB10_HMI_AreaPointer".JobMailbox DB10.DBX10.0 6. SetTime: INOUT parameter of the "Bool" type A button on the operator panel starts time |

---

All rights reserved
2016
Siemens AG 
ã

5.3 Variant 3: PLC (master)  → HMI operator panel (slave)

| No. | Action |
| --- | --- |
|  | synchronization on the operator panel. Link the parameter to the address of the used button. In this example: “DB100_HMI_DataExchange”.Start_TimeSynDB100.DBX10.0 |
|  | SetDate: INOUT parameter of the “Bool” type A button on the operator panel starts date synchronization on the operator panel. Link the parameter to the address of the used button. In this example: “DB100_HMI_DataExchange”.Start_DateSynDB100.DBX10.1 Link to the HMI configuration |
| 7. | In the PLC, other time synchronization settings are not required. |

---

All rights reserved
2016
Siemens AG 
ã

5.3 Variant 3: PLC (master)  → HMI operator panel (slave)

##### HMI configuration

In this configuration, the **PLC** is the timer (master). The operator panel is the time 
receiving component (slave).

<u>Table 5-6</u>

| No. | Action |
| --- | --- |
| 1. | Connections In the project tree, open the “Connections” component. The existing connections are displayed in the working window. Select the connection via which the operator panel is to be synchronized. In this example: HMI_connection Notes: • It is required that a connection to a SIMATIC S7 controller has already been configured. • The area pointer used in this example can be activated separately for each configured connection. In this example: SIMATIC S7 300/400 connection |
| Parameter | Area pointer |
| TP1200 Comfort Interface: ETHERNET Station |  |
| HMI device Address: 172 16 34 200 Access point: S7ONLINE | PLC Address: 172 16 34 34 Expansion slot: 2 Rack: 0 Cyclic operation: ✓ |

---

All rights reserved
2016
Siemens AG 
ã

5.3 Variant 3: PLC (master)  → HMI operator panel (slave)

| No. | Action |
| --- | --- |
| 2. | Selecting the area pointer |

| 3. | Global area pointer of HMI device |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
| 3. | Parameterizing the “JobMailbox” area pointer “JobMailbox” parameter description Active: Check the check box to the left of the “JobMailbox” area pointer. Display name: Name of the area pointer. Set by WinCC. PLC tag: This is where you select the PLC tag you have configured as the data area for the area pointer. In this example: DB10_HMI_AreaPointer.JobMailbox Address: When you have symbolically selected the tag in the “PLC tag” field, this field displays the tag address. Alternatively, enter the tag address manually. In this example: DB10.DBX10.0 Length: Length of the area pointer. Set by WinCC. Acquisition cycle: In this field, define the acquisition cycle. Please keep in mind that a very short acquisition time can influence the operator panel performance. In this example: 1 s |  |  |  |  |
| 4. | HMI screen Two buttons are required to execute the functions (1). • Button 1: Time synchronization In “Properties > Events > Press”, use the “SetBit” system function. Link the parameter to this address: “DB100_HMI_DataExchange”.Start_TimeSyn (DB100.DBX10.0) • Button 2: Date synchronization In “Properties > Events > Press”, use the “SetBit” system function. Link the parameter to this address: “DB100_HMI_DataExchange”.Start_DateSyn (DB100.DBX10.1) |  |  |  |  |
| 5. | Other time synchronization settings are not required. |  |  |  |  |

Time synchronization

Display the time

HMI system time (Slave)
12/31/2000 10:59:39 AM

PLC system time (Master)
12/31/2000 10:59:39 AM

Start set HMI time

Start set HMI date

---

All rights reserved
2016
Siemens AG 
ã

5.4 Variant 4: HMI operator panel (master)  → PLC (slave)

##### Time synchronization between an S7-300 and a TP1200 Comfort Panel

**Project_04, “TimeSyn_Project_04”.**

Time synchronization via the “DateTime” and “JobMailbox” area pointers with job 
number 40.

• CPU: S7-300

• HMI: TP1200 Comfort Panel

• PLC program block: FB110 (S7-300/400) or

FB210 (S7-1200 / S7-1500)

**Note** If you are using an S7-1200 / S7-1500, please follow the information provided in 
chapter <u>4.4</u>.

##### PLC program

<u>Table 5-7</u>

| DB10_HMI_AreaPointer |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
|  | Name | Data type | Offset | Start value |  |
| 1 | Static |  |  |  |  |
| 2 | Coordination | Word | 0.0 | 16#0 |  |
| 3 | DateTime | Date_And_Time | 2.0 | DT#1990-01-01-0 |  |
| 4 | JobMailbox | Array [0..3] of Word | 10.0 |  |  |
| 5 | DataRecord | Array [0..4] of Word | 18.0 |  |  |
| 6 | ProjectID | Word | 28.0 | 16#0 |  |
| 7 | ScreenNumber | Array [0..4] of Word | 30.0 |  |  |
| 8 | DateTimePLC | Struct | 40.0 |  |  |

---

All rights reserved
2016
Siemens AG 
ã

5.4 Variant 4: HMI operator panel (master)  → PLC (slave)

| No. | Action |  |  |  |  |
| --- | --- | --- | --- | --- | --- |
| 2. | DB100 (DB100_HMI_DataExchange) For data exchange between the PLC and the operator panel, DB100 was created with the following data structure. |  |  |  |  |
| 2. | DB100_HMI_DataExchange |  |  |  |  |
| 2. | Name | Data type | Offset | Start value |  |
| 2. | Static |  |  |  |  |
| 1 | PLC_Time_RD_SYS_T | Date_And_Time | 0.0 | DT#1990-01-01-00:00:00 |  |
| 2 | PLC_Time_RD_SYS_T_RetVal | Int | 8.0 | 0 |  |
| 3 | PLC_Time_WR_SYS_T_RetVal | Int | 10.0 | 0 |  |
| 4 | Start_TimeSyn | Bool | 12.0 | false |  |
| 5 | Start_TimeSyn | Bool | 12.0 | false |  |
| 3. | OB1 (Main), network 1 In network 1, program block "FB110"(FB110_TimeSyn_HMI_To_PLC) is called. FB110 evaluates the data of the "DateTime" and "JobMailbox" area pointers and, based on this information, provides the time and date of the PLC. FB110 was created specifically for this time synchronization. It is not necessary to edit the program block. The block includes all the necessary functions. To implement time synchronization, it is only necessary to parameterize FB110 according to the specifications. For details on FB110, please refer to chapter 4.2. Block title: "Main Program Sweep (Cycle)" Comment Network 1: Ubergabe der SPS-Systemzeit an den Bereichszeiger Datum/Uhrzeit Hand-off PLC system time to the area pointer Date/Time EN ENO PDB100.DBX2.0 "DB10_HMI_AreaPointer" DateTime - AreaPointer_DateTime Current_PLCTime - SYS_T PDB100.DBX10.0 "DB10_HMI_AreaPointer" AreaPointer_JobMailbox %DB100.DBX12.0 "DB100_HMI_DataExchange" Start_TimeSyn - SetTime |  |  |  |  |

---

All rights reserved
2016
Siemens AG 
ã

5.4 Variant 4: HMI operator panel (master)  → PLC (slave)

| No. | Action |  |
| --- | --- | --- |
| 4. | FB110, parameterization |  |
| 4. | Name/no. of FB: | Any FB name/number can be selected. In this example: Name: FB110_TimeSyn_HMI_To_PLCFB no.: 110 |
| 4. | Instance DB: | Any instance DB name/number can be selected. In this example: Name: DB110_TimeSyn_HMI_To_PLCFB no.: 110 |
| 4. | AreaPointer_DateTime: | IN parameter of the “Date_And_Time” type Link the parameter to the address of the “DateTime” area pointer. In this example: “DB10_HMI_AreaPointer”.DateTime DB10.DBX2.0 |
| 4. | AreaPointer_Job_Mailbox: | INOUT parameter of the “Array [0..3] of Word” type Link the parameter to the address of the “JobMailbox” area pointer. In this example: “DB10_HMI_AreaPointer”.JobMailbox DB10.DBX10.0 |
| 4. | SetTime: | INOUT parameter of the “Bool” type A button on the panel starts time synchronization. Link the parameter to the address of this button. In this example: “DB100_HMI_DataExchange”.Start_TimeSyn DB100.DBX12.0 |
| 4. | Current_PLCTime: | OUT parameter of the “Date_And_Time” type The tag is used to output the current PLC system time on the panel. Link the parameter to the address of this “date/time field”. In this example: “DB100_HMI_DataExchange”.PLC_Time_RD_SYS_T DB100.DBX0.0 |
| 4. | Link to the HMI configuration |  |
| 5. | In the PLC, other time synchronization settings are not required. |  |

---

All rights reserved
2016
Siemens AG 
ã

5.4 Variant 4: HMI operator panel (master)  → PLC (slave)

##### HMI configuration

In this configuration, the **operator panel** is the timer (master). The PLC is the time 
receiving component (slave).

<u>Table 5-8</u>

| No. | Action |
| --- | --- |
| 1. | Connections In the project tree, open the “Connections” component. The existing connections are displayed in the working window. Select the connection via which the operator panel is to be synchronized. In this example: HMI_connection Notes: • It is required that a connection to a SIMATIC S7 controller has already been configured. • The area pointers used in this example can be activated separately for each configured connection. In this example: SIMATIC S7 300/400 connection |
| Project tree | TimeSyn_Project_03 [V11] > HMI_1 [TP1200 Comfort] > Connections |
| Devices |  |
| TimeSyn_Project_03 [V11] | Name Communication driver Station HMI time synchronization mode Partner Node |
| Add new device | HMI_connection SIMATIC S7 300/400 S7300/ET200M station_1 PLC_1 CPU 315-2 FIN/DP |
| Devices &amp; networks |  |
| PLC_1 [CPU 315-2 FIN/DP] |  |
| HMI_1 [TP1200 Comfort] |  |
| Device configuration |  |
| Online &amp; diagnostics | Parameter Area pointer |
| Runtime settings |  |
| Screens | TP1200 Comfort |
| Screen management |  |
| HMI tags | Interface |
| Connections | ETHERNET |
| HMI alarms |  |
| Recipes |  |
| Historical data |  |
| Scripts | HMI device |
| Scheduled tasks | Address: 172 16 34 200 |
| Cycles | Access point S7ONLINE |
| Reports | Rack: |
| Text and graphic lists |  |
| User administration |  |
| Common data |  |

Project tree

Devices

TimeSyn_Project_03 [V11]
Add new device
Devices & networks
PLC_1 [CPU 315-2 PN/DP]
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
Common data

TimeSyn_Project_03 [V11] > HMI_1 [TP1200 Comfort] > Connections

Connections to S7 PLCs in Devices & Networks

Connections
Name Communication driver Station HMI time synchronization mode Partner Node
HMI_connection SIMATIC S7 300/400 S7300/ET200M station_1 PLC_1 CPU 315-2 PN/DP
<Add new>

Parameter Area pointer

TP1200 Comfort
Interface:
ETHERNET

Station

HMI device
Address: 172 16 34 200
Access point: S7ONLINE

PLC
Address: 172 16 34 34
Expansion slot: 2
Rack: 0
Cyclic operation:

---

All rights reserved
2016
Siemens AG 
ã

5.4 Variant 4: HMI operator panel (master)  → PLC (slave)

| No. | Action |
| --- | --- |
| 2. | Selecting the area pointer |

| 3. | Parameterizing the “DateTime” and “JobMailbox” area pointers “DateTime” parameter description Active: Check the check box to the left of the “DateTime” area pointer. Display name: Name of the area pointer. Set by WinCC. PLC tag: This is where you select the PLC tag you have configured as the data area for the area pointer. In this example: DB10_DBX2.0 Length: Length of the area pointer. Set by WinCC. “JobMailbox” parameter description Active: Check the check box to the left of the “JobMailbox” area pointer. Display name: Name of the area pointer. Set by WinCC. PLC tag: This is where you select the PLC tag you have configured as the data area for the area pointer. In this example: DB10_HMI_AreaPointer.JobMailbox Address: When you have symbolically selected the tag in the “PLC tag” field, this field displays the tag address. Alternatively, enter the tag address manually. Address: DB10_DBX10.0 Length: DB10_DBX10.0 Acquisition cycle: In this field, define the acquisition cycle. Please keep in mind that a very short acquisition time can influence the operator panel performance. In this example: 1 min | Length | Acquisition cycle | Acc |
| --- | --- | --- | --- | --- |

---

All rights reserved
2016
Siemens AG 
ã

##### 5 Configuration and Project Engineering

5.4 Variant 4: HMI operator panel (master)  → PLC (slave)

| No. | Action |
| --- | --- |
| 4. | HMI screen |

Time synchronization

Display the time

HMI system time (Master)
12/31/2000 10:59:39 AM

PLC system time (Slave)
12/31/2000 10:59:39 AM

Start
set plc time

$$
\textcircled{1}
$$

---

All rights reserved
2016
Siemens AG 
ã

## 6 Operation of the Application

### 6.1 General

The functionality described below is the same for all time synchronization projects / 
variants.

##### Transferring the configuration files

To operate the application, the following requirements must be met:

• The STEP 7 program was transferred to the PLC.

• The HMI configuration was transferred to the operator panel.

##### Note regarding the CPU system date

To use the application, the CPU system time must be set to a time after the year 
2000. This has to be taken into account particularly after a general reset of the 
CPU.

The “Online > Online & diagnostics > Functions > Set time” menu command allows 
you to edit the CPU system time.

Figure 6-1

Project Edit View Insert Save project Online Options Tools Window Help Go online Ctrl+K Go offline Ctrl+M Simulation Stop runtime/simulation Download to device Ctrl+L Extended download to device... Download and reset PLC program Download user program to Memory Card Upload from device Backup from online device Upload device to PG/PC... HMI Device maintenance Accessible devices... Start CPU Stop CPU Online & diagnostics

TimeSyn_Project_04 [V11] PLC_1 [CPU 317-2 PN/DP]

Online access Diagnostics Functions Assign IP address Set time Firmware update Assign name Reset to factory settings

Set time ___

PG/PC time:
(GMT+01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vien
May 28, 2013 10:23:32 AM

Module time
May 28, 2013 10:24:23 AM
Take from PG/PC Apply

---

All rights reserved
2016
Siemens AG 
ã

#### 6.1.1 Opening the “plant screens”

##### Start screen

When starting the HMI project on the operator panel, the following start screen is 
displayed.

The **“Application example”** button takes you to the navigation overview of the 
created time synchronization plant screens.

Figure 6-2

Siemens Industry Online Support

Siemens - My First Project

Start
Devices & networks
PLC programming
Open existing project
Create new project
Migrate project
Close project

First steps
Project "My First Project" was opened successfully. Please enter the next day.

Start

Device & RPACS
Configure a device
PLC Program
Work PLC program
Configure PLC server

Application example

Siemens Industry Online Support

##### Navigation overview

This screen provides you with the navigation overview of the configured plant 
screens. In this example, there is only the **“Overview”** screen.

The “Overview” screen provides a summary of the task.

The “Time synchronization” button takes you to the time synchronization “plant 
screen”.

Figure 6-3

5/8/2013 19:51
Overview
SIEMENS
Time
synchronization
Clock synchronization between a TP1200 Comfort Panel and a PLC S7-300/400
In this sample, the PLC S7-300 are the "master clock" and set the system time.
The time synchronization is done via the are area pointer "Job mailbox".
Job mailbox "14" -> Set HMI time
Job mailbox "15" -> Set HMI Date
11.04.2013 11:26:20
Separate time synchronization
Separate date synchronization
S7-300
11.04.2013 12:26:20

---

All rights reserved
2016
Siemens AG 
ã

6.1 General

#### 6.1.2 Functionality of the plant screens

The figure below shows the user interface. Except for the labels, the objects shown 
in this figure are identical in all sample projects.

Figure 6-4

Display the time

$\textcircled{1}$ HMI system time (Slave)
5/6/2013 19:03:08

PLC system time (Master)
5/6/2013 19:03:08

Manual setting of the system time

$\textcircled{2}$ HMI system time
5/6/2013 19:03:08

To test the clock synchronization, you can change the HMI system time here.
The HMI system time will be automatically sychronized with the plc system time. The update time are dependet of the acquisition cycle. In this case "1 minute".

##### Section 1:

The top part of the screen contains two output fields. The system time of the 
operator panel and the system time of the PLC are output in the output fields.

The two output fields are not mandatory for implementing time synchronization.

##### Section 2:

The “input/output field” in the bottom part of the screen is used to test the time 
synchronization functionality.

The “input/output field” is **not** mandatory for implementing time synchronization.

---

All rights reserved
2016
Siemens AG 
ã

### 6.2 Adjusting the time zone on the panel and in the CPU

To make sure that the time is displayed correctly, the same time zone must be set 
on the panel and in the CPU.

##### Setting the time zone on the panel

<u>Table 6-1</u>

**No. Action**

1. **Date/Time**
On the panel, open the Control Panel and click the “Date/Time” dialog.
Current Time 16:11:01
Time Zone (GMT+01:00) Amsterdam, Berlin, Bern, Rom
Apply
• In “Time Zone”, select the active time zone for the operator panel.
• Select “Apply” to confirm your setting.
The time displayed in “Current Time” is adjusted to the selected time zone.

**Note regarding Basic Panels**
For Basic Panels, you set the time shift directly in the “Date/Time” dialog box.

---

All rights reserved
2016
Siemens AG 
ã

##### Setting the time zone in the CPU

<u>Table 6-2</u>

| No. | Action |
| --- | --- |
| 1. | Time zone Depending on the controller used, you can directly set the time zone using the menu on the display of the CPU (e.g., S7 1500) or using an online connection between the CPU and the configuration computer. In the configuration user interface, use the project tree to open “Online &amp; diagnostics”. The “Set time” menu item allows you to change the module time. The time zone (“PG/PC time”) is applied from the time zone stored in the PC’s Control Panel. |

Online-Zugänge
Diagnose
Funktionen
IP-Adresse zuweisen
Uhrzeit einstellen
Firmware-Update
Name zuweisen
Rücksetzen der PROFINE...

Uhrzeit einstellen

PG/PC Zeit:
(UTC+01:00) Amsterdam, Berlin, Bern, Rom, Stockholm, Wien
08 . Februar . 2016 11 : 42 : 46

Baugruppenzeit
08 . February . 2014 11 : 41 : 45
Von PG/PC übernehmen Übernehmen

---

All rights reserved
2016
Siemens AG 
ã

### 6.3 TimeSyn_Project_01 (variant 1)

##### S7-1200 / S7-1500 time synchronization

Time synchronization between an S7-1200 and a TP1200 Comfort Panel.

##### HMI_01_TP1200_Slave

PLC (master)  →  HMI operator panel (slave)

<u>Table 6-3</u>

| No. | Action |
| --- | --- |
| 1. | Opening time synchronization |

|  | Select “Application example > Overview > Time synchronization” to open the time synchronization. |
| --- | --- |
| 2. | Specifying HMI time In the I/O field, change the HMI system time and press “Enter” to complete your entry (1). In this example: The HMI time (hour) was changed to 7 p.m. The HMI system time is adjusted to the PLC system time within a minute. |

---

All rights reserved
2016
Siemens AG 
ã

##### HMI_02_TP1200_Master

HMI operator panel (master)  →  PLC (slave)

<u>Table 6-4</u>

| No. | Action |
| --- | --- |
| 1. | Opening time synchronization |

|  | Select “Application example > Overview > Time synchronization” to open the time synchronization. |
| --- | --- |
| 2. | Specifying HMI time In the I/O field, change the HMI system time and press “Enter” to complete your entry (1). In this example: The HMI time (hour) was changed to 7 p.m. The PLC system time is adjusted to the HMI system time within a minute. |

---

All rights reserved
2016
Siemens AG 
ã

### 6.4 TimeSyn_Project_02 (variant 2)

##### PLC (master) → operator panel (slave)

Time synchronization between an S7-300 controller and a TP1200 Comfort Panel. 
Time synchronization is performed via the “DateTimePLC” area pointer.

<u>Table 6-5</u>

| No. | Action |
| --- | --- |
| 1. | Opening time synchronization |

|  | Select “Application example > Overview > Time synchronization” to open the time synchronization. |
| --- | --- |
| 2. | Specifying HMI time In the I/O field, change the HMI system time and press “Enter” to complete your entry (1). In this example: The HMI time (hour) was changed to 9 a.m. The HMI system time is adjusted to the PLC system time within a minute. |

---

All rights reserved
2016
Siemens AG 
ã

### 6.5 TimeSyn_Project_03 (variant 3)

##### PLC (master) → operator panel (slave)

Time synchronization between an S7-300 controller and a TP1200 Comfort Panel. 
Time synchronization is performed via the “JobMailbox” area pointer with controller 
job numbers 14 (time) and 15 (date).

For this time synchronization, the time and the date of the operator panel can be 
synchronized with the PLC system time independently of each other.

<u>Table 6-6</u>

| No. | Action |
| --- | --- |
| 1. | Opening time synchronization Select “Application example > Overview > Time synchronization” to open the time synchronization. |
| 2. | Specifying HMI date (alternatively, the time) • In the I/O field, change the HMI system date and press “Enter” to complete your entry (1). • Select the “Start set HMI date” button (3). In this example: The HMI date (month) was changed to “4”. Selecting the button (3) adjusts the HMI system date to the PLC system date. |

---

All rights reserved
2016
Siemens AG 
ã

### 6.6 TimeSyn_Project_04 (variant 4)

##### Operator panel (master) → PLC (slave)

Time synchronization between a TP1200 Comfort Panel and a S7-300 controller. 
Time synchronization is performed via the “DateTime” and “JobMailbox” area 
pointers with job number 40.

<u>Table 6-7</u>

| No. | Action |
| --- | --- |
| 1. | Opening time synchronization |

|  | Select “Application example > Overview > Time synchronization” to open the time synchronization. |
| --- | --- |
| 2. | Specifying HMI time • In the I/O field, change the HMI system time and press “Enter” to complete your entry (1). • Select the “Start set plc time” button (2). In this example: The HMI time (hour) was changed to 9 a.m. Selecting the button adjusts the PLC system time to the HMI system time. |

---

All rights reserved
2016
Siemens AG 
ã

7.1 Replacing the PLC

## 7 Notes and Tips

The aim of the following notes and tips is to provide support if you want to 
customize the supplied configurations to your project environment.

### 7.1 Replacing the PLC

Instead of the SIMATIC PLC used, other SIMATIC controllers from the WinCC TIA 
Portal hardware catalog can also be used.

When replacing the SIMATIC controller, consider and then check the following:

• Only SIMATIC S7-1200 and S7-1500 controllers support time synchronization 
via “HMI time synchronization mode”.

• If you are using the function blocks from the “TimeSyn_Project_03” (FB120) or 
“TimeSyn_Project_04” (FB110) projects, the SIMATIC controller used must 
support the “STL” programming language.
The FBs were created in the STL programming language.

• If, for example, the connection paths (Ethernet/PROFIBUS) have changed, 
check the following in the HMI configuration …

– the configured HMI connection

– the connections used in the global area pointer

– the addresses of the area pointers used.

### 7.2 Replacing the HMI operator panel

Instead of the TP1200 Comfort Panel used, you can also use a different operator 
panel from the WinCC TIA Portal hardware catalog.

When replacing the HMI operator panel, consider and then check the following.

• Area pointers used

Before replacing the operator panel, make sure that the new operator panel 
supports the area pointer used. For more information, please refer to the 
WinCC (TIA Portal) system manual.

• Creating a new connection (PROFINET → PROFIBUS).

If you create a new connection instead of the existing connection, you have to 
rebuild the area pointers.

---

All rights reserved
2016
Siemens AG 
ã
Copyright

## 8 Links & Literature

### 8.1 References

This list is not complete and only represents a selection of relevant literature.

Table 8-1

|  | Topic | Title |
| --- | --- | --- |
| /1/ | STEP7 SIMATIC S7-300/400 | Automating with STEP7 in STL and SCL Author: Hans Berger Publicis Publishing ISBN: 978-3-89578-412-5 |
| /2/ | STEP7 SIMATIC S7-300/400 | Automating with STEP7 in LAD and FBD Author: Hans Berger Publicis Publishing ISBN: 978-3-89578-410-1 |
| /3/ | STEP7 SIMATIC S7-300 | Automating with SIMATIC S7-300 inside TIA Portal Author: Hans Berger Publicis Publishing ISBN: 978-3-89578-443-9 |
| /4/ | STEP7 SIMATIC S7-400 | Automating with SIMATIC S7-400 inside TIA Portal Author: Hans Berger Publicis Publishing ISBN: 978-3-89578-383-8 |
| /5/ | STEP7 SIMATIC S7-1200 | Automating with SIMATIC S7-1200 Author: Hans Berger Publicis Publishing ISBN: 978-3-89578-385-2 |

### 8.2 Internet links

This list is not complete and only represents a selection of relevant information.
Table 8-2

|  | Topic | Title |
| --- | --- | --- |
| 1 | Reference to the entry | https://support.industry.siemens.com/cs/ww/de/view/69864408 |
| 2 | Siemens Industry Online Support | https://support.industry.siemens.com |
| 3 | WinCC Advanced V13.0 System Manual | https://support.industry.siemens.com/cs/ww/en/view/91479053 |
| 4 | FAQ | How do you parameterize a structured data type in the IN_OUT area of an FB and a function call with an ARRAY variable? https://support.industry.siemens.com/cs/ww/en/view/19106712 |

---

All rights reserved
2016
Siemens AG 
ã
Copyright

## 9 History

Table 9-1

| Version | Date | Modifications |
| --- | --- | --- |
| V1.0 | 08/2013 | First version |
| V1.1 | 09/2013 | Chapter 2.1, notes added |
| V1.2 | 11/2013 | Controller job 13/14 => controller job 14/15 |
| V1.3 | 04/2015 | FBs adjusted. Initialization of address register |
| V4.0 | 03/2016 | Documentation completely revised |
| V4.0 | 04/2017 | Figures under chpater 3.2 changed (german -> english) |