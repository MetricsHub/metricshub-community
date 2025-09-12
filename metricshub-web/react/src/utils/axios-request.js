import axios from "axios";

const instance = axios.create({});

export const httpRequest = async ({ ...options }) => {
	return await instance(options);
};
