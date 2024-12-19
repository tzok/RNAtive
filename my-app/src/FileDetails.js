import React, { useEffect, useState } from "react";
import "./FileDetails.css";
import { Alert, Collapse, Spin, Table } from "antd";

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

  const canonicalColumns = data.canonicalPairs.headers.map((header, index) => ({
    title: header,
    dataIndex: index,
    key: index,
  }));
  const nonCanonicalColumns = data.nonCanonicalPairs.headers.map((header, index) => ({
    title: header,
    dataIndex: index,
    key: index,
  }));
  const stackingColumns = data.stackings.headers.map((header, index) => ({
    title: header,
    dataIndex: index,
    key: index,
  }));
  const details = [
    {
      key: "1",
      label: "Secondary structure",
      children: <pre>{data.dotBracket}</pre>,
    },
    {
      key: "2",
      label: "Canonical base pairs",
      children: <Table dataSource={data.canonicalPairs.rows} columns={canonicalColumns} />,
    },
    {
      key: "3",
      label: "Non-canonical base pairs",
      children: <Table dataSource={data.nonCanonicalPairs.rows} columns={nonCanonicalColumns} />,
    },
    {
      key: "4",
      label: "Stacking interactions",
      children: <Table dataSource={data.stackings.rows} columns={stackingColumns} />,
    },
  ];

  return <Collapse items={details} defaultActiveKey={"1"} />;
};

export default FileDetails;
