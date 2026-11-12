# Changelog

## [0.2.1](https://github.com/skjaere/mock-nntp-server/compare/v0.2.0...v0.2.1) (2026-11-12)


### Bug Fixes

* answer unmocked STAT, QUIT, DATE and CAPABILITIES per RFC 3977 ([09a6664](https://github.com/skjaere/mock-nntp-server/commit/09a666468b54d432b004c82a6009eb158b5d4dfb))
* stamp the Docker image with its build time ([bc43233](https://github.com/skjaere/mock-nntp-server/commit/bc43233b02c453a8a6345ab6f31e5cb661ecd3da))

## [0.2.0](https://github.com/skjaere/mock-nntp-server/compare/v0.1.0...v0.2.0) (2026-02-25)


### Features

* Add test helpers for generating and registering archive NZBs
* adding support for stat mocks
* supporting removing body expectations, and returning 430 when body expectation exists


### Bug Fixes

* bumping kotlin-compression-utils to 0.3.1
* fixing bug with xml escaping
* fixing coordinates of kotlin-compression-utils
* inline release-please workflow instead of referencing reusable workflow
* resolve test failures from NNTP server shutdown and error handling
* return 430 for BODY commands with no mock, handle 4xx in test helpers

## Changelog
