import React, { useEffect, useState } from "react";
import { Alert, Col, Collapse, Spin, Table, Row, Tooltip } from "antd";
import { getTableColumns, getTableRows } from "./utils/tableUtils";
import DownloadButton from "./DownloadButton";
import SvgImg from "./SvgImg"; // Import SvgImg
import { QuestionCircleOutlined } from "@ant-design/icons";
const FileDetails = ({
  taskId,
  serverAddress,
  filename,
  fileCount,
  // rchieSvgName prop is not used here as svgName is constructed directly
}) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch(
          `${serverAddress}/${taskId}/result/${filename}`
        );
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

  const canonicalColumns = getTableColumns(
    data.canonicalPairs.headers,
    data.canonicalPairs.rows,
    fileCount
  );
  const nonCanonicalColumns = getTableColumns(
    data.nonCanonicalPairs.headers,
    data.nonCanonicalPairs.rows,
    fileCount
  );
  const stackingColumns = getTableColumns(
    data.stackings.headers,
    data.stackings.rows,
    fileCount
  );
  const canonicalRows = getTableRows(data.canonicalPairs.rows);
  const nonCanonicalRows = getTableRows(data.nonCanonicalPairs.rows);
  const stackingRows = getTableRows(data.stackings.rows);

  const details = [
    {
      key: filename + "-2d-structure",
      label: (
        <Tooltip
          title={
            <div>
              <p style={{ marginBottom: "8px" }}>
                Interaction colors reflect confidence (probability). The{" "}
                <a
                  href="https://matplotlib.org/cmocean/#algae"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Algae colormap
                </a>{" "}
                is used, with red color for forbidden interactions:
              </p>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  marginBottom: "5px",
                }}
              >
                <div
                  style={{
                    width: "15px",
                    height: "15px",
                    backgroundColor: "#d7f9d0",
                    marginRight: "8px",
                    border: "1px solid #ccc",
                  }}
                ></div>
                <span>Low (e.g., 0%)</span>
              </div>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  marginBottom: "5px",
                }}
              >
                <div
                  style={{
                    width: "15px",
                    height: "15px",
                    backgroundColor: "#129450",
                    marginRight: "8px",
                    border: "1px solid #ccc",
                  }}
                ></div>
                <span>Medium (e.g., 50%)</span>
              </div>
              <div style={{ display: "flex", alignItems: "center" }}>
                <div
                  style={{
                    width: "15px",
                    height: "15px",
                    backgroundColor: "#122414",
                    marginRight: "8px",
                    border: "1px solid #ccc",
                  }}
                ></div>
                <span>High (e.g., 100%)</span>
              </div>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  marginTop: "5px",
                }}
              >
                <div
                  style={{
                    width: "15px",
                    height: "15px",
                    backgroundColor: "#FF0000",
                    marginRight: "8px",
                    border: "1px solid #ccc",
                  }}
                ></div>
                <span>Forbidden interactions</span>
              </div>
            </div>
          }
        >
          Secondary structure <QuestionCircleOutlined />
        </Tooltip>
      ),
      children: (
        <div>
          <Row gutter={16}>
            <Col xs={24} md={{ flex: '0 1 auto' }}>
              <div style={{ maxWidth: "100%", overflowX: "auto" }}>
                <SvgImg
                  key="svg-varna-model"
                  serverAddress={serverAddress}
                  taskId={taskId}
                  svgName={filename} // VARNA SVG for the model
                />
              </div>
            </Col>
            <Col xs={24} md={{ flex: '1 1 auto' }}>
              <div style={{ maxWidth: "100%", overflowX: "auto" }}>
                <SvgImg
                  key="svg-rchie-model"
                  serverAddress={serverAddress}
                  taskId={taskId}
                  svgName={`rchie-${filename}`} // R-Chie SVG for the model
                />
              </div>
            </Col>
          </Row>
          <pre
            key="dotbracket"
            style={{
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
              marginTop: "16px",
            }}
          >
            {data.dotBracket}
          </pre>
        </div>
      ),
    },
    {
      key: filename + "-base-pairs",
      label: "Canonical base pairs",
      children: (
        <>
          <Row
            align="right"
            style={{
              display: "flex",
              justifyContent: "space-between",
              width: "100%",
            }}
          >
            <div></div>
            <Tooltip
              title={
                <div>
                  Constraint match indicates whether a base pair complies with
                  the secondary structure constraints defined at input:
                  <br />
                  '+': means the base pair was explicitly required in the input
                  constraints,
                  <br />
                  '-': indicates that at least one nucleotide in the pair was
                  specified as unpaired
                  <br />
                  'n/a': means no constraint was provided for this base pair.
                </div>
              }
            >
              <QuestionCircleOutlined />
            </Tooltip>
          </Row>
          <Table dataSource={canonicalRows} columns={canonicalColumns} />
          <DownloadButton
            dataSource={canonicalRows}
            columns={canonicalColumns}
            fileName={`${filename}_canonical_base_pairs.txt`}
          />
        </>
      ),
    },
    {
      key: filename + "-non-canonical-pairs",
      label: "Non-canonical base pairs",
      children: (
        <>
          <Row
            align="right"
            style={{
              display: "flex",
              justifyContent: "space-between",
              width: "100%",
            }}
          >
            <div></div>
            <Tooltip
              title={
                <div>
                  Constraint match indicates whether a base pair complies with
                  the secondary structure constraints defined at input:
                  <br />
                  '+': means the base pair was explicitly required in the input
                  constraints,
                  <br />
                  '-': indicates that at least one nucleotide in the pair was
                  specified as unpaired
                  <br />
                  'n/a': means no constraint was provided for this base pair.
                </div>
              }
            >
              <QuestionCircleOutlined />
            </Tooltip>
          </Row>
          <Table dataSource={nonCanonicalRows} columns={nonCanonicalColumns} />
          <DownloadButton
            dataSource={nonCanonicalRows}
            columns={nonCanonicalColumns}
            fileName={`${filename}_non-canonical_base_pairs.txt`}
          />
        </>
      ),
    },
    {
      key: filename + "-stacking-interactions",
      label: "Stacking interactions",
      children: (
        <>
          <Table dataSource={stackingRows} columns={stackingColumns} />
          <DownloadButton
            dataSource={stackingRows}
            columns={stackingColumns}
            fileName={`${filename}_stacking_interactions.txt`}
          />
        </>
      ),
    },
  ];

  return <Collapse items={details} defaultActiveKey={"1"} />;
};

export default FileDetails;
