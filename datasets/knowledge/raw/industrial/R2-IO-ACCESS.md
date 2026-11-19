×

Siemens Industry Online Support

Siemens AG

VIEW

- [Product Support](https://support.industry.siemens.com/cs/products?lc=en-ls)
- [Forum](https://support.industry.siemens.com/forum/ls/en/)
- [mySupport](https://support.industry.siemens.com/cs/my?lc=en-ls)

Entry type:FAQEntry ID:42781105,
Entry date:09/20/2018

Rate

# How do you diagnose IO access errors with the S7-1200?

- Entry

- Associated product(s)


In STEP 7 (TIA Portal) you program the diagnostic error interrupt OB and enable the necessary functions for the module to be diagnosed.

[1\. Evaluating the diagnostics error events with the diagnostic error interrupt OB](https://support.industry.siemens.com/cs/#chapter_5468994_0) [2\. Enabling and understanding the different diagnostic error events](https://support.industry.siemens.com/cs/#chapter_5468994_1) [3\. Online diagnostics with STEP 7 (TIA Portal)](https://support.industry.siemens.com/cs/#chapter_5468994_2) [4\. Further InformationMore information is available in the following table:](https://support.industry.siemens.com/cs/#chapter_5468994_3)

## **1\. Evaluating the diagnostics error events with the diagnostic error interrupt OB**

The IO access errors are also known as diagnostic errors. The following diagnostic errors are supported:

- No encoder or load power (on the signal module / signal board)
- High limit exceeded (of the value at an analog input or output)
- Low limit exceeded (of the value at an analog input or output)
- Wire break (at an analog current output)
- Short circuit (at an analog voltage output)

All diagnostic error events trigger the execution of the diagnostic error interrupt OB. The diagnostic error interrupt OB includes startup information that helps you determine the circumstances of the error occurrence:

- From which device and channel the error was reported.
- Whether the event is due to the occurrence or removal of an error.

You can program instructions in the diagnostic error interrupt OB to examine these startup values and to take appropriate action. The occurrence or removal of any of several different diagnostic error conditions results in a diagnostic error event if the following conditions are met:

- The program of the S7-1200 has a diagnostic error interrupt OB.
- The diagnostics error event has been enabled for this module.

If there is no diagnostic error interrupt OB, then the CPU ignores the error. The diagnostic error interrupt OB interrupts the normal cyclic program execution as soon as a diagnostics-compatible module recognizes an error.

When you create a new project, there is not yet a diagnostic error interrupt OB available. Proceed as follows to program the diagnostic error interrupt OB:

1. In the Project tree you open the folder structure of your PLC station and then the "Program blocks" folder.
2. Double-click the "Add new block" function.
3. In the "Add new block" dialog you click the "Organization block" button and in the selection list displayed you select "Diagnostic error interrupt" OB.
4. Close the dialog with OK. After inserting the Diagnostic error interrupt OB in your program folder, the block is opened automatically.

**Note** You can use only one "Diagnostic error interrupt" OB in the program.
![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_1_en.png)

Fig. 1

5. To read out the startup information for the diagnostic error interrupt OB you insert the "MOVE" instruction three times in the program editor of the diagnostic error interrupt OB. The MOVE instruction is in the "Instructions" task card under "Basic instructions > Move operations".
6. Parameterize the input parameter of each "MOVE" instruction with one of the input parameters of the diagnostic error interrupt OB ("IO\_State", "LADDR" and "Channel").
7. Parameterize the output parameter of each "MOVE" instruction with a DB tag that has the same data type as the input parameters of the diagnostic error interrupt OB.
8. Program the error query ("MultiError") with a bit link.


![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_2_en.png)

Fig. 2


The diagnostic error interrupt OB has the following has the following startup information:

- IO\_State: State of the hardware object
- LADDR: Hardware ID of the hardware object that has triggered the diagnostic error interrupt
- Channel: Channel number
- MultiError: Signal "True" if there is more than one error

The hardware identifier (HW ID) identifies modules or functional units of modules (for example outputs and high speed counters). This integer is transferred by the system along with diagnostic error interrupts to allow the faulty module or functional unit to be localized.

To find the hardware ID, in the device configuration you open the properties of the module and select the "System constants" tab (Fig. 3).

![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_3_en.png)

Fig. 3

## **2\. Enabling and understanding the different diagnostic error events**

The operating system monitors the diagnostics-compatible devices for the diagnostic errors mentioned above. Diagnostic error events are indicated by flashing red LEDs. In the following you see additional information about the different diagnostic error events and how to enable them.

1. In the Project tree you open the folder structure of your PLC station and then double-click "Device configuration".
2. In the device configuration you double-click the object where you want to enable the diagnostic error events.

Now, in the inspector window of the "General" tab, you can edit the displayed properties and parameters of the selected object.

|     |     |
| --- | --- |
| 1. | **No encoder or load power**<br>The power supply is insufficient or missing.<br>The following S7-1200 LEDs start to flash (LED color: red): <br>- The "ERROR" LED on the CPU. <br>- All LEDs of the signal module / signal board.<br>You enable the power supply diagnostics as follows: <br>1. In the area navigation you click the "AI 4/AQ 2" folder. <br>2. Enable the option "Enable power supply diagnostics".<br>   <br>   <br>   ![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_4_en.png)<br>   <br>   Fig. 4 |
| 2. | **"High limit exceeded"**<br> The high limit of an analog input / output has been exceeded (output value greater than "+32511"). <br>The following S7-1200 LEDs start to flash (LED color: red): <br>- The "ERROR" LED on the CPU. <br>- The "DIAG" LED if it is a signal module. <br>- The LED of the associated channel. <br>You enable the overflow diagnostics as follows:<br>1. In the area navigation you open the "AI 4/AQ 2" folder and click "Analog inputs".<br>2. Enable the option: "Enable overflow diagnostics".<br>   <br>![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_5_en.png)<br>Fig. 5 |
| 3. | **"Low limit exceeded"**<br> The low limit of an analog input / output has been exceeded (output value for current less than "0", for less than "-32512"). <br>The following S7-1200 LEDs start to flash (LED color: red): <br>- The "ERROR" LED on the CPU. <br>- The "DIAG" LED if it is a signal module. <br>- The LED of the associated channel. <br>You enable the underflow diagnostics as follows:<br>1. In the area navigation you open the "AI 4/AQ 2" folder and click "Analog inputs".<br>2. Enable the option: "Enable underflow diagnostics".<br>See Fig. 05 for more details. |
| 4. | **"Wire break"**<br> The circuit of an analog Current output is not closed. <br>The following S7-1200 LEDs start to flash (LED color: red): <br>- The "ERROR" LED on the CPU. <br>- The "DIAG" LED if it is a signal module. <br>- The LED of the associated channel. <br>You enable the wire break diagnostics as follows:<br>1. In the area navigation you navigate to the desired channel, for example: "AI 4/AQ 2 > Analog outputs > Channel1".<br>2. For measurement type or analog output type you select "Current".<br>3. Enable the option "Enable broken wire diagnostics".<br>   <br>![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_6_en.png)<br>Fig. 6 |
| 5. | **"In the area navigation"**<br> The contacts of an analog Voltage output are short-circuited. <br>The following S7-1200 LEDs start to flash (LED color: red): <br>- The "ERROR" LED on the CPU. <br>- The "DIAG" LED if it is a signal module. <br>- The LED of the associated channel. <br>You enable the short circuit diagnostics as follows:<br>1. In the area navigation you navigate to the desired channel, for example: "AI 4/AQ 2 > Analog outputs > Channel1".<br>2. For analog output type you select "Voltage".<br>3. Enable the option "Enable short circuit diagnostics".<br>   <br>![](https://support.industry.siemens.com/cs/images/42781105/42781105_S7-1200_peripheral-errors_7_en.png)<br>Fig. 7 |

## **3\. Online diagnostics with STEP 7 (TIA Portal)**

If you can also access your S7-1200 CPU online with STEP 7 (TIA Portal), then you can make use of the "Online & diagnostics" function.

1. In the Project tree you mark the CPU to be diagnosed, "PLC\_1 \[CPU 1215C DC/DC/DC\]", for example.
2. In the toolbar you click the "Go online" function.
3. Then in the Project tree you double-click "Online & diagnostics".
4. In the "Online access" dialog you mark the "Diagnostics buffer" item.
5. In the "Events" table you select a diagnostics error event.
6. Details of the selected event are shown underneath the "Events" table.

**Note**

"Incoming event" indicates the beginning of an event.

"Outgoing event" indicates the end of an event.

## **4\. Further Information**    **More information is available in the following table:**

| Entry type | Link |
| --- | --- |
| Manual | [Diagnostics buffer](https://support.industry.siemens.com/cs/ww/en/view/109773506/20343456395 "Beitrags ID: 109773506") |
| Manual | [Display diagnostics events in the CPU](https://support.industry.siemens.com/cs/ww/en/view/91696622/61296829451 "Beitrags ID: 91696622") |
| Manual | [Go online to monitor the values in the CPU](https://support.industry.siemens.com/cs/ww/en/view/91696622/26009060107 "Beitrags ID: 91696622") |
| FAQ | [How do CPUs of the S7-1200/S7-1500 series react in the case of IO access errors to the input parameters of a block?](https://support.industry.siemens.com/cs/document/89377245/how-do-cpus-of-the-s7-1200-s7-1500-series-react-in-the-case-of-io-access-errors-to-the-input-parameters-of-a-block-?lc=en-ls "ID: 89377245") |
| FAQ | [How do you implement module and channel diagnostics in the user program of the SIMATIC S7-1200/1500?](https://support.industry.siemens.com/cs/document/109480387/how-do-you-implement-module-and-channel-diagnostics-in-the-user-program-of-the-simatic-s7-1200-s7-1500-?lc=en-ls "ID: 109480387") |

**Creation Environment** The screens in this FAQ response were created with STEP 7 (TIA Portal) V15.

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
- [Automation TechnologyProducts for specific requirementsAutomatision technology for extreme conditionsSIPLUS extreme Industrial Automation SystemsControllersSIPLUS Basic ControllerSIPLUS S7-1200SIPLUS I/O modulesSIPLUS Analog Modules](https://support.industry.siemens.com/cs/products?pnid=18085)
- [Automation TechnologyAutomation SystemsIndustrial Automation Systems SIMATIC Software for SIMATIC ControllerSTEP 7 (TIA Portal)](https://support.industry.siemens.com/cs/products?pnid=24471)

Rate entry

Submit rating

Feedback for the article with the entry ID 42781105

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
[Go to the Forum](https://support.industry.siemens.com/forum/ls/en)

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
- [Favorites](https://support.industry.siemens.com/cs/my/favorite?lc=en-ls)
- [My requests](https://support.industry.siemens.com/cs/my/srm?lc=en-ls)
- [CAx downloads](https://support.industry.siemens.com/cs/my/cax?lc=en-ls)
- [My Products / Clipboard](https://support.industry.siemens.com/my/ls/en/products#myProductNode|nodeId|gCB3EA198-337A-4514-8F7A-5B346DC4E5D0)

Related links

- [Compatibility Tool](https://support.industry.siemens.com/cs/ww/en/view/64847781)
- [Register your SINAMICS Drive](https://www.automation.siemens.com/LDReg/?HTTPS=REDIR)
- [Visit our Converting Community](https://support.industry.siemens.com/tf/ww/en/threads/295)
- [Information on the packaging](https://support.industry.siemens.com/cs/ww/en/view/109813875)