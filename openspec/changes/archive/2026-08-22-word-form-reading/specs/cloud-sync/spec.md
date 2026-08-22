## MODIFIED Requirements

### Requirement: Whole-file sync model
The system SHALL sync the `easyvocabook.db` file as a single atomic unit (upload or download the
entire file). Record-level merge is not supported.

Before accepting a downloaded DB, the system SHALL check `db_info.version`:
- If remote version > app's supported version → refuse sync, show "Please update the app"
- Otherwise → proceed with the latest-wins decision

A downloaded database whose version is older than the current schema version SHALL be migrated
when it is opened, following `specs/db-schema/spec.md` § DB version migration guard.

#### Scenario: Remote DB version too new
- **WHEN** the downloaded DB has a `db_info.version` greater than the version the app supports
- **THEN** sync is aborted and the user sees "Please update the app to open this file"

#### Scenario: Remote DB version older than current
- **WHEN** the downloaded DB has a `db_info.version` lower than the current schema version
- **THEN** sync proceeds and the file is migrated to the current version when it is opened
