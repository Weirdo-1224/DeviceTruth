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

Entry type:FAQEntry ID:109800515,
Entry date:03/09/2026

Rate

# Fix Certificate Error between HMI Unified Panel and SIMATIC S7-1500/S7-1200 or Software Controller

- Entry

- Associated product(s)


When establishing a connection between an HMI Unified Basic Panel or HMI Unified Comfort Panel (from image version V17) with an S7-1500 (FW V2.9), S7-1200 (FW V4.5) or Software Controller (FW V21.9), a general Certificate error occurs after project transfer from the TIA Portal.

[Description](https://support.industry.siemens.com/cs/#chapter_5622063_0) [Causes](https://support.industry.siemens.com/cs/#chapter_5622063_1) [Procedure](https://support.industry.siemens.com/cs/#chapter_5622063_2) [1\. Configuration](https://support.industry.siemens.com/cs/#chapter_5622063_3) [2\. Check/Create Certificates](https://support.industry.siemens.com/cs/#chapter_5622063_5) [3\. Unified Comfort Panel](https://support.industry.siemens.com/cs/#chapter_5622063_6)

## Description

The system message "General certificate error" is displayed in the message display with the following message text:

> "General certificate error for PLC - \[IP address of the controller\].Details CN=PLC-1/Communication-1 error = 9"
>
> ![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_001_en.png)Fig. 01

## Causes

This behavior occurs when:

- The time on the Unified Comfort Panel is not within the Validity Period of the CPU Certificate.
- CPU (S7-15xx/S7-12xx FW 2.9/4.5, Software Controller FW 21.9): Invalid or misconfigured CPU Certificates.

> **Note** The behavior occurs regardless of the connection type. From image version V17 of the HMI Unified Panel, a Certificate exchange and a validity check always take place, regardless of whether "secure connection only" is activated in the CPU or not.

**Functionality of the Certificate Exchange**

With Firmware Versions S7-1500 (from V2.9), S7-1200 (from V4.5) and Software Controller (from V21.9), S7 communication is protected by a Certificate-based mechanism. A Connection is only established if the Unified Panel explicitly trusts the Certificate of the Controller. There are two Scenarios for this:

> 1\. Automatic Trust Building (Integrated Projects)

> If the Controller and HMI are in the same Project, the Certificate is automatically trusted when the HMI is loaded. If the Certificate is changed, the HMI Project must be reloaded.
>
> 2\. Manual Trust Building (Non-Integrated Projects / ChangeConnection)
>
> If the Controller is not in the same Project or the connection is changed at Runtime, its Certificate must be manually imported onto the HMI and classified as trustworthy.

## Procedure

Below you will find the procedure for rectifying the general Certificate error in the connection between Unified Comfort Panels and S7-1500/S7-1200 CPUs or Software Controllers. Proceed as follows.

## 1\. Configuration

Check the correct configuration of the HMI communication in the TIA Portal. Follow the steps in the manual (SIMATIC ET 200 SP) in the section: [Using secure HMI communication](https://docs.tia.siemens.cloud/r/simatic_et_200sp_manual_collection_enus_21/general-information/communication-function-manuals/communication/communications-services/secure-communication/secure-pg/hmi-communication/using-secure-hmi-communication).

## 2\. Check/Create Certificates

**2.1 Check Validity**

> The procedure differs depending on the firmware version of the CPU. Select the CPU in the TIA Portal and proceed as follows:

> > From FW >=V4.0
> >
> > - Click on "Properties" -> "Protection & Security" -> "Certificate Manager" -> "Certificate management with TIA Portal".

> > FW V2.9
> >
> > - Click on "Properties" -> "Protection & Security" -> "Certificate Manager".

> In the table, the date of Validity of the Certificate is listed in the "Valid until" column.
>
> ![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_002_en.png)
>
> Fig. 02
>
> If the device Certificate has expired (Valid Until date is in the past) or is Invalid, do the following:
>
> - Delete Certificate: Right-click on the Certificate -> "Delete Certificate".
> - Recreate Certificate: See how to do this in Section 2.2.

**2.2 Create Certificate**

- On the right, under "Device Certificates" in the table below, click <"Add new>". After that, press the "ellipsis button" to open the context menu.
- Then click on the "Create" button.


![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_003_en.png)

Fig. 03

- In the Create Certificate Dialog Box, Create the Certificate and select the appropriate purpose from the Drop-Down menu.


![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_004_en.png)

Fig. 04

- Once a Certificate has been created in the Certificate manager, you must then assign it to the respective application in the specific properties.

For the OPC UA Certificate, for example, this is done in the CPU under "Properties" -> "OPC UA" -> "Server" -> "Security" -> "Certificates" -> "Select Certificate separately".

> Alternatively, Certificates can be created directly in the TIA Portal within the designated categories. This method offers the advantage that the Certificate is automatically assigned to the correct application and parameterized with the appropriate default settings. Certificates are created separately for each Certificate type:

> - OPC UA Certificate: "Properties" -> "OPC UA" -> "Server" -> "Security" -> "Certificates" -> "Create Server Certificates".
> - Web server Certificate: "Properties" -> "Web server" -> "Security" -> "Create Server Certificate".
> - PG/HMI communication Certificate: Configure in the CPU Settings under "Protection & Security" -> "Connection mechanisms" -> "Communication Mode to TIA Portal and HMI" and create the corresponding Certificate.

> **Note**
>
> It is possible to specify several controller IP addresses in the Certificate. This offers the advantage that no new Certificate needs to be created when replacing different system or spare parts.

**2.3** **Export Certificate**

> This manual step is only necessary in the following two scenarios:
>
> - When using the "ChangeConnection" system function (runtime change of the controller)
> - When connecting to an external controller (e.g. from another TIA Portal project).

> To avoid manually distributing individual Device Certificates, export the Certificate from the parent Certificate authority (CA). If the HMI trusts this CA, this automatically applies to all Certificates issued by it. To do this, follow these steps:

> 1\. Open Certificate Manager
>
> In the TIA Portal, go to the properties of the CPU and open the Certificate manager. The procedure according to the firmware is as follows:

> > From FW >=V4.0
>
> - Click on "Properties" -> "Protection & Security" -> "Certificate Manager" -> "Certificate management with TIA Portal".
>
> > FW V2.9
>
> - Click on "Properties" -> "Protection & Security" -> "Certificate Manager".

> 2\. Select and export CA Certificate
>
> Right-click on the selected CA Certificate and select Export from the context menu
>
> ![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_005_en.png)
>
> Fig. 05

- In the dialog box, select the export format (default is .cer or .crt).
- Set a location that you can access later from the HMI (e.g. a USB stick).
- Confirm the process and save the file.

> The exported CA Certificate can now be imported to the HMI device to establish the trust.

## 3\. Unified Comfort Panel

> **3.1** **Setting the time**
>
> To ensure that the time of the Unified Comfort Panel is within the validity period of the CPU certificate, the following options are available for adjusting the time on the Unified Comfort Panel.
>
> **Set manually**

- Open the settings on the Unified Comfort Panel.
- Click on "Language, Region and Formats" -> "Date and time".
- Activate the option "Set date and time manually". This option is active by default.


![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_006_en.png)

Fig. 06


> **Activating Automatic Time Synchronization**

- Open the Settings on the Unified Comfort Panel.
- Click on "Language, Region and Formats" -> "Date and time".
- Activate the Option "Synchronize time with a NTP (Network Time Protocol) Server".


![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_006_en.png)

Fig. 06


> > For more information on setting the date and time correctly, please refer to the corresponding manual:
> >
> > [HMI Unified Basic Panels](https://docs.tia.siemens.cloud/r/unified_basic_panels_enus_21/operating-the-control-panel/language-region-and-formats/date-and-time)
> >
> > [HMI Unified Comfort Panels](https://docs.tia.siemens.cloud/r/unified_comfort_panels_enus_21/operating-the-control-panel/language-region-and-formats/date-and-time)

> **3.2 Import Certificate and classify as trustworthy**

> Connect the external storage medium (e.g. a USB stick) on which the exported Certificate file is saved.

> > 1\. Open Certificate Management:
> >
> > - On the Unified Panel, navigate to the following Path via the Control Panel: "Security" -> "Certificates".

> > 2\.  Start the import process
> >
> > - Select the Import button and navigate to the Certificate file on the connected storage medium. Confirm the selection to carry out the import.

> > 3\. Trustworthiness of the Certificate
> >
> > After successful import, the Certificate must be explicitly trusted

- Select the newly imported Certificate. Depending on whether you have Imported a CA Certificate or a single Device Certificate, you will find it in one of the following Tabs:

- "Certificate Authorities" or
- "Other Certificates"


![](https://support.industry.siemens.com/cs/images/109800515/109800515_Picture_008_en.png)

Fig. 08


- Select the desired Certificate from the list.

Then activate the "Trust" option to establish the trust position. Further information can be found in the corresponding manual:

[Unified Basic Panels - Certificates](https://docs.tia.siemens.cloud/r/unified_basic_panels_enus_21/operating-the-control-panel/security/certificates)

[Unified Comfort Panels - Certificates](https://docs.tia.siemens.cloud/r/unified_comfort_panels_enus_21/operating-the-control-panel/security/certificates)

**FAQ changes since the last release**

- FAQ updated to V21

Security information

In order to protect technical infrastructures, systems, machines and networks against cyber threats, it is necessary to implement – and continuously maintain – a holistic, state-of-the-art IT security concept. Siemens’ products and solutions constitute one element of such a concept. For more information about cyber security, please visit

[https://www.siemens.com/cybersecurity#Ouraspiration.](https://www.siemens.com/cybersecurity#Ouraspiration "https://www.siemens.com/cybersecurity#Ouraspiration")

Also available in the following languages:

- German
- Spanish
- Chinese (simplified)
- French

### Entry belongs to product tree folder(s):

- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCBasic Controller](https://support.industry.siemens.com/cs/products?pnid=13639)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCAdvanced Controller](https://support.industry.siemens.com/cs/products?pnid=13715)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCSoftware Controller](https://support.industry.siemens.com/cs/products?pnid=13911)
- [Automation TechnologyProducts for specific requirementsAutomatision technology for extreme conditionsOperator Control and Monitoring SystemsSIPLUS Operator Control and Monitoring DevicesSIPLUS Comfort Panels](https://support.industry.siemens.com/cs/products?pnid=18188)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels Standard](https://support.industry.siemens.com/cs/products?pnid=26033)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels PRO](https://support.industry.siemens.com/cs/products?pnid=29578)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels Hygienic](https://support.industry.siemens.com/cs/products?pnid=29586)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesBasic HMIUnified Basic Panels](https://support.industry.siemens.com/cs/products?pnid=29863)
- [Automation TechnologyProducts for specific requirementsAutomatision technology for extreme conditionsOperator Control and Monitoring SystemsSIPLUS Operator Control and Monitoring DevicesSIPLUS HMI Unified Basic Panels](https://support.industry.siemens.com/cs/products?pnid=30437)

Rate entry

Submit rating

Feedback for the article with the entry ID 109800515

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