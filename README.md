# mod-translations
FOLIO Translations Management Module

Copyright (C) 2020 The KnowledgeWare Technologies Devolopers Team

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

* [Introduction](#introduction)
* [Compilation](#compilation)
* [Docker Build](#docker-build)
* [Installing a module](#installing-a-module)
* [Deploying a module](#deploying-a-module)

## Introduction

install any backend module to okapi

## Compilation

```
   mvn clean install
```

See that it says "BUILD SUCCESS" near the end.

## Docker Build

Build the docker container running from root folder:

```
   docker build -t mod-translations:1.0.0-SNAPSHOT .
```

## Installing a module

First of all you need a running Okapi instance.
(Note that [specifying](../README.md#setting-things-up) an explicit 'okapiurl' might be needed.)

```
   cd ../okapi
   java -jar okapi-core/target/okapi-core-fat.jar dev [for development mode]
```

Declare a module to Okapi:

```
curl -w '\n' -X POST -D -   \
   -H "Content-type: application/json"   \
   -d @target/ModuleDescriptor.json \
   http://localhost:9130/_/proxy/modules
```

That ModuleDescriptor tells Okapi what the module is called, what services it
provides, and how to deploy it.

## Deploying a module

Next we need to deploy the module. There is a deployment descriptor in
`target/DeploymentDescriptor.json`. It tells Okapi to start the module on 'localhost'.

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
