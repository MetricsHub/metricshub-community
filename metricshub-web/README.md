# MetricsHub Web Application

This directory contains the source code for the MetricsHub web application, which is built using React. The web application provides a user interface for interacting with MetricsHub features and functionalities.

The web application is built using node.js and is packaged into a zip file using Maven.

[Vite](https://vite.dev/) is used as the build tool for the React application.

## Prerequisites

- Have [Maven 3.x properly installed and configured](https://maven.apache.org/download.cgi).
- Ensure that you have the required Node.js version specified in the `pom.xml` file.

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

## File Naming Conventions

This project follows consistent naming conventions for maintainability and clarity:

### File Extensions

**Important**: All files that contain JSX must use the `.jsx` extension. Files without JSX use `.js`.

### PascalCase for React Components

All files that export React components used in JSX should use **PascalCase** and `.jsx` extension:

- **Components**: `QuestionDialog.jsx`, `Navbar.jsx`, `ErrorBoundary.jsx`
- **Pages**: `LoginPage.jsx`, `ConfigurationPage.jsx`, `ExplorerPage.jsx`
- **Guards**: `AuthGuard.jsx`
- **Layouts**: `AuthLayout.jsx`

**Examples:**

```
components/common/QuestionDialog.jsx
components/navbar/Navbar.jsx
pages/LoginPage.jsx
guards/AuthGuard.jsx
layouts/auth/AuthLayout.jsx
```

### kebab-case for Everything Else

All non-component files use **kebab-case**:

- **Hooks**: `use-auth.js`, `use-mounted.js`, `use-snackbar.js` 
- **Services**: `backup-service.js`, `download-service.js`
- **Utils**: `backup-names.js`, `yaml-lint-utils.js`, `axios-request.js`
- **Redux Slices**: `config-slice.js`, `application-status-slice.js`
- **Redux Thunks**: `config-thunks.js`, `application-status-thunks.js`

**Examples:**

```
hooks/use-auth.js
services/backup-service.js
utils/backup-names.js
store/slices/config-slice.js
store/thunks/config-thunks.js
```

### Test Files

Test files follow the same naming as their source files with `.test.jsx` or `.test.js` suffix:

- `QuestionDialog.test.jsx` (tests `QuestionDialog.jsx`)
- `use-auth.test.js` (tests `use-auth.js`)
- `AuthGuard.test.jsx` (tests `AuthGuard.jsx`)

**Rule**: If a file contains JSX (React components, contexts, HOCs), it must use `.jsx`. Otherwise, use `.js`.

## Code Quality

### Formatting

Code is automatically formatted with Prettier. Run before committing:

```bash
npm run format
```

### Linting

ESLint is configured to enforce code quality. Run before committing:

```bash
npm run lint
```
