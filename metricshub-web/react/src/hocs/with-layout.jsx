// src/hocs/with-layout.js
// eslint-disable-next-line no-unused-vars
export const withLayout = (Layout) => (Component) => (props) => {
	return (
		<Layout>
			<Component {...props} />
		</Layout>
	);
};
