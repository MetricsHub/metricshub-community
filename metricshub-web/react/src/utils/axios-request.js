import axios from "axios";

const instance = axios.create({
	baseURL: `http://localhost:31888`,
});

export const httpRequest = async ({ ...options }) => {
	return await instance(options);
};
