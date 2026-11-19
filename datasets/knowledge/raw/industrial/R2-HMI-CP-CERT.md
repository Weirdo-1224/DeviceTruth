Due to routine maintenance activities SiePortal may experience brief interruptions from Monday, November 16 03:00 PM CEST to Tuesday, November 17 09:00 PM CEST.

×

Siemens Industry Online Support

Siemens AG

VIEW

SiePortal

- Products & Services











  - [Drive technology](https://mall.industry.siemens.com/mall/en/ar/Catalog/Products/9990173?tree=CatalogTree&SiepCountryCode=AR)

  - [Automation technology](https://mall.industry.siemens.com/mall/en/ar/Catalog/Products/10045207?tree=CatalogTree&SiepCountryCode=AR)

  - [Energy](https://mall.industry.siemens.com/mall/en/ar/Catalog/Products/10313567?tree=CatalogTree&SiepCountryCode=AR)

  - [Building Technologies](https://mall.industry.siemens.com/mall/en/ar/Catalog/Products/10047631?tree=CatalogTree&SiepCountryCode=AR)

  - [Low-Voltage controls and distribution](https://mall.industry.siemens.com/mall/en/ar/Catalog/Products/9990301?tree=CatalogTree&SiepCountryCode=AR)

  - [Services](https://mall.industry.siemens.com/mall/en/ar/Catalog/Products/10008397?tree=CatalogTree&SiepCountryCode=AR)

  - [List of configurators](https://mall.industry.siemens.com/mall/en/ar/Catalog/Configurators?SiepCountryCode=AR)


- [Support](https://sieportal.siemens.com/en-ar/support)











  - [Knowledge base](https://sieportal.siemens.com/en-ar/search?scope=knowledgebase&CountryCode=ar&Language=en&SearchTerm=&EntryTypes=all&Page=0&PageSize=20)

  - [Forum](https://sieportal.siemens.com/en-ar/support/forum)

  - [Topic pages (important topics at a glance)](https://support.industry.siemens.com/cs/sc/3058/important-topics-at-a-glance?lc=en-ar)

  - [Create master data download](https://sieportal.siemens.com/en-ar/onemd4c/wizard/1)

  - [Create support request](https://sieportal.siemens.com/en-ar/mysieportal/support-requests/create)

  - [Contact](trigger:contactMenu)


- [mySiePortal](https://sieportal.siemens.com/en-ar/mysieportal)











  - [My conversations](https://sieportal.siemens.com/en-ar/mysieportal/my-conversations)

  - Installed products




    - [My registered products](https://myregistration.siemens.com/?lang=en)


  - Service Requests




    - [Support requests](https://sieportal.siemens.com/en-ar/mysieportal/support-requests/management)

    - [Field Service requests](https://support.industry.siemens.com/cs/my/requests?lc=en-ar)


  - Lists & notifications




    - [My searches / filters](https://support.industry.siemens.com/cs/my/filter?lc=en-ar)

    - [My favorites](https://support.industry.siemens.com/cs/my/favorite?lc=en-ar)

    - [My notifications](https://support.industry.siemens.com/cs/my/notification?lc=en-ar)

    - [My product lists](https://support.industry.siemens.com/my/ar/en/productsV2)


  - Downloads & documentation




    - [Master data hub](https://sieportal.siemens.com/en-ar/onemd4c/datahub)


- [Cart](https://mall.industry.siemens.com/goos/Cart/Pages/Cart.aspx?currentlang=en&regionUrl=ar&SiepCountryCode=ar)

Entry type:FAQEntry ID:109974056,
Entry date:02/02/2026

Rate

# WinCC Unified: Certificate handling for HMI connections via communication processors (CPs) with S7-1200/1500

- Entry

- Associated product(s)


For HMI connections via Communication Processors (CPs), additional device certificates are required because CPs have their own security settings and are not automatically synchronized with the CPU.

[Description](https://support.industry.siemens.com/cs/#chapter_5824986_0) [Causes](https://support.industry.siemens.com/cs/#chapter_5824986_2) [Procedure](https://support.industry.siemens.com/cs/#chapter_5824986_3) [Further Information](https://support.industry.siemens.com/cs/#chapter_5824986_4)

## Description

In TIA Portal projects with S7-1200 or S7-1500 CPUs, a Communication Processor (CP) can be used alongside the integrated interface. CPs have independent security configurations and are not automatically secured by the CPU's certificates or settings. They operate autonomously with their own security settings. When adding a CP, its description does not appear in the CPU's communication certificate.

The communication certificate for the CP is not automatically generated. Therefore, secure communication between a WinCC Unified Panel or WinCC Unified Runtime and the CPU via the CP is initially unavailable. A certificate for the CP can only be created after activating project protection for the entire project.

During certificate verification at the CP, the following message may appear in the CP's security properties: "Project protection not active".

![](https://support.industry.siemens.com/cs/images/109974056/109974056_Picture_01.png)

Fig. 01

## Causes

- CPs have independent security/certificate settings and are not automatically synchronized with the CPU
- A CP device certificate is generated/managed only when Security Functions are activated at the CP. If deactivated, no certificate is created
- Secure connections are impossible without activated Security Functions and assigned CP device certificate
- Automatic certificate synchronization between CPU and CP or between multiple CPs does not occur. Partner certificates must be manually imported and assigned (See: [SIMATIC NET S7-1500 - Industrial Ethernet SIMATIC CP 1543-1](https://support.industry.siemens.com/cs/document/109986722/simatic-net-s7-1500-industrial-ethernet-simatic-cp-1543-1?lc=en-ar))


## Procedure

**1\. Activate Project Protection**

Please Note the Project protection cannot be deactivated after activation.

- Navigate to "Security settings" -> "Settings" in the project tree.
- Click "Protect project" -> "Protect project".
- Enter username and password for the project administrator.

- Confirm with "OK".


![](https://support.industry.siemens.com/cs/images/109974056/109974056_Picture_02.png)

Fig. 02

- Project protection is now active, and the administrator account has been created.

**2.** **Activate Security Functions at CP**

- Select the Communication Processor in "Device Configuration".
- Navigate to the "Security" section and activate the "Activate Security features" option.


![](https://support.industry.siemens.com/cs/images/109974056/109974056_Picture_03.png)

Fig. 03


> Security Functions are now active.

**3.** **Verify or Create Certificate**

- Click "Security" -> "Certificate Manager\] to display certificates.

A created certificate will be shown in this section.

To create a new certificate, click "Add New" in the table..


![](https://support.industry.siemens.com/cs/images/109974056/109974056_Picture_04.png)

Fig. 04

- **(Optional)** Message:

**"Adding of device certificates is deactivated. The protection of confidential data has been activated but no password has been defined."**


![](https://support.industry.siemens.com/cs/images/109974056/109974056_Picture_05.png)

Fig. 05



This message indicates that the protection of confidential configuration data has been activated, but no password has been set. Therefore, no certificates can be added because the required password for secure key generation is missing. Proceed as follows:


1. Click the message or navigate to "Protection & Security" -> "Protection of the PLC configuration data".
2. Set a password via the "Setup" button for protecting confidential PLC configuration data. The password field remains highlighted in red until a password is assigned.

> After password assignment, certificates can be added and the protection mechanism fully utilized.

**4.** **Compile and Load Project**

**Compile Project**

- Select the respective device (CPU, CP, and any Unified Panel) in TIA Portal.
- For each device:

Right-click device -> "Compile" -> "Compile All". This prepares all settings and certificates for the device.


**Load Configuration**

- Select the relevant device (e.g., PLC/CPU, Communication Processor, or HMI panel) in TIA Portal.

- Right-click device -> "Load in device..."
- Select target -> "Start loading"

This transfers the project configuration - including all relevant settings and certificates - to the selected device. Existing projects on the device are replaced, and data is updated per "Load preview" dialog settings.

> **Note** For communication issues, verify:
>
> - Time and time synchronization between CPU/CP/HMI.
> - Validity period of certificates.

## Further Information

For additional details, see:

- [Fix Certificate Error between HMI Unified Panel and SIMATIC S7-1500/S7-1200 or Software Controller](https://support.industry.siemens.com/cs/document/109800515/fix-certificate-error-between-hmi-unified-panel-and-simatic-s7-1500-s7-1200-or-software-controller?lc=en-ar)

- [Establishing a connection between WinCC Unified and CPU with firmware 2.9/4.5](https://support.industry.siemens.com/cs/document/109961590/establishing-a-connection-between-wincc-unified-and-cpu-with-firmware-2.9-4.5?lc=en-ar)

- [Configuration of an HMI connection to S7-1500/S7-1200 CPUs](https://support.industry.siemens.com/cs/document/109955142/configuring-an-hmi-connection-to-s7-1500-s7-1200-cpus?lc=en-ar)

- [Using Certificates with TIA Portal](https://support.industry.siemens.com/cs/document/109769068/using-certificates-with-tia-portal-?lc=en-ar)

Security information

In order to protect technical infrastructures, systems, machines and networks against cyber threats, it is necessary to implement – and continuously maintain – a holistic, state-of-the-art IT security concept. Siemens’ products and solutions constitute one element of such a concept. For more information about cyber security, please visit

[https://www.siemens.com/cybersecurity#Ouraspiration.](https://www.siemens.com/cybersecurity#Ouraspiration "https://www.siemens.com/cybersecurity#Ouraspiration")

Also available in the following languages:

- German
- Spanish

### Entry belongs to product tree folder(s):

- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCBasic ControllerS7-1200](https://support.industry.siemens.com/cs/products?pnid=13683)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCAdvanced ControllerS7-1500](https://support.industry.siemens.com/cs/products?pnid=13716)
- [Automation TechnologyProducts for specific requirementsAutomatision technology for extreme conditionsOperator Control and Monitoring SystemsSIPLUS Operator Control and Monitoring DevicesSIPLUS Comfort Panels](https://support.industry.siemens.com/cs/products?pnid=18188)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels Standard](https://support.industry.siemens.com/cs/products?pnid=26033)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels PRO](https://support.industry.siemens.com/cs/products?pnid=29578)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels Hygienic](https://support.industry.siemens.com/cs/products?pnid=29586)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesBasic HMIUnified Basic Panels](https://support.industry.siemens.com/cs/products?pnid=29863)
- [Automation TechnologyProducts for specific requirementsAutomatision technology for extreme conditionsOperator Control and Monitoring SystemsSIPLUS Operator Control and Monitoring DevicesSIPLUS HMI Unified Basic Panels](https://support.industry.siemens.com/cs/products?pnid=30437)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC Unified (TIA Portal)SIMATIC WinCC Unified EngineeringWinCC Unified ES (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=30762)

Rate entry

Submit rating

Feedback for the article with the entry ID 109974056

Personalization saves you typing! If you have already been registered, you can login directly. If you are not registered yet, you can inform and register yourself without obligation here (our service is free of charge).

- Log in
- Register

Name

Phone number

Email

DetailPages.Subject

Comment

Number of characters: 0 (max. 500)

Send copy of comment to sender. (login required)

Cancel

Send

Note: The feedback always relates to the current entry. Your message will be forwarded to our technical editors working in the Online Support. In a few days, you will receive a response if your feedback requires one.

Rate entry

Submit rating

Requests and feedback

What do you want to do?

- You want to discuss in our forum and exchange experiences with other users
[Go to the Forum](https://support.industry.siemens.com/forum/ar/en)

- You have a technical question / problem: Ask the Technical Support
Create support request

Note: Depending on the effort involved, support may be subject to a charge. In this case, please have a [Siemens Industry Service Card](https://mall.industry.siemens.com/mall/en/WW/Catalog/Products/10360997?activeTab=productinformation) ready.

- You want to create CAx data for one or more products
Go to the CAx download manager

- You would like to send us feedback on this Entry
Provide feedback

Note: The feedback always relates to the current entry. Your message will be forwarded to our technical editors working in the Online Support. In a few days, you will receive a response if your feedback requires one.


- PDF


Share this Page:

Support to the statistics

With this function the IDs found are listed according to number (format .txt).

Generate list

Copy URL

Copy URL

Display page in new design

mySupport Cockpit

* * *

- Add to mySupport favorites
- [Favorites](https://support.industry.siemens.com/cs/my/favorite?lc=en-ar)
- [My requests](https://support.industry.siemens.com/cs/my/srm?lc=en-ar)
- [CAx downloads](https://support.industry.siemens.com/cs/my/cax?lc=en-ar)
- [My Products / Clipboard](https://support.industry.siemens.com/my/ar/en/products#myProductNode|nodeId|gCB3EA198-337A-4514-8F7A-5B346DC4E5D0)

Related links

- [Compatibility Tool](https://support.industry.siemens.com/cs/ww/en/view/64847781)
- [Register your SINAMICS Drive](https://www.automation.siemens.com/LDReg/?HTTPS=REDIR)
- [Visit our Converting Community](https://support.industry.siemens.com/tf/ww/en/threads/295)
- [Information on the packaging](https://support.industry.siemens.com/cs/ww/en/view/109813875)

Topic pages for this entry

- [WinCC Unified – Overview of the Most Important Documents and Links](https://support.industry.siemens.com/cs/document/109777887/wincc-unified-%E2%80%93-overview-of-the-most-important-documents-and-links?lc=en-AR)
- [TIA Portal - Overview of the Most Important Documents and Links - Communication](https://support.industry.siemens.com/cs/document/90940081/tia-portal-overview-of-the-most-important-documents-and-links-communication?lc=en-AR)