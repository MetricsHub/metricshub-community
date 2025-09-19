// Higher Order Component to wrap a component with a specified layout
// eslint-disable-next-line no-unused-vars
export const withLayout = (Layout) => (Component) => (props) => {
	return (
		<Layout>
			<Component {...props} />
		</Layout>
	);
};
