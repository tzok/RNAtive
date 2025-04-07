import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Slider,
  Spin,
  Switch,
  Table,
  Tabs,
  Tooltip,
  Upload,
} from "antd";
import { getTableColumns, getTableRows } from "./utils/tableUtils";
import { QuestionCircleOutlined, UploadOutlined } from "@ant-design/icons";

import SvgImg from "./SvgImg";
import FileDetails from "./FileDetails";
import * as configs from "./config";
import DownloadButton from "./DownloadButton";
import "./customTextInput.css";

const { TextArea } = Input;

const consensusOptions = [
  {
    value: "ALL",
    label: "All interactions",
  },
  {
    value: "CANONICAL",
    label: "Canonical base pairs",
  },
  {
    value: "NON_CANONICAL",
    label: "Non-canonical base pairs",
  },
  {
    value: "STACKING",
    label: "Stacking interactions",
  },
];
const analyzerOptions = [
  {
    value: "RNAPOLIS",
    label: "RNApolis Annotator",
  },
  {
    value: "BPNET",
    label: "bpnet",
  },
  {
    value: "FR3D",
    label: "FR3D",
  },
  {
    value: "MCANNOTATE",
    label: "MC-Annotate",
  },
  {
    value: "RNAVIEW",
    label: "RNAView",
  },
  {
    value: "BARNABA",
    label: "barnaba",
  },
];
const visualizerOptions = [
  {
    value: "VARNA",
    label: "VARNA",
  },
  {
    value: "RNAPUZZLER",
    label: "RNApuzzler",
  },
  {
    value: "PSEUDOVIEWER",
    label: "PseudoViewer",
  },
  {
    value: "RCHIE",
    label: "R-Chie",
  },
];
const molProbityOptions = [
  {
    value: "ALL",
    label: "No filtering - accept all models regardless of quality",
  },
  {
    value: "GOOD_AND_CAUTION",
    label: "Medium - accept models with good or acceptable quality",
  },
  {
    value: "GOOD_ONLY",
    label: "Strict - only accept high-quality models",
  },
];

function Home() {
  const serverAddress = configs.default.SERVER_ADDRESS;

  const { id } = useParams();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [removalReasons, setRemovalReasons] = useState(null);
  const [serverError, setServerError] = useState(null);
  const [taskIdComplete, setTaskIdComplete] = useState(null);
  const [fileList, setFileList] = useState([]);
  const [consensusMode, setConsensusMode] = useState(consensusOptions[0].value);
  const [analyzer, setAnalyzer] = useState(analyzerOptions[0].value);
  const [visualizer, setVisualizer] = useState(visualizerOptions[0].value);
  const [molProbityFilter, setMolProbityFilter] = useState(
    molProbityOptions[0].value
  );
  const [isFuzzy, setIsFuzzy] = useState(true);
  const [confidenceLevel, setConfidenceLevel] = useState(fileList.length);
  const [dotBracket, setDotBracket] = useState(null);

  // Ensure confidenceLevel is within valid range when fileList updates
  useEffect(() => {
    setConfidenceLevel((prev) => Math.min(prev, fileList.length) || 2);
  }, [fileList.length]);
  const handleSliderChange = (value) => {
    if (value <= fileList.length) {
      setConfidenceLevel(value);
    }
  };
  const beforeUpload = (file) => {
    file.url = URL.createObjectURL(file);
    file.obj = new File([file], file.name, { type: file.type });
    return false;
  };

  const handleDownload = (file) => {
    const link = document.createElement("a");
    link.href = file.url;
    link.download = file.name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleFileListChange = ({ fileList: newFileList }) => {
    // Clean up removed file URLs
    fileList.forEach((file) => {
      if (!newFileList.find((f) => f.uid === file.uid)) {
        URL.revokeObjectURL(file.url);
      }
    });
    setFileList(newFileList);
  };

  // Clean up on unmount
  useEffect(() => {
    return () => {
      fileList.forEach((file) => {
        URL.revokeObjectURL(file.url);
      });
    };
  }, []);

  // Perform actions with the ID if necessary (e.g., fetch data based on the ID)
  useEffect(() => {
    if (id) {
      handleSendData(id);
    }
  }, [id]);

  const handleDotBracket = (event) => {
    setDotBracket(event.target.value);
  };

  const handleSendData = async (taskIdArg = "") => {
    const POLL_INTERVAL = 3000; // 3 seconds
    let taskId = taskIdArg || null;

    try {
      // Step 1: Create and send the payload if taskId is not provided
      if (!taskId) {
        setIsLoading(true);
        setResponse(null);
        setServerError(null);
        setRemovalReasons(null);

        // Prepare the files data
        const files = await Promise.all(
          fileList.map(async (file) => ({
            name: file.obj.name,
            content: await file.obj.text(), // Reads file content as text
          }))
        );

        // Prepare the payload
        const payload = {};
        payload.files = files;
        payload.consensusMode = consensusMode;
        payload.analyzer = analyzer;
        payload.visualizationTool = visualizer;
        if (isFuzzy) {
          payload.confidenceLevel = null;
        } else {
          payload.confidenceLevel = confidenceLevel;
        }
        payload.molProbityFilter = molProbityFilter;
        if (dotBracket) {
          payload.dotBracket = dotBracket;
        }

        const response = await fetch(serverAddress, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(payload),
        });

        if (!response.ok) {
          throw new Error(`Server responded with status ${response.status}`);
        }

        const { taskId: newTaskId } = await response.json();
        taskId = newTaskId;
        navigate(`/${newTaskId}`, { replace: true }); //navigate to new taskid
      }

      // Step 2: Poll for task status
      const status = await pollTaskStatus(taskId, POLL_INTERVAL, setResponse);

      // Step 3: Handle the result based on task status
      if (status === "COMPLETED") {
        await fetchTaskResult(taskId);
      }
    } catch (error) {
      console.error("Error in submission process:", error.message);
      if (taskId) {
        console.error(`Task ID: ${taskId}`);
      }
    }
  };

  const pollTaskStatus = async (taskId, pollInterval, setResponse) => {
    while (true) {
      const statusResponse = await fetch(`${serverAddress}/${taskId}/status`, {
        method: "GET",
      });

      if (!statusResponse.ok) {
        throw new Error(
          `Failed to get status. Status: ${statusResponse.status}`
        );
      }

      const statusData = await statusResponse.json();
      const { status, message, removalReasons } = statusData;
      setRemovalReasons(removalReasons);

      if (status === "FAILED") {
        console.error("FAILED:", message);
        console.error("FAILED REASONS:", removalReasons);
        setResponse({
          error: message || "Task failed with no additional message.",
        });
        setServerError(message);
        setIsLoading(false);
        return;
      }

      if (status === "COMPLETED") {
        await fetchTaskResult(taskId, setResponse);
        return;
      }

      // If still processing, wait and try again
      await new Promise((resolve) => setTimeout(resolve, pollInterval));
    }
  };

  const fetchTaskResult = async (taskId, setResponse) => {
    try {
      const resultResponse = await fetch(`${serverAddress}/${taskId}/result`, {
        method: "GET",
      });

      if (!resultResponse.ok) {
        throw new Error(
          `Failed to get result. Status: ${resultResponse.status}`
        );
      }

      const resultData = await resultResponse.json();

      // Set the response state to trigger UI update
      setResponse(resultData);
    } catch (error) {
      console.error("Error fetching task result:", error.message);
      setResponse({ error: error.message });
    } finally {
      setIsLoading(false);
      setTaskIdComplete(taskId);
    }
  };

  const loadRNAPuzzlesExample = async () => {
    try {
      // List of example files
      const exampleFiles = [
        "1_bujnicki_1_rpr.pdb",
        "1_bujnicki_2_rpr.pdb",
        "1_bujnicki_3_rpr.pdb",
        "1_bujnicki_4_rpr.pdb",
        "1_bujnicki_5_rpr.pdb",
        "1_chen_1_rpr.pdb",
        "1_das_1_rpr.pdb",
        "1_das_2_rpr.pdb",
        "1_das_3_rpr.pdb",
        "1_das_4_rpr.pdb",
        "1_das_5_rpr.pdb",
        "1_dokholyan_1_rpr.pdb",
        "1_major_1_rpr.pdb",
      ];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        })
      );

      // Convert fetched files into compatible format
      const newFiles = files.map((file, index) => ({
        uid: Date.now() + index,
        name: file.name,
        status: "done",
        url: URL.createObjectURL(file),
        originFileObj: file,
        obj: file,
      }));

      setFileList(newFiles); // Replace current files with examples
      setDotBracket(
        ">strand_A\n" +
          "CCGCCGCGCCAUGCCUGUGGCGG\n" +
          "((((((((.((((...(((((((\n" +
          ">strand_B\n" +
          "CCGCCGCGCCAUGCCUGUGGCGG\n" +
          ")))))))..)))).).)))))))"
      );
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  const loadDecoyExamples = async () => {
    try {
      // List of example files
      const exampleFiles = [
        "1a9nR_M1.pdb",
        "1a9nR_M2.pdb",
        "1a9nR_M3.pdb",
        "1a9nR_M4.pdb",
        "1a9nR_M5.pdb",
        "1a9nR_M6.pdb",
        "1a9nR_M7.pdb",
        "1a9nR_M8.pdb",
        "1a9nR_M9.pdb",
      ];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        })
      );

      // Convert fetched files into compatible format
      const newFiles = files.map((file, index) => ({
        uid: Date.now() + index,
        name: file.name,
        status: "done",
        url: URL.createObjectURL(file),
        originFileObj: file,
        obj: file,
      }));

      setFileList(newFiles); // Replace current files with examples
      setDotBracket(
        ">strand_R\n" +
          "CCUGGUAUUGCAGUACCUCCAGGU\n" +
          "(((((.............)))))."
      );
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  const handleRemovalReasons = () => {
    let columns = [
      {
        title: "Filename",
        dataIndex: "filename",
        key: "filename",
      },
      {
        title: "Removal reason",
        dataIndex: "reason",
        key: "reason",
      },
    ];
    let rows = [];
    let i = 0;
    Object.entries(removalReasons).map(([filename, reasons]) => {
      reasons.map((reason) => {
        rows.push({ key: i, filename: filename, reason: reason });
        i++;
      });
    });
    return [columns, rows];
  };

  const renderContent = () => {
    if (isLoading) {
      if (id) {
        return (
          <Row justify={"center"}>
            <Spin tip={"Waiting for completion of task: " + id} size="large">
              <div style={{ width: "100vw", padding: 24 }} />
            </Spin>
          </Row>
        );
      }
      return (
        <Row justify={"center"}>
          <Spin tip={"Sending data to the server"} size="large">
            <div style={{ width: "100vw", padding: 24 }} />
          </Spin>
        </Row>
      );
    }

    if (serverError) {
      if (removalReasons && Object.keys(removalReasons).length > 0) {
        const [columns, rows] = handleRemovalReasons();
        return (
          <Row justify={"center"} style={{ marginBottom: 24 }}>
            <Col span={20}>
              <Alert
                type="error"
                message="Error"
                description={
                  <div>
                    <p>{serverError}</p>
                    <Table dataSource={rows} columns={columns} />
                  </div>
                }
              />
              <Button
                onClick={() => {
                  setServerError(null);
                  setIsLoading(false);
                  setResponse(null);
                  navigate(`/`, { replace: true }); //navigate to no taskid
                }}
              >
                Retry
              </Button>
            </Col>
          </Row>
        );
      }
      return (
        <Row justify={"center"}>
          <Col span={20}>
            <Alert
              type="error"
              message="Error"
              description={
                <div>
                  <p>{serverError}</p>
                </div>
              }
            />
            <Button
              onClick={() => {
                setServerError(null);
                setIsLoading(false);
                setResponse(null);
                navigate(`/`, { replace: true }); //navigate to no taskid
              }}
            >
              Retry
            </Button>
          </Col>
        </Row>
      );
    }

    if (response) {
      const totalFiles = response.fileNames.length;
      const rankingColumns = getTableColumns(
        response.ranking.headers,
        response.ranking.rows,
        totalFiles
      );
      const canonicalColumns = getTableColumns(
        response.canonicalPairs.headers,
        response.canonicalPairs.rows,
        totalFiles
      );
      const nonCanonicalColumns = getTableColumns(
        response.nonCanonicalPairs.headers,
        response.nonCanonicalPairs.rows,
        totalFiles
      );
      const stackingColumns = getTableColumns(
        response.stackings.headers,
        response.stackings.rows,
        totalFiles
      );
      const rankingRows = getTableRows(response.ranking.rows);
      const canonicalRows = getTableRows(response.canonicalPairs.rows);
      const nonCanonicalRows = getTableRows(response.nonCanonicalPairs.rows);
      const stackingRows = getTableRows(response.stackings.rows);

      const consensusDetails = [
        {
          key: "consensus-2d-structure",
          label: "Secondary structure",
          children: [
            <SvgImg
              key="svg"
              serverAddress={serverAddress}
              taskId={taskIdComplete}
            />,
            <pre key="dotbracket">{response.dotBracket}</pre>,
          ],
        },
        {
          key: "consensus-base-pairs",
          label: "Canonical base pairs",
          children: (
            <>
              <Table dataSource={canonicalRows} columns={canonicalColumns} />
              <DownloadButton
                dataSource={canonicalRows}
                columns={canonicalColumns}
                fileName={`consensus_base_pairs.txt`}
              />
            </>
          ),
        },
        {
          key: "consensus-non-canonical-pairs",
          label: "Non-canonical base pairs",
          children: (
            <>
              <Table
                dataSource={nonCanonicalRows}
                columns={nonCanonicalColumns}
              />
              <DownloadButton
                dataSource={nonCanonicalRows}
                columns={nonCanonicalColumns}
                fileName={`consensus_non_canonical_pairs.txt`}
              />
            </>
          ),
        },
        {
          key: "consensus-stacking-interactions",
          label: "Stacking interactions",
          children: (
            <>
              <Table dataSource={stackingRows} columns={stackingColumns} />
              <DownloadButton
                dataSource={stackingRows}
                columns={stackingColumns}
                fileName={`consensus_stacking_interactions.txt`}
              />
            </>
          ),
        },
      ];
      const perFileDetails = response.fileNames.map((filename, index) => ({
        key: index,
        label: filename,
        children: (
          <FileDetails
            taskId={taskIdComplete}
            serverAddress={serverAddress}
            filename={filename}
            fileCount={totalFiles}
          />
        ),
      }));

      return (
        <Row justify={"center"}>
          <Col span={20}>
            <Card title={"Ranking"} style={{ marginBottom: "24px" }}>
              <Table dataSource={rankingRows} columns={rankingColumns} />
              <DownloadButton
                dataSource={rankingRows}
                columns={rankingColumns}
                fileName={`ranking.txt`}
              />
            </Card>

            <Card
              title={"Consensus structure"}
              style={{ marginBottom: "24px" }}
            >
              <Collapse items={consensusDetails} />
            </Card>

            <Card
              title={"Results for each file"}
              style={{ marginBottom: "24px" }}
            >
              <Tabs items={perFileDetails} tabPosition={"left"} />
            </Card>

            {removalReasons &&
              Object.keys(removalReasons).length > 0 &&
              (() => {
                const [columns, rows] = handleRemovalReasons();
                return (
                  <Card
                    title={"Removed files"}
                    style={{ marginBottom: "24px" }}
                  >
                    <Table dataSource={rows} columns={columns} />
                  </Card>
                );
              })()}
          </Col>
        </Row>
      );
    }

    // Default view with the send button
    return (
      <Row justify={"center"}>
        <Col span={20}>
          <div style={{ marginBottom: "24px", textAlign: "justify" }}>
            {/* <p>RNAtive is a consensus-based RNA structure analysis system that combines multiple structural models to identify 
              reliable base pairs and stacking interactions. Upload your RNA 3D structure models in PDB or mmCIF format, and 
              RNAtive will analyze them using state-of-the-art base pair annotation tools. The system generates a consensus 
              structure by comparing annotations across all models, providing a reliable representation of the RNA's secondary 
              structure and tertiary interactions.</p> */}
            <p>
              RNAtive is a consensus-based RNA structure analysis system
              designed to process multiple structural models to identify
              reliable base pairs and stacking interactions. Tailored for RNA
              structural biologists and bioinformaticians, it aids in validating
              RNA models, improving structural predictions, and studying the
              evolution of RNA structures. The tool accepts 2-100 RNA 3D
              structure models in PDB or mmCIF format, analyzes them using
              state-of-the-art base pair annotation tools, and generates a
              consensus structure by comparing annotations across all input
              models. Additionally, it ranks the input models based on their
              consistency with the derived consensus.
            </p>
          </div>

          <Form labelCol={{ span: 6 }} wrapperCol={{ span: 14 }}>
            <Form.Item
              label={
                <span>
                  Load example{" "}
                  <Tooltip title="Load one of the predefined datasets.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Row gutter={8}>
                <Col>
                  <Button onClick={loadRNAPuzzlesExample}>
                    <Tooltip
                      title={
                        "13 models submitted to the first challenge in RNA-Puzzles contest."
                      }
                    >
                      RNA-Puzzles 1
                    </Tooltip>
                  </Button>
                </Col>
                <Col>
                  <Button onClick={loadDecoyExamples}>
                    <Tooltip
                      title={
                        "Nine decoys of the U2 small nuclear RNA (PDB id: 1A9N)"
                      }
                    >
                      Decoys
                    </Tooltip>
                  </Button>
                </Col>
              </Row>
            </Form.Item>

            <Form.Item
              label={
                <span>
                  Files{" "}
                  <Tooltip title="Upload one or more structural files in PDB or PDBx/mmCIF format for analysis.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Upload
                accept={".pdb,.cif"}
                multiple={true}
                beforeUpload={beforeUpload}
                fileList={fileList}
                onChange={handleFileListChange}
                onDownload={handleDownload}
                showUploadList={{
                  showDownloadIcon: true,
                  downloadIcon: "Download",
                }}
              >
                <Button icon={<UploadOutlined />}>Upload</Button>
              </Upload>
            </Form.Item>

            <Form.Item
              label={
                <span>
                  Molprobity filter{" "}
                  <Tooltip title="When enabled, individual models undergo MolProbity evaluation, receiving ratings of 'good', 'caution', or 'warning' across four key metrics: clashscore, backbone conformation, bonds, and angles. Based on the selected filter, models failing to meet quality standards will be excluded from subsequent evaluation.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Select
                options={molProbityOptions}
                defaultValue={molProbityOptions[0]}
                onChange={setMolProbityFilter}
              />
            </Form.Item>

            <Form.Item
              label={
                <span>
                  Expected 2D structure{" "}
                  <Tooltip title="If desired, input the expected 2D structure using dot-bracket notation. These specified base pairs will be considered essential, and their absence in models will result in lower rankings.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <TextArea
                rows={6}
                variant={"filled"}
                placeholder={"Optional"}
                value={dotBracket}
                onChange={handleDotBracket}
                style={{ fontFamily: "monospace" }}
              />
            </Form.Item>

            <Form.Item
              label={
                <span>
                  Base pair analyzer{" "}
                  <Tooltip title="Select one of the integrated tools for extracting nucleotide interactions.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Select
                options={analyzerOptions}
                defaultValue={analyzerOptions[0]}
                onChange={setAnalyzer}
              />
            </Form.Item>

            <Form.Item
              label={
                <span>
                  Consensus mode{" "}
                  <Tooltip title="Specify which categories of nucleotide interactions should be included when building the consensus secondary structure and comparing models.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Select
                options={consensusOptions}
                defaultValue={consensusOptions[0]}
                onChange={setConsensusMode}
              />
            </Form.Item>

            <Form.Item
              label={
                <span>
                  Fuzzy mode{" "}
                  <Tooltip title="In fuzzy mode, every nucleotide interaction within the specified consensus mode contributes to model ranking calculations based on how frequently it appears across models. When fuzzy mode is disabled, interaction frequency acts as a filtering mechanism, with INF computations only considering interactions that surpass minimum confidence thresholds.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Switch checked={isFuzzy} onChange={setIsFuzzy} />
            </Form.Item>

            {!isFuzzy && (
              <Form.Item
                label={
                  <span style={{ opacity: fileList.length < 2 ? 0.5 : 1 }}>
                    Confidence level{" "}
                    <Tooltip title="The minimum percentage threshold determining whether a nucleotide interaction becomes part of the consensus secondary structure. For instance, setting a 50 threshold requires an interaction to appear in at least half of the analyzed models to be included in the consensus structure.">
                      <QuestionCircleOutlined />
                    </Tooltip>
                  </span>
                }
              >
                <Row gutter={8} style={{ display: "flex" }}>
                  <Col flex={"auto"}>
                    <Slider
                      min={2}
                      max={fileList.length} // Ensure max updates dynamically
                      onChange={handleSliderChange}
                      value={confidenceLevel}
                      disabled={fileList.length < 2} // Gray out if fileList.length < 2
                    />
                  </Col>
                  <Col flex={"none"}>
                    <InputNumber
                      min={2}
                      max={fileList.length}
                      value={confidenceLevel}
                      onChange={handleSliderChange}
                      disabled={fileList.length < 2} // Gray out if fileList.length < 2
                      status=""
                      style={{
                        color: fileList.length < 2 ? "gray" : "inherit",
                      }}
                    />
                  </Col>

                  {fileList.length >= 2 && (
                    <span
                      style={{
                        opacity: fileList.length < 2 ? 0.5 : 1,
                        display: "inline-block",
                        minWidth: "4ch",
                        fontFamily: "monospace",
                      }}
                    >
                      {Math.round((confidenceLevel / fileList.length) * 100)}%
                    </span>
                  )}
                  {/* {Math.round((confidenceLevel / fileList.length) * 100) > 9 &&
                    Math.round((confidenceLevel / fileList.length) * 100) <
                      100 && (
                      <span style={{ opacity: fileList.length < 2 ? 0.5 : 1 }}>
                        {"     "}
                        {Math.round((confidenceLevel / fileList.length) * 100)}%
                      </span>
                    )}
                  {Math.round((confidenceLevel / fileList.length) * 100) <
                    10 && (
                    <span style={{ opacity: fileList.length < 2 ? 0.5 : 1 }}>
                      {"  "}
                      {Math.round((confidenceLevel / fileList.length) * 100)}%
                    </span>
                  )} */}
                </Row>
              </Form.Item>
            )}

            <Form.Item
              label={
                <span>
                  Visualizer{" "}
                  <Tooltip title="Pick the visualization tool for consensus structures. Currently, VARNA is the sole option supporting non-canonical base pair visualization, employing distinct symbols for each Leontis-Westhof classification.">
                    <QuestionCircleOutlined />
                  </Tooltip>
                </span>
              }
            >
              <Select
                options={visualizerOptions}
                defaultValue={visualizerOptions[0]}
                onChange={setVisualizer}
              />
            </Form.Item>

            <Form.Item wrapperCol={{ offset: 6 }}>
              {fileList.length < 2 ? (
                <Tooltip title="Upload at least 2 files">
                  <Button type="primary" disabled={true}>
                    Submit
                  </Button>
                </Tooltip>
              ) : (
                <Button type="primary" onClick={() => handleSendData()}>
                  Submit
                </Button>
              )}
            </Form.Item>
          </Form>
        </Col>
      </Row>
    );
  };
  return <div>{renderContent()}</div>;
}

export default Home;
