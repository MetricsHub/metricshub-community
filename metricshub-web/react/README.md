# MetricsHub Web - React Frontend

This directory contains the React frontend application for MetricsHub.

## Development

### Prerequisites

- Node.js v22.19.0 (installed automatically via Maven)
- npm (comes with Node.js)

### Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run build:watch` - Build in watch mode
- `npm run lint` - Run ESLint
- `npm run format` - Format code with Prettier
- `npm run format:check` - Check code formatting
- `npm run preview` - Preview production build

## Testing

### Testing Framework

This project uses the following testing tools:

- **Vitest** - Fast test runner with native Vite integration
- **React Testing Library** - Component testing utilities
- **happy-dom** - Fast and lightweight DOM implementation for Node.js
- **@testing-library/jest-dom** - Custom DOM matchers
- **@testing-library/user-event** - User interaction simulation
- **MSW** - API mocking (optional, for integration tests)

### Running Tests

#### Run tests in watch mode (development)

```bash
npm test
```

#### Run tests once (CI/build)

```bash
npm run test:run
```

#### Run tests with UI

```bash
npm run test:ui
```

#### Run tests with coverage

```bash
npm run test:coverage
```

### Test File Organization

Tests are co-located with their source files:

```
src/
  components/
    common/
      QuestionDialog.jsx
      QuestionDialog.test.jsx  ← Test file
  hooks/
    use-auth.js
    use-auth.test.jsx  ← Test file
  ...
```

### Writing Tests

#### Component Tests

Test components using React Testing Library:

```jsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MyComponent from "./MyComponent";

describe("MyComponent", () => {
	it("renders correctly", () => {
		render(<MyComponent title="Test" />);
		expect(screen.getByText("Test")).toBeInTheDocument();
	});

	it("handles user interactions", async () => {
		const user = userEvent.setup();
		render(<MyComponent />);

		const button = screen.getByRole("button");
		await user.click(button);

		expect(screen.getByText("Clicked")).toBeInTheDocument();
	});
});
```

#### Hook Tests

Test custom hooks using `renderHook`:

```jsx
import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { useMyHook } from "./use-my-hook";

describe("useMyHook", () => {
	it("returns expected values", () => {
		const { result } = renderHook(() => useMyHook());
		expect(result.current.value).toBeDefined();
	});
});
```

#### Redux Slice Tests

Test Redux slices by creating a test store:

```jsx
import { describe, it, expect, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { mySliceReducer, myAction } from "./mySlice";

describe("mySlice", () => {
	let store;

	beforeEach(() => {
		store = configureStore({
			reducer: { mySlice: mySliceReducer },
		});
	});

	it("handles action correctly", () => {
		store.dispatch(myAction({ data: "test" }));
		const state = store.getState().mySlice;
		expect(state.data).toBe("test");
	});
});
```

#### Testing with Providers

Use test utilities from `src/test/utils.jsx` for components that need providers:

```jsx
import { renderWithRedux } from "../test/utils";

// Component needs Redux
const { store } = renderWithRedux(<MyComponent />);

// Component needs Router
import { renderWithRouter } from "../test/utils";
renderWithRouter(<MyComponent />);

// Component needs both
import { renderWithReduxAndRouter } from "../test/utils";
renderWithReduxAndRouter(<MyComponent />);

// Component needs Auth
import { renderWithAuth } from "../test/utils";
renderWithAuth(<MyComponent />);

// Component needs everything
import { renderWithAllProviders } from "../test/utils";
renderWithAllProviders(<MyComponent />);
```

### Testing Best Practices

1. **Test user behavior, not implementation**
   - Focus on what users see and do
   - Avoid testing internal state or implementation details

2. **Use semantic queries**
   - Prefer `getByRole`, `getByLabelText`, `getByText`
   - Avoid `getByTestId` unless necessary

3. **Test accessibility**
   - Use `getByRole` to ensure proper ARIA roles
   - Test keyboard navigation where applicable

4. **Mock external dependencies**
   - Mock API calls using MSW or vi.mock
   - Mock browser APIs when needed

5. **Keep tests isolated**
   - Each test should be independent
   - Use `beforeEach` to set up clean state

6. **Test error states**
   - Test both success and failure scenarios
   - Test loading states

### Example Test Files

The following example tests are available as references:

- **Component**: `src/components/common/QuestionDialog.test.jsx`
- **Hook**: `src/hooks/use-auth.test.jsx`
- **HOC**: `src/hocs/with-auth-guard.test.jsx`
- **Guard**: `src/guards/auth-guard.test.jsx`
- **Redux Slice**: `src/store/slices/applicationStatusSlice.test.js`

### Test Coverage

Aim for meaningful test coverage:

- **Components**: Test user interactions and rendering
- **Hooks**: Test return values and side effects
- **Redux**: Test actions, reducers, and selectors
- **Utilities**: Test edge cases and error handling

### Continuous Integration

Tests are automatically run during the Maven build process:

```bash
mvn clean verify
```

The test phase runs after linting and before building. All tests must pass for the build to succeed.

### Debugging Tests

1. **Use Vitest UI**

   ```bash
   npm run test:ui
   ```

   Opens a visual interface to run and debug tests

2. **Use `screen.debug()`**

   ```jsx
   render(<MyComponent />);
   screen.debug(); // Prints the rendered HTML
   ```

3. **Use `--reporter=verbose`**
   ```bash
   npm test -- --reporter=verbose
   ```

### Common Issues

#### Tests failing due to missing providers

- Use the test utilities from `src/test/utils.jsx`
- Ensure all required providers are wrapped

#### Async operations not completing

- Use `waitFor` from `@testing-library/react`
- Use `findBy*` queries for async elements

#### Mocking not working

- Ensure `vi.mock()` is called at the top level
- Check that mocks are reset in `beforeEach`

#### Vitest command not found

- Ensure dependencies are installed: `npm install`
- Try using `npx vitest` instead of `vitest`
- Verify Node.js version matches project requirements (v22.19.0)
