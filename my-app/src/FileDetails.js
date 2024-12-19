import React, { useEffect, useState } from "react";
import { Alert, Collapse, Spin, Table } from "antd";
import { getTableColumns, getTableRows } from "./utils/tableUtils";

const FileDetails = ({ taskId, serverAddress, filename }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch(`${serverAddress}/${taskId}/result/${filename}`);
        if (!response.ok) {
          throw new Error("Failed to fetch file details");
        }
        const result = await response.json();
        setData(result);
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [taskId, serverAddress, filename]);

  if (loading) {
    return <Spin />;
  }
  if (error) {
    return <Alert type={"error"} message={error.message} />;
  }
  if (!data) {
    return <Alert type={"warning"} message={"No details available"} />;
  }

  const canonicalColumns = getTableColumns(data.canonicalPairs.headers, data.canonicalPairs.rows, 1);
  const nonCanonicalColumns = getTableColumns(data.nonCanonicalPairs.headers, data.nonCanonicalPairs.rows, 1);
  const stackingColumns = getTableColumns(data.stackings.headers, data.stackings.rows, 1);
  const canonicalRows = getTableRows(data.canonicalPairs.rows);
  const nonCanonicalRows = getTableRows(data.nonCanonicalPairs.rows);
  const stackingRows = getTableRows(data.stackings.rows);

  const details = [
    {
      key: filename + "-2d-structure",
      label: "Secondary structure",
      children: <pre>{data.dotBracket}</pre>,
    },
    {
      key: filename + "-base-pairs",
      label: "Canonical base pairs",
      children: <Table dataSource={canonicalRows} columns={canonicalColumns} />,
    },
    {
      key: filename + "-non-canonical-pairs",
      label: "Non-canonical base pairs",
      children: <Table dataSource={nonCanonicalRows} columns={nonCanonicalColumns} />,
    },
    {
      key: filename + "-stacking-interactions",
      label: "Stacking interactions",
      children: <Table dataSource={stackingRows} columns={stackingColumns} />,
    },
  ];

  return <Collapse items={details} defaultActiveKey={"1"} />;
};

export default FileDetails;
