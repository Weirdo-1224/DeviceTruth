Due to routine maintenance activities SiePortal may experience brief interruptions from Monday, November 16 07:00 AM CEST to Tuesday, November 17 01:00 PM CEST.

×

Siemens Industry Online Support

Siemens AG

VIEW

SiePortal

- Products & Services











  - [Drive technology](https://mall.industry.siemens.com/mall/en/ww/Catalog/Products/9990173?tree=CatalogTree&SiepCountryCode=WW)

  - [Automation technology](https://mall.industry.siemens.com/mall/en/ww/Catalog/Products/10045207?tree=CatalogTree&SiepCountryCode=WW)

  - [Energy](https://mall.industry.siemens.com/mall/en/ww/Catalog/Products/10313567?tree=CatalogTree&SiepCountryCode=WW)

  - [Building Technologies](https://mall.industry.siemens.com/mall/en/ww/Catalog/Products/10047631?tree=CatalogTree&SiepCountryCode=WW)

  - [Low-Voltage controls and distribution](https://mall.industry.siemens.com/mall/en/ww/Catalog/Products/9990301?tree=CatalogTree&SiepCountryCode=WW)

  - [Services](https://mall.industry.siemens.com/mall/en/ww/Catalog/Products/10008397?tree=CatalogTree&SiepCountryCode=WW)

  - [List of configurators](https://mall.industry.siemens.com/mall/en/ww/Catalog/Configurators?SiepCountryCode=WW)


- [Support](https://sieportal.siemens.com/en-ww/support)











  - [Knowledge base](https://sieportal.siemens.com/en-ww/search?scope=knowledgebase&CountryCode=ww&Language=en&SearchTerm=&EntryTypes=all&Page=0&PageSize=20)

  - [Forum](https://sieportal.siemens.com/en-ww/support/forum)

  - [Topic pages (important topics at a glance)](https://support.industry.siemens.com/cs/sc/3058/important-topics-at-a-glance?lc=en-ww)

  - [Create master data download](https://sieportal.siemens.com/en-ww/onemd4c/wizard/1)

  - [Create support request](https://sieportal.siemens.com/en-ww/mysieportal/support-requests/create)

  - [Contact](trigger:contactMenu)


- [mySiePortal](https://sieportal.siemens.com/en-ww/mysieportal)











  - Installed products




    - [My registered products](https://myregistration.siemens.com/?lang=en)


  - Service request




    - [Support requests](https://sieportal.siemens.com/en-ww/mysieportal/support-requests/management)

    - [Field Service Request](https://support.industry.siemens.com/cs/my/requests?lc=en-ww)


  - Lists & notifications




    - [My searches / filters](https://support.industry.siemens.com/cs/my/filter?lc=en-ww)

    - [My favorites](https://support.industry.siemens.com/cs/my/favorite?lc=en-ww)

    - [My notifications](https://support.industry.siemens.com/cs/my/notification?lc=en-ww)

    - [My product lists](https://support.industry.siemens.com/my/ww/en/productsV2)


  - Product data & documentation




    - [Master data hub](https://sieportal.siemens.com/en-ww/onemd4c/datahub)


- [Cart](https://mall.industry.siemens.com/goos/Cart/Pages/Cart.aspx?currentlang=en&regionUrl=ww&SiepCountryCode=ww)

Entry type:FAQEntry ID:85239632,
Entry date:01/19/2024

Rate

# With the S7-1200, why do you get the message indicating that the signal board is not available because of an incorrect type?

- Entry

- Associated product(s)


You get this message because there is a difference between the version of the signal board integrated in the CPU and the version of the signal module configured in the device configuration.

**Description**

There are currently two versions of the S7-1200 analog input board, V1.0 and V2.0. If you configure the signal board version V2.0 in the device configuration, but have slotted the same type but version V1.0 in the CPU, you get this message in the diagnostics buffer: "Hardware component not available because of incorrect type". The signal boards with the following order numbers are concerned.

- SB1231 AI1 x 12Bit 6ES7231-4HA30-0XB0

- SB1231 AI1 x RTD 6ES7231-5PA30-0XB0

- SB1231 AI1 x TC 6ES7231-5QA30-0XB0

**Remedy**

Ensure that the signal board in the device configuration and the signal board slotted in the CPU of the plant are of the same firmware version. Either you use the signal board with firmware version V2.0 for your CPU in the plant or you change the version of the signal board in the device configuration from V2.0 to V1.0.

In this
video we will show you how to change the version of the signal board in the
device configuration of the S7-1200 CPU from V2.0 to V1.0 in TIA Portal.

![](https://cf-images.eu-west-1.prod.boltdns.net/v1/static/1813624294001/a20f04e4-56dc-4d7c-a69c-c872755d4c1c/958dfa6a-5aed-4c10-9bf8-32f1d1949ac2/1280x720/match/image.jpg)

Video Player is loading.

Play Video

PlaySkip BackwardSkip Forward

Mute

Current Time 0:00

/

Duration 0:00

Loaded: 0%

0:00

Stream Type LIVE

Seek to live, currently behind liveLIVE

Remaining Time -0:00

1x

Playback Rate

Chapters

- Chapters

AD Audio Descriptions

Captions

- captions settings, opens captions settings dialog
- captions off, selected

Audio Track

Fullscreen

This is a modal window.

## The video connection was lost, please confirm you are connected to the internet

**Error Code:** MEDIA\_ERR\_NETWORK

Technical details
:

DASH request error at URL: http://manifest.prod.boltdns.net/manifest/v1/dash/live-baseurl/clear/1813624294001/a20f04e4-56dc-4d7c-a69c-c872755d4c1c/6s/manifest.mpd?fastly\_token=NmFmYzY3NzRfZGU2ODMwMTU1ZTVkNzk0YWQwNmY0ZmI1MWY4MjFmOWFkYzU1YzhhODg0NWQwOGEwNzYxOTRiYzg4YWUyNDc1Yg%3D%3D

**Session ID:** 2026-11-17:df968ebda8a7321727db5384

**Player Element ID:** vjs\_video\_3

OK

Close Modal Dialog

Beginning of dialog window. Escape will cancel and close the window.

TextColorWhiteBlackRedGreenBlueYellowMagentaCyanOpacityOpaqueSemi-TransparentText BackgroundColorBlackWhiteRedGreenBlueYellowMagentaCyanOpacityOpaqueSemi-TransparentTransparentCaption Area BackgroundColorBlackWhiteRedGreenBlueYellowMagentaCyanOpacityTransparentSemi-TransparentOpaque

Font Size50%75%100%125%150%175%200%300%400%Text Edge StyleNoneRaisedDepressedUniformDrop shadowFont FamilyProportional Sans-SerifMonospace Sans-SerifProportional SerifMonospace SerifCasualScriptSmall Caps

ResetDone

Close Modal Dialog

End of dialog window.

Close Modal Dialog

This is a modal window. This modal can be closed by pressing the Escape key or activating the close button.

1. Open the
device configuration of the S7-1200 CPU.
2. In the
hardware catalog, navigate to the analog input boards.
3. Select the
analog signal board to be replaced.
4. Change the
version of the selected signal board from V2.0 to V1.0. To do this,
open the "Information" window.
5. Drag-and-drop
the module to the slot of the S7-1200 CPU.
6. The
"Change device - AI1 Signal board" dialog opens with a comparison of
the two modules with different versions (current device and new device). Click
"OK" to replace the module automatically.
7. Save and
compile the project.
8. Load your
program into the CPU.


The version difference between the signal board
integrated in the CPU and the configured signal module in the device
configuration is then resolved and the error message no longer appears.

**Note**

The change of version described above is possible only with TIA Portal V12 onwards. If you have created your project in an earlier version, you must first upgrade the project to the current version. For this you use the menu command "Project > Upgrade".

**FAQ changes since the last release**

The FAQ has been revised and the video has been added

Security information

In order to protect technical infrastructures, systems, machines and networks against cyber threats, it is necessary to implement – and continuously maintain – a holistic, state-of-the-art IT security concept. Siemens’ products and solutions constitute one element of such a concept. For more information about cyber security, please visit

[https://www.siemens.com/cybersecurity#Ouraspiration.](https://www.siemens.com/cybersecurity#Ouraspiration "https://www.siemens.com/cybersecurity#Ouraspiration")

Also available in the following languages:

- German
- French
- Italian
- Spanish
- Chinese (simplified)

### Entry belongs to product tree folder(s):

- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCBasic ControllerS7-1200Central processing unitsStandard CPUs](https://support.industry.siemens.com/cs/products?pnid=13685)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC PLCBasic ControllerS7-1200Central processing unitsFail-safe CPUs](https://support.industry.siemens.com/cs/products?pnid=13686)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalPLC programmingSTEP 7 Professional (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=14673)
- [Automation TechnologyIndustry softwareAutomation softwareTIA PortalPLC programmingSTEP 7 Basic (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=14674)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC Software for SIMATIC ControllerSTEP 7 (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=24471)

Rate entry

Submit rating

Feedback for the article with the entry ID 85239632

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
[Go to the Forum](https://support.industry.siemens.com/forum/ww/en)

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
- [Favorites](https://support.industry.siemens.com/cs/my/favorite?lc=en-ww)
- [My requests](https://support.industry.siemens.com/cs/my/srm?lc=en-ww)
- [CAx downloads](https://support.industry.siemens.com/cs/my/cax?lc=en-ww)
- [My Products / Clipboard](https://support.industry.siemens.com/my/ww/en/products#myProductNode|nodeId|gCB3EA198-337A-4514-8F7A-5B346DC4E5D0)

Related links

- [Compatibility Tool](https://support.industry.siemens.com/cs/ww/en/view/64847781)
- [Register your SINAMICS Drive](https://www.automation.siemens.com/LDReg/?HTTPS=REDIR)
- [Visit our Converting Community](https://support.industry.siemens.com/tf/ww/en/threads/295)
- [Information on the packaging](https://support.industry.siemens.com/cs/ww/en/view/109813875)