package gov.irs.sbse.os.ts.csp.alsentity.ale.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Module View column allowlist — the 87 display labels business users can add
 * via the "add field" list, per the HQ all fields document (Module tab).
 *
 * Source: Eric's "HQ all fields" doc, transcribed 2026-08-18.
 * Count verified at 87, matching the 87-of-130 figure from the 08/14 meeting.
 *
 * <p>THESE ARE DISPLAY LABELS, NOT DB COLUMN NAMES. Do not put them in a
 * SELECT. Filter the view-column metadata (vcMetas) by this set and let
 * QueryBuildUtil.getBaseSql build the projection from the surviving metas.
 * That keeps the "add field" UI, the grid projection, and the CSV export
 * deriving from one list instead of three that drift.
 *
 * <p>Ordering below is the doc's order — alphabetical by underlying column
 * name, NOT display order. Display order comes from the user's saved layout.
 * Do not use this ordering to drive the grid or the CSV header sequence.
 */
public final class ModuleViewColumns {

    private ModuleViewColumns() { }

    private static final Set<String> LABELS = Collections.unmodifiableSet(
            new LinkedHashSet<>(java.util.Arrays.asList(

        "Age Ind",
        "AGI",
        "INC IND",
        "AGI/TPI Yr",              // -- VERIFY: appears as """AGI/TPI Yr""" in the
                                   //    source file, i.e. the value itself carries
                                   //    literal double quotes. Flagged 08/11 as one
                                   //    of three headers with embedded whitespace or
                                   //    quoting artifacts. Confirm with MODman owner
                                   //    whether the quotes are part of the label.
        "ALPH",
        "Assigned CFF",
        "Assigned To Field",
        "Assigned Queue",
        "Assigned RO",             // -- VERIFY: M_ASSNRO vs ASSNRO. See "RO" below --
                                   //    two distinct labels, and the 08/11 review
                                   //    could not tell which maps to which.
        "BAL 941 ALL TDA",
        "BAL 941 14 QTRs",
        "BOD Code",
        "BOD CL",
        "Case Code",
        "Case Type",               // -- VERIFY: flagged 08/11 as having no identified
                                   //    source column; stubbed in the CSV formatter.
        "PDT",
        "CASE CC",
        "Sel Code",
        "City",                    // mirrored from case table
        "Closed Date",             // -- VERIFY: CLSDT vs CLOSEDT
        "941 TDA",
        "941 14",
        "HOW CLSD",
        "Date Death",
        "Overage Date",
        "Pot Age Date",
        "Dis Vic",
        "Emp Hours",
        "Emp Tch",
        "FATCA",
        "Fed Con",
        "FED EMP",
        "Field Hours",
        "Case Grade",
        "HI INC",                  // -- VERIFY: HINFIND
        "Case Hours",
        "IND 941",
        "Initial Contact",
        "INN SP",
        "IRS EMP",
        "L5857 APPT",
        "L725D APPT",              // -- VERIFY: M_L725DT vs L725DT across the four
        "L725D GEN",               //    L725 entries.
        "L725B GEN",
        "L725B APPT",
        "L5857 GEN",
        "L903",
        "Lrg $",
        "LFI",                     // -- VERIFY: LFIIND vs ENT_LFIIND
        "LLC IND",
        "Last Touch",
        "MOD Cnt",
        "Bus Org Case",
        "OIC ACC",
        "Date Selected",
        "APPROVED PROGNAME 1",
        "APPROVED PROGNAME 2",
        "Potentia RO",             // -- VERIFY: reads "Potentia RO" in the source.
                                   //    Likely "Potential RO" truncated. Confirm
                                   //    before matching on exact label text.
        "Pyr Ent",
        "Pyr IDRS",
        "Pick Ind",
        "PRTY",
        "RO",                      // -- VERIFY: see "Assigned RO" above
        "Rpt Ind",
        "ST",                      // mirrored from case table
        "Stat Ind",
        "Case Status",
        "Street Address",          // mirrored from case table
        "2nd Street Address",      // mirrored from case table
        "Sub Code",
        "TDA Cnt",
        "TDI Cnt",
        "941 TDI",
        "Theft Ind",
        "TIN",                     // mirrored from case table
        "File Src",
        "Tin Type",
        "Balance",
        "Total Hours",
        "Tot Tch",
        "IRP Income",
        "Taxpayer Name",           // mirrored from case table
        "Taxpayer Name - Line 2",  // mirrored from case table
        "Name Ctrl",               // mirrored from case table
        "TPI",
        "Contact DueDATE",         // -- VERIFY: irregular casing preserved from source
        "Zip Code"                 // mirrored from case table
    )));

    /** The 87 permitted Module View display labels. Immutable. */
    public static Set<String> labels() {
        return LABELS;
    }

    public static boolean isPermitted(String label) {
        return label != null && LABELS.contains(label.trim());
    }

    /** Guard against silent transcription loss. Call from a unit test. */
    public static final int EXPECTED_COUNT = 87;
}
