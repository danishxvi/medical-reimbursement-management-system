# 13. Paper Form to System Mapping

**Author:** Danish Husain

The five pages an employee fills today, and the HoS/HoO certificate, are fully represented in MRMS. Most fields are no longer typed by the employee at all.

| Paper form | Field | In MRMS | Who provides it |
|------------|-------|---------|-----------------|
| Annexure I (check list) | DGEHS card no. and place of issue | `employee_profile.dgehs_card_no`, `dgehs_card_place` | Office, once |
| | Card validity from / to | `dgehs_valid_from`, `dgehs_valid_to` (checked against treatment dates) | Office, once |
| | Ward entitlement | `ward_entitlement` | Office, once |
| | Name, designation | account and profile | Office, once |
| | Documents a) to l), Yes / No | Derived automatically from attachment categories (check list on every claim) | System |
| | Bank, branch, account, MICR, IFSC | profile (account stored masked) | Office, once |
| Annexure II (Revised Medical 2004) | Items 8 to 15 (card, address, phones, e-mail, bank) | profile | Office once; employee edits contact details |
| | 16. Patient and relationship | patient chosen from dependents | Employee, per claim |
| | 17. Basic pay | profile | Office, once |
| | 18. Hospital name and address; OPD period; indoor admission and discharge | claim header | Employee, per claim |
| | 19. Total amount claimed (consultation, investigation, medical, other x OPD, indoor) | computed from bills | System |
| | 20. Referral details, 21. Medical advance | claim header | Employee, per claim |
| | Declaration | part of the undertaking accepted on submission | Employee |
| School medical application form | S.No, date, receipt no., item, price | one bill per line (`claim_item`) with the uploaded bill | Employee |
| | DGEHS rates, restriction | calculation sheet columns | HoS |
| Calculation sheet | Treatment / investigation, DGEHS code, rate charged, DGEHS approved rate, restricted claim, remarks | `claim_item.dgehs_code`, `amount_claimed`, `dgehs_rate`, `amount_restricted`, `hos_remarks` | Employee (code, amount), HoS (rate, restriction) |
| | Signature of DDO and HoS | HoS certification with password confirmation, recorded with name and time | HoS |
| Undertaking (7 points) | Beneficiary, dependency, 3 month limit, DGEHS restriction, tallied with prescription, NAC attached, no inadmissible medicine | shown in full, accepted on submission; the 3 month limit and NAC coverage are also enforced by validation | Employee |
| Certificate by HoS/HoO (7 points) | Examined per rules, restricted per DGEHS rate, self / family, within time, beneficiary, checked with prescription, N/A certificate with MOIC stamp | shown in full, confirmed before forwarding; e-NAC countersignature replaces the stamp | HoS |
| Dispensary stamp on prescription | Non availability certificate, pharmacist and MO signature | e-NAC with item level decisions and countersignature | Pharmacist, MO |

Terms used on the forms: **AMA** (Authorised Medical Attendant, the dispensary), **MOIC** (Medical Officer In Charge), **DDO** (Drawing and Disbursing Officer, usually the HoS in a school).
