import React, { useEffect, useState } from "react";
import "./FileDetails.css";
import { Alert, Collapse, Spin, Table } from "antd";

const FileDetails = ({ taskId, serverAddress, filename }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const getTableColumns = (headers) => {
    return headers.map((header, index) => ({
      title: header,
      dataIndex: index,
      key: index,
    }));
  };

  const getTableRows = (rows) => {
    return rows.map((row, index) => ({
      ...row,
      key: index,
    }));
  };

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

  const canonicalColumns = getTableColumns(data.canonicalPairs.headers);
  const nonCanonicalColumns = getTableColumns(data.nonCanonicalPairs.headers);
  const stackingColumns = getTableColumns(data.stackings.headers);
  const canonicalRows = getTableRows(data.canonicalPairs.rows);
  const nonCanonicalRows = getTableRows(data.nonCanonicalPairs.rows);
  const stackingRows = getTableRows(data.stackings.rows);

  const details = [
    {
      key: "1",
      label: "Secondary structure",
      children: <pre>{data.dotBracket}</pre>,
    },
    {
      key: "2",
      label: "Canonical base pairs",
      children: <Table dataSource={canonicalRows} columns={canonicalColumns} />,
    },
    {
      key: "3",
      label: "Non-canonical base pairs",
      children: <Table dataSource={nonCanonicalRows} columns={nonCanonicalColumns} />,
    },
    {
      key: "4",
      label: "Stacking interactions",
      children: <Table dataSource={stackingRows} columns={stackingColumns} />,
    },
  ];

  return <Collapse items={details} defaultActiveKey={"1"} />;
};

export default FileDetails;
