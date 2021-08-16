# mod-translations
FOLIO Translations Management Module

Copyright (C) 2020 The KnowledgeWare Technologies Devolopers Team

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

* [Introduction](#introduction)
* [Use case(s)](#use-cases)
* [Jira Related Issues Links](#jira-related-issues-links)
* [Compilation](#compilation)
* [Docker Build](#docker-build)
* [Installing a module](#installing-a-module)
* [Deploying a module](#deploying-a-module)

## Introduction

Each FOLIO installation has lists of controlled vocabulary terms (for instance, “Formats” and “Resource types” in Inventory, “Patron Groups” and “Refund Reasons” in Users — see [FOLIO-2802](https://issues.folio.org/browse/FOLIO-2802) for a more complete list).  In installations that use more than one language, these terms do not display translated values when the locale of the session is changed.

So we need a mechanism for storing translated strings for controlled vocabulary terms.

## Use case(s)

As a FOLIO Stripes app user, I want to see strings in my native locale so I can understand the user interface.

As a systems librarian, I want to enter controlled vocabulary in two or more locales so that my users can understand the user interface; some countries, regions or institutions are bilingual or multilingual.

## Jira Related Issues Links
https://issues.folio.org/browse/UXPROD-3148 <br />
https://issues.folio.org/browse/FOLIO-3258 <br />
https://issues.folio.org/browse/UXPROD-515 <br />

## Compilation

```
   mvn clean install
```

## Docker Build

```
   docker build -t mod-translations:1.0.0-SNAPSHOT .
```

## Installing a module

Declare a module to Okapi:

```
curl -w '\n' -X POST -D -   \
   -H "Content-type: application/json"   \
   -d @target/ModuleDescriptor.json \
   http://localhost:9130/_/proxy/modules
```

## Deploying a module

Deploy it via Okapi discovery:

```
curl -w '\n' -D - -s \
  -X POST \
  -H "Content-type: application/json" \
  -d @target/DeploymentDescriptor.json  \
  http://localhost:9130/_/discovery/modules
```

Then we need to enable the module for the tenant:

```
curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @target/TenantModuleDescriptor.json \
    http://localhost:9130/_/proxy/tenants/kware_test/modules
```
