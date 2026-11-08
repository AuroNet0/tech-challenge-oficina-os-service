ALTER TABLE mechanic_assignments DROP CONSTRAINT mechanic_assignments_capability_check;
ALTER TABLE mechanic_assignments ADD CONSTRAINT mechanic_assignments_capability_check
    CHECK (capability IN ('OPERATIONAL_READ','VEHICLE_OPERATIONAL_READ',
        'DIAGNOSTIC_READ','DIAGNOSTIC_WRITE','DIAGNOSTIC_COMPLETE',
        'REPAIR_READ','REPAIR_WRITE','REPAIR_COMPLETE','HISTORY_READ'));
