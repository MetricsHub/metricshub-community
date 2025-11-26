import * as React from "react";
import { Navigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { selectLastVisitedPath } from "../../store/slices/explorer-slice";
import { paths } from "../../paths";

const ExplorerRedirect = () => {
    const lastVisitedPath = useSelector(selectLastVisitedPath);
    const target = lastVisitedPath || paths.explorerWelcome;
    return <Navigate to={target} replace />;
};

export default ExplorerRedirect;
