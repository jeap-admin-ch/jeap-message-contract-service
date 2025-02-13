# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.27.0] - 2025-02-13

### Changed

- Update parent from 26.23.0 to 26.24.2
- Disable license plugins for service instances

## [3.26.0] - 2025-02-10

### Changed

- Update parent from 26.22.3 to 26.23.0
- Publish to maven central

## [3.25.0] - 2025-02-03

### Changed

- Prepare repository for Open Source distribution

## [3.24.0] - 2025-01-20

### Changed

- Users with the role 'messagecontract-contract-upload' can also check the compatibility of deployments

## [3.23.0] - 2025-01-14

### Changed

- Added the module jeap-message-contract-service-instance which will instantiate an MCS instance when used as parent project.
- Update parent from 26.21.1 to 26.22.3

## [3.22.0] - 2024-12-19

### Changed

- Update parent from 26.14.0 to 26.21.1

## [3.21.1] - 2024-10-31

### Changed

- Update parent from 26.5.0 to 26.14.0
- Narrowed dependencies to jeap-messaging, avoid dependency on jeap-messaging maven plugin 

## [3.21.0] - 2024-10-31

### Changed

- Update parent from 26.4.0 to 26.5.0

## [3.20.0] - 2024-10-17

### Changed

- Update parent from 26.3.0 to 26.4.0

## [3.19.0] - 2024-09-20

### Changed

- Update parent from 26.0.0 to 26.3.0

## [3.18.0] - 2024-09-06

### Changed

- Update parent from 25.4.0 to 26.0.0

## [3.17.0] - 2024-08-27

### Changed

- Add a transactionId to the message contract to support projects uploading contracts from different modules

## [3.16.0] - 2024-08-22

### Changed

- Update parent from 25.1.0 to 25.4.0

## [3.15.0] - 2024-07-16

### Changed

- Update parent from 24.5.0 to 25.1.0

## [3.14.0] - 2024-07-16

### Changed

- Update parent from 23.20.0 to 24.5.0, which includes upgrade to Spring Boot 3.3.1

## [3.13.0] - 2024-06-10

### Changed

- Remove spring MVC views listing all contracts/deployments (only for debugging & they might use too much memory)
- Avoid loading avro schema column from database when not necessary

## [3.12.2] - 2024-06-03

### Fixed

- Fix unique constraint violation due to missing flush after delete / before insert

## [3.12.1] - 2024-04-04

### Changed

- Update parent from 23.12.0 to 23.13.0
- Users with the role 'messagecontract-contract-upload' can also register deployments

## [3.12.0] - 2024-03-28

### Changed

- Update parent from 23.10.4 to 23.12.0

## [3.11.0] - 2024-03-20

### Changed

- It is now possible to configure multiple users with the role 'messagecontract-contract-upload' 
- Breaking Change: updated configuration of users in WebSecurityConfig

## [3.10.0] - 2024-03-14

### Changed

- Update parent from 23.0.0 to 23.10.4

## [3.9.0] - 2024-02-05

### Changed

- Update parent from 22.5.0 to 23.0.0

## [3.8.1] - 2024-02-01

### Changed

- Increased logged information on errors in RestControllerExceptionHandler

## [3.8.0] - 2024-01-25

### Changed

- Update parent from 22.2.3 to 22.5.0

## [3.7.0] - 2024-01-23

### Changed

- Update parent from 22.1.0 to 22.2.3

## [3.6.0] - 2024-01-16

### Changed

- Update parent from 22.0.0 to 22.1.0

## [3.5.0] - 2024-01-09

### Changed

- Update parent from 21.2.0 to 22.0.0

## [3.4.0] - 2023-12-15

### Changed

- Update parent from 21.0.0 to 21.2.0

## [3.3.0] - 2023-11-22

### Changed

- Update parent from 20.6.0 to 21.0.0

## [3.2.0] - 2023-10-30

### Changed

- Updated links and information in OpenApi configuration
 
## [3.1.0] - 2023-09-22

### Changed

- Update parent from 20.0.3 to 20.6.0
- Added new delete deployment endpoint

## [3.0.2] - 2023-09-01

### Changed

- Return incompatible schema fragments in compatibility error message

## [3.0.1] - 2023-08-25

### Changed

- Set spring.jpa.properties.hibernate.timezone.default_storage=NORMALIZE

## [3.0.0] - 2023-08-23

### Changed

- Update parent from 19.17.0 to 20.0.0
- Spring Boot 3 migration

## [2.13.0] - 2023-08-09

### Changed

- Update parent from 19.16.1 to 19.17.0

## [2.12.0] - 2023-08-08

### Changed

- Update parent from 19.12.1 to 19.16.1

## [2.11.0] - 2023-05-30

### Changed

- Update parent from 19.10.1 to 19.12.1

## [2.10.0] - 2023-05-02

### Changed

- Added encryption key id field to message contracts.

## [2.9.0] - 2023-04-21

### Changed

- Update parent from 19.2.0 to 19.10.1

## [2.8.0] - 2023-02-21

### Changed

- Update parent from 19.0.0 to 19.2.0

## [2.7.0] - 2023-02-07

### Changed

- Update parent to 19.0.0
- The deleted flag of the message contract added in all requests (deleted message contracts are no longer valid)

## [2.6.0] - 2022-12-23

### Added

- Query parameter for retrieving contracts by environment 

## [2.5.1] - 2022-12-15

### Changed

- Improved error message in case of incompatibilities

## [2.5.0] - 2022-11-17

### Added

- View showing latest deployments per environment

### Fixed

- Clone message type repository only once when multiple contracts are uploaded

## [2.4.0] - 2022-11-10

### Added

- Compatibility check API (can-i-deploy, based on consumer/producer schema compatibility)

## [2.3.0] - 2022-11-04

### Added

- Message type schema import

## [2.2.0] - 2022-10-31

### Changed

- Update parent from 18.0.2 to 18.2.0

## [2.1.0] - 2022-10-13

### Changed

- Update parent from 18.0.0 to 18.0.2 
- Refactoring rest apis (returns 201 instead 200)
- Save deployments only when both appName and appVersion are known

## [2.0.0] - 2022-10-07

### Changed

- Update parent from 17.3.0 to 18.0.0 (spring boot 2.7)

### Added

- Add new Deployment entity to store deployment information

## [1.3.0] - 2022-09-21

### Changed

- Update parent from 17.2.2 to 17.3.0

## [1.2.0] - 2022-09-13

### Changed

- Update parent from 17.2.0 to 17.2.2

## [1.1.0] - 2022-09-01

### Added

- HTML listing of contracts
- Use PUT for contract upload

## [1.0.0] - 2022-08-31

### Added

- Initial revision
