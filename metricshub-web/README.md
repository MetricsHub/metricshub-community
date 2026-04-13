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

## Testing

The React application uses [Vitest](https://vitest.dev/) as the test runner with [React Testing Library](https://testing-library.com/react) for component testing.

### Testing Framework

- **Vitest** - Fast test runner with native Vite integration
- **React Testing Library** - Component testing utilities
- **happy-dom** - Fast DOM implementation for Node.js
- **@testing-library/jest-dom** - Custom DOM matchers
- **@testing-library/user-event** - User interaction simulation

### Running Tests

From the `react` directory:

```bash
# Run tests in watch mode
npm run test

# Run tests with UI
npm run test:ui

# Run tests once (for CI)
npm run test:run

# Run tests with coverage
npm run test:coverage
```

Tests are automatically run during the Maven build process (`mvn verify`) in the `test` phase.

### Test File Organization

Test files are co-located with their source files:

- `QuestionDialog.test.jsx` (tests `QuestionDialog.jsx`)
- `use-auth.test.js` (tests `use-auth.js`)
- `AuthGuard.test.jsx` (tests `AuthGuard.jsx`)

### Test Utilities

Test utilities are located in `src/test/`:

- `setup.js` - Test setup file that imports `@testing-library/jest-dom`
- `test-utils.jsx` - Utility functions for rendering components with providers:
  - `renderWithRedux` - Render with Redux Provider
  - `renderWithRouter` - Render with React Router
  - `renderWithReduxAndRouter` - Render with Redux and Router
  - `renderWithAuth` - Render with Auth Provider
  - `renderWithAllProviders` - Render with all providers
  - `createTestStore` - Create test Redux store

### Writing Tests

Example test using test utilities:

```jsx
import { describe, it, expect } from "vitest";
import { renderWithAllProviders } from "../test/test-utils";
import MyComponent from "./MyComponent";

describe("MyComponent", () => {
	it("renders correctly", () => {
		const { getByText } = renderWithAllProviders(<MyComponent />);
		expect(getByText("Hello")).toBeInTheDocument();
	});
});
```

### Testing Best Practices

1. **Test user behavior, not implementation details** - Focus on what users see and interact with
2. **Use accessible queries** - Prefer `getByRole`, `getByLabelText`, etc.
3. **Keep tests simple** - One test should verify one behavior
4. **Use descriptive test names** - Test names should clearly describe what is being tested
5. **Mock external dependencies** - Use MSW (Mock Service Worker) for API mocking if needed
6. **Test edge cases** - Include tests for error states, empty states, and boundary conditions

### Test Coverage

Run coverage reports to identify untested code:

```bash
npm run test:coverage
```

Coverage reports are generated in the `coverage` directory.
