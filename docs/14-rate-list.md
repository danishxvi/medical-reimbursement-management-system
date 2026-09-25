# 14. DGEHS Rate List

**Author:** Danish Husain

## 14.1 Why

The Head of School must restrict every bill to the approved DGEHS rate on the calculation sheet, and the PAO checks the same restriction. Doing this from paper rate lists is slow and error prone, and wrong restrictions are a common reason for returning claims. MRMS keeps the approved rates in the system and suggests the applicable rate for every bill that carries a rate code.

## 14.2 Source of the seeded list

| | |
|---|---|
| Document | "List of CGHS Rates 2025 for treatment at healthcare organisation" |
| Published at | [dgehs.delhi.gov.in, CGHS approved rates](https://dgehs.delhi.gov.in/dghs/cghs-approved-rates-treatment-and-investigative-procedures) |
| File | `cghs_rate.pdf`, SHA-256 `964b42f58eb0852d93293a658a183cceaafae33ba30a892979262c21695b9746` |
| Order | O.M. F.No.5-16/CGHS(HQ)/HEC/2024(PartI) dated 03.10.2025, effective 13.10.2025 |
| Part used | Annexure I (A): rates for semi private ward in X (Tier I) cities; Delhi is a Tier I city |
| Rows | 1998 (serial numbers 1 to 1998, no gaps, no duplicate codes) |

The rows were extracted from the PDF text and checked mechanically: every rate was compared with the source line, every Non-NABH rate is 85% of the NABH rate as the memorandum states, and every super speciality rate is at least the NABH rate. Rows whose names wrap across lines or pages were checked by hand. The data is in `backend/src/main/resources/db/rates/cghs-2025-tier1.csv` (with the source recorded in its header) and is loaded once by migration `V7__Seed_cghs_2025_rates`.

**Before real use** the Directorate must confirm the DGEHS order adopting these rates and its effective date. The list's notes say so on the administration page.

## 14.3 How the applicable rate is worked out

Following the memorandum:

1. **Hospital basis.** The Head of School chooses the column for the claim:

   | Basis | Used for |
   |-------|----------|
   | NABH | NABH or NABL accredited hospital (default for empanelled hospitals) |
   | Non-NABH | Hospital without accreditation; also every non empanelled private hospital (default for private hospitals) |
   | Super speciality | Super speciality hospital |
   | As billed | Government hospital (default); no restriction by rate |

2. **Ward entitlement.** Listed rates are for a semi private ward. For indoor packages, a general ward entitlement is 5% lower and a private ward entitlement 5% higher. The entitlement comes from the employee's profile.
3. **Uniform services.** Consultations, investigations, radiotherapy and out patient services are the same for every ward.
4. **Dates.** The list in force on the bill date applies. If no list covers the date, no rate is suggested (rather than a wrong one).

The suggestion fills the DGEHS rate and restricts the amount to the lower of the claimed amount and the rate, with a remark such as "Restricted to DGEHS rate (CGHS-2025-T1/LB012/NABH)". The Head of School can change any value; a remark stays mandatory when an amount is restricted. The applied reference is stored with each item and shown to the PAO.

## 14.4 Where it appears

- **Employee, claim form:** the rate code field searches the list by code or name ("LB012", "haemogram") and fills an empty description.
- **Head of School, calculation sheet:** basis selector, the approved rate under each bill, and "Apply suggested rates".
- **PAO:** the applied rate reference beside each item.
- **Administrator, DGEHS rate lists:** every list with its order, period, source and fingerprint; search within a list; import and activation.

## 14.5 Revising the rates

1. Prepare a CSV: `code,name,speciality,non_nabh,nabh,super_speciality` (optional `sr`), UTF-8, lines starting with `#` ignored.
2. Administration, DGEHS rate lists, **Import a revised list**: give the list code, title, order reference, source address, city tier and effective date. Every row is validated (code format, amounts, duplicates, unsafe characters); nothing is imported if any row is wrong. The file's SHA-256 is recorded.
3. The list is imported **inactive**. Check it, then **Activate**. The previous open ended list then ends the day before the new one starts, so bills dated earlier keep their original rates.

Imports, activations and deactivations are written to the audit trail.
