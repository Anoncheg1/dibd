[![Build Status](https://travis-ci.org/Anoncheg1/dibd.svg?branch=master)](https://travis-ci.org/Anoncheg1/dibd)
[![Coverty Scan](https://scan.coverity.com/projects/11880/badge.svg)](https://scan.coverity.com/projects/anoncheg1-dibd)

dibd
======

**dibd** is a decentralized ImageBoard daemon written in Java. It can use various 
backend types, currently supported are MySQL and PostgreSQL (CouchDB in development).

Part of main project [diboard](https://github.com/Anoncheg1/diboard)


![threads vision diagram](https://github.com/Anoncheg1/diboard/blob/master/dibd.png "threads vision diagram")

ER-diagram
----
![ER diagram](https://github.com/Anoncheg1/diboard/blob/master/Database.png "ER diagram")

Requirements
------------

The requirements for building and running dibd are:

* Apache Maven
* Java 8 JDK (or newer)
* PostgreSQL/MySQL installation

Build
-----

dibd uses Apache Maven for building and dependency managing.
Use the following command to build and package dibd:

    $ mvn clean compile package


To start dibd on port 9119:

    $ mvn exec:java -Dexec.mainClass="dibd.App" -Dexec.args="-p 9119"

You may want dibd to listen on the default NNTP port (119) without running as
root user. This can be achieved by redirecting all TCP connections on port 119
to a higher port where dibd can listen as unprivileged user:

 	# iptables -t nat -A PREROUTING -p tcp --dport 119 -j REDIRECT --to-port 9119

Setup
-----

* Create a database in your database system, e.g. named like 'dibd' and give it a
  dedicated database user
* Create the necessary table structure using the dibd.sql file [here](https://github.com/Anoncheg1/diboard)
* Customize the settings within the dibd.conf file
* Start dibd as described above

Used libraries with licenses(dibd):
----------
- javax.mail GPL2
- org.im4java LGPL
- Java JDBC 4.2 (JRE 8+) driver for PostgreSQL database. PostgreSQL License

Contribution
-------------

dibd is Free Software licensed under the terms of the GPLv3.

Please report any issues at https://github.com/Anoncheg1/dibd/ .
