import "./App.css";
// Home.js
import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import "./Home.css";
// import { REACT_APP_SERVER_ADDRESS } from "./config.js";
import "./SendingTest.css";
import SvgImg from "./SvgImg";
import FileDetails from "./FileDetails";
import * as configs from "./config";
import { Alert, Button, Card, Col, Collapse, Form, Input, InputNumber, Row, Select, Slider, Spin, Switch, Table, Tabs, Tooltip, Upload } from "antd";
import { UploadOutlined } from "@ant-design/icons";

const { TextArea } = Input;

//rnapuzzles standarized submissions najlepiej brać stamtąd RP 18 <--- example czy cos
//specjalny riquest na serwer że rób przykład

//parametry do wysłania -- jakiś suwak na to
//confidence level -- od 0 do 1, domyślnie 0.5 antdizajn ma c:

//wybór z listy 6-7 anotatorów, jak w RNApdbee (identify basepairs using) C:

//consensus mode: CANONICAL (domyślny), NON_CANONICAL , STACKING, ALL    c:

// Jednak dodajmy do tego jeszcze jeden parametr - narzędzie do wizualizacji, jedno z:   C:
// VARNA (domyślne),RNApuzzler,PseudoViewer,R-Chie

//opcjonalny parametr dot bracket -- string

//jakiś taki checkbox czy dokonać filtrowania mol probity, domyślnie tak. c:

//tabelka z rank, nazwa + punkty
//https://ant.design/components/table <-taka  ogółem wszystko stąd
//tabelka confidance table cannonical
//tabelki dla par kanonicznych niekanonicznch i stackingu --> dopiero jak się zapytam o model dany to dostane dane
//może być svg wizualizacji struktury 2gorzędowej

//sprawdzić czy github mi dodaje windowsowe entery!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

function Home() {
  //getting the id from params
  const { id } = useParams();
  const navigate = useNavigate();

  //sending
  // const [text2, setText2] = useState("");
  // const handleTextChange2 = (newText) => {
  //   setText2(newText); // Update the text state
  // };
  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [removalReasondisp, setRemovalReasondisp] = useState(null);
  const [serverError, setServerError] = useState(null);
  // Example uploaded files state and other required states
  const [taskIdComplete, setTaskIdComplete] = useState(null);

  const serverAddress = configs.default.SERVER_ADDRESS; // REACT_APP_SERVER_ADDRESS; // process.env.REACT_APP_SERVER_ADDRESS; //"http://localhost:8080/api/compute"; // Replace with actual server address
  // const analyzer = "MCANNOTATE"; // Replace with actual analyzer value
  // const visualizationTool = "VARNA"; // Replace with actual tool value
  // const consensusMode = "CANONICAL";
  // const confidenceLevel = "0.5";
  // const molProbityFilter = "GOOD_AND_CAUTION";

  const consensusOptions = [
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
    {
      value: "ALL",
      label: "All interactions",
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
      label: "Disabled (accept all models)",
    },
    {
      value: "GOOD_AND_CAUTION",
      label: "Basic (reject models in warning category)",
    },
    {
      value: "GOOD_ONLY",
      label: "Full (only accept models in good category)",
    },
  ];

  const [fileList, setFileList] = useState([]);
  const [consensusMode, setConsensusMode] = useState(consensusOptions[0].value);
  const [analyzer, setAnalyzer] = useState(analyzerOptions[0].value);
  const [visualizer, setVisualizer] = useState(visualizerOptions[0].value);
  const [molProbityFilter, setMolProbityFilter] = useState(molProbityOptions[0].value);
  const [isFuzzy, setIsFuzzy] = useState(true);
  const [confidenceLevel, setConfidenceLevel] = useState(50);
  const [dotBracket, setDotBracket] = useState(null);

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

  const handleDotBracket = (event) => {
    setDotBracket(event.target.value);
  };

  const handleSendData = async (taskIdArg = "") => {
    const POLL_INTERVAL = 3000; // 3 seconds
    let taskId = taskIdArg || null;
    // console.log("SERVER:", serverAddress);

    try {
      // Step 1: Create and send the payload if taskId is not provided
      if (!taskId) {
        setIsLoading(true);
        setResponse(null);
        setServerError(null);
        setRemovalReasondisp(null);

        // Log file details
        // fileList.forEach(async (fileObj, index) => {
        //   console.log(`File ${index + 1}:`);
        //   console.log("ID:", fileObj.id);
        //   console.log("Name:", fileObj.file.name);
        //   console.log("Size:", fileObj.file.size, "bytes");
        //   console.log("Type:", fileObj.file.type);
        //   console.log("Text: ", await fileObj.file.text());
        // });

        // Prepare the files data
        const files = await Promise.all(
          fileList.map(async (file) => ({
            name: file.obj.name,
            content: await file.obj.text(), // Reads file content as text
          })),
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
          payload.confidenceLevel = confidenceLevel / 100.0;
        }
        payload.molProbityFilter = molProbityFilter;
        if (dotBracket) {
          payload.dotBracket = dotBracket;
        }
        // console.log("PAYLOAD:", payload);

        //   payload = {
        //     "files": [
        //         {
        //             "name": f.name,
        //             "content": f.read_text(),
        //         }
        //         for f in files
        //     ],
        //     "analyzer": args.analyzer,
        //     "visualizationTool": args.visualization,
        //     "consensusMode": args.consensus_mode,
        //     "confidenceLevel": args.confidence,
        //     "molProbityFilter": args.molprobity_filter,
        // }

        // if dot_bracket:
        //     payload["dotBracket"] = dot_bracket

        // [MCANNOTATE, BARNABA, RNAVIEW, FR3D, BPNET, RNAPOLIS]
        // switch (payload.analyzer) {
        //   case "MC-Annotate":
        //     payload.analyzer = "MCANNOTATE";
        //     break;
        //   case "BARNABA":
        //     payload.analyzer = "BARNABA";
        //     break;
        //   case "RNAview":
        //     payload.analyzer = "RNAVIEW";
        //     break;
        //   case "FR3D":
        //     payload.analyzer = "FR3D";
        //     break;
        //   case "BPnet":
        //     payload.analyzer = "BPNET";
        //     break;
        //   case "RNApolis":
        //     payload.analyzer = "RNAPOLIS";
        //     break;
        // }

        // [ALL, STACKING, CANONICAL, NON_CANONICAL]]
        //"Canonical", "Non-canonical", "Stacking", "All"
        // switch (payload.consensusMode) {
        //   case "MC-All":
        //     payload.consensusMode = "ALL";
        //     break;
        //   case "Stacking":
        //     payload.consensusMode = "STACKING";
        //     break;
        //   case "Canonical":
        //     payload.consensusMode = "CANONICAL";
        //     break;
        //   case "Non-canonical":
        //     payload.consensusMode = "NON_CANONICAL";
        //     break;
        // }
        //[PSEUDOVIEWER, VARNA, RCHIE, RNAPUZZLER]]
        //"VARNA", "RNApuzzler", "PseudoViewer", "R-Chie"]
        // switch (payload.visualizationTool) {
        //   case "VARNA":
        //     payload.visualizationTool = "VARNA";
        //     break;
        //   case "RNApuzzler":
        //     payload.visualizationTool = "RNAPUZZLER";
        //     break;
        //   case "PseudoViewer":
        //     payload.visualizationTool = "PSEUDOVIEWER";
        //     break;
        //   case "R-Chie":
        //     payload.visualizationTool = "RCHIE";
        //     break;
        // }
        // console.log("TEST PAYLOAD:", payload.molProbityFilter);
        // Send the payload
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
      } else {
        //someone might copy the entire line and in such case it is mitigated
        if (taskId.includes("Your unique code: ")) {
          taskId = taskId.replace("Your unique code: ", "");
        }
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

  const formatRemovalReasons = (removalReasons) => {
    const formattedReasons = Object.entries(removalReasons)
      .map(([fileName, reasons]) => {
        const reasonsList = reasons.map((reason) => `- ${reason}`).join("\n");
        return `${fileName}\n${reasonsList}`;
      })
      .join("\n\n");
    return formattedReasons;
  };

  const pollTaskStatus = async (taskId, pollInterval, setResponse) => {
    while (true) {
      const statusResponse = await fetch(`${serverAddress}/${taskId}/status`, {
        method: "GET",
      });

      if (!statusResponse.ok) {
        throw new Error(`Failed to get status. Status: ${statusResponse.status}`);
      }

      const statusData = await statusResponse.json();
      const { status, message, removalReasons } = statusData;

      if (status === "FAILED") {
        console.error("FAILED:", message);
        console.error("FAILED RESASONS:", removalReasons);
        console.error("Task failed:", message);
        const reasons = formatRemovalReasons(removalReasons);
        setRemovalReasondisp(reasons);
        setResponse({
          error: message || "Task failed with no additional message.",
        });
        setServerError(message);
        setIsLoading(false);

        return;
      }

      if (status === "COMPLETED") {
        const reasons = formatRemovalReasons(removalReasons);
        setRemovalReasondisp(reasons);
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
        throw new Error(`Failed to get result. Status: ${resultResponse.status}`);
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

  //koniec sendingu
  // const [text, setText] = useState("");

  // const handleTextChange = (newText) => {
  //   setText(newText); // Update the text state
  // };

  // const [isChecked, setIsChecked] = useState(false);

  // const handleCheckChange = (checked) => {
  //   setIsChecked(checked);
  // };
  // const [sliderValue, setSliderValue] = useState(0.5);

  // const handleSliderChange = (value) => {
  //   setSliderValue(value);
  // };
  // const [uploadedFiles, setUploadedFiles] = useState([]);

  // const onDrop = (acceptedFiles) => {
  //   setUploadedFiles((prevFiles) => [
  //     ...prevFiles,
  //     ...acceptedFiles.map((file) => ({
  //       id: file.name + Date.now(), // Unique id using name and timestamp
  //       file,
  //     })),
  //   ]);
  // };

  // const removeFile = (fileId) => {
  //   setUploadedFiles((prevFiles) => prevFiles.filter((f) => f.id !== fileId));
  // };

  const loadExampleFiles = async () => {
    try {
      // List of example files
      const exampleFiles = ["1_bujnicki_1_rpr.pdb", "1_bujnicki_2_rpr.pdb", "1_bujnicki_3_rpr.pdb", "1_bujnicki_4_rpr.pdb", "1_bujnicki_5_rpr.pdb", "1_chen_1_rpr.pdb", "1_das_1_rpr.pdb", "1_das_2_rpr.pdb", "1_das_3_rpr.pdb", "1_das_4_rpr.pdb", "1_das_5_rpr.pdb", "1_dokholyan_1_rpr.pdb", "1_major_1_rpr.pdb"];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        }),
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
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  // const previewExampleFiles = () => {
  //   const exampleFiles = ["M6_git.pdb", "M6.pdb"]; //TODO, najlepiej jakby to był zip z plikami przykładowymi, bo przeglądarka się oburza na kilka pobrań naraz
  //
  //   exampleFiles.forEach(async (fileName) => {
  //     try {
  //       const response = await fetch(`/examples/${fileName}`);
  //       const blob = await response.blob();
  //       const url = URL.createObjectURL(blob);
  //       const link = document.createElement("a");
  //       link.href = url;
  //       link.download = fileName;
  //       link.click();
  //       URL.revokeObjectURL(url);
  //     } catch (error) {
  //       console.error(`Error downloading ${fileName}:`, error);
  //     }
  //   });
  // };

  // const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop });

  // const consensuses = ["Canonical", "Non-canonical", "Stacking", "All"]; //CANONICAL (domyślny), NON_CANONICAL , STACKING, ALL

  // const [selectedConsensus, setSelectedOption] = useState(consensuses[0]);

  // const handleSelect = (value) => {
  //   setSelectedOption(value); // Update the selected option
  // };

  // const options = ['Option 1', 'Option 2', 'Option 3', 'Option 4'];

  // const [selectedConsensus, setSelectedOption] = useState(consensuses[0]); // Initial selected option

  // const handleSelect = (option) => {
  //   setSelectedOption(option);
  // };

  // const annotators = ["MC-Annotate", "BARNABA", "RNAview", "FR3D", "BPnet", "RNApolis"]; //CANONICAL (domyślny), NON_CANONICAL , STACKING, ALL
  // const [selectedAnnotator, setSelectedOption2] = useState(annotators[0]); // Initial selected option

  // const handleSelect2 = (option2) => {
  //   setSelectedOption2(option2);
  // };

  // const visulalisators = ["VARNA", "RNApuzzler", "PseudoViewer", "R-Chie"];
  // const [selectedVisualisator, setSelectedOption3] = useState(visulalisators[0]); // Initial selected option

  // const handleSelect3 = (option2) => {
  //   setSelectedOption3(option2);
  // };

  // const molprobity = ["GOOD_AND_CAUTION", "GOOD_ONLY", "ALL"];
  // const [selectedMolprobity, setSelectedOption4] = useState(molprobity[0]); // Initial selected option

  // const handleSelect4 = (option2) => {
  //   setSelectedOption4(option2);
  // };

  useEffect(() => {
    if (id) {
      handleSendData(id);
      // Perform actions with the ID if necessary (e.g., fetch data based on the ID)
    }
  }, [id]);

  const formatBracketTxt = (text) => {
    // Split the text by "\n" and return an array of JSX elements
    return text.split("\n").map((line, index) => (
      <React.Fragment key={index}>
        {line}
        <br />
      </React.Fragment>
    ));
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <Row justify={"center"}>
          <Spin tip={"Waiting for completion of task: " + id} size="large">
            <div style={{ width: "100vw", padding: 24 }} />
          </Spin>
        </Row>
        // <header className="App-header">
        //   <div className="rounded-border">
        //     <div className="spinner-container">
        //       <div className="spinner"></div>
        //
        //       {id ? (
        //         <div>
        //           <p>Waiting for completion...</p>
        //           <p>Your task ID: {id}</p>
        //         </div>
        //       ) : (
        //         <p>Sending data...</p>
        //       )}
        //     </div>
        //   </div>
        // </header>
      );
    }

    if (serverError) {
      return (
        <Row justify={"center"}>
          <Col span={20}>
            {removalReasondisp != null ? <Alert type="error" message="Error" description={<div style={{ whiteSpace: "pre-wrap" }}>{serverError + ".\n\n" + removalReasondisp} </div>} /> : <Alert type="error" message="Error" description={serverError} />}
            <Button
              onClick={() => {
                setServerError(null);
                setIsLoading(false);
                setResponse(null);
                navigate(`/`, { replace: true }); //navigate to no taskid
              }}>
              Retry
            </Button>
          </Col>
        </Row>
        // <header className="App-header">
        //   <div className="rounded-border">
        //     <div className="error-container">
        //       <h2>Error</h2>
        //       <p>{serverError}</p>
        //       {removalReasondisp != null ? <pre class="small-txt">{removalReasondisp}</pre> : <p></p>}
        //       <button
        //         className="reset-button"
        //         onClick={() => {
        //           setServerError(null);
        //           setIsLoading(false);
        //           setResponse(null);
        //           navigate(`/`, { replace: true }); //navigate to no taskid
        //         }}>
        //         Retry
        //       </button>
        //     </div>
        //   </div>
        // </header>
      );
    }

    if (response) {
      const rankingColumns = getTableColumns(response.ranking.headers);
      const canonicalColumns = getTableColumns(response.canonicalPairs.headers);
      const nonCanonicalColumns = getTableColumns(response.nonCanonicalPairs.headers);
      const stackingColumns = getTableColumns(response.stackings.headers);
      const rankingRows = getTableRows(response.ranking.rows);
      const canonicalRows = getTableRows(response.canonicalPairs.rows);
      const nonCanonicalRows = getTableRows(response.nonCanonicalPairs.rows);
      const stackingRows = getTableRows(response.stackings.rows);

      const consensusDetails = [
        {
          key: "consensus-2d-structure",
          label: "Secondary structure",
          children: [
            <SvgImg key="svg" serverAddress={serverAddress} taskId={taskIdComplete} />,
            <pre key="dotbracket">{response.dotBracket}</pre>
          ],
        },
        {
          key: "consensus-base-pairs",
          label: "Canonical base pairs",
          children: <Table dataSource={canonicalRows} columns={canonicalColumns} />,
        },
        {
          key: "consensus-non-canonical-pairs",
          label: "Non-canonical base pairs",
          children: <Table dataSource={nonCanonicalRows} columns={nonCanonicalColumns} />,
        },
        {
          key: "consensus-stacking-interactions",
          label: "Stacking interactions",
          children: <Table dataSource={stackingRows} columns={stackingColumns} />,
        },
      ];
      const perFileDetails = response.fileNames.map((filename, index) => ({
        key: index,
        label: filename,
        children: <FileDetails taskId={taskIdComplete} serverAddress={serverAddress} filename={filename} />,
      }));

      return (
        <Row justify={"center"}>
          <Col span={20}>
            <Card title={"Ranking"}>
              <Table dataSource={rankingRows} columns={rankingColumns} />
            </Card>

            <Card title={"Consensus details"}>
              <Collapse items={consensusDetails} />
            </Card>

            <Card title={"Results for each file"}>
              <Tabs items={perFileDetails} tabPosition={"left"} />
            </Card>
          </Col>
        </Row>
        // <header className="App-header">
        //   <div className="rounded-border">
        //     <div className="response-container">
        //       <div className="center-items">
        //         <h2>Server Response</h2>
        //       </div>
        //       <div className="center-items-normal-txt">
        //         <p>Your unique code: {taskIdComplete}</p>
        //       </div>
        //       <div className="center-items-normal-txt">
        //         <p>Visualization:</p>
        //       </div>
        //
        //       <div className="center-items">
        //         <SvgImg serverAddress={serverAddress} taskId={taskIdComplete} />
        //       </div>
        //       <div className="center-items-normal-txt">
        //         <p>Canonical pairs:</p>
        //       </div>
        //       <ResultTable ranking={response.canonicalPairs} />
        //       <div className="center-items-normal-txt">
        //         <p>Non-canonical pairs:</p>
        //       </div>
        //       <ResultTable ranking={response.nonCanonicalPairs} />
        //       <div className="center-items-normal-txt">
        //         <p>Stackings:</p>
        //       </div>
        //       <ResultTable ranking={response.stackings} />
        //       <div className="center-items-normal-txt">
        //         <p>Ranking:</p>
        //       </div>
        //       <ResultTable ranking={response.ranking} />
        //
        //       <div className="center-items-normal-txt">
        //         <p>Dot bracket:</p>
        //       </div>
        //       <p className="small-txt-bracket">{formatBracketTxt(response.dotBracket)}</p>
        //
        //       <div className="center-items-normal-txt">
        //         <p>Results for each file:</p>
        //       </div>
        //       {response.fileNames.map((filename, index) => (
        //         <FileDetails key={index} taskId={taskIdComplete} serverAddress={serverAddress} filename={filename} />
        //       ))}
        //
        //       {removalReasondisp != null || removalReasondisp != "" ? (
        //         <div className="small-txt">
        //           <p>Files removed by MolProbity:</p>
        //           <pre class="small-txt">{removalReasondisp}</pre>
        //         </div>
        //       ) : (
        //         <p></p>
        //       )}
        //
        //       {/* {response.filenames.map((filename, index) => (
        //     <FileDetails
        //       key={index}
        //       taskId={taskIdComplete}
        //       serverAddress={serverAddress}
        //       filename={filename}
        //     />
        //   ))} */}
        //       {/* <FileDetails
        //     key={0}
        //     taskId={taskIdComplete}
        //     serverAddress={serverAddress}
        //     filename={"A2.pdb"}
        //   /> */}
        //       {/* <pre>{JSON.stringify(response, null, 2)}</pre> */}
        //       <button
        //         className="reset-button"
        //         onClick={() => {
        //           navigate(`/`, { replace: true }); //navigate to no taskid
        //           setResponse(null);
        //         }}>
        //         Reset
        //       </button>
        //     </div>
        //   </div>
        // </header>
      );
    }

    // Default view with the send button
    return (
      <Row justify={"center"}>
        <Col span={20}>
          <Form labelCol={{ span: 6 }} wrapperCol={{ span: 14 }}>
            <Form.Item label="Files">
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
                }}>
                <Button icon={<UploadOutlined />}>Upload</Button>
              </Upload>
            </Form.Item>

            <Form.Item label="Consensus mode">
              <Select options={consensusOptions} defaultValue={consensusOptions[0]} onChange={setConsensusMode} />
            </Form.Item>

            <Form.Item label="Base pair analyzer">
              <Select options={analyzerOptions} defaultValue={analyzerOptions[0]} onChange={setAnalyzer} />
            </Form.Item>

            <Form.Item label="Visualizer">
              <Select options={visualizerOptions} defaultValue={visualizerOptions[0]} onChange={setVisualizer} />
            </Form.Item>

            <Form.Item label="MolProbity filter">
              <Select options={molProbityOptions} defaultValue={molProbityOptions[0]} onChange={setMolProbityFilter} />
            </Form.Item>

            <Form.Item label="Fuzzy mode">
              <Switch checked={isFuzzy} onChange={setIsFuzzy} />
            </Form.Item>

            {!isFuzzy && (
              <Form.Item label="Confidence level">
                <Row gutter={8} style={{ display: "flex" }}>
                  <Col flex={"auto"}>
                    <Slider min={1} max={100} onChange={setConfidenceLevel} value={typeof confidenceLevel === "number" ? confidenceLevel : 50} />
                  </Col>
                  <Col flex={"none"}>
                    <InputNumber min={1} max={100} value={confidenceLevel} onChange={setConfidenceLevel} />
                  </Col>
                </Row>
              </Form.Item>
            )}

            <Form.Item label="Dot-bracket">
              <TextArea rows={4} variant={"filled"} placeholder={"Optional"} value={dotBracket} onChange={handleDotBracket} />
            </Form.Item>

            <Form.Item wrapperCol={{ offset: 6 }}>
              <Row gutter={8}>
                <Col>
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
                </Col>
                <Col>
                  <Button onClick={loadExampleFiles}>Load example</Button>
                </Col>
              </Row>
            </Form.Item>
          </Form>
        </Col>
      </Row>
      // <div>
      //   <header className="App-header">
      //     <div className="rounded-border">
      //       <button className="send-button" onClick={loadExampleFiles}>
      //         Load Example
      //       </button>
      //       <button className="send-button" onClick={previewExampleFiles}>
      //         Preview Example Files
      //       </button>
      //       {/* <p
      //       style={{
      //         fontSize:
      //           "25px",
      //         display: "flex",
      //         alignItems: "center",
      //         justifyContent: "center",
      //       }}
      //     >
      //       <b>Drop pdb files here</b>
      //     </p> */}
      //       <div className="home-container">
      //         <div className="dropzone-container" {...getRootProps()}>
      //           <input {...getInputProps()} />
      //           {isDragActive ? <p>Drop the files here ...</p> : <p>Drag & drop files here, or click to select files</p>}
      //         </div>
      //
      //         <div className="file-list">
      //           {uploadedFiles.map((fileWrapper) => (
      //             <div key={fileWrapper.id} className="file-item">
      //               <span
      //                 style={{
      //                   fontSize: "18px" /* This font size is set using a 'string value' */,
      //                   display: "flex",
      //                   alignItems: "center",
      //                   justifyContent: "center",
      //                 }}>
      //                 {fileWrapper.file.name}
      //               </span>
      //               <button className="delete-button" onClick={() => removeFile(fileWrapper.id)}>
      //                 X
      //               </button>
      //             </div>
      //           ))}
      //         </div>
      //       </div>
      //
      //       <div>
      //         <div className="dropdown-text-container">
      //           <p className="dropdown-text">
      //             <b>Pick consensus mode</b>
      //           </p>
      //           <Dropdown options={consensuses} value={selectedConsensus} onSelect={handleSelect} />
      //           <p className="dropdown-text">
      //             <b>Pick annotator</b>
      //           </p>
      //           <Dropdown options={annotators} value={selectedAnnotator} onSelect={handleSelect2} />
      //         </div>
      //
      //         {/* <p>Currently selected option: {selectedOption}</p> */}
      //         {/* <div className="dropdown-text-container">
      //         <p className="dropdown-text">
      //           <b>Pick annotator</b>
      //         </p>
      //         <Dropdown
      //           options={annotators}
      //           value={selectedAnnotator}
      //           onSelect={handleSelect2}
      //         />
      //       </div> */}
      //         <div className="dropdown-text-container">
      //           <p className="dropdown-text">
      //             <b>Pick visulalisator</b>
      //           </p>
      //           <Dropdown options={visulalisators} value={selectedVisualisator} onSelect={handleSelect3} />
      //           <p className="dropdown-text">
      //             <b>Filter with mol probity?</b>
      //           </p>
      //           <Dropdown options={molprobity} value={selectedMolprobity} onSelect={handleSelect4} />
      //         </div>
      //
      //         {/* <div className="dropdown-text-container">
      //         <p className="dropdown-text">
      //           <b>Filter with mol probity?</b>
      //         </p>
      //         <Dropdown
      //           options={molprobity}
      //           value={selectedMolprobity}
      //           onSelect={handleSelect4}
      //         />
      //       </div> */}
      //         <div className="dropdown-text-container">
      //           <p className="dropdown-text">
      //             <b>Pick confidence level</b>
      //           </p>
      //           <Slider_ value={sliderValue} onValueChange={handleSliderChange} />
      //           <p className="dropdown-text">{sliderValue === 0 ? "Fuzzy mode: on" : "Fuzzy mode: off"}</p>
      //         </div>
      //
      //         {/* <p>Current Slider_ Value: {sliderValue.toFixed(2)}</p> */}
      //         {/* <p>Currently selected option: {selectedOption}</p> */}
      //         {/* <div className="dropdown-text-container">
      //         <p className="dropdown-text">
      //           <b>Filter with mol probity? </b>
      //         </p>
      //         <Checkbox
      //           checked={isChecked}
      //           onCheckChange={handleCheckChange}
      //         />
      //         <p
      //           style={{
      //             fontSize:
      //               "18px" ,
      //           }}
      //         >
      //           {" "}
      //           {isChecked ? "Yes" : "No"}
      //         </p>
      //       </div> */}
      //
      //         <div className="dropdown-text-container">
      //           <p className="dropdown-text">
      //             <b>Dot bracket parameter: </b>
      //           </p>
      //           <TextInputWide value={text} onTextChange={handleTextChange} />
      //         </div>
      //       </div>
      //       <div style={{ marginBottom: "60px" }}></div>
      //       <div className="center-items">
      //         <button
      //           className="send-button"
      //           onClick={
      //             () =>
      //               handleSendData([
      //                 selectedAnnotator,
      //                 selectedVisualisator,
      //                 selectedConsensus,
      //                 sliderValue.toFixed(2),
      //                 // isChecked,
      //                 selectedMolprobity,
      //                 //"GOOD_AND_CAUTION",
      //                 text,
      //               ])
      //             // handleSendData([
      //             //   "BPNET",
      //             //   "VARNA",
      //             //   "CANONICAL",
      //             //   0.5,
      //             //   // isChecked,
      //             //   "GOOD_AND_CAUTION",
      //             //   "",
      //             // ])
      //           }>
      //           Send Data
      //         </button>
      //       </div>
      //       <div className="center-items">
      //         <p
      //           style={{
      //             fontSize: "18px" /* This font size is set using a 'string value' */,
      //           }}>
      //           or get already calculated results:
      //         </p>
      //       </div>
      //       <div className="center-items">
      //         <TextInput value={text2} onTextChange={handleTextChange2} />
      //       </div>
      //       <div className="center-items">
      //         <button className="send-button" onClick={() => handleSendData([], text2)}>
      //           Get results
      //         </button>
      //       </div>
      //     </div>
      //   </header>
      // </div>
    );
  };
  return <div>{renderContent()}</div>;
  // return (
  //   <div>
  //     <header className="App-header">
  //       <div class="rounded-border">
  //         <p
  //           style={{
  //             fontSize:
  //               "25px" /* This font size is set using a 'string value' */,
  //             display: "flex",
  //             alignItems: "center",
  //             justifyContent: "center",
  //           }}
  //         >
  //           <b>Drop pdb files here</b>
  //         </p>
  //         <div className="home-container">
  //           <div className="dropzone-container" {...getRootProps()}>
  //             <input {...getInputProps()} />
  //             {isDragActive ? (
  //               <p>Drop the files here ...</p>
  //             ) : (
  //               <p>Drag & drop files here, or click to select files</p>
  //             )}
  //           </div>

  //           <div className="file-list">
  //             {uploadedFiles.map((fileWrapper) => (
  //               <div key={fileWrapper.id} className="file-item">
  //                 <span
  //                   style={{
  //                     fontSize:
  //                       "18px" /* This font size is set using a 'string value' */,
  //                     display: "flex",
  //                     alignItems: "center",
  //                     justifyContent: "center",
  //                   }}
  //                 >
  //                   {fileWrapper.file.name}
  //                 </span>
  //                 <button
  //                   className="delete-button"
  //                   onClick={() => removeFile(fileWrapper.id)}
  //                 >
  //                   X
  //                 </button>
  //               </div>
  //             ))}
  //           </div>
  //         </div>

  //         <div>
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick consensus mode</b>
  //             </p>
  //             <Dropdown
  //               options={consensuses}
  //               value={selectedConsensus}
  //               onSelect={handleSelect}
  //             />
  //           </div>

  //           {/* <p>Currently selected option: {selectedOption}</p> */}
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick annotator</b>
  //             </p>
  //             <Dropdown
  //               options={annotators}
  //               value={selectedAnnotator}
  //               onSelect={handleSelect2}
  //             />
  //           </div>
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick visulalisator</b>
  //             </p>
  //             <Dropdown
  //               options={visulalisators}
  //               value={selectedVisualisator}
  //               onSelect={handleSelect3}
  //             />
  //           </div>
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick confidence level</b>
  //             </p>
  //             <Slider_ value={sliderValue} onValueChange={handleSliderChange} />
  //           </div>

  //           {/* <p>Current Slider_ Value: {sliderValue.toFixed(2)}</p> */}
  //           {/* <p>Currently selected option: {selectedOption}</p> */}
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Filter with mol probity? </b>
  //             </p>
  //             <Checkbox checked={isChecked} onCheckChange={handleCheckChange} />
  //             <p
  //               style={{
  //                 fontSize:
  //                   "18px" /* This font size is set using a 'string value' */,
  //               }}
  //             >
  //               {" "}
  //               {isChecked ? "Yes" : "No"}
  //             </p>
  //           </div>

  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Dot bracket parameter: </b>
  //             </p>
  //             <TextInput value={text} onTextChange={handleTextChange} />
  //           </div>
  //         </div>
  //       </div>
  //     </header>
  //   </div>
  // );
}

export default Home;
