Subject: RE: MTEST – Role-Based Access, Change Role, and Change Access Testing - FAIL

Brian, Sarah, Eric —

Thanks both. We've worked through Brian's note and Sarah's nine findings and mapped them back to root causes. Several of the findings share a single cause, so the fix list is shorter than the count suggests. We've started on the items that don't need anything from you.

To move the rest, we need six things:

1. Acting Group Manager vs. removing the level fields. Brian asks that ELEVEL be controlled only through Change Access. Sarah's AGM requirement needs the same assignment number to work at either employee or group level. We can't do both from the assignment number alone — an AGM needs some way to indicate which of the two they're exercising. Legacy handled this with a choice at login and an inline "AGM" marker, visible in the 261314 capture Sarah sent. Please confirm which approach you want.

2. "National users" in Brian's item 2 — does this mean ELEVEL 0, or users with a staff (8590…) assignment number? The modal currently keys off the assignment prefix, and the two aren't the same population.

3. "Keep Selection as Default" — we have no written definition of this. Sarah's four questions are exactly what we need answered: what value is saved, whether it's the assignment number / role / Org / a combination, when it's applied, and how it behaves for users with multiple assignments or multiple roles.

4. Area 85 assignment numbers by Organization. Sarah notes that Field and Advisory get separate Area 85 numbers even at National level. Could we get the authoritative list of 8590xx prefixes and the Org each maps to? This sits directly upstream of the Area/Territory drill-down issue.

5. MTEST build on 08/11. Brian notes the single-Org behaviour is already coded correctly in TEST. Can someone confirm what build MTEST was running when Sarah tested? If it was behind, part of the list may already be resolved.

6. Two items referenced but not sent to us — SP460 / the CCD "loop" defect, and "FW ENTITY Access Pick T-SIGN Mock Up Update.msg". Please forward both.

Happy to take 1 and 3 on a call if that's faster.

Thanks,
[Name]
