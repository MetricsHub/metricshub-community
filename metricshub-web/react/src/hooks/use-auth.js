import { useContext } from "react";
import { AuthContext } from "../contexts/JwtContext";

/**
 * Custom hook to access authentication context
 *
 * @returns Auth context value
 */
export const useAuth = () => useContext(AuthContext);
