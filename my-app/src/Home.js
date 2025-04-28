import React, { useEffect, useState, useCallback } from "react";

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
  Typography,
  message,
} from "antd";
import { getTableColumns, getTableRows } from "./utils/tableUtils";
import {
  QuestionCircleOutlined,
  CloseOutlined,
  UploadOutlined,
} from "@ant-design/icons";

import SvgImg from "./SvgImg";
import FileDetails from "./FileDetails";
import * as configs from "./config";
import DownloadButton from "./DownloadButton";
import "./customTextInput.css";

const { Text } = Typography;
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
    label: "No filter (keep all models).",
  },
  {
    value: "CLASHSCORE",
    label: "Clashscore filter (remove models with poor clashscore).",
  },
  {
    value: "CLASHSCORE_BONDS_ANGLES",
    label:
      "Strict filter (remove models with poor clashscore, bond, or angle geometry).",
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
  const resetFileList = (file) => {
    //resets both file list and dotBracket textview
    setDotBracket("");
    setFileList([]);
  };
  const resetDotBracket = (file) => {
    //resets the dotBracket textview
    setDotBracket("");
  };
  // Ensure confidenceLevel is within valid range when fileList updates
  useEffect(() => {
    setConfidenceLevel((prev) => Math.min(prev, fileList.length) || 2);
  }, [fileList.length]);
  const handleSliderChange = (value) => {
    if (value <= fileList.length) {
      setConfidenceLevel(value);
    }
  };
  const beforeUpload = async (file) => {
    file.url = URL.createObjectURL(file);
    file.obj = new File([file], file.name, { type: file.type });

    // Check if file is an archive
    const isArchive =
      file.name.toLowerCase().endsWith(".zip") ||
      file.name.toLowerCase().endsWith(".tar.gz") ||
      file.name.toLowerCase().endsWith(".tgz");

    // Add the file to the list with a processing status
    const fileWithUid = {
      uid: `${Date.now()}_${file.name}`,
      name: file.name,
      status: "uploading",
      percent: 0,
      url: file.url,
      obj: file.obj,
      isArchive: isArchive,
    };

    setFileList((prevFileList) => [...prevFileList, fileWithUid]);

    try {
      // Create a FormData object to send the file
      const formData = new FormData();
      formData.append("file", file);

      // Update the status message for archives
      if (fileWithUid.isArchive) {
        setFileList((prevFileList) =>
          prevFileList.map((f) =>
            f.uid === fileWithUid.uid
              ? {
                  ...f,
                  status: "uploading",
                  percent: 10,
                  name: `${file.name} (Extracting archive...)`,
                }
              : f
          )
        );
      }

      // Call the split endpoint
      const response = await fetch(`${serverAddress}/split`, {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Server responded with status ${response.status}`);
      }

      const result = await response.json();

      // If we got split files back
      if (result.files && result.files.length > 0) {
        // If there's more than one file returned, it means the file was split
        // or it was an archive that was extracted
        if (result.files.length > 1 || fileWithUid.isArchive) {
          // Sort files by model number if they follow the pattern base_model_n.ext
          const sortedFiles = [...result.files].sort((a, b) => {
            const modelNumberA = a.name.match(/_model_(\d+)\./);
            const modelNumberB = b.name.match(/_model_(\d+)\./);

            if (modelNumberA && modelNumberB) {
              return parseInt(modelNumberA[1]) - parseInt(modelNumberB[1]);
            }
            return a.name.localeCompare(b.name); // Fallback to alphabetical sort
          });

          // Create File objects for each split file
          const splitFiles = sortedFiles.map((fileData, index) => {
            const newFile = new File(
              [fileData.content],
              fileData.name || `split_${index}_${file.name}`,
              { type: file.type }
            );
            newFile.url = URL.createObjectURL(newFile);
            newFile.obj = newFile;
            return {
              uid: `${Date.now()}_${index}`,
              name: newFile.name,
              status: "done",
              url: newFile.url,
              obj: newFile,
            };
          });

          // Update the file list with the split files
          setFileList((prevFileList) => {
            // Remove the original file (the one with uploading status)
            const filteredList = prevFileList.filter(
              (f) => f.uid !== fileWithUid.uid
            );
            // Add the split files
            const newList = [...filteredList, ...splitFiles];

            // Log the result for debugging
            if (fileWithUid.isArchive) {
              console.log(
                `Archive ${file.name} extracted into ${splitFiles.length} files`
              );
            } else {
              console.log(
                `File ${file.name} split into ${splitFiles.length} files`
              );
            }

            return newList;
          });

          // Clean up the original file URL
          URL.revokeObjectURL(file.url);

          // Return false to prevent the default antd upload behavior
          return false;
        } else {
          // Only one file returned, update the status of the existing file
          setFileList((prevFileList) =>
            prevFileList.map((f) =>
              f.uid === fileWithUid.uid ? { ...f, status: "done" } : f
            )
          );
        }
      } else {
        // No files returned, update the status of the existing file
        setFileList((prevFileList) =>
          prevFileList.map((f) =>
            f.uid === fileWithUid.uid ? { ...f, status: "done" } : f
          )
        );
      }
    } catch (error) {
      console.error("Error processing file:", error);
      // If there's an error, update the file status to error with appropriate message
      const errorMessage = fileWithUid.isArchive
        ? `Error extracting archive: ${error.message}`
        : `Error splitting file: ${error.message}`;

      setFileList((prevFileList) =>
        prevFileList.map((f) =>
          f.uid === fileWithUid.uid
            ? { ...f, status: "error", error: errorMessage, name: file.name }
            : f
        )
      );
    }

    // Return false to prevent the default antd upload behavior in all cases
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

    // We'll let the beforeUpload function handle the file list updates
    // This is mainly for handling file removals
    const hasRemovals = fileList.length > newFileList.length;
    if (hasRemovals) {
      setFileList(newFileList);
    }
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
          if (response.status === 413) {
            throw new Error(
              "Request too large. Please reduce the size or number of files."
            );
          }
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
      setServerError(error.message);
      setIsLoading(false);
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
      // Fetch main results
      const resultResponse = await fetch(`${serverAddress}/${taskId}/result`, {
        method: "GET",
      });
      if (!resultResponse.ok) {
        throw new Error(
          `Failed to get result. Status: ${resultResponse.status}`
        );
      }
      const resultData = await resultResponse.json();

      // Fetch user request parameters
      const requestResponse = await fetch(`${serverAddress}/${taskId}/request`, {
        method: "GET",
      });
      if (!requestResponse.ok) {
        throw new Error(
          `Failed to get request parameters. Status: ${requestResponse.status}`
        );
      }
      const requestData = await requestResponse.json();

      // Combine results and request parameters
      const combinedData = {
        ...resultData,
        userRequest: requestData, // Add the fetched request data
      };

      console.log("Combined Data:", combinedData);
      console.log("User Request Dot Bracket:", combinedData.userRequest?.dotBracket);

      // Set the response state with combined data
      setResponse(combinedData);
    } catch (error) {
      console.error("Error fetching task data:", error.message);
      setResponse({ error: error.message }); // Set error state
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

  const loadmiRNAExample = async () => {
    try {
      // List of example files
      const exampleFiles = [
        "mir663_0001.pdb",
        "mir663_0002.pdb",
        "mir663_0003.pdb",
        "mir663_0004.pdb",
        "mir663_0005.pdb",
        "mir663_0006.pdb",
        "mir663_0007.pdb",
        "mir663_0008.pdb",
        "mir663_0009.pdb",
        "mir663_0010.pdb",
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
          "CCUUCCGGCGUCCCAGGCGGGGCGCCGCGGGACCGCCCUCGUGUCUGUGGCGGUGGGAUCCCGCGGCCGUGUUUUCCUGGUGGCCCGGCC\n" +
          "....((((..(((((((.((.(((((((((((((((((........).))))).....))))))))).)).))..))))).)).)))).."
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

  // Inside your component:
  const copyToClipboard = useCallback(() => {
    navigator.clipboard
      .writeText(id)
      .then(() => message.success("ID copied to clipboard!"))
      .catch(() => message.error("Failed to copy ID."));
  }, [id]);

  const renderContent = () => {
    if (isLoading) {
      if (id) {
        return (
          <Row
            justify="center"
            style={{
              minHeight: "50vh",
              alignItems: "center",
              flexDirection: "column",
              display: "flex",
              padding: 15,
            }}
          >
            <div style={{ marginBottom: 14, textAlign: "center" }}>
              <Text>
                Your task is being processed:{" "}
                <Text
                  underline
                  style={{ color: "#1890ff", cursor: "pointer" }}
                  onClick={copyToClipboard}
                >
                  {id}
                </Text>
                <br />
                Processing time may vary depending on dataset size.
              </Text>
            </div>
            <div style={{ marginBottom: 24, textAlign: "center" }}></div>
            <Spin
              size="large"
              spinning={true}
              style={{ transform: "scale(1.5)" }} // Increase spinner size
            >
              <div style={{ width: "100vw", height: 1 }} />
            </Spin>
          </Row>
        );
      }
      return (
        <Row justify={"center"}>
          <Spin
            spinning={true}
            style={{ transform: "scale(1.5)" }} // Increase spinner size
            tip={"Sending data to the server"}
            size="large"
          >
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
            <pre
              key="dotbracket"
              style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}
            >
              {response.dotBracket}
            </pre>,
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
      const usersRequestDotBracket = [
        {
          key: "2D structure constraints",
          label: "2D structure constraints",
          children: [
            <pre
              key="dotbracket"
              style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}
            >
              {response.userRequest.dotBracket}
            </pre>,
          ],
        },
        {
          key: "other-options",
          label: "Other options",
          children: (
            <>
              {/* confidenceLevel, analyzer, consensusMode, dotBracket, molProbityFilter, visualizationTool} */}
              <div>
                <b>Model quality filter:</b>{" "}
                {molProbityOptions.find(
                  (option) =>
                    option.value === response.userRequest?.molProbityFilter
                )?.label || "Unknown"}
              </div>
              <div>
                <b>
                  {response.userRequest?.confidenceLevel != null
                    ? `confidence level: ${response.userRequest.confidenceLevel}`
                    : "Conditionally weighted consensus"}
                </b>
              </div>
              <div>
                <b>Base pair analyzer:</b>{" "}
                {analyzerOptions.find(
                  (option) => option.value === response.userRequest?.analyzer
                )?.label || "Unknown"}
              </div>
              <div>
                <b>Consensus structure based on:</b>{" "}
                {consensusOptions.find(
                  (option) =>
                    option.value === response.userRequest?.consensusMode
                )?.label || "Unknown"}
              </div>

              <div>
                <b>2D structure viewer:</b>{" "}
                {visualizerOptions.find(
                  (option) =>
                    option.value === response.userRequest?.visualizationTool
                )?.label || "Unknown"}
              </div>
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
            <Card
              title={"Overview of input parameters and constraints"}
              style={{ marginBottom: "24px" }}
            >
              <Collapse items={usersRequestDotBracket} />
            </Card>
            <Card
              title={"Consensus 2D structure"}
              style={{ marginBottom: "24px" }}
            >
              <Collapse items={consensusDetails} />
            </Card>

            <Card
              title={"Model ranking by similarity to the consensus"}
              style={{ marginBottom: "24px" }}
            >
              <Table dataSource={rankingRows} columns={rankingColumns} />
              <DownloadButton
                dataSource={rankingRows}
                columns={rankingColumns}
                fileName={`ranking.txt`}
              />
            </Card>

            <Card
              title={"Model-specific 2D structure analysis results"}
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
            <p style={{ maxWidth: "600px", margin: "0 auto" }}>
              RNAtive is a consensus-based RNA structure analysis system{" "}
              designed to process multiple structural models sharing the same{" "}
              sequence and to identify reliable base pairs and stacking{" "}
              interactions. It supports model validation, improves structural{" "}
              predictions, and facilitates studies of RNA structure evolution.
              The tool accepts a minimum of two RNA 3D structure models in PDB{" "}
              or mmCIF format (with a total file size limit of 100 MB), and
              analyzes them using state-of-the-art base-pair annotation tools.
              Comparing annotations across all input models, it{" "}
              <b> generates a consensus structure </b>
              that highlights recurrent interactions, which are more likely to
              reflect stable, native-like folds. It then{" "}
              <b>evaluates and ranks the input models</b> based on their
              consistency with the derived consensus.
              {/* RNAtive is a consensus-based RNA structure analysis system
              designed to process multiple structural models to identify
              reliable base pairs and stacking interactions. Tailored for RNA
              structural biologists and bioinformaticians, it aids in validating
              RNA models, improving structural predictions, and studying the
              evolution of RNA structures. The tool accepts a minimum of two RNA
              3D structure models in PDB or mmCIF format, analyzes them using
              state-of-the-art base pair annotation tools, and generates a
              consensus structure by comparing annotations across all input
              models. The combined size of all PDB/mmCIF files cannot exceed
              100MB. Additionally, it ranks the input models based on their
              consistency with the derived consensus. */}
            </p>
          </div>

          <Form labelCol={{ span: 6 }} wrapperCol={{ span: 14 }}>
            <Form.Item
              label={
                <span>
                  <div
                    style={{
                      display: "inline-block",
                      whiteSpace: "normal",
                      maxWidth: "100%",
                      lineHeight: "1.7",
                      paddingBottom: "6px",
                    }}
                  ></div>
                  Example datasets{" "}
                  <Tooltip title="Click the button to load a demo dataset for testing and exploration.">
                    <QuestionCircleOutlined
                      style={{ position: "relative", zIndex: 999 }}
                    />
                  </Tooltip>
                </span>
              }
            >
              <Row gutter={8}>
                <Col>
                  <Button onClick={loadRNAPuzzlesExample}>
                    <Tooltip
                      title={
                        "13 models of the regulatory RNA motif from thymidylate synthase mRNA (3MEI), predicted in PZ01."
                      }
                    >
                      RNA-Puzzles models
                    </Tooltip>
                  </Button>
                </Col>
                <Col>
                  <Button onClick={loadDecoyExamples}>
                    <Tooltip
                      title={
                        "9 decoy models of the U2 small nuclear RNA fragment (1A9N)."
                      }
                    >
                      U2 snRNA decoys
                    </Tooltip>
                  </Button>
                </Col>
                <Col>
                  <Button onClick={loadmiRNAExample}>
                    <Tooltip
                      title={
                        "10 models of miRNA miR-663 from Rfam (RF00957), predicted by RNAComposer based on the CentroidFold-generated 2D structure."
                      }
                    >
                      miRNA mir-663
                    </Tooltip>
                  </Button>
                </Col>
              </Row>
            </Form.Item>
            <Form.Item
              label={
                <span>
                  {" "}
                  <b>NOTE</b>{" "}
                </span>
              }
            >
              <span>
                {" "}
                All uploaded RNA 3D structures must share the{" "}
                <b>exact same nucleotide sequence</b>.
              </span>
            </Form.Item>
            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    RNA 3D models{" "}
                    <Tooltip title="Upload at least two RNA structure files in PDB or mmCIF format. You can also upload .zip, .tar.gz, or .tgz archives. Keep total file size under 100 MB">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Row gutter={8}>
                <Col>
                  <Upload
                    accept={".pdb,.cif,.zip,.tar.gz,.tgz"}
                    multiple={true}
                    beforeUpload={beforeUpload}
                    fileList={fileList}
                    onChange={handleFileListChange}
                    onDownload={handleDownload}
                    showUploadList={{
                      showDownloadIcon: true,
                      downloadIcon: "Download",
                      showRemoveIcon: true,
                    }}
                    // We don't need customRequest anymore as we're handling the file status directly
                  >
                    <Button icon={<UploadOutlined />}>Upload</Button>
                  </Upload>
                  {fileList.length > 0 && (
                    <Button onClick={resetFileList}>
                      <Tooltip title={"Remove all uploaded files."}>
                        Reset
                      </Tooltip>
                    </Button>
                  )}
                </Col>
              </Row>
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Model quality filter{" "}
                    <Tooltip title="Filter input models based on structural quality assessed by MolProbity. Choose ‘No filtering’ to include all models, ‘Clashscore only’ to exclude models with poor clash scores, or ‘Strict’ to accept only models with good scores for clashes, bonds, and angles.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Select
                options={molProbityOptions}
                value={molProbityFilter} // Use value prop for controlled component
                onChange={setMolProbityFilter}
              />
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    2D structure constraints{" "}
                    <Tooltip title="Optionally, provide structural constraints using dot-bracket notation. Use brackets (e.g., '()' or '[]') to enforce base pairs, 'x' to enforce unpaired nucleotides, and '.' for positions without constraints. The consensus will follow these user-defined constraints.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <div style={{ position: "relative" }}>
                <TextArea
                  rows={6}
                  variant="filled"
                  placeholder="Optional"
                  value={dotBracket}
                  onChange={handleDotBracket}
                  style={{ fontFamily: "monospace", paddingRight: "30px" }} // Make space for the "x"
                />
                {dotBracket && (
                  <CloseOutlined
                    onClick={resetDotBracket}
                    style={{
                      position: "absolute",
                      top: 8,
                      right: 8,
                      fontSize: "16px",
                      color: "#000",
                      cursor: "pointer",
                      zIndex: 1000,
                    }}
                  />
                )}
              </div>
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Base pair analyzer{" "}
                    <Tooltip title="Pick a tool to annotate nucleotide interactions from input RNA 3D models.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
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
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Consensus structure based on{" "}
                    <Tooltip title="Choose which interaction types to include when comparing models and constructing the consensus secondary structure: canonical base pairs, stacking interactions, non-canonical pairs, or all.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
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
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Conditionally weighted consensus{" "}
                    {/* Frequency-based scoring{" "} */}
                    <Tooltip title="Switch on to rank models based on how often each interaction appears across the input set. Switch off to consider only high-confidence interactions above the threshold.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Switch checked={isFuzzy} onChange={setIsFuzzy} />
            </Form.Item>

            {!isFuzzy && (
              <Form.Item
                label={
                  <div
                    style={{
                      display: "inline-block",
                      whiteSpace: "normal",
                      maxWidth: "100%",
                      lineHeight: "1.5",
                      paddingBottom: "4px",
                    }}
                  >
                    <span style={{ opacity: fileList.length < 2 ? 0.5 : 1 }}>
                      Confidence level{" "}
                      <Tooltip title="Set the minimum number (percentage) of models in which an interaction must appear to be included in the consensus 2D structure. The corresponding percentage is displayed based on the total number of uploaded models. For example, a threshold of 5 models out of 10, corresponding to 50%, means the interaction is added to the consensus only if it occurs in at least half of the models.">
                        <QuestionCircleOutlined
                          style={{ position: "relative", zIndex: 999 }}
                        />
                      </Tooltip>
                    </span>
                  </div>
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
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    2D structure viewer{" "}
                    <Tooltip title="Choose a viewer to display the consensus 2D structure. Currently, only VARNA supports visualizing non-canonical interactions and annotating their Leontis–Westhof classes with dedicated pictograms.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
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
