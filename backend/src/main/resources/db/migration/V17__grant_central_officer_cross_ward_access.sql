-- CivicFix AI — V17 Grant standing cross-ward access to Central Municipal Operations Officer
-- Elena Vance serves as the central municipal operations officer (District 04 Headquarters)
-- coordinating cross-ward complaint dispatch and high-level municipal resolution.

UPDATE users
SET cross_ward_access = TRUE
WHERE email = 'elena.vance@metro.gov';
