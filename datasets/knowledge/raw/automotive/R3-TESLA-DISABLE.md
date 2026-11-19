2026-11-02
Vehicle HV Disablement Procedure
Correction code
See the codes below.
NOTE:
Unless otherwise explicitly
          stated in the procedure, the correction code and its associated FRT reflect all of the
          work required to perform this procedure, including the linked procedures.
Do not stack correction codes unless
            explicitly told to do so.
NOTE:
See
Flat Rate
            Times
to learn more about FRTs and how they are created.
NOTE:
See
Personal Protection
to make sure wearing proper PPE when
          performing the below procedure.
NOTE:
See
Ergonomic Precautions
for safe and healthy working
            practices.
2026-11-02:
Updated this procedure to include both HV disablement methods from the RDU
                        and the ancillary bay.
2026-09-10:
Updated the procedure to use the amp clamp and
                        voltage measurement method at the rear drive unit cables from underneath the
                        vehicle.
2025-11-26:
Added note to keep HV gloves on if procedure that required disablement
                        involves touching HV components.
2025-10-22:
Added info in measurement table on GND point to be used for Negative (-)
                        Lead, modified measurement sequence.
2025-03-24:
Added warning for regional tooling regulations.
2025-01-30:
Clarified B+ and B- probe
                        step.
2024-10-21:
Rearranged multimeter probing
                        images to align with HV measurement table.
2024-06-05:
Added warning to measure the
                        specified order of locations for high voltage.
2023-12-12:
Updated voltage measurement
                        order.
2023-11-08:
Added/updated HV insulating
                        glove steps.
2023-06-29:
Added the whole
                        procedure video as an overview for supplemental purposes only.
2023-08-30:
Added a tip about replacing
                        the probe to the multimeter-related step.
For HV disablement from the rear drive unit:
Correction code
17010000
;
FRT
0.54
For HV disablement from the ancillary bay area:
Correction code
17010010
;
FRT
0.30
Warning
This procedure
                verifies that high voltage power is no longer available to high voltage components
                    (
PTC heater,
A/C compressor, inverters, etc).
                Perform this procedure before servicing high voltage components.
Warning
Remove
            all jewelry (watches, bracelets, rings, necklaces, earrings, ID tags, piercings, etc.)
            from your person, and all objects (keys, coins, pens, pencils, tools, fasteners, etc.)
            from your pockets before performing any procedure that exposes you to high
          voltage.
Warning
If
            corrective eyewear is necessary to safely perform any procedure, make sure that the
            eyewear is securely restrained to the head and cannot fall
          off.
Warning
Always wear High
                Voltage (HV) gloves and safety glasses when handling any high voltage component,
                including test equipment. Before each use, test gloves using an authorized glove
                tester and verify that the testing date on the gloves has not expired.
Warning
All repair and
                operating instructions should be reviewed and understood before working on Tesla
                vehicles, associated components, or associated repair equipment.
Warning
Assume that high
                voltage is present at all times when working on a Tesla vehicle, associated high
                voltage components, or associated high voltage test equipment until proven
                otherwise. Be aware that energy states can change during testing, so err on the side
                of caution.
Warning
Always have an
                electrical safety hook in the area when working with HV components. Electrical
                safety hooks are used to remove an injured person from a live high voltage circuit
                without risking additional injury.
Warning
Use a high voltage
                digital multimeter to check for the presence of high voltage on both sides of the
                connector every time a high voltage connector is disconnected.
Warning
In certain regions,
                a Voltage Absence Tester (VAT) is required for this procedure, and the use of a
                multimeter is not allowed. Always adhere to local or regional tooling
                regulations.
Warning
Make sure that the
                multimeter and leads are capable of handling at least 500V DC.
Warning
Obey all
                instructions when using test equipment or servicing a Tesla vehicle or associated
                high voltage components. If repair instructions are not clear or if there are any
                questions about how to use the test equipment, escalate a Toolbox session before
                proceeding.
Warning
If any test
                equipment fails to operate as expected, do not attempt to diagnose or repair the
                vehicle. Follow the appropriate lock out – tag out procedure for the equipment,
                notify your manager.
Warning
When using test
                equipment, make sure that all cables are connected before turning equipment on or
                otherwise initiating use.
Warning
High voltage is
                present on battery recovery chargers whenever the HV LEDs are lit. However,
                indicators and LEDs can fail. Do not assume that if an LED is not lit no high
                voltage is present. Assume high voltage is present at all times until proven
                otherwise by use of a multimeter.
Warning
Only
            technicians who have completed all required certification courses are permitted to
            perform this procedure. Tesla recommends third party service provider technicians
            undergo equivalent training before performing this procedure. For more information on
            Tesla Technician requirements, or descriptions of the subject matter for third parties,
            see
HV Certification Requirements
. Proper
personal protective equipment (PPE)
and
insulating HV
              gloves
with a minimum rating of
class 0
(1000V) must
            be worn at all times a high voltage cable, busbar, or fitting is handled. Refer to
Tech Note TN-15-92-003
,
High Voltage Awareness
              Care Points
for additional safety
          information.
Equipment:
1083188-01-B - Digital
                        Multimeter Unit, EXTECH 355
1754057-00-A - CURRENT
                        PROBE, GEN, CLAMP, DC, 600V
1140311-00-A - LEVER LOCK, HV CONNECTOR, MODEL 3
1115247-01-A - Electronic Test Lead, CAT IV 1000V
1130480-02-B - Slim Test Probes, CAT III 1000V
From Rear Drive Unit (RDU)
Disconnect 12V/LV power. See
12V/LV Power (Disconnect and Connect)
.
Set the multimeter to
                        measure voltage, and then measure the voltage across the 12V/LV auxiliary
                        battery terminals.
Note
For
LV
(Li-Ion)
                            auxiliary battery: if the voltage across the battery terminals is 0, the
                            battery may have switched off internally. Use a 12V Lead Acid battery
                            for testing. For mobile technicians: use the 12V Lead Acid battery in
                            the Mobile Service vehicle.
CAUTION
For
LV
(Li-Ion)
                            auxiliary battery: if the voltage across the battery terminals is less
                            than 13 volts or greater than 16 volts, the multimeter is not measuring
                            reliably and must not be used.
CAUTION
For
12V
(Lead Acid)
                            auxiliary battery: if the voltage measured is less than 10 volts or
                            greater than 14 volts, the multimeter is not measuring reliably and must
                            not be used.
Remove the rear aero shield panel. See
Panel - Aero Shield - Rear (Remove and Replace)
.
Remove the rear skid plate. See
Skid Plate - HV Battery - Rear (Remove and Replace)
.
Install the current clamp
                        meter onto one cable of the rear drive unit HV harness.
Note
Make sure the meter is
                            zeroed out and is facing the correct direction as shown.
Measure the current in the
                        HV cable.
If the current is
                            within the range of -0.5 A to 0.5 A, continue to the next step.
If the current is
                            outside the range of -0.5 A to 0.5 A, for example -0.8 A or 1.3 A,
                            re-perform the vehicle electrical isolation again to make sure that all
                            was properly shut down. If the measurement is still outside the range of
                            -0.5 A to 0.5 A, escalate for assistance.
Warning
DO NOT
                            DISCONNECT any HV harness when the current measurement is outside the
                            range of -0.5 A to 0.5 A.
Remove the clamp meter from the cable.
Remove the nut that attaches
                        the rear drive unit HV cable bracket to the HV battery.
Note
The number of the
                            fasteners may vary.
Tip
Use of the following
          tool(s) is recommended:
10 mm socket
Remove all jewelry and empty
                        your pockets before continuing this procedure.
Inspect and put on HV
                        insulating gloves, leather outer gloves, and safety glasses before
                        continuing this procedure.
Disconnect the connector that attaches the rear drive unit HV cable to the
                        HV battery.
Note
Release the locking tab and rotate the release
                            lever up to disengage the connector. Do not force the release lever up.
                            Ensure the alignment tabs on the rear drive unit header are not
                            damaged.
Use the high voltage
                        multimeter with CAT III probes to verify that no high voltage is present at
                        the rear drive unit HV header.
Warning
In order to limit
                potential exposure to high voltage, measure for high voltage in the order specified
                below.
Measure the following
                            voltages:
B+ to ground
B- to ground
B+ to B-
Warning
If
                            any voltage is greater than 10V, the contactors are closed or welded.
                            Escalate to Service Engineering.
Make sure the connector lock
                        is 90 degrees from the connector, and then connect the connector that
                        attaches the rear drive unit HV cable to the HV battery.
Note
Use one hand to support
                            the connector while the other latching the locking tab. Once installed,
                            verify that the latch is not damaged and is fully secured in the latched
                            position.
Remove the leather over
                        gloves and the HV insulating gloves.
Install the nut that
                        attaches the rear drive unit HV cable bracket to the HV battery.
Note
The number of the
                            fasteners may vary.
Tip
Use of the following
          tool(s) is recommended:
10 mm socket
Install the rear skid plate. See
Skid Plate - HV Battery - Rear (Remove and Replace)
.
Install the rear aero shield panel. See
Panel - Aero Shield - Rear (Remove and Replace)
.
Return to the procedure that
                        required the vehicle to be electrically isolated.
Warning
Follow all safety
                instructions and PPE requirements of the procedure that required the vehicle to be
                electrically isolated. Do not remove the HV gloves if the procedure requires you to
                handle high voltage cables, busbars, or fittings.
Note
If you are no longer
                            exposed to high voltage, the HV insulating gloves may be removed.
From Ancillary Bay
Open all four doors and
                        lower all four windows.
Move both front seats
                        forward.
Remove the 2nd row lower
                        seat cushion, if not yet removed in the previous step. See
Seat Cushion - Lower - 2nd Row (Remove and Replace)
.
Disconnect 12V/LV power. See
12V/LV Power (Disconnect and Connect)
.
Set the multimeter to
                        measure voltage, and then measure the voltage across the 12V/LV auxiliary
                        battery terminals.
Note
For
LV
(Li-Ion)
                            auxiliary battery: if the voltage across the battery terminals is 0, the
                            battery may have switched off internally. Use a 12V Lead Acid battery
                            for testing. For mobile technicians: use the 12V Lead Acid battery in
                            the Mobile Service vehicle.
CAUTION
For
LV
(Li-Ion)
                            auxiliary battery: if the voltage across the battery terminals is less
                            than 13 volts or greater than 16 volts, the multimeter is not measuring
                            reliably and must not be used.
CAUTION
For
12V
(Lead Acid)
                            auxiliary battery: if the voltage measured is less than 10 volts or
                            greater than 14 volts, the multimeter is not measuring reliably and must
                            not be used.
Remove all jewelry and empty
                        your pockets before continuing this procedure.
Put on HV insulating gloves,
                        leather outer gloves, and safety glasses before continuing this
                        procedure.
Remove the HV probe cover.
                        See
Cover - HV Probe - HV Battery (Remove and Replace)
.
Use a multimeter to probe the voltages.
Warning
In order to limit
                potential exposure to high voltage, measure for high voltage in the order specified
                below.
Measurement Order
Component Test
B+
(Probe opening closer to HV DC connector)
B-
(Probe opening, closer to HV Processor Logic connector)
GND
Measured Voltage less than 10 V
Measured Voltage 10 V or more
1
HV battery positive contactor
Positive (+) Lead
Negative (-) Lead on ancillary bay cover
Pass
Fail
HV battery positive contactor might be seized.
See
HV Battery Contactor Seize Check
.
2
HV battery negative contactor
Positive (+) Lead
Negative (-) Lead on ancillary bay cover
Pass
Fail
HV battery negative contactor might be seized.
See
HV Battery Contactor Seize Check
.
3
HV
Positive (+) Lead
Negative (-) Lead
Pass
Fail
Perform a HV battery contactor seize check.
See
HV Battery Contactor Seize Check
.
Tip
Use of the following
          tool(s) is recommended:
Electrical Protective Gloves
Slim Test Probes -
                  1130480-02-B
Fluke 1587 - 1076921-00-A
Figure 1.
B+ to Ground measurement
Figure 2.
B- to Ground measurement
Figure 3.
B+ to B- measurement
Return to the procedure that
                        required the vehicle to be electrically isolated.
Warning
Follow all safety
                instructions and PPE requirements of the procedure that required the vehicle to be
                electrically isolated. Do not remove the HV gloves if the procedure requires you to
                handle high voltage cables, busbars, or fittings.
Note
If you are no longer
                            exposed to high voltage, the HV insulating gloves may be removed.
