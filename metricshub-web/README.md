# MetricsHub Web Application

This directory contains the source code for the MetricsHub web application, which is built using React. The web application provides a user interface for interacting with MetricsHub features and functionalities.

The web application is built using node.js and is packaged into a JAR file using Maven for deployment. 

[Vite](https://vite.dev/) is used as the build tool for the React application.

## Prerequisites

* Have [Maven 3.x properly installed and configured](https://maven.apache.org/download.cgi).
* Ensure that you have the required Node.js version specified in the `pom.xml` file.

## Building the Project

1. Navigate to the `metricshub-web` directory:
   ```bash
   cd metricshub-web
   ```
2. Run the following Maven command to build the project:
   ```bash
   mvn clean package
   ```
   This command will install the necessary Node.js version, build the React application, and package it into a zip file under the `target` directory.
