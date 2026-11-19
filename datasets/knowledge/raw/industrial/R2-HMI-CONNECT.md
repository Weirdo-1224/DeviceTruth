×

Siemens Industry Online Support

Siemens AG

VIEW

- [Product Support](https://support.industry.siemens.com/cs/products?lc=en-pa)
- [Forum](https://support.industry.siemens.com/forum/pa/en/)
- [mySupport](https://support.industry.siemens.com/cs/my?lc=en-pa)

Entry type:FAQEntry ID:109955142,
Entry date:09/28/2026

Rate

# Configuring an HMI connection to S7-1500/S7-1200 CPUs

- Entry

- Associated product(s)


You want to configure an HMI connection to an S7-1500/S7-1200 CPU, but the connection cannot be established due to insufficient access rights.

[Description](https://support.industry.siemens.com/cs/#chapter_5779445_0) [Cause](https://support.industry.siemens.com/cs/#chapter_5779445_1) [Procedure](https://support.industry.siemens.com/cs/#chapter_5779445_2) [1\. Access Control with Access Levels (Legacy)](https://support.industry.siemens.com/cs/#chapter_5779445_3) [2\. Disabled Access Control](https://support.industry.siemens.com/cs/#chapter_5779445_4) [3\. Access control with User Access (Anonymous)](https://support.industry.siemens.com/cs/#chapter_5779445_5)

## Description

You are using one of the following SIMATIC products:

- SIMATIC HMI Unified Panel (Basic/Comfort)
- HMI Panel (Basic/Comfort/Mobile)
- WinCC Runtime (Unified, Advanced, Professional)
- WinCC V7/V8

and would like to establish a connection to one of the following SIMATIC controllers:

- S7-1500 CPU FW V3.1/V4.0
- S7-1200 from FW V4.7
- S7-1200 G2 FW V1.0
- Software Controller as of FW V30.1

The connection is not established.

## Cause

To establish a connection, the HMI connection must have sufficient access rights to the CPU. Depending on the protection concept used, these rights are provided either by the password for an access level or by a user with assigned roles and function rights.

## Procedure

The following sections describe various options for configuring an HMI connection. Section 1 shows an example configuration with an S7-1500 CPU, firmware version V3.1/V4.0. The procedures described apply accordingly to the controllers mentioned above. Take into account the product-specific differences associated with the CPU, CPU firmware, TIA Portal version, and WinCC variant used.

Information on compatibility and security can be found in the following manual: [Using Legacy-PG/PC communication for TIA Portal - SIMATIC ET 200AL Manual Collection](https://docs.tia.siemens.cloud/r/simatic_et_200al_manual_collection_enus_20/function-manuals/communication/communication/communications-services/secure-communication/secure-pg/hmi-communication/using-legacy-pg/pc-communication-for-tia-portal).

## 1\. Access Control with Access Levels (Legacy)

The example configuration described below uses access control with access levels. For this purpose, access control and the “Use access control via access levels” option are enabled. In TIA Portal V19, this configuration is referred to as “Legacy access control.”

In the example shown, a password is assigned to the “Full access” access level. This password is then entered as the CPU password in the configured HMI connection. In addition, the “Only allow secure PG/PC and HMI communication” option is enabled.

**Notes**

- The “Full access” access level is used in the example configuration shown. It is not required for every HMI function. For each application, use only the permissions that are actually required.
- The “HMI access” access level of legacy access control and the function right of the same name, “HMI access,” in user and role management are assigned using different configuration mechanisms. The two terms must therefore be distinguished from one another. In the following, it will be indicated in each case whether the “HMI access” access level or function right is meant.
- For non-integrated projects, ensure that older project files and settings are compatible with one another. Further information on migration to STEP 7 (TIA Portal) and WinCC can be found in the following article: [Which entries are available in the internet concerning migration to STEP 7 (TIA Portal) and WinCC (TIA Portal)?](https://support.industry.siemens.com/cs/document/56314851/which-entries-are-available-in-the-internet-concerning-migration-to-step-7-(tia-portal)-and-wincc-(tia-portal)-?lc=en-pa)

> **1.** **1 Enabling access control and access control via access levels**

> - Select the appropriate CPU.
> - Click the “General” tab → “Protection & Security” → “Access Control.”
> - Enable the “Enable access control” option, followed by the “Use access control via access levels” option. In TIA Portal V19, this configuration is referred to as “Legacy access control.”
> - In the “Full access” row and the “Access permission” column, assign a password for the “Full access” access level. The presentation of the options may vary slightly depending on the model of the S7-1200 or S7-1500 CPU.
>
>
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_001.png)
>
>   Fig. 01
>
>
> **Notes**
>
> - When using the central User Management (UMC), sections such as “User Management Configuration” and “Connection to User Management Server” appear, but they do not need to be modified for this procedure.
> - Even when access levels are enabled, the created users remain available and can still be used to access the PLC.

> **1.2 Enabling the “Only allow secure PG/PC and HMI communication” option**

> - Select the CPU → click “Properties” → open the “General” tab → “Connection mechanisms”.
> - Enable the “Only allow secure PG/PC and HMI communication” option and confirm your selection by clicking “OK.”
>
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_002.png)
>
>   Fig. 02

> **1.3** **Creating an HMI connection**

> - Select Devices & networks in the Project tree.
> - Click the "Network View" tab.
>
>   If you are using a WinCC Unified RT station, you may need to configure a communication module for the connection. To do this, select “PC Systems” → “Communication modules” in the hardware catalog and add the required communication module.
> - Click “Connections” and create an HMI connection between the HMI device and the CPU.
>
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_003.png)
>
>   Fig. 03
>
> - Open “HMI Connections” (Connections) in the “Project Tree”.
> - Click on the connection and enter the CPU password. For the example configuration shown, the CPU password entered must match the password assigned in step 1.1 to the “Full access” access level.
>
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_004.png)
>
>   Fig. 04
>   The configuration can now be loaded to the CPU.

## 2\. Disabled Access Control

> When access control is disabled, the HMI connection is established without checking a CPU password or CPU user. The “Only allow secure PG/PC and HMI communication” option concerns the secure communication mechanism independently of this. When access control is disabled, comprehensive access to the CPU functions is available, with the exception of the GDS push function for the online transfer of certificates. Disabling access control increases the risk of unauthorized access.
>
> Further information on access rights can be found in the manual [Local user management for PLCs configured with firmware version = V3.1 (RT Unified) - Visualizing Processes with View of Things](https://docs.tia.siemens.cloud/r/en-us/v20/visualizing-processes-with-view-of-things-rt-unified/configuring-wincc-unified-vot-for-plc-rt-unified/configuring-view-of-things-rt-unified/configuring-user-management-rt-unified/local-user-management-for-plcs-configured-with-firmware-version-v3.1-rt-unified).

> **2.1** **Checking and resetting the CPU password for the “Full access” access level**
>
> For HMI connections to S7-1200 and S7-1500 CPUs, the CPU checks whether the connection is authorized. In the configuration shown here, the CPU password entered for the HMI connection must match the password for the “Full access” access level. Otherwise, the CPU may reject the connection attempt.
>
> **Note**
>
> If the HMI connection is to be established without entering a CPU password, different configuration options are available depending on the protection concept used:
>
> - Activate the local user “Anonymous” and assign only the required function rights to this user via a role. This procedure is described in section 3.
> - Reset the password for the “Full access” access level.
> - Disable access control. This procedure is described in section 2.2 and reduces protection against unauthorized access.
>
> Using the user “Anonymous” does not disable access control. The effective permissions are determined by the roles and function rights assigned to the user “Anonymous.”
>
>  **Checking the password for the “Full access” access level**
>
> 1. Select the corresponding CPU.
> 2. Click the “General” tab → “Protection & Security” → “Access Control.”
> 3. In the “Access levels” section, check whether a password has been set in the “Full access (No protection)” row and the “Access permission” column.
>
>
>    ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_005.png)
>
>    Fig. 05

- If a password is displayed and the HMI connection is to be established without a CPU password, follow the steps in the “Resetting the password” section. If no password has been assigned, check the other settings of the configured HMI connection and the protection concept used. Depending on the application, follow the procedure described in section 2.2 or section 3.


> **Resetting the password**
>
> Proceed as follows:
>
> 1. In the Project tree, select “Security settings” → “Users and roles.”
> 2. Activate the “Anonymous” user by selecting the checkbox next to the user.
> 3. Open the “Assigned roles” tab and, depending on the PLC being used, select either the “PLC Administrator” or “PLC F Administrator” role.

> > **Note** In TIA Portal V20, the corresponding role is available by default. In TIA Portal V18 and V19, it may need to be created. If the required role is not available, follow these steps:
> >
> > - Click the “Roles” tab and then “<Add new role>.” Give the role a meaningful name, such as “PLCAdmin.”
> > - Click the “Runtime rights” tab. Under “Function rights categories,” select the desired CPU.
> > - Select the “Full access” or “Full access including fail-safe access” function right. Then assign the created role to the “Anonymous” user.

> 4.Assign the role, for example “PLC F Administrator” on an F-PLC, by selecting the corresponding checkbox.
>
> 5\. Then deactivate the role again by clearing the checkbox.

> **Note**
>
> After resetting, check whether the “Anonymous” user is still enabled and whether an administrator role or the CPU function right “Full access” is still assigned to it. Disable the user or remove any rights that are no longer required if they are not necessary for the intended configuration.
>
> - Result: The “Full access” access-level password is removed from the CPU configuration.
>
>
> Then check the configured HMI connection. A CPU password entered there may need to be removed separately. Finally, load the modified configuration into the CPU.

> **2.2** **Disabling access control**

> - Right-click the CPU and select “Properties.”
> - Click the "General" tab → "Protection & Security" → "Access Control".
>
> - Enable the option "Disable access control".
>
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_006.png)
>
>   Fig. 06

- The configuration can now be loaded to the CPU.

## 3\. Access control with User Access (Anonymous)

> Starting with TIA Portal Version V17, a local user named “Anonymous” is created in a project by default. This user can log in without a password. For security reasons, this anonymous user is disabled by default and must be enabled manually when required. You can assign function rights to the anonymous user through roles and disable the user again at any time.

> > **Note**
> >
> > When enabled, the “Anonymous” user does not require a password. The user’s effective CPU permissions are determined by the assigned roles and CPU function rights. Enable the “Anonymous” user only if a connection without individual user authentication is required. Disable the user again and remove any roles or function rights that are no longer needed if this configuration is not required permanently.
> >
> > Further information can be found in the manual [TIA Portal V20 - Activating or deactivating anonymous user - Managing users and roles](https://docs.tia.siemens.cloud/r/en-us/v20/managing-users-and-roles/activating-or-deactivating-anonymous-user).

> **3.1 Activating Access Control**
>
> - Select the appropriate CPU.
> - Click the “General” tab → “Protection & Security” → “Access Control.”
> - Enable the “Enable access control” option, followed by the “Use access control via access levels” option. In TIA Portal V19, this configuration is referred to as “Legacy access control.”
>
>
>
>   **Note**
>
>
>   This approach uses legacy access control together with the user “Anonymous.” When the “Use access control via access levels” option is enabled, the access level applicable to unauthenticated access is defined through the roles and CPU function rights assigned to the “Anonymous” user.
>
>   CPU function rights are not assigned directly to the “Anonymous” user, but through a role. Assign the user only the CPU function rights required for the respective application.
>
>   If the “Anonymous” user is disabled or has not been assigned the relevant CPU function right, access without individual authentication is not possible.
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_007.png)
>
>   Fig. 07

> **3.2 Activating the "Anonymous" user**

- In the Project tree, select “Security settings” → “Users and roles.”
- Activate the “Anonymous” user by checking the box next to the username.
- You can assign the activated user “Anonymous” a role with the required function rights. This grants the user “Anonymous” the access rights defined by that role, without requiring an individual login with a username and password.


![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_008.png)

Fig. 08


> **3.3** **Creating a Role and Assigning the “HMI access” Function Right**

> - Click the “Roles” tab and then “<Add new role>” to add a new role. Give the role a meaningful name, such as “CommunicationAccess”.
> - Click the “Runtime Rights” tab. Under “Function Rights Categories,” select the desired CPU.
> - On the right-hand side, select the function right “HMI access” and assign it to the role.
>
>
>   ![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_009.png)
>
>   Fig. 09

> > **Notes**
> >
> > - For reading and writing configured process values via the HMI connection, the CPU function right “HMI access” is generally sufficient. The CPU function right “Full access” is not generally required for this purpose.
> > - If the user “Anonymous” is assigned the CPU function right “HMI access” through a role, the HMI can read and write the process values enabled for HMI communication. This should not be equated with general read or write access to the CPU’s hardware configuration or program blocks.
> > - The CPU function right “Read access” additionally includes the CPU function right “HMI access.” Therefore, the designation “Read access” does not mean that the HMI can only read, and not write, the process values enabled via the HMI connection.
> > - Additional CPU function rights may be required for supplementary diagnostic functions, such as ProDiag or specific system diagnostic functions, depending on the CPU, CPU firmware, and WinCC variant used.
> >
> > - Further information on function rights can be found in the manual [SIMATIC STEP 7 S7-1500/ET 200MP Manual](https://support.industry.siemens.com/cs/document/86140384/simatic-s7-1500-et200mp-manual-collection-?lc=en-pa) (Chapter 11.3.3). You can find the HTML version in the [Documentation Portal](https://docs.tia.siemens.cloud/p/automation-with-simatic-s7-1500-et200).

> **3.4** **Assigning a role to the “Anonymous” user**

- Click the “Users” tab.
- Select the user “Anonymous” and open the “Assigned roles” tab.
- Select the previously created role “CommunicationAccess” to assign it to the user “Anonymous.”


![](https://support.industry.siemens.com/cs/images/109955142/109955142_Picture_010.png)

Fig. 10

- The user “Anonymous” has now been assigned the role “CommunicationAccess” with the CPU function right “HMI access.”
- The configuration can now be loaded to the CPU.

**FAQ changes since last release**

- The information on HMI connections and access rights has been revised and clarified.

**Keywords**

PLC, Legacy Mode

Security information

In order to protect technical infrastructures, systems, machines and networks against cyber threats, it is necessary to implement – and continuously maintain – a holistic, state-of-the-art IT security concept. Siemens’ products and solutions constitute one element of such a concept. For more information about cyber security, please visit

[https://www.siemens.com/cybersecurity#Ouraspiration.](https://www.siemens.com/cybersecurity#Ouraspiration "https://www.siemens.com/cybersecurity#Ouraspiration")

Also available in the following languages:

- German
- Spanish
- Chinese (simplified)

### Entry belongs to product tree folder(s):

- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCAdvanced ControllerS7-1500CPUs](https://support.industry.siemens.com/cs/products?pnid=13717)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCSoftware Controller](https://support.industry.siemens.com/cs/products?pnid=13911)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC (TIA Portal)SIMATIC WinCC (TIA Portal) Engineering](https://support.industry.siemens.com/cs/products?pnid=14678)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesBasic HMIBasic Panels](https://support.industry.siemens.com/cs/products?pnid=14737)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedComfort Panels](https://support.industry.siemens.com/cs/products?pnid=14742)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedMobile Panels](https://support.industry.siemens.com/cs/products?pnid=14745)
- [Automation TechnologyOperator control and monitoring systemsHMI SoftwareSIMATIC WinCC V7/V8](https://support.industry.siemens.com/cs/products?pnid=14866)
- [Automation TechnologyOperator control and monitoring systemsHMI SoftwareSIMATIC WinCC V7/V8SIMATIC WinCC V7/V8 Basic-SW](https://support.industry.siemens.com/cs/products?pnid=14867)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC (TIA Portal)SIMATIC WinCC (TIA Portal) Runtime](https://support.industry.siemens.com/cs/products?pnid=24209)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels Standard](https://support.industry.siemens.com/cs/products?pnid=26033)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC Unified (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=26034)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC Unified (TIA Portal)SIMATIC WinCC Unified Runtime](https://support.industry.siemens.com/cs/products?pnid=26036)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=26039)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC (TIA Portal)SIMATIC WinCC (TIA Portal) RuntimeWinCC Runtime Advanced](https://support.industry.siemens.com/cs/products?pnid=27408)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalVisualizationSIMATIC WinCC (TIA Portal)SIMATIC WinCC (TIA Portal) RuntimeWinCC Runtime Professional](https://support.industry.siemens.com/cs/products?pnid=27409)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesAdvanced HMI Panel-basedSIMATIC HMI Unified Comfort PanelsSIMATIC HMI Unified Comfort Panels PRO](https://support.industry.siemens.com/cs/products?pnid=29578)
- [Automation TechnologyOperator control and monitoring systemsOperator devicesBasic HMIUnified Basic Panels](https://support.industry.siemens.com/cs/products?pnid=29863)

Rate entry

Submit rating

Feedback for the article with the entry ID 109955142

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
[Go to the Forum](https://support.industry.siemens.com/forum/pa/en)

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
- [Favorites](https://support.industry.siemens.com/cs/my/favorite?lc=en-pa)
- [My requests](https://support.industry.siemens.com/cs/my/srm?lc=en-pa)
- [CAx downloads](https://support.industry.siemens.com/cs/my/cax?lc=en-pa)
- [My Products / Clipboard](https://support.industry.siemens.com/my/pa/en/products#myProductNode|nodeId|gCB3EA198-337A-4514-8F7A-5B346DC4E5D0)

Related links

- [Compatibility Tool](https://support.industry.siemens.com/cs/ww/en/view/64847781)
- [Register your SINAMICS Drive](https://www.automation.siemens.com/LDReg/?HTTPS=REDIR)
- [Visit our Converting Community](https://support.industry.siemens.com/tf/ww/en/threads/295)
- [Information on the packaging](https://support.industry.siemens.com/cs/ww/en/view/109813875)

Topic pages for this entry

- [WinCC Unified – Overview of the Most Important Documents and Links](https://support.industry.siemens.com/cs/document/109777887/wincc-unified-%E2%80%93-overview-of-the-most-important-documents-and-links?lc=en-PA)
- [TIA Portal - Overview of the Most Important Documents and Links - Communication](https://support.industry.siemens.com/cs/document/90940081/tia-portal-overview-of-the-most-important-documents-and-links-communication?lc=en-PA)